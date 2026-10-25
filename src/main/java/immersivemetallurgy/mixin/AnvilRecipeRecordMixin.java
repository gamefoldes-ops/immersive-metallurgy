package immersivemetallurgy.mixin;

import immersivemetallurgy.component.MeltCarries;

import net.dries007.tfc.common.recipes.AnvilRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shaping a part on the anvil throws the input away and returns a fresh stack, so a flawed ingot would
 * otherwise leave a perfectly clean-looking head. The part is the same item id as the clean one on purpose:
 * the defect belongs to the stack.
 */
@Mixin(AnvilRecipe.class)
public class AnvilRecipeRecordMixin {
    @Inject(method = "assemble(Lnet/dries007/tfc/common/recipes/AnvilRecipe$Inventory;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("TAIL"))
    private void immersiveMetallurgy$carryRecord(AnvilRecipe.Inventory inputs, HolderLookup.Provider registries, CallbackInfoReturnable<ItemStack> cir) {
        MeltCarries.copyInto(inputs.getItem(), cir.getReturnValue());
    }
}
