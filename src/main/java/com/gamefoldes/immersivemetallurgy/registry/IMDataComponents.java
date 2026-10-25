package com.gamefoldes.immersivemetallurgy.registry;

import com.gamefoldes.immersivemetallurgy.ImmersiveMetallurgy;
import com.gamefoldes.immersivemetallurgy.component.AlloyRecord;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class IMDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ImmersiveMetallurgy.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<AlloyRecord>> ALLOY_RECORD =
            DATA_COMPONENTS.register("alloy_record", () -> DataComponentType.<AlloyRecord>builder()
                    .persistent(AlloyRecord.CODEC)
                    .networkSynchronized(AlloyRecord.STREAM_CODEC)
                    .build());

    private IMDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
