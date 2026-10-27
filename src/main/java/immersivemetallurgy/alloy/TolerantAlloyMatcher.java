package immersivemetallurgy.alloy;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.component.DefectProfile;
import immersivemetallurgy.component.DefectProfile.Archetype;
import immersivemetallurgy.component.MeltComposition;
import immersivemetallurgy.recipe.AlloyTolerances;
import immersivemetallurgy.recipe.FlawProfiles;
import immersivemetallurgy.recipe.AlloyTolerances.Rule;
import immersivemetallurgy.registry.IMFluids;
import immersivemetallurgy.registry.IMRecipes;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.dries007.tfc.common.recipes.AlloyRecipe;
import net.dries007.tfc.common.recipes.RecipeHelpers;
import net.dries007.tfc.common.recipes.TFCRecipeTypes;
import net.dries007.tfc.util.AlloyRange;
import net.dries007.tfc.util.FluidAlloy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;

import java.util.HashMap;
import java.util.List;
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

        FlawProfiles profiles = null;
        for (RecipeHolder<FlawProfiles> holder : RecipeHelpers.getRecipes(recipes, IMRecipes.FLAW_PROFILE)) {
            profiles = holder.value();
            break;
        }

        Object2DoubleMap<Fluid> content = ((MeltComposition) (Object) alloy).immersiveMetallurgyLeafContent();
        Candidate best = null;
        for (RecipeHolder<AlloyRecipe> holder : RecipeHelpers.getRecipes(recipes, TFCRecipeTypes.ALLOY)) {
            AlloyRecipe recipe = holder.value();
            Rule rule = rules.get(recipe.result());
            if (rule == null) {
                continue;
            }
            Candidate candidate = analyze(recipe, rule, profiles, holder.id(), content);
            if (candidate == null) {
                continue;
            }
            if (best == null || candidate.flaw < best.flaw - EPS
                    || (Math.abs(candidate.flaw - best.flaw) <= EPS && candidate.id.compareTo(best.id) < 0)) {
                best = candidate;
            }
        }
        return best == null ? pureMetalWithGangue(content, rules, profiles) : best.toMatch();
    }

    /**
     * One metal plus its gangue is the commonest melt in the game - copper ore smelts to copper and slag - and
     * TFC has no recipe for a metal mixed with something that is not a metal, so the whole crucible would
     * collapse to unknown. Slag inside the tolerance is still that metal, slag past it is the metal's flawed
     * form, and slag past the ceiling really is not this metal any more.
     */
    private static Match pureMetalWithGangue(Object2DoubleMap<Fluid> content, Map<Fluid, Rule> rules, FlawProfiles profiles) {
        double slag = 0;
        Fluid metal = null;
        double metalAmount = 0;
        for (Object2DoubleMap.Entry<Fluid> entry : content.object2DoubleEntrySet()) {
            if (entry.getDoubleValue() <= EPS) {
                continue;
            }
            if (entry.getKey() == IMFluids.SLAG.fluid()) {
                slag += entry.getDoubleValue();
                continue;
            }
            if (metal != null) {
                return null;
            }
            metal = entry.getKey();
            metalAmount = entry.getDoubleValue();
        }
        if (metal == null || slag <= EPS) {
            return null;
        }
        Rule rule = rules.get(metal);
        if (rule == null) {
            return null;
        }
        double slagShare = slag / (metalAmount + slag);
        if (slagShare > rule.maxSlag()) {
            return null;
        }
        boolean clean = slagShare <= rule.slagTolerance();
        AlloyRecipe recipe = new AlloyRecipe(List.of(new AlloyRange(metal, 1D, 1D)),
                clean ? metal : rule.flawedResult());
        DefectProfile defect = clean || profiles == null
                ? DefectProfile.CLEAN
                : toProfile(profiles, Map.of(), Map.of(), slagSeverity(rule, slagShare), 0F, (float) slagShare);
        return new Match(recipe, clean, 0, slagShare, defect, Map.of(metal, metalAmount));
    }

    private static Candidate analyze(AlloyRecipe recipe, Rule rule, FlawProfiles profiles, ResourceLocation id, Object2DoubleMap<Fluid> content) {
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
        Map<Fluid, Double> excess = new HashMap<>();
        Map<Fluid, Double> shortage = new HashMap<>();
        int outOfBand = 0;
        for (AlloyRange band : bands.values()) {
            double fraction = components.getOrDefault(band.fluid(), 0D) / metal;
            double shortBy = Math.max(0, band.min() - fraction);
            double overBy = Math.max(0, fraction - band.max());
            if (shortBy > rule.tolerance() + EPS || overBy > rule.tolerance() + EPS) {
                return null;
            }
            deficit += shortBy + overBy;
            if (shortBy > EPS || overBy > EPS) {
                outOfBand++;
            }
            // Kept per metal on purpose: too much tin and too much bismuth are both "over band" and neither
            // ruins a tool in the same way, so the aggregate would throw that difference away.
            excess.put(band.fluid(), Math.min(1D, overBy / rule.tolerance()));
            shortage.put(band.fluid(), Math.min(1D, shortBy / rule.tolerance()));
        }

        double flaw = deficit + Math.max(0, slagShare - rule.slagTolerance());
        AlloyRecipe result = flaw <= EPS ? recipe : new AlloyRecipe(recipe.contents(), rule.flawedResult());
        DefectProfile defect = flaw <= EPS || profiles == null
                ? DefectProfile.CLEAN
                : toProfile(profiles, excess, shortage, slagSeverity(rule, slagShare), spread(outOfBand), (float) slagShare);
        return new Candidate(result, id, flaw, deficit, slagShare, defect, components);
    }

    /** How far into the allowed slag window a melt sits, so 0 is clean and 1 is the most slag still pourable. */
    private static float slagSeverity(Rule rule, double slagShare) {
        double window = rule.maxSlag() - rule.slagTolerance();
        if (window <= EPS) {
            return 0;
        }
        return (float) Mth.clamp((slagShare - rule.slagTolerance()) / window, 0D, 1D);
    }

    /** One element off band scores 0, two score half, three or more score 1: a melt wrong in several ways pours badly. */
    private static float spread(int outOfBand) {
        return Mth.clamp((outOfBand - 1) / 2F, 0F, 1F);
    }

    private static DefectProfile toProfile(FlawProfiles profiles, Map<Fluid, Double> excess, Map<Fluid, Double> shortage,
                                           float slagSeverity, float spread, float slagShare) {
        float brittle = profiles.severity(Archetype.BRITTLE, excess, shortage, slagSeverity, spread);
        float soft = profiles.severity(Archetype.SOFT, excess, shortage, slagSeverity, spread);
        float edgeWear = profiles.severity(Archetype.EDGE_WEAR, excess, shortage, slagSeverity, spread);
        float flowy = profiles.severity(Archetype.FLOWY, excess, shortage, slagSeverity, spread);
        Map<Archetype, FlawProfiles.Defect> byArchetype = profiles.byArchetype();
        float durability = 1F;
        for (Map.Entry<Archetype, FlawProfiles.Defect> entry : byArchetype.entrySet()) {
            durability *= Mth.lerp(severityOf(entry.getKey(), brittle, soft, edgeWear, flowy), 1F, entry.getValue().maxDamage());
        }
        float worst = Math.max(brittle, Math.max(soft, Math.max(edgeWear, flowy)));
        return new DefectProfile(brittle, soft, edgeWear, flowy, slagShare,
                Mth.clamp(durability, profiles.durabilityFloor(), 1F), profiles.grade(worst));
    }

    private static float severityOf(Archetype archetype, float brittle, float soft, float edgeWear, float flowy) {
        return switch (archetype) {
            case BRITTLE -> brittle;
            case SOFT -> soft;
            case EDGE_WEAR -> edgeWear;
            case FLOWY -> flowy;
        };
    }

    private record Candidate(AlloyRecipe result, ResourceLocation id, double flaw, double deficit, double slagShare, DefectProfile defect, Map<Fluid, Double> components) {
        private Match toMatch() {
            return new Match(result, flaw <= EPS, deficit, slagShare, defect, components);
        }
    }

    public record Match(AlloyRecipe recipe, boolean clean, double deficit, double slagShare, DefectProfile defect, Map<Fluid, Double> components) {
    }
}
