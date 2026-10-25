package com.gamefoldes.immersivemetallurgy.registry;

import com.gamefoldes.immersivemetallurgy.ImmersiveMetallurgy;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Ingots that TFC cannot have, because its ingot items are keyed one-to-one on a metal fluid and a flawed
 * pour has no metal. They exist only to be cast, told apart by their {@code c:ingots/flawed_*} tags, and
 * melted back down with their composition attached.
 */
public final class IMItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ImmersiveMetallurgy.MODID);

    public static final DeferredItem<Item> FLAWED_BRONZE_INGOT = ITEMS.registerItem("metal/ingot/flawed_bronze", Item::new);

    public static final DeferredItem<Item> FLAWED_BRONZE_DOUBLE_INGOT = ITEMS.registerItem("metal/double_ingot/flawed_bronze", Item::new);

    private IMItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
