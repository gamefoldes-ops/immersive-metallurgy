package immersivemetallurgy.event;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.block.CastingTableBlock;
import immersivemetallurgy.block.LaunderBlock;
import immersivemetallurgy.block.LaunderChannelBlock;
import immersivemetallurgy.furnace.Launder;
import immersivemetallurgy.registry.IMBlocks;

import net.dries007.tfc.common.blocks.TFCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Laying the launder and the table into one cell, in either order, without crafting anything new.
 *
 * Everything is answered on the click, before anything is placed, so a half never appears in the wrong shape and
 * then jumps: the cell the held block would land in is computed from the clicked face, and what stands there (or
 * does not) decides what is laid. A channel aimed at the empty outlet becomes the trough on its own, aimed at the
 * duct; a channel aimed at the table in front of the duct, or a table aimed at the trough standing on the ground,
 * completes the pair into the combined {@link CastingTableBlock}. Both halves are TFC's own items, so the player
 * only ever lays things they already had, and breaking the pair hands both back.
 */
@EventBusSubscriber(modid = ImmersiveMetallurgy.MODID)
public final class IMCastingTable {
    private IMCastingTable() {
    }

    @SubscribeEvent
    public static void onLayHalf(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        if (!player.mayBuild()) {
            return;
        }
        boolean channelItem = held.is(TFCBlocks.CHANNEL.get().asItem());
        boolean tableItem = held.is(TFCBlocks.MOLD_TABLE.get().asItem());
        if (!channelItem && !tableItem) {
            return;
        }
        BlockPos clicked = event.getPos();
        BlockState clickedState = level.getBlockState(clicked);
        boolean onOurHalf = clickedState.is(IMBlocks.LAUNDER.get())
                || clickedState.is(IMBlocks.LAUNDER_CHANNEL.get())
                || clickedState.is(IMBlocks.CASTING_TABLE.get());
        // The cell the held block would actually be placed into, not the cell that was clicked: clicking the duct
        // aims at the outlet in front of it, and clicking the ground aims at the cell standing on it.
        BlockPos target = clicked.relative(event.getFace());
        BlockState there = level.getBlockState(target);
        Direction aim = Launder.sideOf(level, target);
        BlockState laid = null;
        if (channelItem && aim != null) {
            if (there.isAir()) {
                laid = IMBlocks.LAUNDER.get().defaultBlockState().setValue(LaunderBlock.FACING, aim);
            } else if (there.is(TFCBlocks.MOLD_TABLE.get())) {
                laid = IMBlocks.CASTING_TABLE.get().defaultBlockState().setValue(CastingTableBlock.FACING, aim);
            } else if (there.is(IMBlocks.LAUNDER.get())) {
                // A second channel on a standing launder: instead of a table under the trough, a channel in the
                // same cell, joining the channels beside it and carrying the metal on to tables further along.
                laid = IMBlocks.LAUNDER_CHANNEL.get().defaultBlockState()
                        .setValue(LaunderChannelBlock.FACING, there.getValue(LaunderBlock.FACING));
            }
        } else if (tableItem && there.is(IMBlocks.LAUNDER.get())) {
            laid = IMBlocks.CASTING_TABLE.get().defaultBlockState()
                    .setValue(CastingTableBlock.FACING, there.getValue(LaunderBlock.FACING));
        }
        // A click on one of our halves that converts nothing is inert on BOTH sides. Letting the client predict a
        // vanilla placement the server refuses is what made the held stack look spent until the slot resynced.
        if (laid == null || !laid.canSurvive(level, target)) {
            if (onOurHalf) {
                event.setUseBlock(TriState.FALSE);
                event.setUseItem(TriState.FALSE);
                event.setCancellationResult(InteractionResult.CONSUME);
            }
            return;
        }
        if (level.isClientSide) {
            // The server lays and pays; the client only stands down so it never predicts a second, conflicting lay.
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            event.setCancellationResult(InteractionResult.CONSUME);
            return;
        }
        if (!level.setBlock(target, laid, Block.UPDATE_ALL) || !level.getBlockState(target).is(laid.getBlock())) {
            return;
        }
        level.playSound(null, target, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1F, 0.9F);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCancellationResult(InteractionResult.CONSUME);
    }
}