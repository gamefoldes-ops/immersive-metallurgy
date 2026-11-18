package immersivemetallurgy.block;

import immersivemetallurgy.furnace.Launder;

import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.dries007.tfc.common.blocks.devices.ChannelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The launder and a TFC channel in one cell: the trough on top, unchanged, and under it instead of a mold table
 * an ordinary channel that joins the channels beside it and carries the metal on to tables further along.
 *
 * It keeps TFC's own channel block entity, so the connections, the flow drawing and the slag bake-on are TFC's
 * own; only the trough above and the cell's shape are ours. Like the combined table it has no item: a channel
 * laid on a standing launder completes the pair ({@link immersivemetallurgy.event.IMCastingTable}), and breaking
 * it hands both channels back.
 */
public class LaunderChannelBlock extends ChannelBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public LaunderChannelBlock(ExtendedProperties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction aim = Launder.sideOf(context.getLevel(), context.getClickedPos());
        return defaultBlockState().setValue(FACING, aim != null ? aim : context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        BlockState updated = super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        Direction aim = Launder.sideOf(level, pos);
        return aim == null ? updated : updated.setValue(FACING, aim);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.or(super.getShape(state, level, pos, context),
                Launder.shapeFor(state.getValue(FACING)));
    }
}