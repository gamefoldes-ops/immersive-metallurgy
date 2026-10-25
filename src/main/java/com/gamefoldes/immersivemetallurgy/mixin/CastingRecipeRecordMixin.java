package com.gamefoldes.immersivemetallurgy.mixin;

import com.gamefoldes.immersivemetallurgy.component.AlloyRecord;
import com.gamefoldes.immersivemetallurgy.registry.IMDataComponents;

import net.dries007.tfc.common.component.mold.IMold;
import net.dries007.tfc.common.recipes.CastingRecipe;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The mold is still full when {@link CastingRecipe#assemble(IMold)} runs, so the poured composition can be
 * copied onto the cast item here and read back once that item is melted again.
 */
@Mixin(CastingRecipe.class)
public class CastingRecipeRecordMixin {
    @Inject(method = "assemble(Lnet/dries007/tfc/common/component/mold/IMold;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("TAIL"))
    private void immersiveMetallurgy$carryRecord(IMold mold, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack cast = cir.getReturnValue();
        if (cast.isEmpty()) {
            return;
        }
        FluidStack inMold = mold.getFluidInTank(0);
        AlloyRecord record = inMold.get(IMDataComponents.ALLOY_RECORD.get());
        if (record != null && !record.isEmpty()) {
            cast.set(IMDataComponents.ALLOY_RECORD.get(), record);
        }
    }
}
