package immersivemetallurgy.block;

import immersivemetallurgy.block.entity.RefiningFurnaceBlockEntity;
import immersivemetallurgy.registry.IMBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Slag that left the bath with nowhere to go. Where the channel's outlet has no plate to catch it, the slag freezes
 * in that cell as a lump of it, and the only way to get at the metal still hanging in it is to break it out with a
 * pickaxe — which is what a slag plate is worth, told as a cost rather than as a bonus.
 *
 * The lump holds no inventory of its own: the amount is kept by the hearth, because the furnace is the thing that
 * knows what the slag was made of, and the block is the evidence of it. Breaking the block is therefore the only
 * gesture that empties it, and it drops dust exactly once.
 */
public class SlagHeapBlock extends Block {
    private static final VoxelShape HEAP_SHAPE = Shapes.box(0.125, 0, 0.125, 0.875, 0.375, 0.875);

    public SlagHeapBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replaced, boolean moving) {
        if (!replaced.is(state.getBlock())) {
            // Whatever is left in the hearth's account for this cell comes out as dust now, once and only once,
            // because the account is cleared by the same call that pays it.
            furnaceFor(level, pos).ifPresent(furnace -> furnace.harvestSlagHeap(pos));
        }
        super.onRemove(state, level, pos, replaced, moving);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return HEAP_SHAPE;
    }

    /** The hearth that owns this cell as its slag outlet, if a furnace still claims it. */
    private static java.util.Optional<RefiningFurnaceBlockEntity> furnaceFor(Level level, BlockPos pos) {
        for (BlockPos near : BlockPos.withinManhattan(pos, 3, 2, 3)) {
            RefiningFurnaceBlockEntity candidate = level.getBlockEntity(near, IMBlockEntities.HEARTH.get()).orElse(null);
            if (candidate != null && candidate.ownsSlagOutlet(pos)) {
                return java.util.Optional.of(candidate);
            }
        }
        return java.util.Optional.empty();
    }
}
