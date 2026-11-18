package immersivemetallurgy.mixin;

import immersivemetallurgy.furnace.SlagDirt;
import immersivemetallurgy.registry.IMDataComponents;

import net.dries007.tfc.common.blocks.TFCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * A channel's dirt is not a component TFC writes on its own drop, so it is put on the drop here, on the way out of
 * the ground: break a dirty channel and the piece in your hand is the same dirty piece, ready to be laid again or
 * thrown away. The hook sits on the shared drop path but only answers for a block entity that carries dirt.
 */
@Mixin(Block.class)
public abstract class BlockDropsDirtMixin {
    @Inject(method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)Ljava/util/List;",
            at = @At("RETURN"))
    private static void immersiveMetallurgy$carryDirt(BlockState state, ServerLevel level, BlockPos pos, BlockEntity entity,
                                                      CallbackInfoReturnable<List<ItemStack>> cir) {
        if (!(entity instanceof SlagDirt dirt) || dirt.slagDirt() <= 0) {
            return;
        }
        for (ItemStack drop : cir.getReturnValue()) {
            if (drop.is(TFCBlocks.CHANNEL.get().asItem())) {
                drop.set(IMDataComponents.SLAG_DIRT.get(), dirt.slagDirt());
            }
        }
    }
}