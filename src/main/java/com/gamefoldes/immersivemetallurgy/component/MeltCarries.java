package com.gamefoldes.immersivemetallurgy.component;

import com.gamefoldes.immersivemetallurgy.registry.IMDataComponents;

import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.world.item.ItemStack;
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

    /**
     * Assembling a tool joins several pieces, and a welded joint is only as good as its worst part, so each
     * archetype is taken at its worst across the pieces. The composition comes from whichever piece was the
     * most wrong, because that is the melt a re-smelter would have to fix.
     */
    public static void mergeWorstInto(Iterable<ItemStack> pieces, MutableDataComponentHolder to) {
        float brittle = 0;
        float soft = 0;
        float edgeWear = 0;
        float flowy = 0;
        float slagShare = 0;
        AlloyRecord worstRecord = null;
        float worstTotal = -1;

        for (ItemStack piece : pieces) {
            DefectProfile defect = piece.get(IMDataComponents.DEFECT_PROFILE.get());
            if (defect != null) {
                brittle = Math.max(brittle, defect.brittle());
                soft = Math.max(soft, defect.soft());
                edgeWear = Math.max(edgeWear, defect.edgeWear());
                flowy = Math.max(flowy, defect.flowy());
                slagShare = Math.max(slagShare, defect.slagShare());
            }
            AlloyRecord record = piece.get(IMDataComponents.ALLOY_RECORD.get());
            float total = defect == null ? 0 : defect.brittle() + defect.soft() + defect.edgeWear() + defect.flowy();
            if (record != null && total > worstTotal) {
                worstTotal = total;
                worstRecord = record;
            }
        }

        if (worstRecord != null && !to.has(IMDataComponents.ALLOY_RECORD.get())) {
            to.set(IMDataComponents.ALLOY_RECORD.get(), worstRecord);
        }
        DefectProfile merged = new DefectProfile(brittle, soft, edgeWear, flowy, slagShare);
        if (!merged.isClean() && !to.has(IMDataComponents.DEFECT_PROFILE.get())) {
            to.set(IMDataComponents.DEFECT_PROFILE.get(), merged);
        }
    }
}
