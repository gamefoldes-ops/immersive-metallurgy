package com.gamefoldes.immersivemetallurgy.mixin;

import com.gamefoldes.immersivemetallurgy.component.MeltCarries;

import net.dries007.tfc.common.recipes.WeldingRecipe;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Welding joins two pieces into a fresh stack, so without this the joined metal forgets what it was made of
 * and how badly it was wrong. Double ingots are made here, and so are the tool parts that need two heads.
 */
@Mixin(WeldingRecipe.class)
public class WeldingRecipeRecordMixin {
    @Inject(method = "assemble(Lnet/dries007/tfc/common/recipes/WeldingRecipe$Inventory;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("TAIL"))
    private void immersiveMetallurgy$carryRecord(WeldingRecipe.Inventory inputs, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        MeltCarries.copyInto(inputs.getMain(), result);
        MeltCarries.copyInto(inputs.getSecondary(), result);
    }
}
