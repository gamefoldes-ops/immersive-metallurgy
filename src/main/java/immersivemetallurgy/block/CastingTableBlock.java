package immersivemetallurgy.block;

import immersivemetallurgy.furnace.Launder;

import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.dries007.tfc.common.blocks.devices.MoldTableBlock;
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
 * A mold table with the furnace's launder built onto it, as one block the player places.
 *
 * The trough used to be drawn over somebody's plain table by the table's own renderer, which meant a channel a
 * player never laid and a hood that renderer could not be trusted to show. Here the clay is part of the block:
 * the board is TFC's own mold table, model and all, and the trough — cheeks cut back at forty-five degrees toward
 * the open end, a hood over the mouth — is a model of ours turned to {@link #FACING}. The block still creates
 * TFC's own {@code MoldTableBlockEntity}, because TFC's channel and crucible look the table up by that exact type
 * and would not fill a vessel standing on a block entity of our own; so every pour, cast and cooling recipe TFC
 * runs on a table runs on this one too, and the table's renderer draws the metal and the vessels on it as before.
 *
 * {@link #FACING} is the side the hole stands on. It is aimed at the neighbouring duct that carries a line, both
 * when the block is set down and whenever a neighbour changes, because the furnace writes a duct's line only once
 * the stack is assembled — which may be after the table is already standing here.
 */
public class CastingTableBlock extends MoldTableBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public CastingTableBlock(ExtendedProperties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        Direction aim = Launder.sideOf(context.getLevel(), context.getClickedPos());
        return state.setValue(FACING, aim != null ? aim : context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        BlockState updated = super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        Direction aim = Launder.sideOf(level, pos);
        return aim == null ? updated : updated.setValue(FACING, aim);
    }

    /** The board TFC gives a table, plus the trough standing against the wall the hole is in. */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.or(super.getShape(state, level, pos, context), Launder.shapeFor(state.getValue(FACING)));
    }
}