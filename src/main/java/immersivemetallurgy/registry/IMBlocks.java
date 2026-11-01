package immersivemetallurgy.registry;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.block.RefiningDuctBlock;
import immersivemetallurgy.block.RefiningHearthBlock;
import immersivemetallurgy.block.SlagHeapBlock;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The three blocks a refining furnace is built out of. Everything refractory can also be TFC's own firebrick, so
 * what is registered here is only what has to remember something or what TFC does not have: the hearth holds the
 * fire and the whole furnace's state, the duct is a brick with a hole in it that a route runs through, and the slag
 * heap is what freezes in an outlet that has nothing standing in it to catch the slag. The metal half of the output
 * is not ours at all — that is TFC's channel and mold table, with our own clay plate on it — and neither is the
 * table itself. The era gate is the hearth, the one recipe here that cannot be made without a steel sheet.
 */
public final class IMBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ImmersiveMetallurgy.MODID);
    public static final DeferredRegister.Items BLOCK_ITEMS = DeferredRegister.createItems(ImmersiveMetallurgy.MODID);

    public static final DeferredBlock<RefiningHearthBlock> REFINING_HEARTH = BLOCKS.registerBlock("refining_hearth",
            RefiningHearthBlock::new, refractory());
    public static final DeferredBlock<RefiningDuctBlock> REFINING_DUCT = BLOCKS.registerBlock("refining_duct",
            RefiningDuctBlock::new, firedClay());
    public static final DeferredBlock<SlagHeapBlock> SLAG_HEAP = BLOCKS.registerBlock("slag_heap",
            SlagHeapBlock::new, crust());

    public static final DeferredHolder<Item, BlockItem> REFINING_HEARTH_ITEM =
            BLOCK_ITEMS.registerSimpleBlockItem(REFINING_HEARTH);
    public static final DeferredHolder<Item, BlockItem> REFINING_DUCT_ITEM =
            BLOCK_ITEMS.registerSimpleBlockItem(REFINING_DUCT);

    private IMBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ITEMS.register(modEventBus);
    }

    /** The refractory shell is fired clay, so it is broken with a pickaxe and stands up to the bath. */
    private static BlockBehaviour.Properties refractory() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .requiresCorrectToolForDrops()
                .strength(3.5F, 4.5F)
                .sound(SoundType.STONE)
                .lightLevel(state -> state.getValue(RefiningHearthBlock.LIT) ? 13 : 0);
    }

    /**
     * Fired clay that is not part of the wall: the duct is a brick with a hole in it, and a wall of them is still
     * a wall. Broken with a pickaxe, because what has been fired has been fired.
     */
    private static BlockBehaviour.Properties firedClay() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .requiresCorrectToolForDrops()
                .strength(3.0F, 4.0F)
                .sound(SoundType.STONE);
    }

    /**
     * What slag leaves behind when it has nowhere to go: not fired and not stone, a crust that a pickaxe breaks
     * apart into the dust it was made of. Weak on purpose — the furnace never holds it, only the account in the
     * hearth does, so clearing the outlet was always meant to be cheap labour rather than a puzzle.
     */
    private static BlockBehaviour.Properties crust() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(1.5F, 3.0F)
                .sound(SoundType.DEEPSLATE)
                .noOcclusion();
    }
}
