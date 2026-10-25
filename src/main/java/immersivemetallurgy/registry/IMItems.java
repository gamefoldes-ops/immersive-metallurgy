package immersivemetallurgy.registry;

import immersivemetallurgy.ImmersiveMetallurgy;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * Ingots that TFC cannot have, because its ingot items are keyed one-to-one on a metal fluid and a flawed
 * pour has no metal. One pair of items per flawed metal, never one per defect: the defect profile travels
 * as a data component, and the metal identity is what TFC recipes and Millenaire need to see.
 */
public final class IMItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ImmersiveMetallurgy.MODID);

    /** Kept so the creative tab can hold something of ours up as its icon. */
    public static final List<DeferredHolder<Item, Item>> FLAWED_INGOTS = new ArrayList<>();

    static {
        for (IMAlloys alloy : IMAlloys.ALL) {
            FLAWED_INGOTS.add(ITEMS.registerItem(alloy.flawedIngotPath(), Item::new));
            if (alloy.doubleIngot()) {
                ITEMS.registerItem(alloy.flawedDoubleIngotPath(), Item::new);
            }
        }
    }

    private IMItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
