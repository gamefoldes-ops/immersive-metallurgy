package immersivemetallurgy.component;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.recipe.FlawProfiles;
import immersivemetallurgy.registry.IMDataComponents;
import immersivemetallurgy.registry.IMFluids;

import net.dries007.tfc.util.Helpers;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
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

        DefectProfile defect = measuredOrImplied(from);
        if (defect != null && !defect.isClean() && !to.has(IMDataComponents.DEFECT_PROFILE.get())) {
            to.set(IMDataComponents.DEFECT_PROFILE.get(), defect);
        }
        // The dirt a pour left in the metal stays in the scrap. Without this a flawed ingot melted next to
        // clean copper launders itself into clean bronze, because the slag only ever rode on the fluid and never
        // came back with the item.
        Float owed = from.get(IMDataComponents.SLAG_RATIO.get());
        if (owed != null && owed > 0 && !to.has(IMDataComponents.SLAG_RATIO.get())) {
            to.set(IMDataComponents.SLAG_RATIO.get(), owed);
        } else if (defect != null && defect.slagShare() > 0 && !to.has(IMDataComponents.SLAG_RATIO.get())) {
            to.set(IMDataComponents.SLAG_RATIO.get(), defect.slagShare() / (1F - defect.slagShare()));
        }
        priceTheDefect(to);
    }

    /**
     * A pour's identity is the fluid it is made of, and only a flawed metal has a flawed fluid, so this is the
     * one place that decides whether a melt keeps the clean name whatever produced it handed it. TFC picks that
     * name from the item id or from a recipe's own output, and neither of them knows that the metal remembers
     * being wrong: a plate forged from flawed bronze is TFC's plate, and the blast furnace's answer is cast
     * iron by recipe. Metal does not become clean by being moved, so the melt is relabelled here.
     */
    public static FluidStack keepTheFlaw(DataComponentHolder source, FluidStack melt) {
        DefectProfile defect = measuredOrImplied(source);
        if (defect == null || defect.isClean()) {
            return melt;
        }
        Fluid flawed = IMFluids.flawedFluidOf(IMFluids.alloyOf(melt.getFluid()));
        if (flawed == null || flawed == melt.getFluid()) {
            return melt;
        }
        FluidStack relabelled = new FluidStack(flawed, melt.getAmount());
        copyInto(source, relabelled);
        return relabelled;
    }

    /**
     * Assembling a tool joins several pieces, and a welded joint is weak wherever either half was, so each
     * archetype adds up across the pieces. The total is then shared out, because two identical halves should
     * cost exactly what one of them did and a doubled severity would price a careful smith out of steel.
     * The composition comes from whichever piece was the most wrong, because that is the melt a re-smelter
     * would have to fix.
     */
    public static void mergeWorstInto(Iterable<ItemStack> pieces, MutableDataComponentHolder to) {
        float divisor = 1F;
        FlawProfiles profiles = FlawProfiles.of(Helpers.getUnsafeRecipeManager());
        if (profiles != null) {
            divisor = profiles.weldMergeDivisor();
        }
        mergeWorstInto(pieces, to, divisor);
    }

    /**
     * As above, with the sharing written out. A bloom gets the full dose rather than a shared one: nothing
     * drives slag out of a bloom in the stack of charcoal it was fired in, and the step that does is the
     * anvil — so halving here would make the bloomery a cleaner, which is exactly what it must not be.
     */
    public static void mergeWorstInto(Iterable<ItemStack> pieces, MutableDataComponentHolder to, float divisor) {
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
            DefectProfile defect = measuredOrImplied(piece);
            if (defect != null) {
                brittle += defect.brittle();
                soft += defect.soft();
                edgeWear += defect.edgeWear();
                flowy += defect.flowy();
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
            // Welding two flawed halves does not average them out: a joint is weak wherever either half was,
            // and now and then the two faults feed each other. Sharing the sum by two keeps a weld of two
            // equal halves at exactly the severity of either one.
            FlawProfiles profiles = FlawProfiles.of(Helpers.getUnsafeRecipeManager());
            brittle = Math.min(1F, brittle / divisor);
            soft = Math.min(1F, soft / divisor);
            edgeWear = Math.min(1F, edgeWear / divisor);
            flowy = Math.min(1F, flowy / divisor);
            if (profiles != null) {
                grade = profiles.grade(Math.max(brittle, Math.max(soft, Math.max(edgeWear, flowy))));
            }
            DefectProfile merged = new DefectProfile(brittle, soft, edgeWear, flowy, slagShare, durability, grade);
            if (!merged.isClean()) {
                to.set(IMDataComponents.DEFECT_PROFILE.get(), merged);
            }
        }
        priceTheDefect(to);
    }

    /**
     * The defect a piece of metal has, measured or — when nothing was ever measured on it — the one its
     * identity implies. Melting needs the answer before it knows what fluid will come out, and a thing that
     * was forged from flawed metal is flawed metal however its stack was made.
     */
    public static DefectProfile defectOf(DataComponentHolder holder) {
        return measuredOrImplied(holder);
    }

    /**
     * The flaw a piece carries because of what it is, when nothing was ever measured on it. A flawed ingot
     * lifted out of a chest or a creative tab has no profile attached, but it is still flawed metal, and metal
     * does not become clean by being hammered: without this, a part forged from it read as better than the
     * ingot it was beaten out of.
     */
    private static DefectProfile measuredOrImplied(DataComponentHolder holder) {
        DefectProfile measured = holder.get(IMDataComponents.DEFECT_PROFILE.get());
        if (measured != null && !measured.isClean()) {
            return measured;
        }
        if (!(holder instanceof ItemStack stack) || !isFlawedIdentity(stack)) {
            return measured;
        }
        FlawProfiles profiles = FlawProfiles.of(Helpers.getUnsafeRecipeManager());
        return profiles == null ? measured : profiles.baselineFlaw();
    }

    private static boolean isFlawedIdentity(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return ImmersiveMetallurgy.MODID.equals(id.getNamespace()) && id.getPath().contains("/flawed_");
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
