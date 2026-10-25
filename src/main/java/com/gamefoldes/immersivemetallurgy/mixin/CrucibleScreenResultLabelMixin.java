package com.gamefoldes.immersivemetallurgy.mixin;

import com.gamefoldes.immersivemetallurgy.client.IMMetalNames;
import com.gamefoldes.immersivemetallurgy.component.AlloyRecord;
import com.gamefoldes.immersivemetallurgy.component.MeltComposition;
import com.gamefoldes.immersivemetallurgy.registry.IMDataComponents;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.dries007.tfc.client.screen.CrucibleScreen;
import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
import net.dries007.tfc.common.container.BlockEntityContainer;
import net.dries007.tfc.util.FluidAlloy;
import net.dries007.tfc.util.data.FluidHeat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The crucible screen names its contents after the fluid, and TFC only ships molten names, so a cooled
 * crucible still reads "Molten Bronze", and junk or flawed metal says nothing about what is in it. This
 * relabels the result line by temperature, and writes the composition of any fluid whose name hides it as
 * its own list under the content rows.
 */
@Mixin(CrucibleScreen.class)
public class CrucibleScreenResultLabelMixin {
    /** TFC draws its content rows from this offset, 18px apart, three of them at a time. */
    @Unique
    private static final int LIST_TOP = 22;
    @Unique
    private static final int LIST_ROW_HEIGHT = 18;
    @Unique
    private static final int LIST_VISIBLE_ROWS = 3;
    @Unique
    private static final int LINE_HEIGHT = 9;
    @Unique
    private static final int TEXT_COLOUR = 0x555555;

    @Redirect(method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/fluids/FluidType;getDescription()Lnet/minecraft/network/chat/Component;", ordinal = 0))
    private Component immersiveMetallurgy$nameResult(FluidType type) {
        Component molten = type.getDescription();
        FluidStack result = immersiveMetallurgy$crucible().getAlloyResult();
        if (result.isEmpty()) {
            return molten;
        }
        return immersiveMetallurgy$stateAwareName(result.getFluid(), molten);
    }

    @Inject(method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V", at = @At("TAIL"))
    private void immersiveMetallurgy$listCompositions(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        CrucibleBlockEntity crucible = immersiveMetallurgy$crucible();
        FluidAlloy alloy = crucible.getAlloy();
        if (alloy.getAmount() <= 0) {
            return;
        }
        Object2ObjectMap<Fluid, AlloyRecord> compositions = ((MeltComposition) (Object) alloy).immersiveMetallurgyCompositions();
        if (compositions.isEmpty()) {
            return;
        }
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        Font font = Minecraft.getInstance().font;
        int left = screen.getGuiLeft() + 10;
        int top = screen.getGuiTop() + LIST_TOP + Math.min(alloy.getContent().size(), LIST_VISIBLE_ROWS) * LIST_ROW_HEIGHT + 2;
        for (Object2ObjectMap.Entry<Fluid, AlloyRecord> entry : compositions.object2ObjectEntrySet()) {
            if (!alloy.getContent().containsKey(entry.getKey()) || entry.getValue().parts().size() < 2) {
                continue;
            }
            graphics.drawString(font, immersiveMetallurgy$stateAwareName(entry.getKey(), entry.getKey().getFluidType().getDescription())
                    .copy().withStyle(ChatFormatting.UNDERLINE), left, top, TEXT_COLOUR);
            top += LINE_HEIGHT;
            for (AlloyRecord.FluidParts part : entry.getValue().parts()) {
                graphics.drawString(font, immersiveMetallurgy$part(part, entry.getValue().totalParts()), left + 6, top, TEXT_COLOUR);
                top += LINE_HEIGHT;
            }
            top += 2;
        }
    }

    @Unique
    private CrucibleBlockEntity immersiveMetallurgy$crucible() {
        return (CrucibleBlockEntity) ((BlockEntityContainer<?>) ((MenuAccess<?>) (Object) this).getMenu()).getBlockEntity();
    }

    @Unique
    private Component immersiveMetallurgy$stateAwareName(Fluid fluid, Component molten) {
        FluidHeat heat = FluidHeat.get(fluid);
        if (heat == null || immersiveMetallurgy$crucible().getTemperature() >= heat.meltTemperature()) {
            return molten;
        }
        return IMMetalNames.metalName(fluid).orElse(molten);
    }

    @Unique
    private static MutableComponent immersiveMetallurgy$part(AlloyRecord.FluidParts part, int total) {
        return Component.empty()
                .append(IMMetalNames.name(part.fluid()))
                .append(Component.literal(" " + Math.round(100F * part.parts() / total) + "%"));
    }
}
