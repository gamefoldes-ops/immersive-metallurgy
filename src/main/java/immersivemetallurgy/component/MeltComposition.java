package immersivemetallurgy.component;

import immersivemetallurgy.alloy.TolerantAlloyMatcher;

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
     * What the container's own pour was judged to be, when TFC never asked because the bath holds a single
     * fluid. Kept with the record rather than recomputed, because every drip of one pour has to arrive as the
     * same fluid with the same components or the mold refuses to merge it with what it already holds.
     */
    TolerantAlloyMatcher.Match immersiveMetallurgyPourAnswer();

    void immersiveMetallurgySetPourAnswer(TolerantAlloyMatcher.Match answer);

    /**
     * Takes up to {@code mB} of one fluid out of what the container holds, and answers what really came out.
     * Skimming slag has to be able to remove dirt without touching the metal, and no public method of the
     * container does that: its own drain is proportional, so it would take metal at the same time.
     */
    int immersiveMetallurgyTake(Fluid fluid, int mB);

    /**
     * How much of this bath's slag the next skim can lift: 0 while nothing is working on it, 1 when real flux
     * has made the layer thin and cohesive enough to come off whole, and between the two when a re-used basic
     * slag is doing the same job partly. Anything above 0 also means the skim leaves the metal behind. It is
     * set when a flux meets slag hot enough to float, and it dies with the slag, so a fresh batch of dirt
     * needs fresh flux.
     */
    float immersiveMetallurgyFluxPower();

    void immersiveMetallurgySetFluxPower(float power);

    /**
     * The gangue a melt promised but could not yet hand over, in fractions of a millibucket. Slag is a whole
     * unit and a scoop of crushed ore is five, so twelve percent of one melt is not even one - the debt has to
     * survive from one fill to the next or the small change is rounded away and crushed ore looks clean.
     */
    double immersiveMetallurgySlagOwed();

    void immersiveMetallurgySetSlagOwed(double mB);
}
