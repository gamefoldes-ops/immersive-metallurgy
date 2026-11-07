package immersivemetallurgy.furnace;

import immersivemetallurgy.recipe.RefiningFurnaceStats;

import net.dries007.tfc.common.blocks.MoltenBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The bath, seen from outside: TFC's own molten blocks standing up the shaft's centre column, which is how the
 * bloomery and the blast furnace show what is inside them instead of leaving a hole in the wall.
 *
 * Reusing them is deliberate. TFC has no renderer and no API for this — {@code MoltenBlock.manageMoltenBlockTower}
 * is the whole mechanism, and it is what the game's own shaft furnaces call once a second — so our furnace paints
 * its bath through the same method and inherits the same look, the same light, and the same answer when something
 * stands in the shaft. The tower is measured in millilitres rather than in items: TFC's helper counts
 * {@code itemCount / itemsPerLayer} and rounds up into quarter-layers, so handing it the millilitres in there and
 * one layer's {@code layerCapacity} says the same thing in the unit this mod works in. And what is in there is the
 * melt plus whatever has been thrown down the throat and not reached its temperature yet, because the bloomery
 * counts its charge the same way — an empty-looking shaft that is holding twelve ingots is the worst possible read.
 *
 * Two rules come from how our furnace reads its own build rather than from TFC:
 * <ul>
 *   <li>the topmost open layer is the throat and never gets filled — a bath that reaches it is a bath that is
 *       spilling, and nothing could be thrown down the shaft anyway;</li>
 *   <li>{@link FurnaceShape#isShaft} counts a molten cell as open shaft, because the stack is re-scanned every
 *       few seconds and furniture the furnace made itself must not read as a build that came apart.</li>
 * </ul>
 * What the tower cannot do is wear the bath's colour: TFC draws one generic molten texture for every metal, so a
 * brass bath and an iron bath glow the same. The number that says which metal is standing in there is the
 * window's, and it stays the window's.
 */
public final class MoltenTower {
    private MoltenTower() {
    }

    /** The cells the tower may stand in: every layer of the stack except the throat. */
    private static int height(FurnaceShape shape) {
        return Math.max(0, shape.layers() - 1);
    }

    /** The lowest cell of the centre column, one above the sole the hearth is set into. */
    private static BlockPos base(FurnaceShape shape) {
        return shape.center().above(1);
    }

    /**
     * What the tower would look like right now, in quarter-layers, with the fire folded in because a lit shaft
     * draws a different texture than a cold one. The furnace repaints on a change of this rather than every tick:
     * the helper writes blocks, and a bath that is merely warm should not make the world save a column each second.
     */
    public static int signature(FurnaceShape shape, RefiningFurnaceStats stats, int bathMb, boolean lit) {
        if (shape == null || height(shape) <= 0 || bathMb <= 0) {
            return -1;
        }
        int perLayer = Math.max(1, stats.layerCapacity());
        int quarters = (bathMb * 4 + perLayer / 2) / perLayer;
        return quarters * 2 + (lit ? 1 : 0);
    }

    /** Stands the bath up in the shaft, to the height its millilitres ask for. */
    public static void show(Level level, FurnaceShape shape, RefiningFurnaceStats stats, int bathMb, boolean lit) {
        if (shape == null || level == null || level.isClientSide || height(shape) <= 0) {
            return;
        }
        MoltenBlock.manageMoltenBlockTower(level, base(shape), lit, height(shape), Math.max(0, bathMb),
                Math.max(1, stats.layerCapacity()));
    }

    /** Takes the tower down: an empty bath, a stack that came apart, a hearth that stopped existing. */
    public static void hide(Level level, FurnaceShape shape) {
        if (shape == null || level == null || level.isClientSide || height(shape) <= 0) {
            return;
        }
        MoltenBlock.removeMoltenBlockTower(level, base(shape), height(shape));
    }
}
