package immersivemetallurgy.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * The hole a route runs through: the tap under the bath, the channel's mouth in the wall, and the column that
 * carries slag down to its outlet. It holds nothing and answers to nothing — which of the three it is the
 * furnace works out from where it stands, and the thing a player opens is the channel laid from the tap outward,
 * because that is the part of a cupola a worker actually has reach of.
 */
public class RefiningDuctBlock extends Block {
    public RefiningDuctBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
