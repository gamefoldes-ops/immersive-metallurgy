package immersivemetallurgy.mixin;

import immersivemetallurgy.component.DefectProfile;
import immersivemetallurgy.component.MeltCarries;
import immersivemetallurgy.recipe.OreFouling;
import immersivemetallurgy.registry.IMDataComponents;
import immersivemetallurgy.registry.IMFluids;

import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.dries007.tfc.util.Helpers;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Melting an item that remembers what it was made of produces a fluid that remembers it too, which is what
 * keeps junk in the crucible reversible and lets flawed metal be diluted instead of thrown away.
 *
 * Raw ore is also priced here: the gangue that came out of the ground rides along as a ratio on the yielded
 * fluid, and the alloy turns that ratio into slag when the fluid is actually added. A ratio rather than an
 * amount, because a vessel melts a whole stack at once and multiplies the recipe's millilitres afterwards.
 */
@Mixin(HeatingRecipe.class)
public class HeatingRecipeRecordMixin {
    @Inject(method = "assembleFluid(Lnet/minecraft/world/item/ItemStack;)Lnet/neoforged/neoforge/fluids/FluidStack;",
            at = @At("TAIL"), cancellable = true)
    private void immersiveMetallurgy$carryRecord(ItemStack input, CallbackInfoReturnable<FluidStack> cir) {
        FluidStack output = cir.getReturnValue();
        if (output.isEmpty()) {
            return;
        }
        output = keepTheFlaw(input, output);
        cir.setReturnValue(output);
        MeltCarries.copyInto(input, output);
        OreFouling fouling = OreFouling.of(Helpers.getUnsafeRecipeManager());
        if (fouling == null) {
            return;
        }
        Float ratio = fouling.slagRatioOf(input, Mth.randomBetween(RandomSource.create(), -1F, 1F));
        if (ratio != null && ratio > 0) {
            output.set(IMDataComponents.SLAG_RATIO.get(), ratio);
        }
    }

    /**
     * TFC picks the fluid of a melt from the item's id, and in this mod the fluid id is what carries purity. A
     * plate forged from flawed bronze is still TFC's own plate, so its heating recipe answers with clean bronze,
     * and the flaw would ride on the stack as data that nothing downstream reads: a bath holding one fluid never
     * asks the tolerant matcher what it is, so the next ingot poured out of it came out clean. Metal does not
     * become clean by being melted, so the melt is handed the flawed identity of the same metal.
     */
    @Unique
    private static FluidStack keepTheFlaw(ItemStack input, FluidStack melt) {
        DefectProfile defect = MeltCarries.defectOf(input);
        if (defect == null || defect.isClean()) {
            return melt;
        }
        Fluid flawed = IMFluids.flawedFluidOf(IMFluids.alloyOf(melt.getFluid()));
        // Null for a fluid that is not one of our metals at all, and equal to itself for the items that already
        // pour from the flawed identity - either way TFC's own answer stands.
        return flawed == null || flawed == melt.getFluid() ? melt : new FluidStack(flawed, melt.getAmount());
    }
}
