package immersivemetallurgy.block;

import immersivemetallurgy.registry.IMBlocks;
import immersivemetallurgy.registry.IMTags;

import net.dries007.tfc.common.blocks.TFCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The hole a route runs through: the tap under the bath, the channel's mouth in the wall, and the column that
 * carries slag down to its outlet. It holds nothing and answers to nothing — which of the three it is the
 * furnace works out from where it stands, and the thing a player opens is the channel laid from the tap outward,
 * because that is the part of a cupola a worker actually has reach of.
 *
 * The furnace does write down which line a hole carries, and that is the only thing it writes. Not for its own
 * use — it knows the build and does not need to be told — but because the tap's spout is drawn over the mold
 * table standing in front of the tap hole, and the renderer that owns that cell is TFC's, with no way to ask the
 * furnace anything. A block state is the one thing every reader can see without a query, and it survives a reload
 * for free.
 *
 * The six face flags are the other thing a reader can see: a duct that joins nothing is a pipe mouth on every face,
 * and once it joins anything the mouths stay only on the faces that join — another duct, the table an outlet runs
 * to, or the furnace's own refractory. They are recomputed from the neighbours rather than stored by hand, because
 * a wall that is faced in ducts today and brick tomorrow must redraw itself without being asked.
 */
public class RefiningDuctBlock extends Block {
    /** Which line this hole carries: 0 nothing yet, 1 the tap, 2 the slag. */
    public static final int LINE_NONE = 0;
    public static final int LINE_TAP = 1;
    public static final int LINE_SLAG = 2;

    /** What is running through this hole right now: 0 nothing, 1 metal, 2 slag. */
    public static final int FLOW_NONE = 0;
    public static final int FLOW_METAL = 1;
    public static final int FLOW_SLAG = 2;

    public static final net.minecraft.world.level.block.state.properties.IntegerProperty LINE =
            net.minecraft.world.level.block.state.properties.IntegerProperty.create("line", LINE_NONE, LINE_SLAG);

    /**
     * Written by the furnace while something is actually moving, and read by the renderer that owns the cell in
     * front of the hole. It costs a block update per change rather than per tick, because the flow is held open
     * for a few ticks after the last millilitre and only switched when that counter starts and stops.
     */
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty FLOW =
            net.minecraft.world.level.block.state.properties.IntegerProperty.create("flow", FLOW_NONE, FLOW_SLAG);

    /**
     * The face that opens into the shaft itself, as a {@link Direction#get3DDataValue()} (0 = none). The six join
     * flags only ever open onto neighbours, and the shaft is air, so the two mouths a player sees from inside the
     * stack — the tap at the floor and the channel at its own height — are written here by the furnace instead.
     */
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty MOUTH =
            net.minecraft.world.level.block.state.properties.IntegerProperty.create("mouth", 0, 5);

    public RefiningDuctBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LINE, LINE_NONE).setValue(FLOW, FLOW_NONE)
                .setValue(MOUTH, 0)
                .setValue(BlockStateProperties.NORTH, false).setValue(BlockStateProperties.EAST, false)
                .setValue(BlockStateProperties.SOUTH, false).setValue(BlockStateProperties.WEST, false)
                .setValue(BlockStateProperties.UP, false).setValue(BlockStateProperties.DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LINE, FLOW, MOUTH, BlockStateProperties.NORTH, BlockStateProperties.EAST, BlockStateProperties.SOUTH,
                BlockStateProperties.WEST, BlockStateProperties.UP, BlockStateProperties.DOWN);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            refresh(state, level, pos);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from,
                                   boolean isMoving) {
        if (!level.isClientSide) {
            refresh(state, level, pos);
        }
    }

    /**
     * Recompute which faces show a bore and write the flags back only if they actually changed. A duct that joins
     * nothing is a mouth on every face; once it joins anything the mouths stay only on the joined faces. The rule
     * is folded into the six flags here because 1.21.1's multipart {@code when} is a plain conjunction and cannot
     * express "this face, or no face at all" itself.
     */
    private void refresh(BlockState state, Level level, BlockPos pos) {
        boolean[] joins = new boolean[Direction.values().length];
        boolean joined = false;
        for (Direction side : Direction.values()) {
            joins[side.ordinal()] = connects(level, pos.relative(side), side);
            joined |= joins[side.ordinal()];
        }
        BlockState updated = state;
        for (Direction side : Direction.values()) {
            updated = updated.setValue(property(side), joins[side.ordinal()] || !joined);
        }
        if (updated != state) {
            level.setBlockAndUpdate(pos, updated);
        }
    }

    /**
     * A bore opens onto another duct on any axis, onto the table an outlet runs to on the flat, and onto the
     * furnace's own refractory or hearth — the mouth where a route leaves the stack is drawn on the furnace side
     * too, not only on the free end of the pipe.
     */
    private boolean connects(Level level, BlockPos neighbor, Direction side) {
        BlockState state = level.getBlockState(neighbor);
        if (state.getBlock() instanceof RefiningDuctBlock) {
            return true;
        }
        if (state.is(IMTags.REFINING_REFRACTORY) || state.getBlock() instanceof RefiningFurnaceBlock) {
            return true;
        }
        return side.getAxis() != Direction.Axis.Y
                && (state.is(TFCBlocks.MOLD_TABLE.get()) || state.is(IMBlocks.CASTING_TABLE.get())
                        || state.is(IMBlocks.LAUNDER.get()) || state.is(IMBlocks.LAUNDER_CHANNEL.get()));
    }

    private static BooleanProperty property(Direction side) {
        return switch (side) {
            case NORTH -> BlockStateProperties.NORTH;
            case EAST -> BlockStateProperties.EAST;
            case SOUTH -> BlockStateProperties.SOUTH;
            case WEST -> BlockStateProperties.WEST;
            case UP -> BlockStateProperties.UP;
            case DOWN -> BlockStateProperties.DOWN;
        };
    }
}