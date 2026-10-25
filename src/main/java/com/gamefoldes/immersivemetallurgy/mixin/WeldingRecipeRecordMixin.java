package com.gamefoldes.immersivemetallurgy.mixin;

import com.gamefoldes.immersivemetallurgy.component.AlloyRecord;
import com.gamefoldes.immersivemetallurgy.registry.IMDataComponents;

import net.dries007.tfc.common.recipes.WeldingRecipe;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Welding is how double ingots are made, and it builds a fresh stack out of an anvil rule, so the two
 * halves' composition has to be handed to the result or the joined metal forgets what it is.
 */
@Mixin(WeldingRecipe.class)
public class WeldingRecipeRecordMixin {
    @Inject(method = "assemble(Lnet/dries007/tfc/common/recipes/WeldingRecipe$Inventory;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("TAIL"))
    private void immersiveMetallurgy$carryRecord(WeldingRecipe.Inventory inputs, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (result.isEmpty() || result.has(IMDataComponents.ALLOY_RECORD.get())) {
            return;
        }
        AlloyRecord record = inputs.getMain().get(IMDataComponents.ALLOY_RECORD.get());
        if (record == null) {
            record = inputs.getSecondary().get(IMDataComponents.ALLOY_RECORD.get());
        }
        if (record != null && !record.isEmpty()) {
            result.set(IMDataComponents.ALLOY_RECORD.get(), record);
        }
    }
}
