package immersivemetallurgy.component;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.world.level.material.Fluid;

/**
 * Implemented onto {@code FluidAlloy} by mixin. A container keeps every melt it holds under its own
 * identity, so flawed metal stays flawed metal in the contents list, but alloying has to be computed on
 * the metals it was actually made of. This is that second view, plus the table it is derived from.
 */
public interface MeltComposition {
    Object2DoubleMap<Fluid> immersiveMetallurgyLeafContent();

    Object2ObjectMap<Fluid, AlloyRecord> immersiveMetallurgyCompositions();

    /**
     * The ratio the first pour out of this bath was made of. It is remembered instead of recomputed because
     * a container that copies its alloy on every operation would otherwise derive it from millilitres that
     * drift by rounding, and a mold refuses a second drip whose record differs from the first one it took.
     */
    AlloyRecord immersiveMetallurgyRecord();

    void immersiveMetallurgySetRecord(AlloyRecord record);

    /**
     * The defect the tolerant matcher worked out for this container's current result. The matcher runs
     * inside TFC's own alloy lookup, which has nowhere to return it, so it leaves it here and the stamping
     * of the result fluid picks it up in the same call.
     */
    DefectProfile immersiveMetallurgyDefect();

    void immersiveMetallurgySetDefect(DefectProfile defect);

    /**
     * Takes up to {@code mB} of one fluid out of what the container holds, and answers what really came out.
     * Skimming slag has to be able to remove dirt without touching the metal, and no public method of the
     * container does that: its own drain is proportional, so it would take metal at the same time.
     */
    int immersiveMetallurgyTake(Fluid fluid, int mB);

    /**
     * Whether a flux is working on this bath right now, which is what makes the next skim take slag without
     * dragging metal after it. It is set when flux meets slag that is molten enough to float, and it dies
     * with the slag, so a fresh batch of dirt needs fresh flux.
     */
    boolean immersiveMetallurgyFluxed();

    void immersiveMetallurgySetFluxed(boolean fluxed);
}
