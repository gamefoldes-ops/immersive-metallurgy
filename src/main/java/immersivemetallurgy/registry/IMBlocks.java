package immersivemetallurgy.registry;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.block.CastingTableBlock;
import immersivemetallurgy.block.LaunderBlock;
import immersivemetallurgy.block.LaunderChannelBlock;
import immersivemetallurgy.block.RefiningDuctBlock;
import immersivemetallurgy.block.RefiningFurnaceBlock;
import immersivemetallurgy.block.SlagHeapBlock;

import net.dries007.tfc.common.blockentities.MoldTableBlockEntity;
import net.dries007.tfc.common.blockentities.TFCBlockEntities;
import net.dries007.tfc.common.blocks.ExtendedProperties;
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
 * The blocks a refining furnace is built out of. Everything refractory can also be TFC's own firebrick, so
 * what is registered here is only what has to remember something or what TFC does not have: the hearth holds the
 * fire and the whole furnace's state, the duct is a brick with a hole in it that a route runs through, the casting
 * table is TFC's mold table with the launder built onto it, and the slag heap is what freezes in an outlet that has
 * nothing standing in it to catch the slag. The metal half of the output is not ours at all — that is TFC's channel
 * and mold table, with our own clay plate on it — and neither is the table itself. The era gate is the hearth, the
 * one recipe here that cannot be made without a steel sheet.
 */
public final class IMBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ImmersiveMetallurgy.MODID);
    public static final DeferredRegister.Items BLOCK_ITEMS = DeferredRegister.createItems(ImmersiveMetallurgy.MODID);

    public static final DeferredBlock<RefiningFurnaceBlock> REFINING_FURNACE = BLOCKS.registerBlock("refining_furnace",
            RefiningFurnaceBlock::new, refractory());
    public static final DeferredBlock<RefiningDuctBlock> REFINING_DUCT = BLOCKS.registerBlock("refining_duct",
            RefiningDuctBlock::new, firedClay());
    public static final DeferredBlock<SlagHeapBlock> SLAG_HEAP = BLOCKS.registerBlock("slag_heap",
            SlagHeapBlock::new, crust());

    /**
     * The table the furnace's output lands on, with the launder built onto it. It keeps TFC's own mold table block
     * entity — the type TFC's channel and crucible look a table up by — so a pour, a cast and a cooling recipe all
     * run on it exactly as on a plain table; only the block, its model and its shape are ours. It has no item and
     * no recipe: it is the cell where a launder and an ordinary TFC mold table have both been laid, in either
     * order ({@link LaunderBlock} and {@link immersivemetallurgy.event.IMCastingTable}), so nothing new has to be
     * crafted. Breaking it drops the ordinary table and the TFC channel back. Registered through a supplier rather
     * than the property factory because its constructor takes TFC's {@link ExtendedProperties}, which is where the
     * block entity and its ticker are wired.
     */
    public static final DeferredBlock<CastingTableBlock> CASTING_TABLE = BLOCKS.register("refining_casting_table",
            () -> new CastingTableBlock(ExtendedProperties.of(table())
                    .blockEntity(() -> TFCBlockEntities.MOLD_TABLE.get())
                    .serverTicks(MoldTableBlockEntity::serverTick)));

    /**
     * The launder on its own, before a table stands under it: the trough half of the casting table with no board
     * and no vessel. Like the combined table it has no item — a player lays TFC's own channel at the outlet and it
     * swaps for this trough — and clicking it with a mold table completes the pair. Breaking it drops the channel.
     */
    public static final DeferredBlock<LaunderBlock> LAUNDER = BLOCKS.registerBlock("refining_launder",
            LaunderBlock::new, table());

    /**
     * The launder with a channel under it instead of a table: the trough unchanged, and TFC's own channel below
     * it, keeping TFC's channel block entity so it joins the channels beside it and carries the metal on. No item
     * of its own — a channel laid on a standing launder completes the pair, and breaking it drops both channels.
     */
    public static final DeferredBlock<LaunderChannelBlock> LAUNDER_CHANNEL =
            BLOCKS.register("refining_launder_channel", () -> new LaunderChannelBlock(
                    ExtendedProperties.of(table()).blockEntity(() -> TFCBlockEntities.CHANNEL.get())));

    public static final DeferredHolder<Item, BlockItem> REFINING_FURNACE_ITEM =
            BLOCK_ITEMS.registerSimpleBlockItem(REFINING_FURNACE);
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
                .lightLevel(state -> state.getValue(RefiningFurnaceBlock.LIT) ? 13 : 0);
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
     * The casting table is fired clay like the duct, but it is not a full cube: a board with a trough standing on
     * it, so it never occludes the faces of what stands beside it. Broken with a pickaxe, the same as the rest of
     * the furnace's clay.
     */
    private static BlockBehaviour.Properties table() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .requiresCorrectToolForDrops()
                .strength(3.0F, 4.0F)
                .sound(SoundType.STONE)
                .noOcclusion();
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
