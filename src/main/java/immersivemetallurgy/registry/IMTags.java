package immersivemetallurgy.registry;

import immersivemetallurgy.ImmersiveMetallurgy;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * The blocks a player may hone an edge on. {@code honing_station} answers a plain right-click, while
 * {@code honing_station_sneak} only answers a sneaking one, because stations that already open a menu —
 * TFC's anvils — must keep it. Add to either from data, not from code.
 */
public final class IMTags {
    public static final TagKey<Block> HONING_STATION = tag("honing_station");
    public static final TagKey<Block> HONING_STATION_SNEAK = tag("honing_station_sneak");

    /** What a player may skim floating slag with: TFC's ceramic bowls, and anything else data adds. */
    public static final TagKey<Item> SLAG_SKIMMER = itemTag("slag_skimmers");

    /** What does not survive being pushed against a bath of molten metal, and gets destroyed for trying. */
    public static final TagKey<Item> BURNS_IN_CRUCIBLE = itemTag("burns_in_the_crucible");

    private IMTags() {
    }

    private static TagKey<Block> tag(String name) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ImmersiveMetallurgy.MODID, name));
    }

    private static TagKey<Item> itemTag(String name) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(ImmersiveMetallurgy.MODID, name));
    }
}
