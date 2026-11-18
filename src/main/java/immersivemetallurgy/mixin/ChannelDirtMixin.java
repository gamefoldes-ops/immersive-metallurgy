package immersivemetallurgy.mixin;

import immersivemetallurgy.furnace.SlagDirt;

import net.dries007.tfc.common.blockentities.ChannelBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A channel remembers the slag that has run through it, in its own saved tag so the count survives a restart, and
 * a clogged channel answers every flow question with no: TFC draws its metal off those answers, so a clogged run
 * goes dry on screen as well as in the furnace's own walk.
 */
@Mixin(ChannelBlockEntity.class)
public abstract class ChannelDirtMixin implements SlagDirt {
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

    @Inject(method = "hasFlow", at = @At("HEAD"), cancellable = true)
    private void immersiveMetallurgy$clogHasFlow(CallbackInfoReturnable<Boolean> cir) {
        if (clogged()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isFlowing", at = @At("HEAD"), cancellable = true)
    private void immersiveMetallurgy$clogIsFlowing(CallbackInfoReturnable<Boolean> cir) {
        if (clogged()) {
            cir.setReturnValue(false);
        }
    }
}