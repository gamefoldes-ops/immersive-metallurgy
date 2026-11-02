package immersivemetallurgy.mixin;

import immersivemetallurgy.registry.IMItems;

import net.dries007.tfc.common.blockentities.MoldTableBlockEntity;
import net.dries007.tfc.common.blocks.devices.MoldTableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeping the slag plate in the table when the table goes.
 *
 * A table being taken away gives back whatever mould it holds, and the furnace's slag plate is not a mould a
 * player owns: it is the fitting the furnace laid in its own slag outlet, and a furnace that loses it silently
 * stops refining. So the plate leaves no item behind — the table's own drops carry the cast in the other slot,
 * and the ground under a broken outlet is no place to find a vessel that was never meant to be carried.
 */
@Mixin(MoldTableBlock.class)
public class MoldTablePlateDropMixin {

    @Inject(method = "onRemove(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V", at = @At("HEAD"))
    private void immersiveMetallurgy$leaveTheFitting(BlockState state, Level level, BlockPos pos,
                                                     BlockState next, boolean moving, CallbackInfo ci) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof MoldTableBlockEntity table)) {
            return;
        }
        if (table.getMoldStack().is(IMItems.SLAG_PLATE.get())) {
            table.getInventory().setStackInSlot(0, ItemStack.EMPTY);
        }
    }
}
