package immersivemetallurgy.mixin;

import immersivemetallurgy.registry.IMAlloys;
import immersivemetallurgy.registry.IMFluids;

import net.dries007.tfc.common.recipes.BloomeryRecipe;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A bloomery is the only way to wrought iron, so it has to take dirty cast iron: refusing it would leave a
 * player holding flawed pig iron with nowhere to put it. TFC matches the charge on the fluid its recipe names,
 * and this mod says that a melt which remembers being flawed is the flawed fluid, so the two answers never met.
 * It is the same metal either way, and only the purity differs — which the bloom keeps and carries onward.
 */
@Mixin(BloomeryRecipe.class)
public class BloomeryInputMixin {
    @Inject(method = "matchesInput(Lnet/neoforged/neoforge/fluids/FluidStack;)Z", at = @At("TAIL"), cancellable = true)
    private void immersiveMetallurgy$takeDirtyMelt(FluidStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() || stack.isEmpty()) {
            return;
        }
        IMAlloys alloy = IMFluids.alloyOf(stack.getFluid());
        if (alloy == null) {
            return;
        }
        Fluid clean = IMFluids.cleanFluidOf(alloy);
        if (clean == null || clean == stack.getFluid()) {
            return;
        }
        BloomeryRecipe self = (BloomeryRecipe) (Object) this;
        if (self.getInputFluid().test(new FluidStack(clean, stack.getAmount()))) {
            cir.setReturnValue(true);
        }
    }
}
