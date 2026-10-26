package immersivemetallurgy.mixin;

import immersivemetallurgy.component.MeltComposition;
import immersivemetallurgy.component.MeltLedger;
import immersivemetallurgy.registry.IMDataComponents;

import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The other half of {@link CrucibleLedgerCaptureMixin}. TFC has already put the melt into the block entity by
 * the time vanilla finishes handing the item's components over, so the table can simply be poured back into
 * the alloy it describes. It is hooked here rather than on the crucible's own component reader because that
 * reader's argument type is a protected interface inside {@code BlockEntity} and cannot be named from a mixin.
 */
@Mixin(BlockEntity.class)
public class CrucibleLedgerRestoreMixin {
    @Inject(method = "applyComponentsFromItemStack(Lnet/minecraft/world/item/ItemStack;)V", at = @At("TAIL"))
    private void immersiveMetallurgy$readLedger(ItemStack stack, CallbackInfo ci) {
        if ((Object) this instanceof CrucibleBlockEntity crucible) {
            MeltLedger ledger = stack.get(IMDataComponents.MELT_LEDGER.get());
            if (ledger != null) {
                ledger.applyTo(((MeltComposition) (Object) crucible.getAlloy()).immersiveMetallurgyCompositions());
            }
        }
    }
}
