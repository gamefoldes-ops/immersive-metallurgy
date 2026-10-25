package immersivemetallurgy.client;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.component.AlloyRecord;
import immersivemetallurgy.component.DefectProfile;
import immersivemetallurgy.recipe.AlloyTolerances;
import immersivemetallurgy.recipe.AlloyTolerances.Rule;
import immersivemetallurgy.recipe.FlawProfiles;
import immersivemetallurgy.registry.IMAlloys;
import immersivemetallurgy.registry.IMDataComponents;
import immersivemetallurgy.registry.IMRecipes;

import net.dries007.tfc.common.recipes.AlloyRecipe;
import net.dries007.tfc.common.recipes.RecipeHelpers;
import net.dries007.tfc.common.recipes.TFCRecipeTypes;
import net.dries007.tfc.util.AlloyRange;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Colours metal by how wrong its melt was. The art already carries the metal's own colour, so this is a lean
 * toward whichever component was poured in excess over the alloy's own midpoint, and it is how far past the
 * band that excess went: a bronze short of tin goes copper-red, a bronze with too much tin goes pale and
 * cold, and a casting that hits its band stays exactly as TFC drew it. Unlike per-melt textures this also
 * reaches tools, whose item ids are TFC's own.
 *
 * The palette, the strength and the bands are read from the synced recipes, so they are client synced and
 * reloadable. The renderer reads the tint as ARGB and multiplies the vertex alpha by its alpha byte, so
 * every answer here has to be opaque - 0xFFFFFF is invisible, not white.
 */
@EventBusSubscriber(modid = ImmersiveMetallurgy.MODID, value = Dist.CLIENT)
public final class IMItemTints {
    private static final int NO_TINT = -1;
    /** Half a second is long enough that a chest full of flawed metal costs nothing per frame. */
    private static final long PALETTE_TTL_MILLIS = 500;

    private static final Map<String, Integer> PALETTE = new HashMap<>();
    private static final Map<Fluid, Band> BANDS = new HashMap<>();
    private static long paletteReadAt;
    private static float lean = 0.45F;
    private static float defectDarken = 0.35F;
    private static float span = 0.45F;

    /** What one alloy is nominally made of, and how far a component may drift before it stops being that alloy. */
    private record Band(Map<Fluid, Double> midpoints, double tolerance) {
    }

    private IMItemTints() {
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        ItemColor tint = (stack, layer) -> layer == 0 ? tintOf(stack) : NO_TINT;
        event.register(tint, flawableItems().toArray(new Item[0]));
    }

    private static int tintOf(ItemStack stack) {
        AlloyRecord record = stack.get(IMDataComponents.ALLOY_RECORD.get());
        if (record == null || record.isEmpty()) {
            return NO_TINT;
        }
        refreshPalette();

        Band band = BANDS.get(alloyOf(stack));
        if (band == null) {
            return NO_TINT;
        }

        double total = record.totalParts();
        double[] poured = new double[3];
        double[] expected = new double[3];
        double deviation = 0;
        for (AlloyRecord.FluidParts part : record.parts()) {
            int color = paletteOf(part.fluid());
            if (color == NO_TINT) {
                continue;
            }
            double share = part.parts() / total;
            Double midpoint = band.midpoints().get(part.fluid());
            if (midpoint != null) {
                deviation = Math.max(deviation, Math.abs(share - midpoint));
            }
            accumulate(poured, color, share);
        }
        for (Map.Entry<Fluid, Double> entry : band.midpoints().entrySet()) {
            int color = paletteOf(entry.getKey());
            if (color != NO_TINT) {
                accumulate(expected, color, entry.getValue());
            }
        }

        double[] deviationPerChannel = new double[3];
        double widest = 0;
        for (int channel = 0; channel < 3; channel++) {
            deviationPerChannel[channel] = poured[channel] / Math.max(1, expected[channel]) - 1;
            widest = Math.max(widest, Math.abs(deviationPerChannel[channel]));
        }
        if (widest < 0.01 || deviation <= 0) {
            return NO_TINT;
        }

        DefectProfile defect = stack.get(IMDataComponents.DEFECT_PROFILE.get());
        float strength = Mth.clamp(
                (lean + (defect == null ? 0 : defect.worst() * defectDarken)) * Mth.clamp((float) (deviation / band.tolerance()), 0F, 1F),
                0F, 1F);
        return FastColor.ARGB32.opaque(hueShift(deviationPerChannel, widest, strength));
    }

