package immersivemetallurgy.mixin;

import immersivemetallurgy.furnace.SlagDirt;
import immersivemetallurgy.registry.IMDataComponents;

import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A crucible that slag has run into keeps the bake-on count beside its melt, in the saved tag and on the block
 * item, so a crucible moved from a slag run to a melting fire still carries what it was used for. Unlike a channel
 * it never clogs: a crucible is a pot, and a pot can always be filled again — the dirt is a record, not a blockage.
 */
@Mixin(CrucibleBlockEntity.class)
public abstract class CrucibleDirtMixin implements SlagDirt {
    @Unique
    private int immersiveMetallurgy$slagDirt;

    @Override
    public int slagDirt() {
        return immersiveMetallurgy$slagDirt;
    }

    @Override
    public void slagDirt(int dirt) {
        immersiveMetallurgy$slagDirt = dirt;
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void immersiveMetallurgy$saveDirt(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        tag.putInt("im_slag_dirt", immersiveMetallurgy$slagDirt);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void immersiveMetallurgy$loadDirt(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        immersiveMetallurgy$slagDirt = tag.getInt("im_slag_dirt");
    }

    @Inject(method = "collectImplicitComponents", at = @At("TAIL"))
    private void immersiveMetallurgy$writeDirt(DataComponentMap.Builder builder, CallbackInfo ci) {
        builder.set(IMDataComponents.SLAG_DIRT.get(), immersiveMetallurgy$slagDirt > 0 ? immersiveMetallurgy$slagDirt : null);
    }
}