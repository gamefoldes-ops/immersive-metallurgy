package immersivemetallurgy.event;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.block.entity.RefiningFurnaceBlockEntity;
import immersivemetallurgy.recipe.RefiningFurnaceStats;
import immersivemetallurgy.registry.IMBlockEntities;
import immersivemetallurgy.registry.IMBlocks;

import net.dries007.tfc.util.events.StartFireEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Lighting and dousing the furnace the way TFC works its own forges: only with shift held.
 *
 * A plain right-click opens the furnace window and eats the click, so neither the firestarter nor the shovel
 * ever gets to act without shift — exactly like TFC's forges. While sneaking with a held item the game skips
 * the block's useItemOn and useWithoutItem outright, so the fire is caught from StartFireEvent (the event TFC's
 * firestarter and flint-and-pyrite both fire) and the shovel from RightClickBlock, each gated on shift. A
 * refusal is a silent click, never a message.
 */
@EventBusSubscriber(modid = ImmersiveMetallurgy.MODID)
public final class IMFurnaceFire {
    private IMFurnaceFire() {
    }

    @SubscribeEvent
    public static void onFireStart(StartFireEvent event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        Player player = event.getPlayer();
        if (level.isClientSide || player == null || !player.isSecondaryUseActive()
                || event.getState().getBlock() != IMBlocks.REFINING_FURNACE.get()) {
            return;
        }
        RefiningFurnaceBlockEntity furnace = level.getBlockEntity(pos, IMBlockEntities.HEARTH.get()).orElse(null);
        RefiningFurnaceStats stats = RefiningFurnaceStats.of(level.getRecipeManager());
        // A firestarter clicked on the hearth is always the hearth's own business: it either lights, or the fire
        // is already alight / cooling / unfed and the click is a silent no-op. Cancelling either way keeps the
        // firestarter's default flame off the furnace, so a second click does nothing at all — no sound, no swing.
        event.setCanceled(true);
        if (furnace == null || stats == null || !furnace.canLight(stats)) {
            return;
        }
        if (furnace.light(stats)) {
            level.playSound(null, pos, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1F, 0.8F);
            player.swing(InteractionHand.MAIN_HAND, true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        ItemStack held = event.getItemStack();
        if (level.isClientSide || event.getHand() != InteractionHand.MAIN_HAND
                || !event.getEntity().isSecondaryUseActive()
                || level.getBlockState(pos).getBlock() != IMBlocks.REFINING_FURNACE.get()
                || !held.is(ItemTags.SHOVELS)) {
            return;
        }
        RefiningFurnaceBlockEntity furnace = level.getBlockEntity(pos, IMBlockEntities.HEARTH.get()).orElse(null);
        // Shift+shovel on the hearth is the dousing handle and nothing else: deny the block and the item so the
        // shovel never digs or flattens here. A furnace already out or cooling refuses the quench, and that click
        // is inert — no hiss, no swing — while a fire actually dropped hisses and the arm follows through.
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        if (furnace == null || !furnace.quench()) {
            event.setCancellationResult(InteractionResult.PASS);
            return;
        }
        event.setCancellationResult(InteractionResult.CONSUME);
        level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1F, 1F);
        event.getEntity().swing(InteractionHand.MAIN_HAND, true);
    }
}