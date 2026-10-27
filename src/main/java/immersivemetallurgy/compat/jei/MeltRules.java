package immersivemetallurgy.compat.jei;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.recipe.AlloyTolerances;
import immersivemetallurgy.recipe.FlawProfiles;
import immersivemetallurgy.recipe.OreFouling;
import immersivemetallurgy.registry.IMAlloys;
import immersivemetallurgy.registry.IMFluids;
import immersivemetallurgy.registry.IMRecipes;

import net.dries007.tfc.common.recipes.RecipeHelpers;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The four tuning tables, flattened into rows a player can read and search.
 *
 * <p>Grouping is the whole job: the fouling table has eighty-odd entries, and a page per entry is a wall nobody
 * reads. What a player is asking is "how dirty is this kind of thing", so entries that share a ratio and a kind
 * become one row whose icons are the items themselves - which is also what makes the row come up when one of
 * them is looked up.
 */
public final class MeltRules {
    /** Two lines of icons; past that the row is about the number, not the list. */
    private static final int MAX_ICONS = 18;

    private MeltRules() {
    }

    public static List<MeltRule> of(RecipeManager recipes) {
        List<MeltRule> rows = new ArrayList<>();
        tolerances(rows, recipes);
        fouling(rows, recipes);
        defects(rows, recipes);
        wear(rows, recipes);
        return rows;
    }

    private static void tolerances(List<MeltRule> rows, RecipeManager recipes) {
        AlloyTolerances table = first(recipes, IMRecipes.ALLOY_TOLERANCE);
        if (table == null) {
            return;
        }
        for (AlloyTolerances.Rule rule : table.rules()) {
            IMAlloys alloy = IMFluids.alloyOf(rule.alloy());
            if (alloy == null) {
                continue;
            }
            rows.add(new MeltRule(List.of(item("tfc:metal/ingot/" + alloy.key()), flawed(alloy)), List.of(
                    key("jei.tolerance.title", metal(alloy)),
                    key("jei.tolerance.bands", text(percent(rule.tolerance()))),
                    key("jei.tolerance.slag", text(percent(rule.slagTolerance())), text(percent(rule.maxSlag()))))));
        }
    }

    private static void fouling(List<MeltRule> rows, RecipeManager recipes) {
        OreFouling table = first(recipes, IMRecipes.ORE_FOULING);
        if (table == null) {
            return;
        }
        Map<String, MeltRule.Builder> groups = new LinkedHashMap<>();
        for (OreFouling.Entry entry : table.entries()) {
            String kind = kindOf(entry.ingredient());
            if (kind == null) {
                continue;
            }
            MeltRule.Builder group = groups.computeIfAbsent(kind + percent(entry.slagRatio()),
                    key -> MeltRule.builder(key("jei.fouling." + kind), key("jei.fouling.ratio", text(percent(entry.slagRatio())))));
            group.icons(entry.ingredient(), MAX_ICONS);
        }
        groups.values().forEach(group -> rows.add(group.build()));

        rows.add(new MeltRule(List.of(item("tfc:ceramic/vessel"), item("tfc:crucible")), List.of(
                key("jei.fouling.pot"),
                key("jei.fouling.pot_floor", text(percent(table.vesselSlagFloor()))))));
    }

    private static void defects(List<MeltRule> rows, RecipeManager recipes) {
        FlawProfiles table = first(recipes, IMRecipes.FLAW_PROFILE);
        if (table == null) {
            return;
        }
        for (FlawProfiles.Defect defect : table.defects()) {
            rows.add(new MeltRule(List.of(), List.of(
                    key("jei.defect.title", key("defect." + defect.key())),
                    key("jei.defect.costs", text(percent(1F - defect.damage())), text(percent(1F - defect.miningSpeed())),
                            text(percent(defect.dulling())), text(percent(defect.chipChance()))),
                    key("jei.defect.feeds", text(percent(defect.slagWeight())), text(percent(defect.spreadWeight()))))));
        }
        rows.add(new MeltRule(List.of(), List.of(
                key("jei.grades.title"),
                key("jei.grades.bands", text(percent(table.grades().medium())), text(percent(table.grades().heavy()))),
                key("jei.grades.floor", text(percent(1F - table.durabilityFloor())), text(percent(table.unmeasuredFlaw()))),
                key("jei.grades.weld", text(table.weldMergeDivisor())))));
    }

    private static void wear(List<MeltRule> rows, RecipeManager recipes) {
        FlawProfiles table = first(recipes, IMRecipes.FLAW_PROFILE);
        if (table == null) {
            return;
        }
        FlawProfiles.Wear wear = table.wear();
        rows.add(new MeltRule(List.of(item("tfc:stone/hammer")), List.of(
                key("jei.wear.title"),
                key("jei.wear.edge", text(wear.sharpnessPerBlock()), text(wear.sharpnessPerHit())),
                key("jei.wear.dull", text(percent(1F - wear.dullDamageFloor())), text(percent(1F - wear.dullSpeedFloor()))),
                key("jei.wear.honing", text(wear.honingRestore()), text(Math.round(wear.honingCeiling())),
                        text(percent(wear.honingStructureCost())), text(wear.honingTicks() / 20F)))));
    }

    /** Which kind of raw thing an ingredient is, because that is what the row is labelled by. */
    private static String kindOf(Ingredient ingredient) {
        for (ItemStack stack : ingredient.getItems()) {
            String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            if (path.startsWith("ore/")) {
                String grade = path.substring(path.lastIndexOf('/') + 1).split("_")[0];
                return switch (grade) {
                    case "rich" -> "ore_rich";
                    case "normal" -> "ore_normal";
                    case "poor" -> "ore_poor";
                    case "small" -> "ore_small";
                    default -> null;
                };
            }
            if (path.startsWith("crushed_ore/")) {
                return "crushed";
            }
            if (path.startsWith("washed_ore/")) {
                return "washed";
            }
            if (path.startsWith("slag_dust")) {
                return "dust";
            }
        }
        return null;
    }

    /** The one table of a type, which is how all four of ours are written: a file per table, not per row. */
    private static <T extends Recipe<?>> T first(RecipeManager recipes, Supplier<RecipeType<T>> type) {
        for (RecipeHolder<T> holder : RecipeHelpers.getRecipes(recipes, type)) {
            return holder.value();
        }
        return null;
    }

    private static Component metal(IMAlloys alloy) {
        return Component.translatable("metal.tfc." + alloy.key());
    }

    private static ItemStack flawed(IMAlloys alloy) {
        return item(ImmersiveMetallurgy.MODID + ":" + alloy.flawedIngotPath());
    }

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
    }

    private static Component key(String path, Object... arguments) {
        Object[] converted = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            converted[i] = arguments[i] instanceof Component component ? component : Component.literal(arguments[i].toString());
        }
        return Component.translatable("immersive_metallurgy." + path, converted);
    }

    private static Component text(Object value) {
        return Component.literal(value.toString());
    }

    private static String percent(double fraction) {
        return Math.round(100 * fraction) + "%";
    }
}
