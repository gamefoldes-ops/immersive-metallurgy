package immersivemetallurgy.event;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.block.entity.RefiningFurnaceBlockEntity;
import immersivemetallurgy.recipe.RefiningFurnaceStats;
import immersivemetallurgy.registry.IMBlockEntities;

import net.dries007.tfc.common.blocks.TFCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Knocking the tap open.
 *
 * A cupola tap is stopped with a bar and knocked open when the heat is ready, and that is what an empty hand on
 * the tap's outlet does here — on the mold table standing in it, or on the casting channel run out of it if the
 * player chose to lay one — starting a flow that runs until the mould is full or the bath is. A refusal is spoken
 * in the action bar rather than left as a silent click, except on the table: the click that will not open a tap
 * may still be the click that lifts a finished cast off it, so TFC keeps that answer.
 */
@EventBusSubscriber(modid = ImmersiveMetallurgy.MODID)
public final class IMFurnaceTap {
    private IMFurnaceTap() {
    }

    /** Whether the tap table is standing there with nothing on it, which is the emptying gesture. */
    private static boolean lineEmpty(RefiningFurnaceBlockEntity furnace, BlockPos outlet) {
        return furnace.tapLine() != null && furnace.tapLine().table().getMoldStack().isEmpty()
                && outlet.equals(furnace.shape().tapOutlet());
    }
    @SubscribeEvent
    public static void onRightClickOutlet(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        Block clicked = level.getBlockState(event.getPos()).getBlock();
        if (level.isClientSide || event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().isEmpty()
                || (clicked != TFCBlocks.CHANNEL.get() && clicked != TFCBlocks.MOLD_TABLE.get())) {
            return;
        }
        BlockPos outlet = event.getPos();
        for (BlockPos near : BlockPos.withinManhattan(outlet, 4, 3, 4)) {
            RefiningFurnaceBlockEntity furnace = level.getBlockEntity(near, IMBlockEntities.HEARTH.get()).orElse(null);
            if (furnace == null || !furnace.formed() || !furnace.isTapHandle(outlet)) {
                continue;
            }
            RefiningFurnaceStats stats = RefiningFurnaceStats.of(level.getRecipeManager());
            if (stats == null) {
                return;
            }
            if (clicked == TFCBlocks.MOLD_TABLE.get() && lineEmpty(furnace, outlet)) {
                // An empty tap table is the furnace's dump: what is left of the heat is drawn off and laid in the
                // slag dish, because the last few dozen millilitres will not fill a mould and nobody should have to
                // break the walls to empty a hearth. Nothing is handed over here — skimmings are lifted off the
                // dish, which is the one place they are ever taken.
                String heel = furnace.drainHeel(stats);
                if (!heel.equals("empty")) {
                    event.setUseBlock(TriState.FALSE);
                    event.setUseItem(TriState.FALSE);
                    event.setCancellationResult(InteractionResult.CONSUME);
                    if (!heel.equals("none")) {
                        event.getEntity().displayClientMessage(Component.translatable("screen."
                                + ImmersiveMetallurgy.MODID + ".furnace.tap." + heel), true);
                    } else {
                        level.playSound(null, outlet, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.6F, 1.4F);
                    }
                    return;
                }
            }
            String why = furnace.startTap(stats, outlet);
            if (!why.equals("none")) {
                if (clicked == TFCBlocks.CHANNEL.get()) {
                    event.setUseBlock(TriState.FALSE);
                    event.setUseItem(TriState.FALSE);
                    event.setCancellationResult(InteractionResult.CONSUME);
                }
                event.getEntity().displayClientMessage(Component.translatable("screen." + ImmersiveMetallurgy.MODID
                        + ".furnace.tap." + why, furnace.tapContents()), true);
                return;
            }
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            event.setCancellationResult(InteractionResult.CONSUME);
            level.playSound(null, outlet, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.7F, 0.8F);
            return;
        }
    }
}
