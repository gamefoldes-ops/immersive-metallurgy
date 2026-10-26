package immersivemetallurgy.mixin;

import immersivemetallurgy.client.IMMetalNames;
import immersivemetallurgy.component.MeltComposition;
import immersivemetallurgy.registry.IMFluids;

import net.dries007.tfc.client.screen.CrucibleScreen;
import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
import net.dries007.tfc.common.container.BlockEntityContainer;
import net.dries007.tfc.util.FluidAlloy;
import net.dries007.tfc.util.data.FluidHeat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The crucible screen names its contents after the fluid, and TFC only ships molten names, so a cooled
 * crucible still reads "Molten Bronze" in both its headline and its content rows. This relabels both by
 * temperature, marks the slag row once flux has made it liftable so the bowl gesture has a target the
 * player can see, and puts each row's percentage over the denominator the alloy arithmetic actually uses.
 */
@Mixin(CrucibleScreen.class)
public class CrucibleScreenResultLabelMixin {
    /** Which fluid the row being drawn belongs to, remembered from its name pass for the share pass. */
    @Unique
    private Fluid immersiveMetallurgy$row;

    @Redirect(method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/fluids/FluidType;getDescription()Lnet/minecraft/network/chat/Component;", ordinal = 0))
    private Component immersiveMetallurgy$nameResult(FluidType type) {
        FluidStack result = immersiveMetallurgy$crucible().getAlloyResult();
        return result.isEmpty() ? type.getDescription() : immersiveMetallurgy$stateAwareName(result.getFluid(), type.getDescription());
    }

    /**
     * Rows are drawn as plain strings, so only the wording survives, and the name is cut off at 141 px
     * before the colon is added — the marker stays short, and slag keeps its bare name instead of the
     * "Molten" prefix, for that reason.
     */
    @Redirect(method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/fluids/FluidType;getDescription()Lnet/minecraft/network/chat/Component;", ordinal = 1))
    private Component immersiveMetallurgy$nameContentRow(FluidType type) {
        Fluid fluid = immersiveMetallurgy$contentFluid(type);
        this.immersiveMetallurgy$row = fluid;
        if (fluid == null) {
            return type.getDescription();
        }
        MutableComponent name = (fluid == IMFluids.SLAG.fluid()
                ? IMMetalNames.name(fluid)
                : immersiveMetallurgy$stateAwareName(fluid, type.getDescription())).copy();
        if (fluid == IMFluids.SLAG.fluid() && ((MeltComposition) (Object) immersiveMetallurgy$crucible().getAlloy()).immersiveMetallurgyFluxPower() > 0F) {
            name.append(Component.literal(" ")).append(Component.translatable("immersive_metallurgy.screen.skim_ready").withStyle(ChatFormatting.GOLD));
        }
        return name;
    }

    /**
     * TFC divides every row by the whole bath, but the alloy bands are read over metal alone, so a dirty
     * crucible shows a bronze whose copper never reaches 88% and the player cannot steer by the numbers.
     * Slag keeps the share of the bath, because that is the figure the clean tolerance is measured against.
     */
    @Redirect(method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
            at = @At(value = "INVOKE", target = "Lnet/dries007/tfc/util/FluidAlloy;getAmount()I", ordinal = 2))
    private int immersiveMetallurgy$denominator(FluidAlloy alloy) {
        int bath = alloy.getAmount();
        if (this.immersiveMetallurgy$row == null || this.immersiveMetallurgy$row == IMFluids.SLAG.fluid()) {
            return bath;
        }
        return Math.max(1, bath - (int) alloy.getContent().getDouble(IMFluids.SLAG.fluid()));
    }

    /** A source and its flowing variant share a fluid type, so a row is only renamed when one fluid of that type is in the crucible. */
    @Unique
    private Fluid immersiveMetallurgy$contentFluid(FluidType type) {
        Fluid matched = null;
        for (Fluid fluid : immersiveMetallurgy$crucible().getAlloy().getContent().keySet()) {
            if (fluid.getFluidType() != type) {
                continue;
            }
            if (matched != null) {
                return null;
            }
            matched = fluid;
        }
        return matched;
    }

    @Unique
    private CrucibleBlockEntity immersiveMetallurgy$crucible() {
        return (CrucibleBlockEntity) ((BlockEntityContainer<?>) ((MenuAccess<?>) (Object) this).getMenu()).getBlockEntity();
    }

    @Unique
    private Component immersiveMetallurgy$stateAwareName(Fluid fluid, Component molten) {
        FluidHeat heat = FluidHeat.get(fluid);
        if (heat == null || immersiveMetallurgy$crucible().getTemperature() >= heat.meltTemperature()) {
            return IMMetalNames.moltenName(fluid);
        }
        return IMMetalNames.metalName(fluid).orElse(molten);
    }
}
