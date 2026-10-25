package immersivemetallurgy.registry;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.component.AlloyRecord;
import immersivemetallurgy.component.DefectProfile;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.mojang.serialization.Codec;

public final class IMDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ImmersiveMetallurgy.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<AlloyRecord>> ALLOY_RECORD =
            DATA_COMPONENTS.register("alloy_record", () -> DataComponentType.<AlloyRecord>builder()
                    .persistent(AlloyRecord.CODEC)
                    .networkSynchronized(AlloyRecord.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DefectProfile>> DEFECT_PROFILE =
            DATA_COMPONENTS.register("defect_profile", () -> DataComponentType.<DefectProfile>builder()
                    .persistent(DefectProfile.CODEC)
                    .networkSynchronized(DefectProfile.STREAM_CODEC)
                    .build());

    /** How sharp an edge still is, 0 to 100. Structural durability is the vanilla max-damage component. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> SHARPNESS =
            DATA_COMPONENTS.register("sharpness", () -> DataComponentType.<Float>builder()
                    .persistent(Codec.FLOAT)
                    .networkSynchronized(ByteBufCodecs.FLOAT)
                    .build());

    /**
     * Slag a melting item brings with it, per unit of the metal it yields. It rides the fluid stack rather
     * than being added as slag immediately, because the vessel multiplies a melt by stack size after the
     * recipe has run, and an absolute millilitre figure would not follow.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> SLAG_RATIO =
            DATA_COMPONENTS.register("slag_ratio", () -> DataComponentType.<Float>builder()
                    .persistent(Codec.FLOAT)
                    .networkSynchronized(ByteBufCodecs.FLOAT)
                    .build());

    private IMDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
