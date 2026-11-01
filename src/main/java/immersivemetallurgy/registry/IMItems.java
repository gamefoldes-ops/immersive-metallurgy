package immersivemetallurgy.registry;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.recipe.RefiningFurnaceStats;

import net.dries007.tfc.common.items.MoldItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ingots that TFC cannot have, because its ingot items are keyed one-to-one on a metal fluid and a flawed
 * pour has no metal. One pair of items per flawed metal, never one per defect: the defect profile travels
 * as a data component, and the metal identity is what TFC's own recipes need to see.
 */
public final class IMItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ImmersiveMetallurgy.MODID);

    /** Kept so the creative tab can hold something of ours up as its icon. */
    public static final List<DeferredHolder<Item, Item>> FLAWED_INGOTS = new ArrayList<>();

    /**
     * What leaves a bath as dirt: slag with a use, rather than nothing to show for the work.
     */
    public static final DeferredHolder<Item, Item> SLAG_DUST =
            ITEMS.registerItem("slag_dust", properties -> new Item(properties.stacksTo(16)));

    /**
     * The dish the furnace's slag runs into: a flat plate of fired clay with a rim, and the one vessel in the game
     * that will take slag. It stands on TFC's mold table at the channel's outlet, the table draws it filling
     * because that is what a table does with whatever stands on it, and a click takes the slag back out as the dust
     * the bath's metal makes. Slag is in no other vessel's tag and this plate is in no other fluid's, which is how
     * «шлак не льётся в формы» survives having a slag vessel at all.
     */
    public static final DeferredHolder<Item, MoldItem> SLAG_PLATE =
            ITEMS.registerItem("slag_plate", properties -> new MoldItem(IMItems::slagPlateMb,
                    IMTags.USABLE_IN_SLAG_PLATE, properties));

    /**
     * The furnace's own mould, sized for a whole module of metal at once. It stands on the same TFC table as any
     * other mould and casts through the same casting recipe, so the flaw the bath carried comes out written on the
     * ingots; what makes it the furnace's is that a crucible cannot fill it, because no crucible holds a module.
     */
    public static final DeferredHolder<Item, MoldItem> REFINING_MOLD =
            ITEMS.registerItem("refining_mold", properties -> new MoldItem(IMItems::refiningMoldMb,
                    IMTags.USABLE_IN_REFINING_MOLD, properties));

    /**
     * The same tray knocked out of common clay instead of fire clay. It holds what the fireclay one holds and is
     * fired at the same heat; the difference is only what it costs to be wrong, which is the whole reason a player
     * who cannot reach fire clay yet is allowed to build a furnace at all.
     */
    public static final DeferredHolder<Item, MoldItem> CLAY_REFINING_MOLD =
            ITEMS.registerItem("clay_refining_mold", properties -> new MoldItem(IMItems::refiningMoldMb,
                    IMTags.USABLE_IN_REFINING_MOLD, properties));

    /** Both vessels take their size from the tuning sheet rather than from a number written here. */
    private static int slagPlateMb() {
        RefiningFurnaceStats stats = RefiningFurnaceStats.cached();
        return stats == null ? 0 : stats.slagPlateMb();
    }

    private static int refiningMoldMb() {
        RefiningFurnaceStats stats = RefiningFurnaceStats.cached();
        return stats == null ? 0 : stats.pourVolume();
    }

    /**
     * The mold as it comes off the anvil, before the kiln has been through it. It is only clay, so it is only an
     * {@link Item}: what makes it fireable is the {@code item_heat} row that names its temperature, because TFC
     * hands the heat component to every item such a row exists for. The slag plate has no unfired form because
     * nobody crafts it — the furnace sets one up in its own table.
     */
    public static final DeferredHolder<Item, Item> UNFIRED_REFINING_MOLD =
            ITEMS.registerItem("unfired_refining_mold", properties -> new Item(properties.stacksTo(16)));
    public static final DeferredHolder<Item, Item> UNFIRED_CLAY_REFINING_MOLD =
            ITEMS.registerItem("unfired_clay_refining_mold", properties -> new Item(properties.stacksTo(16)));

    /**
     * Every ore variety TFC lets a quern grind, named without its grade prefix. Dressing is one chain for all
     * of them: the quern breaks a lump into crushed ore, the barrel washes four scoops of that into washed ore,
     * and the quern grinds washed ore back into TFC's own ore powder, which stays the pigment and the glass
     * colour it already was.
     *
     * The list is what {@code tools/generate_ore_dressing.py} reads back, so an item and its recipes cannot
     * drift apart. Gravity separation is the honest reason for the oxide and native ones; for the sulfides the
     * barrel stands in for the hand-picking and roasting a real plant would have used, which is the one place
     * this chain is a game rather than a furnace.
     */
    public static final List<String> ORE_VARIETIES = List.of("bismuthinite", "cassiterite", "garnierite", "hematite",
            "limonite", "magnetite", "malachite", "native_copper", "native_gold", "native_silver", "sphalerite",
            "tetrahedrite");

    /** What the quern makes of a lump: the mineral freed from the rock, and every bit of the rock still in it. */
    public static final Map<String, DeferredHolder<Item, Item>> CRUSHED_ORES = new LinkedHashMap<>();

    /** What the barrel makes of four scoops: twenty millibuckets of metal with the stone off it, and no slag. */
    public static final Map<String, DeferredHolder<Item, Item>> WASHED_ORES = new LinkedHashMap<>();

    /**
     * One dust per metal, named for the bath it was lifted off, because what slag is good for depends on what
     * is still hanging in it. The plain dust above stays for a bath that is not any metal we can name.
     */
    public static final Map<String, DeferredHolder<Item, Item>> SLAG_DUSTS = new LinkedHashMap<>();

    static {
        for (IMAlloys alloy : IMAlloys.ALL) {
            FLAWED_INGOTS.add(ITEMS.registerItem(alloy.flawedIngotPath(), Item::new));
            if (alloy.doubleIngot()) {
                ITEMS.registerItem(alloy.flawedDoubleIngotPath(), Item::new);
            }
            SLAG_DUSTS.put(alloy.key(), ITEMS.registerItem(alloy.slagDustPath(), properties -> new Item(properties.stacksTo(16))));
        }
        for (String variety : ORE_VARIETIES) {
            CRUSHED_ORES.put(variety, ITEMS.registerItem("crushed_ore/" + variety, properties -> new Item(properties.stacksTo(64))));
            WASHED_ORES.put(variety, ITEMS.registerItem("washed_ore/" + variety, properties -> new Item(properties.stacksTo(64))));
        }
    }

    public static Item slagDust(IMAlloys alloy) {
        DeferredHolder<Item, Item> dust = SLAG_DUSTS.get(alloy.key());
        return dust == null ? SLAG_DUST.get() : dust.get();
    }

    private IMItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
