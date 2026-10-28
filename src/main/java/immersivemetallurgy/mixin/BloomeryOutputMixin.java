package immersivemetallurgy.mixin;

import immersivemetallurgy.component.MeltCarries;

import net.dries007.tfc.common.blockentities.BloomeryBlockEntity;
import net.dries007.tfc.common.recipes.BloomeryRecipe;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A bloom is not clean metal: real wrought iron carries a percent or two of slag as fibrous stringers, which is
 * why it had to be shingled and welded on the anvil to get the dirt out. TFC builds the bloom as a fresh stack
 * out of nothing but the recipe, so a charge of dirty pig iron would come out as a clean bloom and the whole
 * iron and steel line would launder itself at this one step — the anvil already carries a defect onward, and
 * so this only has to hand the bloom what the charge brought.
 */
@Mixin(BloomeryBlockEntity.class)
public class BloomeryOutputMixin {
    @Redirect(method = "completeRecipe()V",
            at = @At(value = "INVOKE", target = "Lnet/dries007/tfc/common/recipes/BloomeryRecipe;assembleOutput()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack immersiveMetallurgy$carryTheFlaw(BloomeryRecipe recipe) {
        ItemStack bloom = recipe.assembleOutput();
        MeltCarries.mergeWorstInto(((BloomeryBlockEntity) (Object) this).getInputStacks(), bloom);
        return bloom;
    }
}
