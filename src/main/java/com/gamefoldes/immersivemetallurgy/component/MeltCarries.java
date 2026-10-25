package com.gamefoldes.immersivemetallurgy.component;

import com.gamefoldes.immersivemetallurgy.registry.IMDataComponents;

import net.minecraft.core.component.DataComponentHolder;
import net.neoforged.neoforge.common.MutableDataComponentHolder;

/**
 * What a melt is made of and how badly it is wrong have to survive every hand over in TFC's metal pipeline,
 * and each of those hand overs builds a fresh stack. One helper keeps the copies from drifting apart. Both
 * {@code ItemStack} and {@code FluidStack} write through the same NeoForge holder, so one method fits every
 * direction the metal travels.
 */
public final class MeltCarries {
    private MeltCarries() {
    }

    public static void copyInto(DataComponentHolder from, MutableDataComponentHolder to) {
        AlloyRecord record = from.get(IMDataComponents.ALLOY_RECORD.get());
        if (record != null && !record.isEmpty() && !to.has(IMDataComponents.ALLOY_RECORD.get())) {
            to.set(IMDataComponents.ALLOY_RECORD.get(), record);
        }

        DefectProfile defect = from.get(IMDataComponents.DEFECT_PROFILE.get());
        if (defect != null && !defect.isClean() && !to.has(IMDataComponents.DEFECT_PROFILE.get())) {
            to.set(IMDataComponents.DEFECT_PROFILE.get(), defect);
        }
    }
}
