package com.gamefoldes.immersivemetallurgy.alloy;

import com.gamefoldes.immersivemetallurgy.ImmersiveMetallurgy;
import com.gamefoldes.immersivemetallurgy.recipe.AlloyTolerances;
import com.gamefoldes.immersivemetallurgy.recipe.AlloyTolerances.Rule;
import com.gamefoldes.immersivemetallurgy.registry.IMFluids;
import com.gamefoldes.immersivemetallurgy.registry.IMRecipes;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.dries007.tfc.common.recipes.AlloyRecipe;
import net.dries007.tfc.common.recipes.RecipeHelpers;
import net.dries007.tfc.common.recipes.TFCRecipeTypes;
import net.dries007.tfc.util.AlloyRange;
import net.dries007.tfc.util.FluidAlloy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;

import java.util.HashMap;
import java.util.Map;

/**
 * TFC collapses any melt that misses every alloy band onto {@code Metal.UNKNOWN}, which destroys the
 * information about what was actually in the crucible. This walks the same alloy recipes with bands
 * widened by {@link Rule} and keeps the deviation, so an off-ratio melt still knows what it is.
 */
public final class TolerantAlloyMatcher {
    private static final double EPS = 1.0E-9;

    private TolerantAlloyMatcher() {
    }

    /**
     * @return the best band-violating fit, or null when the melt is not any alloy we can describe,
     *         in which case TFC's own unknown result stands
     */
    public static Match match(RecipeManager recipes, FluidAlloy alloy) {
        Map<Fluid, Rule> rules = new HashMap<>();
        for (RecipeHolder<AlloyTolerances> holder : RecipeHelpers.getRecipes(recipes, IMRecipes.ALLOY_TOLERANCE)) {
            for (Rule rule : holder.value().rules()) {
                rules.put(rule.alloy(), rule);
            }
        }
        if (rules.isEmpty()) {
            return null;
        }

        Object2DoubleMap<Fluid> content = alloy.getContent();
        Candidate best = null;
        for (RecipeHolder<AlloyRecipe> holder : RecipeHelpers.getRecipes(recipes, TFCRecipeTypes.ALLOY)) {
            AlloyRecipe recipe = holder.value();
            Rule rule = rules.get(recipe.result());
            if (rule == null) {
                continue;
            }
            Candidate candidate = analyze(recipe, rule, holder.id(), content);
            if (candidate == null) {
                continue;
            }
            if (best == null || candidate.flaw < best.flaw - EPS
                    || (Math.abs(candidate.flaw - best.flaw) <= EPS && candidate.id.compareTo(best.id) < 0)) {
                best = candidate;
            }
        }
        return best == null ? null : best.toMatch();
    }

    private static Candidate analyze(AlloyRecipe recipe, Rule rule, ResourceLocation id, Object2DoubleMap<Fluid> content) {
        Map<Fluid, AlloyRange> bands = new HashMap<>();
        for (AlloyRange range : recipe.contents()) {
            bands.put(range.fluid(), range);
        }

        Map<Fluid, Double> components = new HashMap<>();
        double metal = 0;
        double slag = 0;
        for (Map.Entry<Fluid, Double> entry : content.entrySet()) {
            Fluid fluid = entry.getKey();
            double amount = entry.getValue();
            if (fluid == recipe.result() || amount <= 0) {
                // TFC likewise ignores metal already alloyed into this recipe's own result
                continue;
            }
            if (fluid == IMFluids.SLAG.fluid()) {
                slag += amount;
                continue;
            }
            if (!bands.containsKey(fluid)) {
                return null;
            }
            components.merge(fluid, amount, Double::sum);
            metal += amount;
        }
        if (metal <= 0) {
            return null;
        }

        double slagShare = slag / (metal + slag);
        if (slagShare > rule.maxSlag()) {
            return null;
        }

        double deficit = 0;
        for (AlloyRange band : bands.values()) {
            double fraction = components.getOrDefault(band.fluid(), 0D) / metal;
            double shortBy = Math.max(0, band.min() - fraction);
            double overBy = Math.max(0, fraction - band.max());
            if (shortBy > rule.tolerance() + EPS || overBy > rule.tolerance() + EPS) {
                return null;
            }
            deficit += shortBy + overBy;
        }

        double flaw = deficit + Math.max(0, slagShare - rule.slagTolerance());
        AlloyRecipe result = flaw <= EPS ? recipe : new AlloyRecipe(recipe.contents(), rule.flawedResult());
        return new Candidate(result, id, flaw, deficit, slagShare, components);
    }

    private record Candidate(AlloyRecipe result, ResourceLocation id, double flaw, double deficit, double slagShare, Map<Fluid, Double> components) {
        private Match toMatch() {
            boolean clean = flaw <= EPS;
            if (!clean) {
                ImmersiveMetallurgy.LOGGER.info("Forgiving melt for {}: deficit={}, slag={}", id, deficit, slagShare);
            }
            return new Match(result, clean, deficit, slagShare, components);
        }
    }

    public record Match(AlloyRecipe recipe, boolean clean, double deficit, double slagShare, Map<Fluid, Double> components) {
    }
}
