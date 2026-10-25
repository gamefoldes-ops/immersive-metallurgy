package com.gamefoldes.immersivemetallurgy.event;

import com.gamefoldes.immersivemetallurgy.ImmersiveMetallurgy;
import com.gamefoldes.immersivemetallurgy.component.DefectProfile;
import com.gamefoldes.immersivemetallurgy.component.MeltCarries;
import com.gamefoldes.immersivemetallurgy.registry.IMDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Tells the player what is wrong with a piece of metal, in the words a smith would use, so that one item id
 * per alloy still reads as a different object in the hand. The name of the worst defect and how strong it
 * is, because the numbers themselves belong in the tuning file, not in a tooltip.
 */
@EventBusSubscriber(modid = ImmersiveMetallurgy.MODID)
public final class IMItemTooltips {
    private IMItemTooltips() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Float sharpness = stack.get(IMDataComponents.SHARPNESS.get());
        if (sharpness != null && sharpness < MeltCarries.FULL_SHARPNESS) {
            event.getToolTip().add(Component.translatable("immersive_metallurgy.tooltip.sharpness",
                            Component.literal(Math.round(sharpness) + "%"))
                    .withStyle(sharpness < 40 ? ChatFormatting.RED : ChatFormatting.GRAY));
        }

        DefectProfile profile = stack.get(IMDataComponents.DEFECT_PROFILE.get());
        if (profile == null || profile.isClean()) {
            return;
        }
        DefectProfile.Archetype dominant = profile.dominant();
        int severity = Math.round(100 * profile.severity(dominant));
        event.getToolTip().add(0, Component.translatable("immersive_metallurgy.tooltip.flawed",
                        Component.translatable("immersive_metallurgy.defect." + dominant.key()))
                .withStyle(severity >= 66 ? ChatFormatting.RED : ChatFormatting.GOLD));
    }
}
