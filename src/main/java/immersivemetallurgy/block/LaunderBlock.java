package immersivemetallurgy.block;

import immersivemetallurgy.block.entity.LaunderBlockEntity;
import immersivemetallurgy.furnace.Launder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The launder on its own: the trough a player lays at the furnace's outlet before any table stands under it. It is
 * the channel half of the casting table with no board and no vessel, so it holds nothing and casts nothing — it is
 * simply the clay the metal will run down once a table is added. The table is not clicked onto it: it is placed the
 * way a table normally is, on the ground the table stands on, and because that cell already holds this trough the
 * placement completes the pair into {@link CastingTableBlock} instead of failing ({@link
 * immersivemetallurgy.event.IMCastingTable}).
 *
 * Like the combined table it has no item of its own: a player places TFC's own channel at the outlet and it is
 * swapped for this trough, aimed at the duct that feeds it. Breaking it hands the TFC channel back.
 */
public class LaunderBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public LaunderBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaunderBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction aim = Launder.sideOf(context.getLevel(), context.getClickedPos());
        return defaultBlockState().setValue(FACING, aim != null ? aim : context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Direction aim = Launder.sideOf(level, pos);
        return aim == null ? state : state.setValue(FACING, aim);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Launder.shapeFor(state.getValue(FACING));
    }
}