    /**
     * The tint is the melt's colour compared with the colour the alloy should have, per channel: too much tin
     * pulls green and blue up over red, too much copper does the opposite. Only the *ratios* matter, so they
     * are amplified until the widest one is a full step and the brightest channel is then brought back to
     * full, because a multiply tint can darken but never brighten, and brightness is already the art's job.
     */
    private static int hueShift(double[] deviationPerChannel, double widest, float strength) {
        double peak = 0;
        double[] tint = new double[3];
        for (int channel = 0; channel < 3; channel++) {
            tint[channel] = 1 + deviationPerChannel[channel] / widest * span;
            peak = Math.max(peak, tint[channel]);
        }

        int color = 0;
        for (int channel = 0; channel < 3; channel++) {
            int target = (int) Math.round(255 * tint[channel] / peak);
            int shifted = Math.round(255 + (target - 255) * strength);
            color |= Math.min(255, Math.max(0, shifted)) << (16 - channel * 8);
        }
        return color;
    }

    private static void accumulate(double[] channels, int color, double weight) {
        channels[0] += ((color >> 16) & 0xFF) * weight;
        channels[1] += ((color >> 8) & 0xFF) * weight;
        channels[2] += (color & 0xFF) * weight;
    }

    private static int paletteOf(Fluid fluid) {
        Integer color = PALETTE.get(BuiltInRegistries.FLUID.getKey(fluid).toString());
        return color == null ? NO_TINT : color;
    }

    /**
     * An object belongs to the alloy its id names: {@code tfc:metal/pickaxe/bronze} and
     * {@code immersive_metallurgy:metal/ingot/flawed_bronze} are both bronze, and that is the only link
     * between a tool and the bands its melt was poured against.
     */
    private static Fluid alloyOf(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        int leaf = path.lastIndexOf('/');
        if (leaf < 0) {
            return Fluids.EMPTY;
        }
        String key = path.substring(leaf + 1);
        if (key.startsWith("flawed_")) {
            key = key.substring("flawed_".length());
        }
        return BuiltInRegistries.FLUID.get(ResourceLocation.fromNamespaceAndPath("tfc", "metal/" + key));
    }

    private static void refreshPalette() {
        long now = System.currentTimeMillis();
        if (now - paletteReadAt < PALETTE_TTL_MILLIS) {
            return;
        }
        paletteReadAt = now;
        if (Minecraft.getInstance().level == null) {
            return;
        }
        RecipeManager recipes = Minecraft.getInstance().level.getRecipeManager();
        FlawProfiles profiles = FlawProfiles.of(recipes);
        if (profiles == null) {
            return;
        }
        PALETTE.clear();
        for (FlawProfiles.MetalColor color : profiles.palette().colors()) {
            PALETTE.put(BuiltInRegistries.FLUID.getKey(color.fluid()).toString(), color.color());
        }
        lean = profiles.palette().lean();
        defectDarken = profiles.palette().defectDarken();
        span = profiles.palette().hueSpan();

        Map<Fluid, Double> toleranceByAlloy = new HashMap<>();
        for (RecipeHolder<AlloyTolerances> holder : RecipeHelpers.getRecipes(recipes, IMRecipes.ALLOY_TOLERANCE)) {
            for (Rule rule : holder.value().rules()) {
                toleranceByAlloy.put(rule.alloy(), rule.tolerance());
            }
        }
        BANDS.clear();
        for (RecipeHolder<AlloyRecipe> holder : RecipeHelpers.getRecipes(recipes, TFCRecipeTypes.ALLOY)) {
            AlloyRecipe recipe = holder.value();
            Double tolerance = toleranceByAlloy.get(recipe.result());
            if (tolerance == null) {
                continue;
            }
            Map<Fluid, Double> midpoints = new HashMap<>();
            for (AlloyRange range : recipe.contents()) {
                midpoints.put(range.fluid(), (range.min() + range.max()) / 2D);
            }
            BANDS.put(recipe.result(), new Band(midpoints, tolerance));
        }
    }

    /** The items that can hold flawed metal: our own flawed ones, and TFC's parts and tools of supported alloys. */
    private static List<Item> flawableItems() {
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (ImmersiveMetallurgy.MODID.equals(id.getNamespace())) {
                if (id.getPath().contains("flawed_")) {
                    items.add(item);
                }
            } else if ("tfc".equals(id.getNamespace())) {
                for (IMAlloys alloy : IMAlloys.ALL) {
                    if (id.getPath().endsWith("/" + alloy.key())) {
                        items.add(item);
                        break;
                    }
                }
            }
        }
        return items;
    }
}
