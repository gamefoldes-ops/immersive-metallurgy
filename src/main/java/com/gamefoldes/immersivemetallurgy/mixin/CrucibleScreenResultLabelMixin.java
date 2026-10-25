package com.gamefoldes.immersivemetallurgy.mixin;

import com.gamefoldes.immersivemetallurgy.client.IMMetalNames;
import com.gamefoldes.immersivemetallurgy.component.AlloyRecord;
import com.gamefoldes.immersivemetallurgy.registry.IMDataComponents;

import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
import net.dries007.tfc.common.container.BlockEntityContainer;
import net.dries007.tfc.client.screen.CrucibleScreen;
import net.dries007.tfc.util.data.FluidHeat;
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
 * crucible still reads "Molten Bronze" and junk or flawed metal says nothing about what is in it. This
 * relabels the result line: below the metal's melt temperature it uses the plain metal name, and a fluid
 * whose identity hides its recipe gets that recipe spelled out next to it.
 */
@Mixin(CrucibleScreen.class)
public class CrucibleScreenResultLabelMixin {
    @Redirect(method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/fluids/FluidType;getDescription()Lnet/minecraft/network/chat/Component;", ordinal = 0))
    private Component immersiveMetallurgy$nameContents(FluidType type) {
        Component molten = type.getDescription();
        CrucibleBlockEntity crucible = immersiveMetallurgy$crucible();
        FluidStack result = crucible.getAlloyResult();
        if (result.isEmpty()) {
            return molten;
        }
        Fluid fluid = result.getFluid();
        Component label = immersiveMetallurgy$stateAwareName(crucible, fluid, molten);

        AlloyRecord record = result.get(IMDataComponents.ALLOY_RECORD.get());
        if (record == null || record.parts().size() < 2 || !IMMetalNames.hidesItsComponents(fluid)) {
            return label;
        }
        return label.copy().append(Component.literal(" (").append(immersiveMetallurgy$breakdown(record)).append(")"));
    }

    @Unique
    private CrucibleBlockEntity immersiveMetallurgy$crucible() {
        return (CrucibleBlockEntity) ((BlockEntityContainer<?>) ((MenuAccess<?>) (Object) this).getMenu()).getBlockEntity();
    }

    @Unique
    private Component immersiveMetallurgy$stateAwareName(CrucibleBlockEntity crucible, Fluid fluid, Component molten) {
        FluidHeat heat = FluidHeat.get(fluid);
        if (heat == null || crucible.getTemperature() >= heat.meltTemperature()) {
            return molten;
        }
        return IMMetalNames.metalName(fluid).orElse(molten);
    }

    @Unique
    private static Component immersiveMetallurgy$breakdown(AlloyRecord record) {
        int total = record.totalParts();
        MutableComponent list = Component.empty();
        for (AlloyRecord.FluidParts part : record.parts()) {
            if (!list.getSiblings().isEmpty()) {
                list.append(", ");
            }
            list.append(IMMetalNames.name(part.fluid()))
                    .append(Component.literal(" " + Math.round(100F * part.parts() / total) + "%"));
        }
        return list;
    }
}
