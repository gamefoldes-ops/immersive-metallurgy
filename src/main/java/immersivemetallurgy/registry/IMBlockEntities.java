package immersivemetallurgy.registry;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.block.entity.RefiningFurnaceBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The one thing the furnace remembers: the hearth, which holds the bath, the charge and the whole firing. The duct
 * is a hole in a wall and the slag heap is a crust, so neither has anything to remember — which outlet is which
 * the hearth works out from the build, and what stands in those outlets belongs to TFC.
 */
public final class IMBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, ImmersiveMetallurgy.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RefiningFurnaceBlockEntity>> HEARTH =
            BLOCK_ENTITY_TYPES.register("refining_hearth", () -> BlockEntityType.Builder.of(
                    RefiningFurnaceBlockEntity::new, IMBlocks.REFINING_HEARTH.get()).build(null));

    private IMBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
