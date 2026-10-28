package immersivemetallurgy.mixin;

import immersivemetallurgy.alloy.TolerantAlloyMatcher;
import immersivemetallurgy.component.DefectProfile;
import immersivemetallurgy.component.MeltCarries;
import immersivemetallurgy.registry.IMDataComponents;
import immersivemetallurgy.registry.IMFluids;

import net.dries007.tfc.common.blockentities.BloomeryBlockEntity;
import net.dries007.tfc.common.recipes.BloomeryRecipe;
import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.dries007.tfc.util.Helpers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A bloomery is not a cleaner. It has a stack of charcoal and no flux, so nothing takes the rock out of the
 * iron: real blooms come out full of slag stringers, and that is exactly why they were shingled and welded on
 * the anvil before use. TFC builds the bloom as a fresh stack out of nothing but the recipe, so an ore charge
 * would hand over clean iron and the whole wrought iron and steel line would launder itself at this one step.
 * So the bloom now carries both things the charge knew: the defect of metal that was already flawed, and the
 * gangue of rock that was never metal at all.
 */
@Mixin(BloomeryBlockEntity.class)
public class BloomeryOutputMixin {
    @Redirect(method = "completeRecipe()V",
            at = @At(value = "INVOKE", target = "Lnet/dries007.tfc/common/recipes/BloomeryRecipe;assembleOutput()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack immersiveMetallurgy$carryTheFlaw(BloomeryRecipe recipe) {
        ItemStack bloom = recipe.assembleOutput();
        BloomeryBlockEntity self = (BloomeryBlockEntity) (Object) this;
        // Shared by one, not by the weld divisor: a bloom is fired out of a charge, and nothing in a stack of
        // charcoal takes slag out of the iron.
        MeltCarries.mergeWorstInto(self.getInputStacks(), bloom, 1F);

        double metal = 0;
        double slag = 0;
        Fluid metalFluid = null;
        for (ItemStack charge : self.getInputStacks()) {
            if (charge.isEmpty()) {
                continue;
            }
            HeatingRecipe heating = HeatingRecipe.getRecipe(charge);
            if (heating == null) {
                continue;
            }
            FluidStack melt = heating.assembleFluid(charge);
            if (melt.isEmpty()) {
                continue;
            }
            if (metalFluid == null && IMFluids.alloyOf(melt.getFluid()) != null) {
                metalFluid = melt.getFluid();
            }
            Float ratio = melt.get(IMDataComponents.SLAG_RATIO.get());
            double share = ratio == null || ratio <= 0 ? 0D : ratio / (1D + ratio);
            slag += melt.getAmount() * share;
            metal += melt.getAmount() * (1D - share);
        }
        if (metalFluid == null || metal <= 0 || slag <= 0) {
            return bloom;
        }
        DefectProfile gangue = TolerantAlloyMatcher.slagProfile(Helpers.getUnsafeRecipeManager(),
                metalFluid, slag / (metal + slag));
        if (gangue.isClean()) {
            return bloom;
        }
        DefectProfile carried = bloom.get(IMDataComponents.DEFECT_PROFILE.get());
        bloom.set(IMDataComponents.DEFECT_PROFILE.get(), carried == null ? gangue : carried.worseThan(gangue));
        return bloom;
    }
}
