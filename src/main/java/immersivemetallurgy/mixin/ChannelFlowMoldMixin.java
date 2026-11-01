package immersivemetallurgy.mixin;

import immersivemetallurgy.registry.IMItems;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.dries007.tfc.util.ChannelFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The furnace's mould is sized for a whole module of metal at once, and a crucible has no business filling it:
 * the vessel is the reason to build the shaft at all, and a crucible that could fill it too would leave the
 * furnace as nothing but a slower way to do the same job.
 *
 * This is the one place TFC decides what a channel run may pour into, and it asks only about the fluid and the
 * vessel — which is right for every mould in the game and is the whole reason a per-source rule has to be written
 * here rather than expressed in data. Everything else about a crucible's pour stays TFC's own answer.
 */
@Mixin(ChannelFlow.class)
public class ChannelFlowMoldMixin {
    @Inject(method = "couldBeFilled(Lnet/neoforged/neoforge/items/IItemHandlerModifiable;"
            + "Lnet/neoforged/neoforge/fluids/FluidStack;I)Z", at = @At("HEAD"), cancellable = true)
    private static void immersiveMetallurgy$furnaceMoldStaysForTheFurnace(IItemHandlerModifiable inventory,
                                                                        FluidStack fluid, int amount,
                                                                        CallbackInfoReturnable<Boolean> cir) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (inventory.getStackInSlot(slot).is(IMItems.REFINING_MOLD.get())) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
