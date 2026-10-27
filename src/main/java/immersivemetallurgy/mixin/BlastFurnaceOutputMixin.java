package immersivemetallurgy.mixin;

import immersivemetallurgy.component.MeltCarries;

import net.dries007.tfc.common.recipes.BlastFurnaceRecipe;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A blast furnace melts its charge through the very same heating recipes a crucible uses, so the gangue the
 * ore carried does come in with it — and then this call answers with the fluid the recipe names, which knows
 * nothing about any of that. Without the carry, a stack of dirty ore driven through a blast furnace would come
 * out cleaner than the same stack smelted anywhere else in the game, and the tap on a refining furnace would
 * have nothing left to be worth building for.
 */
@Mixin(BlastFurnaceRecipe.class)
public class BlastFurnaceOutputMixin {
    @Inject(method = "assembleFluidOutput(Lnet/neoforged/neoforge/fluids/FluidStack;)Lnet/neoforged/neoforge/fluids/FluidStack;",
            at = @At("TAIL"), cancellable = true)
    private void immersiveMetallurgy$carryTheMelt(FluidStack input, CallbackInfoReturnable<FluidStack> cir) {
        FluidStack output = cir.getReturnValue();
        if (output.isEmpty() || input.isEmpty()) {
            return;
        }
        FluidStack carried = MeltCarries.keepTheFlaw(input, output);
        if (carried == output) {
            MeltCarries.copyInto(input, output);
        }
        cir.setReturnValue(carried);
    }
}
