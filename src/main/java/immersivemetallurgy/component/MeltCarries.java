package immersivemetallurgy.component;

import immersivemetallurgy.registry.IMDataComponents;

import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.MutableDataComponentHolder;

/**
 * What a melt is made of, how badly it is wrong and what that costs the object have to survive every hand
 * over in TFC's metal pipeline, and each of those hand overs builds a fresh stack. One helper keeps the
 * copies from drifting apart. Both {@code ItemStack} and {@code FluidStack} write through the same NeoForge
 * holder, so one method fits every direction the metal travels.
 */
public final class MeltCarries {
    /** A freshly forged edge, and the ceiling honing alone cannot push past without a different repair. */
    public static final float FULL_SHARPNESS = 100F;

    private MeltCarries() {
    }

    public static float sharpnessOf(ItemStack stack) {
        Float sharpness = stack.get(IMDataComponents.SHARPNESS.get());
        return sharpness == null ? FULL_SHARPNESS : sharpness;
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
        // The dirt a pour left in the metal stays in the scrap. Without this a flawed ingot melted next to
        // clean copper launders itself into clean bronze without a single skim, because the slag only ever
        // rode on the fluid and never came back with the item.
        if (defect != null && defect.slagShare() > 0 && !to.has(IMDataComponents.SLAG_RATIO.get())) {
            to.set(IMDataComponents.SLAG_RATIO.get(), defect.slagShare() / (1F - defect.slagShare()));
        }
        priceTheDefect(to);
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
        float durability = 1;
        int grade = 0;
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
                durability = Math.min(durability, defect.durabilityFactor());
                grade = Math.max(grade, defect.grade());
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
        if (!to.has(IMDataComponents.DEFECT_PROFILE.get())) {
            DefectProfile merged = new DefectProfile(brittle, soft, edgeWear, flowy, slagShare, durability, grade);
            if (!merged.isClean()) {
                to.set(IMDataComponents.DEFECT_PROFILE.get(), merged);
            }
        }
        priceTheDefect(to);
    }

    /**
     * Wear applies to every metal, clean included, so a freshly made edge always gets its sharpness. A
     * flawed tool additionally starts life with its lower ceiling already full: the defect is a shorter
     * object, not a half-used one. Only ever applied to a fresh stack, which the sharpness marker tells us,
     * because applying it twice would shrink the same tool again.
     */
    private static void priceTheDefect(MutableDataComponentHolder holder) {
        if (!(holder instanceof ItemStack stack) || !stack.isDamageableItem() || stack.has(IMDataComponents.SHARPNESS.get())) {
            return;
        }
        DefectProfile defect = stack.get(IMDataComponents.DEFECT_PROFILE.get());
        if (defect != null && !defect.isClean()) {
            stack.set(DataComponents.MAX_DAMAGE, Math.max(1, Math.round(stack.getMaxDamage() * defect.durabilityFactor())));
        }
        stack.set(IMDataComponents.SHARPNESS.get(), FULL_SHARPNESS);
    }
}
