package com.gamefoldes.immersivemetallurgy.mixin;

import com.gamefoldes.immersivemetallurgy.component.AlloyRecord;
import com.gamefoldes.immersivemetallurgy.registry.IMDataComponents;

import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Melting an item that remembers its components produces a fluid that remembers them too, which is what
 * keeps junk in the crucible reversible.
 */
@Mixin(HeatingRecipe.class)
public class HeatingRecipeRecordMixin {
    @Inject(method = "assembleFluid(Lnet/minecraft/world/item/ItemStack;)Lnet/neoforged/neoforge/fluids/FluidStack;",
            at = @At("TAIL"))
    private void immersiveMetallurgy$carryRecord(ItemStack input, CallbackInfoReturnable<FluidStack> cir) {
        AlloyRecord record = input.get(IMDataComponents.ALLOY_RECORD.get());
        if (record == null || record.isEmpty()) {
            return;
        }
        FluidStack output = cir.getReturnValue();
        if (!output.isEmpty()) {
            output.set(IMDataComponents.ALLOY_RECORD.get(), record);
        }
    }
}
