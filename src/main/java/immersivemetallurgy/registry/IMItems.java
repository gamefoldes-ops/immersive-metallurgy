package immersivemetallurgy.registry;

import immersivemetallurgy.ImmersiveMetallurgy;

import immersivemetallurgy.item.IMWhetstoneItem;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Ingots that TFC cannot have, because its ingot items are keyed one-to-one on a metal fluid and a flawed
 * pour has no metal. One pair of items per flawed alloy, never one per defect: the defect profile travels
 * as a data component, and the alloy identity is what TFC recipes and Millenaire need to see.
 */
public final class IMItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ImmersiveMetallurgy.MODID);

    public static final DeferredHolder<Item, IMWhetstoneItem> WHETSTONE =
            ITEMS.register("whetstone", () -> new IMWhetstoneItem(new Item.Properties().stacksTo(1).durability(120)));

    static {
        for (IMAlloys alloy : IMAlloys.ALL) {
            ITEMS.registerItem(alloy.flawedIngotPath(), Item::new);
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
