package com.gamefoldes.immersivemetallurgy.component;

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
     * The defect the tolerant matcher worked out for this container's current result. The matcher runs
     * inside TFC's own alloy lookup, which has nowhere to return it, so it leaves it here and the stamping
     * of the result fluid picks it up in the same call.
     */
    DefectProfile immersiveMetallurgyDefect();

    void immersiveMetallurgySetDefect(DefectProfile defect);
}
