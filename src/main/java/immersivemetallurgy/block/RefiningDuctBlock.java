package immersivemetallurgy.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

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
 */
public class RefiningDuctBlock extends Block {
    /** Which line this hole carries: 0 nothing yet, 1 the tap, 2 the slag. */
    public static final int LINE_NONE = 0;
    public static final int LINE_TAP = 1;
    public static final int LINE_SLAG = 2;

    public static final net.minecraft.world.level.block.state.properties.IntegerProperty LINE =
            net.minecraft.world.level.block.state.properties.IntegerProperty.create("line", LINE_NONE, LINE_SLAG);

    public RefiningDuctBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LINE, LINE_NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LINE);
    }
}
