package immersivemetallurgy.event;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.furnace.SlagDirt;
import immersivemetallurgy.registry.IMDataComponents;

import net.dries007.tfc.common.blockentities.ChannelBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The dirt travelling on a channel or crucible item: laid back into the block entity when the piece is placed, and
 * said out loud on the tooltip so a dirty piece is recognisable in the hand before it is laid. A clogged channel
 * refuses to be placed at all — it is slag baked into a pipe, and the only thing left to do with it is throw it away.
 */
@EventBusSubscriber(modid = ImmersiveMetallurgy.MODID)
public final class IMChannelDirt {
    private IMChannelDirt() {
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        Integer dirt = held.get(IMDataComponents.SLAG_DIRT.get());
        if (dirt == null || dirt <= 0) {
            return;
        }
        BlockEntity entity = event.getLevel().getBlockEntity(event.getPos());
        if (!(entity instanceof SlagDirt carrier)) {
            return;
        }
        if (entity instanceof ChannelBlockEntity && dirt >= SlagDirt.CLOG_AT) {
            event.setCanceled(true);
            return;
        }
        carrier.slagDirt(dirt);
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        Integer dirt = event.getItemStack().get(IMDataComponents.SLAG_DIRT.get());
        if (dirt == null || dirt <= 0) {
            return;
        }
        if (dirt >= SlagDirt.CLOG_AT) {
            event.getToolTip().add(Component.translatable(ImmersiveMetallurgy.MODID + ".tooltip.clogged")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            event.getToolTip().add(Component.translatable(ImmersiveMetallurgy.MODID + ".tooltip.slag_dirt", dirt)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}