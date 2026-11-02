package immersivemetallurgy.mixin;

import immersivemetallurgy.registry.IMItems;

import net.dries007.tfc.common.blockentities.MoldTableBlockEntity;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Refusing to hand the slag plate over.
 *
 * A mold table gives its mould back on a sneak-click, and swaps it for whatever valid mould is held — neither of
 * which is something to argue with, because both are how every other mould in the world is set down and picked
 * up. The slag plate is the exception: it is the fitting a furnace lays in its own slag outlet, and a furnace
 * that has lost it silently stops refining while still burning charcoal. So the table is answered where the
 * vessel is actually kept rather than where the click happened, which means a plate sitting in a table no
 * furnace claims any more still cannot be lifted out of it.
 */
@Mixin(MoldTableBlockEntity.class)
public class MoldTablePlateLockMixin {

    @Inject(method = "onRightClick(Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/"
            + "ItemInteractionResult;", at = @At("HEAD"), cancellable = true)
    private void immersiveMetallurgy$keepTheFitting(Player player, CallbackInfoReturnable<ItemInteractionResult> ci) {
        MoldTableBlockEntity table = (MoldTableBlockEntity) (Object) this;
        if (table.getMoldStack().is(IMItems.SLAG_PLATE.get())) {
            ci.setReturnValue(ItemInteractionResult.CONSUME);
        }
    }
}
