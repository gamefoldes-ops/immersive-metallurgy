package immersivemetallurgy.mixin;

import immersivemetallurgy.component.MeltComposition;
import immersivemetallurgy.component.MeltLedger;
import immersivemetallurgy.registry.IMDataComponents;

import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
import net.dries007.tfc.util.FluidAlloy;
import net.minecraft.core.component.DataComponentMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A crucible that travels as a block item keeps its melt in TFC's own component, which is {@code FluidAlloy}'s
 * codec, and that codec has nowhere for the table of what each fluid was made of. Without the table a flawed
 * pour arrives back on the forge as a fluid nothing can expand, so the table is written beside the alloy on
 * the way out and read back by {@link CrucibleLedgerRestoreMixin} on the way in.
 */
@Mixin(CrucibleBlockEntity.class)
public class CrucibleLedgerCaptureMixin {
    @Inject(method = "collectImplicitComponents(Lnet/minecraft/core/component/DataComponentMap$Builder;)V", at = @At("TAIL"))
    private void immersiveMetallurgy$writeLedger(DataComponentMap.Builder builder, CallbackInfo ci) {
        FluidAlloy alloy = ((CrucibleBlockEntity) (Object) this).getAlloy();
        MeltLedger ledger = MeltLedger.of(((MeltComposition) (Object) alloy).immersiveMetallurgyCompositions());
        // Setting null clears the entry, so an emptied crucible does not carry an old pour around.
        builder.set(IMDataComponents.MELT_LEDGER.get(), ledger);
    }
}
