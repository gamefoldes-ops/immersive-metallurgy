package com.gamefoldes.immersivemetallurgy.mixin;

import com.gamefoldes.immersivemetallurgy.component.MeltCarries;

import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Melting an item that remembers what it was made of produces a fluid that remembers it too, which is what
 * keeps junk in the crucible reversible and lets flawed metal be diluted instead of thrown away.
 */
@Mixin(HeatingRecipe.class)
public class HeatingRecipeRecordMixin {
    @Inject(method = "assembleFluid(Lnet/minecraft/world/item/ItemStack;)Lnet/neoforged/neoforge/fluids/FluidStack;",
            at = @At("TAIL"))
    private void immersiveMetallurgy$carryRecord(ItemStack input, CallbackInfoReturnable<FluidStack> cir) {
        MeltCarries.copyInto(input, cir.getReturnValue());
    }
}
