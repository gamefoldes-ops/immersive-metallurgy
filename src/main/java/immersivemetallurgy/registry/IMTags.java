package immersivemetallurgy.registry;

import immersivemetallurgy.ImmersiveMetallurgy;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/**
 * The blocks and fluids the world is asked about: where an edge may be honed, what a furnace wall may be built
 * of, and what each of the furnace's vessels will accept. {@code honing_station} answers a plain right-click,
 * while {@code honing_station_sneak} only answers a sneaking one, because stations that already open a menu —
 * TFC's anvils — must keep it. Add to any of them from data, not from code.
 */
public final class IMTags {
    public static final TagKey<Block> HONING_STATION = tag("honing_station");
    public static final TagKey<Block> HONING_STATION_SNEAK = tag("honing_station_sneak");

    /**
     * What the walls of a refining furnace may be built of. TFC's firebricks and their reinforced form both
     * stand in a bath, and the tag is where a datapack can add another refractory without touching code.
     */
    public static final TagKey<Block> REFINING_REFRACTORY = tag("refining_refractory");

    /**
     * What a slag plate will hold: slag and nothing else. This is its own tag rather than a row added to TFC's
     * mold tags precisely so that «slag is never castable» stays true everywhere else in the game — a plate that
     * only accepts slag cannot launder dirt into an ingot, and a mould that only accepts metal cannot hold dirt.
     */
    public static final TagKey<Fluid> USABLE_IN_SLAG_PLATE = fluidTag("usable_in_slag_plate");

    /**
     * What the furnace's large mould will take: the same molten metals every other mould accepts — TFC's own and
     * ours — because the mould is a mould, and only the size of it is the furnace's.
     */
    public static final TagKey<Fluid> USABLE_IN_REFINING_MOLD = fluidTag("usable_in_refining_mold");

    private IMTags() {
    }

    private static TagKey<Block> tag(String name) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ImmersiveMetallurgy.MODID, name));
    }

    private static TagKey<Fluid> fluidTag(String name) {
        return TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath(ImmersiveMetallurgy.MODID, name));
    }
}
