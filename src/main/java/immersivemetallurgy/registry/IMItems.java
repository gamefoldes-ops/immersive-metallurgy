package immersivemetallurgy.registry;

import immersivemetallurgy.ImmersiveMetallurgy;

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
     * What comes off a skim: dirt with a use, rather than nothing to show for the work.
     */
    public static final DeferredHolder<Item, Item> SLAG_DUST =
            ITEMS.registerItem("slag_dust", properties -> new Item(properties.stacksTo(16)));

    /**
     * Every ore variety TFC lets a quern grind, named without its grade prefix. Dressing is one chain for all
     * of them: the quern breaks a lump into crushed ore, the barrel washes five scoops of that into a
     * concentrate, and the quern grinds a concentrate into TFC's own ore powder, which stays the pigment and
     * the glass colour it already was.
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

    /** What the barrel makes of five scoops: the metal worth twenty-five millibuckets, with no stone left. */
    public static final Map<String, DeferredHolder<Item, Item>> ORE_CONCENTRATES = new LinkedHashMap<>();

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
            ORE_CONCENTRATES.put(variety, ITEMS.registerItem("ore_concentrate/" + variety, properties -> new Item(properties.stacksTo(64))));
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
