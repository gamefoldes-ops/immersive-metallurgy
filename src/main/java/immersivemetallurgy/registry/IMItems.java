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
     * The metals whose ore answers to gravity. Hematite, limonite, magnetite, cassiterite, malachite and the
     * three native metals are heavy enough that water can part them from quartz, which is the whole basis of
     * hand dressing; sphalerite, bismuthinite, tetrahedrite and garnierite are not, and no amount of washing
     * makes their concentrate clean. The list is what {@code tools/generate_ore_dressing.py} reads, so the
     * items and their recipes cannot drift apart.
     *
     * The crushed half of the chain is deliberately not here: TFC's own quern already grinds every one of
     * those ores into {@code tfc:powder/<ore>}, seven from a rich lump down to two from the small fragments,
     * and adding a rival recipe only made the game choose the one it already had.
     */
    public static final List<String> DRESSABLE_METALS = List.of("cast_iron", "copper", "gold", "silver", "tin");

    /**
     * What five scoops of TFC's ore powder become in a barrel of water. The powder itself has no use in TFC at
     * all - no item_heat, no heating recipe - so this is the reason to grind anything, and it is also the only
     * route to metal with no slag in it: five scoops wash into one concentrate carrying the same twenty-five
     * millibuckets they held between them, which is what one normal lump was worth.
     */
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
        for (String metal : DRESSABLE_METALS) {
            ORE_CONCENTRATES.put(metal, ITEMS.registerItem("ore_concentrate/" + metal, properties -> new Item(properties.stacksTo(64))));
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
