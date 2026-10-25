package immersivemetallurgy.mixin;

import immersivemetallurgy.alloy.TolerantAlloyMatcher;
import immersivemetallurgy.component.MeltComposition;

import net.dries007.tfc.common.recipes.AlloyRecipe;
import net.dries007.tfc.util.FluidAlloy;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AlloyRecipe.class)
public class AlloyRecipeGetMixin {
    /**
     * TFC only ever asks this for a melt whose proportions are exact, so a null here is the moment the
     * composition would otherwise be thrown away. Replacing just this answer keeps every on-ratio melt
     * on TFC's own code path, and runs once per content change rather than once per rendered frame.
     */
    @Inject(method = "get(Lnet/minecraft/world/item/crafting/RecipeManager;Lnet/dries007/tfc/util/FluidAlloy;)Lnet/dries007/tfc/common/recipes/AlloyRecipe;",
            at = @At("TAIL"), cancellable = true)
    private static void immersiveMetallurgy$tolerateOffRatio(RecipeManager recipes, FluidAlloy alloy, CallbackInfoReturnable<AlloyRecipe> cir) {
        if (cir.getReturnValue() != null) {
            return;
        }
        TolerantAlloyMatcher.Match match = TolerantAlloyMatcher.match(recipes, alloy);
        if (match != null) {
            // AlloyRecipe has one return value and it is the fluid, so the defect travels through the container.
            ((MeltComposition) (Object) alloy).immersiveMetallurgySetDefect(match.defect());
            cir.setReturnValue(match.recipe());
        }
    }
}
