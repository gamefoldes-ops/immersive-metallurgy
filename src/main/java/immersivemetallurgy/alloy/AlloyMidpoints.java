package immersivemetallurgy.alloy;

import immersivemetallurgy.component.AlloyRecord;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.dries007.tfc.common.recipes.AlloyRecipe;
import net.dries007.tfc.common.recipes.RecipeHelpers;
import net.dries007.tfc.common.recipes.TFCRecipeTypes;
import net.dries007.tfc.util.AlloyRange;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;

/**
 * The proportions a metal is made of when nobody wrote them down: the middle of TFC's own bands.
 *
 * A flawed or junk melt can reach a crucible without an {@link AlloyRecord} — given by command, loaded from a
 * much older save, or carried in a stack that travelled as a block item. It is still that alloy, and the
 * arithmetic needs something to work with, so the middle of the band is assumed. That is what a smith would
 * assume of a lump they cannot test, and it is deliberately the one guess that cannot make the melt worse than
 * it already is: the middle is inside every band at once, so an assumed batch dilutes like a clean one instead
 * of poisoning the bath the way an unknown fluid does.
 */
public final class AlloyMidpoints {
    /** Bands are fractions of one, and the record is kept in whole numbers, so the guess is scaled up first. */
    private static final double SCALE = 1000D;

    private AlloyMidpoints() {
    }

    /** The middle of this metal's recipe, or null when the metal has none because it is a leaf. */
    public static AlloyRecord of(RecipeManager recipes, Fluid metal) {
        for (RecipeHolder<AlloyRecipe> holder : RecipeHelpers.getRecipes(recipes, TFCRecipeTypes.ALLOY)) {
            AlloyRecipe recipe = holder.value();
            if (recipe.result() != metal) {
                continue;
            }
            Object2DoubleMap<Fluid> middle = new Object2DoubleOpenHashMap<>();
            for (AlloyRange range : recipe.contents()) {
                middle.mergeDouble(range.fluid(), (range.min() + range.max()) / 2D * SCALE, Double::sum);
            }
            return middle.isEmpty() ? null : AlloyRecord.fromContent(middle);
        }
        return null;
    }
}
