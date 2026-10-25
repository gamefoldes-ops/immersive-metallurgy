package com.gamefoldes.immersivemetallurgy.mixin;

import com.gamefoldes.immersivemetallurgy.ImmersiveMetallurgy;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.dries007.tfc.util.FluidAlloy;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Temporary probe: verifies that TFC's own classes can be intercepted and that
 * mojmap field/method names resolve without a refmap.
 */
@Mixin(FluidAlloy.class)
public class CrucibleResultProbeMixin {
    @Shadow
    private Object2DoubleMap<Fluid> content;

    @Shadow
    private int amount;

    @Inject(method = "getResult(Lnet/minecraft/world/item/crafting/RecipeManager;)Lnet/neoforged/neoforge/fluids/FluidStack;", at = @At("TAIL"))
    private void immersiveMetallurgy$logAlloyResult(RecipeManager recipes, CallbackInfoReturnable<FluidStack> cir) {
        if (this.content.isEmpty()) {
            return;
        }
        ImmersiveMetallurgy.LOGGER.debug("Alloy probe: amount={} content={} result={}",
                this.amount, this.content, cir.getReturnValue());
    }
}
