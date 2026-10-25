package com.gamefoldes.immersivemetallurgy.mixin;

import com.gamefoldes.immersivemetallurgy.component.AlloyRecord;
import com.gamefoldes.immersivemetallurgy.registry.IMDataComponents;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.dries007.tfc.common.component.fluid.FluidContainerInfo;
import net.dries007.tfc.util.FluidAlloy;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Both the crucible and mold vessels collapse their content map into one fluid inside
 * {@link FluidAlloy#getResult(RecipeManager)}, and every drain or tank read is a copy of that result, so
 * stamping the composition there puts it on all metal that leaves a container. Filling in reverse then
 * spreads a recorded stack back over its components, which is what lets junk and flawed metal be refined
 * by adding clean ore instead of being stuck as unknown forever.
 */
@Mixin(FluidAlloy.class)
public class FluidAlloyRecordMixin {
    @Shadow
    private Object2DoubleMap<Fluid> content;

    @Inject(method = "getResult(Lnet/minecraft/world/item/crafting/RecipeManager;)Lnet/neoforged/neoforge/fluids/FluidStack;",
            at = @At("TAIL"))
    private void immersiveMetallurgy$stampComposition(RecipeManager recipes, CallbackInfoReturnable<FluidStack> cir) {
        if (this.content.size() <= 1) {
            return;
        }
        AlloyRecord record = AlloyRecord.fromContent(this.content);
        if (record != null) {
            cir.getReturnValue().set(IMDataComponents.ALLOY_RECORD.get(), record);
        }
    }

    @Inject(method = "fill(Lnet/neoforged/neoforge/fluids/FluidStack;Lnet/neoforged/neoforge/fluids/capability/IFluidHandler$FluidAction;Lnet/dries007/tfc/common/component/fluid/FluidContainerInfo;)I",
            at = @At("HEAD"), cancellable = true)
    private void immersiveMetallurgy$expandComposition(FluidStack stack, IFluidHandler.FluidAction action, FluidContainerInfo capacity, CallbackInfoReturnable<Integer> cir) {
        AlloyRecord record = stack.get(IMDataComponents.ALLOY_RECORD.get());
        if (record == null || record.isEmpty()) {
            return;
        }
        FluidAlloy self = (FluidAlloy) (Object) this;
        int[] filled = {0};
        record.expand(stack.getAmount(), (fluid, part) ->
                filled[0] += self.fill(new FluidStack(fluid, part), action, capacity));
        cir.setReturnValue(filled[0]);
    }
}
