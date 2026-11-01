package immersivemetallurgy.event;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.block.entity.RefiningFurnaceBlockEntity;
import immersivemetallurgy.registry.IMBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Cleaning the slag plate.
 *
 * A table standing in a furnace's slag outlet carries our plate, and the plate holds molten slag rather than a
 * cast — so TFC's own click on that table has nothing to hand back, because slag has no casting recipe to finish.
 * The click that pays the dust out is therefore ours, and it claims the interaction only when there is something
 * to pay: an empty plate, or any table in the world that no furnace is draining into, keeps TFC's behaviour whole.
 */
@EventBusSubscriber(modid = ImmersiveMetallurgy.MODID)
public final class IMSlagPlate {
    private IMSlagPlate() {
    }

    @SubscribeEvent
    public static void onRightClickTable(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide || event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().isEmpty()) {
            return;
        }
        BlockPos table = event.getPos();
        for (BlockPos near : BlockPos.withinManhattan(table, 3, 2, 3)) {
            RefiningFurnaceBlockEntity furnace = level.getBlockEntity(near, IMBlockEntities.HEARTH.get()).orElse(null);
            if (furnace == null || !furnace.ownsSlagOutlet(table)) {
                continue;
            }
            // The click is answered whether or not there was slag to pay out, because the plate is a fitting and
            // not a possession: TFC's own table would otherwise hand it back into the inventory on an idle click,
            // and a furnace whose slag catcher can wander off is a furnace that silently stops refining.
            if (furnace.cleanSlagPlate(event.getEntity()) > 0) {
                level.playSound(null, table, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.6F, 1.2F);
            }
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            event.setCancellationResult(InteractionResult.CONSUME);
            return;
        }
    }
}
