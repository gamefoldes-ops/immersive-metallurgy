package immersivemetallurgy.furnace;

import immersivemetallurgy.registry.IMBlocks;
import immersivemetallurgy.registry.IMTags;

import net.dries007.tfc.common.blocks.TFCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape a refining furnace has to be built in, and the two routes out of the bath.
 *
 * The body is a 3x3 stack of refractory walls around an open column: the sole on the ground, one metal-carrying
 * layer per block of height above it, and the top layer standing open to the sky as the throat everything is
 * charged through. The door stands one layer above the sole, in the wall over the tap, so the sole is a full ring
 * and the tap lies below the door the way a cupola's tap lies below its working door. What a furnace holds is
 * therefore its own height written out — one layer, one module, one pour — and no tower is needed to reach the big
 * capacities. The duct and the firebrick are interchangeable in a wall, so a stack may be faced entirely in ducts
 * or entirely in brick and still be the same furnace.
 *
 * The two routes are read off the wall over the duct, because the two outputs sit at different depths in the bath.
 * Metal is the heavy thing and lies at the bottom, so the tap is the duct in the floor with brick over it, and the
 * cell outside it is where the metal goes: TFC's own channel and mold table, which the player lays beside the
 * furnace and clicks on the channel. Slag floats, so the channel sits a fixed number of layers below the top of
 * the stack and the slag has to be able to run down to the floor from there: the same duct with a column of ducts
 * over it is the slag route, and the cell outside it holds a table with a slag plate on it — or, with nothing to
 * catch it there, the slag freezes into a lump that has to be broken out with a pickaxe.
 *
 * The tap outlet may stand empty — the metal simply waits in the bath until a table and mould are laid — but the
 * slag outlet must already hold a table, because the catcher is a fitting of the machine rather than an extra. And
 * a bath only reaches the slag channel if it was filled to match the height the player chose, which is what makes
 * a tall furnace something to plan rather than to keep extending.
 */
