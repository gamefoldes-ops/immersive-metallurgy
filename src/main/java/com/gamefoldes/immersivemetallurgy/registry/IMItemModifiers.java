package com.gamefoldes.immersivemetallurgy.registry;

import com.gamefoldes.immersivemetallurgy.ImmersiveMetallurgy;
import com.gamefoldes.immersivemetallurgy.recipe.CopyMeltModifier;

import net.dries007.tfc.common.recipes.outputs.ItemStackModifierType;
import net.dries007.tfc.common.recipes.outputs.ItemStackModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Our output modifier, registered into TFC's own item-modifier registry so it can be named from a recipe
 * file like TFC's {@code copy_forging_bonus}.
 */
public final class IMItemModifiers {
    public static final DeferredRegister<ItemStackModifierType<?>> TYPES =
            DeferredRegister.create(ItemStackModifiers.KEY, ImmersiveMetallurgy.MODID);

    public static final DeferredHolder<ItemStackModifierType<?>, ItemStackModifierType<CopyMeltModifier>> COPY_MELT =
            TYPES.register("copy_melt", () -> new ItemStackModifierType<>(CopyMeltModifier.CODEC, CopyMeltModifier.STREAM_CODEC));

    private IMItemModifiers() {
    }

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }
}
