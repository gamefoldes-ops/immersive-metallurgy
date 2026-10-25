package immersivemetallurgy.mixin;

import immersivemetallurgy.component.MeltCarries;

import net.dries007.tfc.common.component.mold.IMold;
import net.dries007.tfc.common.recipes.CastingRecipe;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The mold is still full when {@link CastingRecipe#assemble(IMold)} runs, so the melt's composition and
 * defects can be copied onto the cast item here, before the mold is emptied and that knowledge is gone.
 */
@Mixin(CastingRecipe.class)
public class CastingRecipeRecordMixin {
    @Inject(method = "assemble(Lnet/dries007/tfc/common/component/mold/IMold;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("TAIL"))
    private void immersiveMetallurgy$carryRecord(IMold mold, CallbackInfoReturnable<ItemStack> cir) {
        MeltCarries.copyInto(mold.getFluidInTank(0), cir.getReturnValue());
    }
}