public record FurnaceShape(int modules, int layers, int channelLayer, Direction facing, BlockPos hearth,
                           BlockPos center, BlockPos tapWall, BlockPos tapOutlet, BlockPos slagWall,
                           BlockPos slagOutlet, BlockPos channel) {

    /** Which of the two outlets a cell outside the sole is, when the hearth owns it at all. */
    public enum Outlet { TAP, SLAG }

    /**
     * What stopped a stack from being a furnace. The hearth shows the first of these it found, because a build
     * that silently refuses is a puzzle with no clues.
     */
    public enum Fault {
        NONE("none"),
        NOT_FACING("not_facing"),
        WALL_NOT_BRICK("wall_not_brick"),
        NO_CASTING("no_casting"),
        NO_SLAG("no_slag"),
        TWO_CASTING("two_casting"),
        TWO_SLAG("two_slag"),
        BAD_COLUMN("bad_column"),
        BAD_HEIGHT("bad_height"),
        THROAT_BLOCKED("throat_blocked"),
        NO_SLAG_TABLE("no_slag_table");

        private final String key;

        Fault(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    /**
     * The scan's answer: the shape when there is one, and the first thing that stopped it when there is not. The
     * two wall holes are carried even on a fault, because they are read off the build itself — a slag column is a
     * slag column whether or not its catcher is laid yet — and the player can only lay that catcher once the hole
     * says which line it is. Withholding the walls until the stack is complete would lock the slag outlet shut:
     * no conduit means no mark, and no mark means no conduit can be placed.
     */
    public record Result(FurnaceShape shape, Fault fault, @Nullable BlockPos tapWall, @Nullable BlockPos slagWall) {
        public boolean formed() {
            return shape != null;
        }

        public static Result of(FurnaceShape shape) {
            return new Result(shape, Fault.NONE, shape.tapWall(), shape.slagWall());
        }

        public static Result fault(Fault fault) {
            return new Result(null, fault, null, null);
        }

        public static Result fault(Fault fault, BlockPos tapWall, BlockPos slagWall) {
            return new Result(null, fault, tapWall, slagWall);
        }
    }

    /** The cell one further out from the centre than this wall cell, which is where the furnace's output lands. */
    public static BlockPos outside(BlockPos center, BlockPos wall) {
        return wall.relative(direction(center, wall));
    }

    /** Whether this position is one of the furnace's two outlets, and which — null when it is neither. */
    public Outlet outlet(BlockPos pos) {
        if (pos.equals(tapOutlet)) {
            return Outlet.TAP;
        }
        return pos.equals(slagOutlet) ? Outlet.SLAG : null;
    }

    public static Result scan(LevelAccessor level, BlockPos hearth, Direction facing, int maxModules,
                              int channelOffsetFromTop) {
        if (facing.getAxis() == Direction.Axis.Y) {
            return Result.fault(Fault.NOT_FACING);
        }
        BlockPos center = hearth.relative(facing).below(1);

        BlockPos tapWall = null;
        BlockPos slagWall = null;
        for (BlockPos wall : soleWalls(center)) {
            if (!isWall(level, wall)) {
                return Result.fault(Fault.WALL_NOT_BRICK);
            }
            if (!isDuct(level, wall)) {
                continue;
            }
            // The cell over the floor duct says what is supposed to leave past it: brick caps the bath in for the
            // metal lying at the bottom of it, and a duct hands the slag down from the channel higher up.
            if (isDuct(level, wall.above(1))) {
                if (slagWall != null) {
                    return Result.fault(Fault.TWO_SLAG);
                }
                slagWall = wall;
            } else {
                if (tapWall != null) {
                    return Result.fault(Fault.TWO_CASTING);
                }
                tapWall = wall;
            }
        }
        if (tapWall == null) {
            return Result.fault(Fault.NO_CASTING);
        }
        if (slagWall == null) {
            return Result.fault(Fault.NO_SLAG);
        }
        if (!isWall(level, center)) {
            // The floor under the bath has to be something: the tap runs out of it, and the metal rests on it.
            return Result.fault(Fault.WALL_NOT_BRICK);
        }

        int layers = shaftHeight(level, center, maxModules + 1);
        int modules = layers - 1;
        if (modules < 1 || modules > maxModules) {
            return Result.fault(Fault.BAD_HEIGHT);
        }
        // The topmost open layer is the throat, so it has to stand open: without it nothing can be charged, and
        // a capped column would be counted as metal it can never hold.
        if (!isShaft(level.getBlockState(center.above(layers + 1)))) {
            return Result.fault(Fault.THROAT_BLOCKED);
        }

        int channelLayer = modules + 1 - channelOffsetFromTop;
        // The channel is only a channel if the slag can actually reach the floor: every cell between the two has
        // to be duct, or the bath would drain into a brick. Nothing may run above it either, or a bath that was
        // never filled to the height this stack asks for would find a hole and leave anyway.
        for (int layer = 1; layer <= channelLayer; layer++) {
            if (!isDuct(level, slagWall.above(layer))) {
                return Result.fault(Fault.BAD_COLUMN);
            }
        }
        for (int layer = channelLayer + 1; layer <= layers; layer++) {
            for (BlockPos wall : shaftWalls(center, layer)) {
                if (isDuct(level, wall)) {
                    return Result.fault(Fault.BAD_COLUMN);
                }
            }
        }

        // The slag catcher is a fitting of the machine, not an optional extra: a furnace expensive enough to build
        // is expected to stand complete, so the stack is not a furnace until a conduit starts at the slag outlet —
        // a launder or a TFC channel, or the combined table that carries its own launder. A bare mold table there
        // is not a slag run: the slag has to leave the hole through clay it can bake onto, and the table with the
        // plate may wait further along the run, or not stand at all until the player wants the slag caught.
        BlockPos slagOutlet = outside(center, slagWall);
        if (!isConduit(level, slagOutlet) && !level.getBlockState(slagOutlet).is(IMBlocks.CASTING_TABLE.get())) {
            // The walls are still marked so the player can lay the very conduit this fault is waiting for.
            return Result.fault(Fault.NO_SLAG_TABLE, tapWall, slagWall);
        }

        return Result.of(new FurnaceShape(modules, layers, channelLayer, facing, hearth, center, tapWall,
                outside(center, tapWall), slagWall, slagOutlet, slagWall.above(channelLayer)));
    }

    /** How many open layers stand over the sole: the centre empty, the eight cells around it wall, duct or door. */
    private static int shaftHeight(LevelAccessor level, BlockPos center, int limit) {
        for (int layer = 1; layer <= limit; layer++) {
            if (!isShaft(level.getBlockState(center.above(layer)))) {
                return layer - 1;
            }
            for (BlockPos wall : shaftWalls(center, layer)) {
                if (!isWall(level, wall) && !isHearth(level, wall)) {
                    return layer - 1;
                }
            }
        }
        return limit;
    }

    /** The eight wall cells of the sole: a full ring now that the door stands a layer above it. */
    private static List<BlockPos> soleWalls(BlockPos center) {
        return shaftWalls(center, 0);
    }

    /** The eight wall cells of one layer of the shaft, counted from the sole upward. */
    private static List<BlockPos> shaftWalls(BlockPos center, int layer) {
        List<BlockPos> walls = new ArrayList<>(8);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x != 0 || z != 0) {
                    walls.add(center.offset(x, layer, z));
                }
            }
        }
        return walls;
    }

    /**
     * Which way one cell sits from another, on the flat plane. Written out because 1.21.1's own version of this
     * went by a different name in every recent version, and a furnace that reads its own walls should not care.
     */
    public static Direction direction(BlockPos from, BlockPos to) {
        int y = to.getY() - from.getY();
        if (y > 0) {
            return Direction.UP;
        }
        if (y < 0) {
            return Direction.DOWN;
        }
        int x = to.getX() - from.getX();
        int z = to.getZ() - from.getZ();
        if (x > 0) {
            return Direction.EAST;
        }
        if (x < 0) {
            return Direction.WEST;
        }
        if (z > 0) {
            return Direction.SOUTH;
        }
        return Direction.NORTH;
    }

    private static boolean isShaft(BlockState state) {
        // Air is an open shaft. So is the molten column the furnace stands in its own shaft — that tower is
        // furniture the hearth paints from the bath, not something the player built, and a stack that read its own
        // bath as a wall would shorten itself on the next scan and then call that a fault.
        return state.isAir() || state.canBeReplaced() || state.is(TFCBlocks.MOLTEN.get());
    }

    /** A wall cell is refractory brick or the duct a route runs through, and the two are one and the same wall. */
    private static boolean isWall(LevelAccessor level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(IMTags.REFINING_REFRACTORY) || isDuct(level, pos);
    }

    private static boolean isDuct(LevelAccessor level, BlockPos pos) {
        return level.getBlockState(pos).is(IMBlocks.REFINING_DUCT.get());
    }

    /** A cell a slag run can start in: TFC's channel or our own launder, both of which carry it onward. */
    private static boolean isConduit(LevelAccessor level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(TFCBlocks.CHANNEL.get()) || state.is(IMBlocks.LAUNDER.get())
                || state.is(IMBlocks.LAUNDER_CHANNEL.get());
    }

    /** The door is a wall the player stands at: it closes one cell of the layer it stands in, one above the sole. */
    private static boolean isHearth(LevelAccessor level, BlockPos pos) {
        return level.getBlockState(pos).is(IMBlocks.REFINING_FURNACE.get());
    }
}
