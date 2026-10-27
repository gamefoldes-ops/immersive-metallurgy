package immersivemetallurgy.mixin;

import immersivemetallurgy.component.MeltCarries;
import immersivemetallurgy.recipe.OreFouling;
import immersivemetallurgy.registry.IMDataComponents;

import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.dries007.tfc.util.Helpers;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
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
        output = MeltCarries.keepTheFlaw(input, output);
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
}
