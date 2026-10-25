package com.gamefoldes.immersivemetallurgy.mixin;

import com.gamefoldes.immersivemetallurgy.client.IMMetalNames;
import com.gamefoldes.immersivemetallurgy.component.AlloyRecord;
import com.gamefoldes.immersivemetallurgy.component.MeltComposition;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
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
import net.minecraft.core.registries.BuiltInRegistries;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The crucible screen names its contents after the fluid, and TFC only ships molten names, so a cooled
 * crucible still reads "Molten Bronze", junk and flawed metal say nothing about what is in them, and a
 * crucible holding copper, tin, bronze and flawed bronze together gives no way to tell how much copper it
 * has in total. This relabels the result by temperature and writes two debug lists below the panel.
 */
@Mixin(CrucibleScreen.class)
public class CrucibleScreenResultLabelMixin {
    @Unique
    private static final int LINE_HEIGHT = 9;
    @Unique
    private static final int HEADER_COLOUR = 0xE0E0E0;
    @Unique
    private static final int PART_COLOUR = 0xA0A0A0;

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
        MeltComposition container = (MeltComposition) (Object) alloy;
        Object2ObjectMap<Fluid, AlloyRecord> compositions = container.immersiveMetallurgyCompositions();
        Object2DoubleMap<Fluid> leaves = container.immersiveMetallurgyLeafContent();
        if (compositions.isEmpty() && leaves.size() < 2) {
            return;
        }
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        Font font = Minecraft.getInstance().font;
        int left = screen.getGuiLeft() + 10;
        int top = screen.getGuiTop() + screen.getYSize() + 3;
        top = immersiveMetallurgy$drawHiddenRecipes(graphics, font, left, top, alloy, compositions);
        immersiveMetallurgy$drawMetalTotals(graphics, font, left, top, alloy, leaves);
    }

    @Unique
    private int immersiveMetallurgy$drawHiddenRecipes(GuiGraphics graphics, Font font, int left, int top,
                                                      FluidAlloy alloy, Object2ObjectMap<Fluid, AlloyRecord> compositions) {
        for (Object2ObjectMap.Entry<Fluid, AlloyRecord> entry : compositions.object2ObjectEntrySet()) {
            if (!alloy.getContent().containsKey(entry.getKey()) || entry.getValue().parts().size() < 2) {
                continue;
            }
            graphics.drawString(font, immersiveMetallurgy$stateAwareName(entry.getKey(), entry.getKey().getFluidType().getDescription())
                    .copy().withStyle(ChatFormatting.UNDERLINE), left, top, HEADER_COLOUR);
            top += LINE_HEIGHT;
            AlloyRecord record = entry.getValue();
            for (AlloyRecord.FluidParts part : record.parts()) {
                graphics.drawString(font, immersiveMetallurgy$line(part.fluid(), 100F * part.parts() / record.totalParts()), left + 6, top, PART_COLOUR);
                top += LINE_HEIGHT;
            }
            top += 2;
        }
        return top;
    }

    /**
     * The rows inside the screen list bronze and flawed bronze as themselves, so the only way to see how
     * much copper is actually in the crucible is to add the hidden recipes up.
     */
    @Unique
    private void immersiveMetallurgy$drawMetalTotals(GuiGraphics graphics, Font font, int left, int top,
                                                     FluidAlloy alloy, Object2DoubleMap<Fluid> leaves) {
        graphics.drawString(font, Component.translatable("immersive_metallurgy.screen.metal_totals")
                .copy().withStyle(ChatFormatting.UNDERLINE), left, top, HEADER_COLOUR);
        top += LINE_HEIGHT;
        List<Object2DoubleMap.Entry<Fluid>> ordered = new ArrayList<>(leaves.object2DoubleEntrySet());
        ordered.sort(Comparator.comparingDouble((Object2DoubleMap.Entry<Fluid> entry) -> -entry.getDoubleValue())
                .thenComparing(entry -> BuiltInRegistries.FLUID.getKey(entry.getKey()).getPath()));
        for (Object2DoubleMap.Entry<Fluid> leaf : ordered) {
            graphics.drawString(font, immersiveMetallurgy$line(leaf.getKey(), 100F * leaf.getDoubleValue() / alloy.getAmount()), left + 6, top, PART_COLOUR);
            top += LINE_HEIGHT;
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
    private static MutableComponent immersiveMetallurgy$line(Fluid fluid, double percent) {
        return Component.empty()
                .append(IMMetalNames.name(fluid))
                .append(Component.literal(" " + Math.round(percent) + "%"));
    }
}
