package immersivemetallurgy.block.entity;

import immersivemetallurgy.registry.IMBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The launder remembers nothing: it is clay the metal runs down. The entity exists only so the stream has a
 * renderer to hang on — what is flowing is read off the duct behind it and the sink ahead of it, live, every frame.
 */
public class LaunderBlockEntity extends BlockEntity {
    public LaunderBlockEntity(BlockPos pos, BlockState state) {
        super(IMBlockEntities.LAUNDER.get(), pos, state);
    }
}