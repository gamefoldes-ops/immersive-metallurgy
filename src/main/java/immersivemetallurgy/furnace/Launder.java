package immersivemetallurgy.furnace;

import immersivemetallurgy.block.RefiningDuctBlock;
import immersivemetallurgy.registry.IMBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The furnace's launder as a place in the world rather than a picture.
 *
 * The trough is drawn, not placed — one cell holds one block and the cell it hangs in belongs to somebody's mold
 * table — but a thing you can walk through is not a thing you built. So the same footprint the renderer uses is
 * written here as a shape, and the table takes it on top of its own. Both of the furnace's holes hang one, so
 * both of them are asked for here, and neither of them asks what it carries.
 */
public final class Launder {
    /** The trough's own box, in the cell's sixteenths, measured with the hole it comes out of on the +x side. */
    private static final int FLOOR = 5;
    private static final int RIM = 10;
    private static final int NEAR = 5;
    private static final int FAR = 16;
    private static final int SIDE = 5;
    private static final int OTHER_SIDE = 11;

    private Launder() {
    }

    /** The side this cell's hole stands on, for the given line, or null when no such hole faces it. */
    @Nullable
    public static Direction lineSide(BlockGetter level, BlockPos cell, int line) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockState near = level.getBlockState(cell.relative(side));
            if (near.is(IMBlocks.REFINING_DUCT.get()) && near.getValue(RefiningDuctBlock.LINE) == line) {
                return side;
            }
        }
        return null;
    }

    /** The side either of the furnace's holes stands on, which is all a shape needs to know. */
    @Nullable
    public static Direction sideOf(BlockGetter level, BlockPos cell) {
        Direction tap = lineSide(level, cell, RefiningDuctBlock.LINE_TAP);
        return tap != null ? tap : lineSide(level, cell, RefiningDuctBlock.LINE_SLAG);
    }

    /** What is running through the hole on this side, if anything: {@link RefiningDuctBlock#FLOW_NONE} for quiet. */
    public static int flow(BlockGetter level, BlockPos cell, @Nullable Direction side) {
        if (side == null) {
            return RefiningDuctBlock.FLOW_NONE;
        }
        BlockState near = level.getBlockState(cell.relative(side));
        return near.is(IMBlocks.REFINING_DUCT.get()) ? near.getValue(RefiningDuctBlock.FLOW)
                : RefiningDuctBlock.FLOW_NONE;
    }

    /**
     * The trough as a shape in this cell, standing against the wall the hole is in.
     *
     * It is given as one box rather than the six pieces the model draws, because a shape the player is pushed by
     * has to be simple and closed, and the difference between the trough's cheeks and its floor is two pixels.
     */
    public static VoxelShape shapeFor(Direction side) {
        return switch (side) {
            case EAST -> box(NEAR, FLOOR, SIDE, FAR, RIM, OTHER_SIDE);
            case WEST -> box(0, FLOOR, SIDE, 16 - NEAR, RIM, OTHER_SIDE);
            case SOUTH -> box(SIDE, FLOOR, NEAR, OTHER_SIDE, RIM, FAR);
            default -> box(SIDE, FLOOR, 0, OTHER_SIDE, RIM, 16 - NEAR);
        };
    }

    private static VoxelShape box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return Shapes.box(minX / 16D, minY / 16D, minZ / 16D, maxX / 16D, maxY / 16D, maxZ / 16D);
    }
}
