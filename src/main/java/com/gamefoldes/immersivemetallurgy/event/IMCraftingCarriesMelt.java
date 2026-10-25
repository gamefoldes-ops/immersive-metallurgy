package com.gamefoldes.immersivemetallurgy.event;

import com.gamefoldes.immersivemetallurgy.ImmersiveMetallurgy;
import com.gamefoldes.immersivemetallurgy.component.MeltCarries;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Putting a head on a handle is plain crafting, and vanilla crafting hands back a fresh stack that knows
 * nothing about its ingredients, so a flawed head would produce a tool that reads as clean. A welded joint
 * inherits the worst of its parts, which is what the merge does here.
 */
@EventBusSubscriber(modid = ImmersiveMetallurgy.MODID)
public final class IMCraftingCarriesMelt {
    private IMCraftingCarriesMelt() {
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack result = event.getCrafting();
        if (result.isEmpty()) {
            return;
        }
        Container grid = event.getInventory();
        List<ItemStack> pieces = new ArrayList<>(grid.getContainerSize());
        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            pieces.add(grid.getItem(slot));
        }
        MeltCarries.mergeWorstInto(pieces, result);
    }
}
