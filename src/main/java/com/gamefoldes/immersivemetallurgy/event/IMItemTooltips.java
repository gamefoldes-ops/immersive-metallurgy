package com.gamefoldes.immersivemetallurgy.event;

import com.gamefoldes.immersivemetallurgy.ImmersiveMetallurgy;
import com.gamefoldes.immersivemetallurgy.component.DefectProfile;
import com.gamefoldes.immersivemetallurgy.component.MeltCarries;
import com.gamefoldes.immersivemetallurgy.recipe.FlawProfiles;
import com.gamefoldes.immersivemetallurgy.registry.IMDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Tells the player what is wrong with a piece of metal and exactly what that costs: the defects it carries
 * and the numbers they push around. Everything here is derived, never stored, so retuning the flaw profile
 * file changes the tooltip without touching the item.
 */
@EventBusSubscriber(modid = ImmersiveMetallurgy.MODID)
public final class IMItemTooltips {
    private IMItemTooltips() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Component> lines = new ArrayList<>();

        Player player = event.getEntity();
        FlawProfiles profiles = player == null ? null : FlawProfiles.of(player.level().getRecipeManager());
        DefectProfile profile = stack.get(IMDataComponents.DEFECT_PROFILE.get());
        if (profile != null && !profile.isClean()) {
            lines.addAll(defectLines(profile, profiles));
        }

        Float sharpness = stack.get(IMDataComponents.SHARPNESS.get());
        if (sharpness != null && sharpness < MeltCarries.FULL_SHARPNESS) {
            lines.add(translated("tooltip.sharpness", percent(sharpness))
                    .withStyle(sharpness < 40 ? ChatFormatting.RED : ChatFormatting.GRAY));
        }

        if (!lines.isEmpty()) {
            event.getToolTip().addAll(0, lines);
        }
    }

    private static List<Component> defectLines(DefectProfile profile, FlawProfiles profiles) {
        List<Component> lines = new ArrayList<>();

        MutableComponent names = Component.empty();
        float worst = 0;
        for (DefectProfile.Archetype archetype : DefectProfile.Archetype.values()) {
            float severity = profile.severity(archetype);
            if (severity <= 0) {
                continue;
            }
            if (!names.getSiblings().isEmpty()) {
                names.append(", ");
            }
            names.append(Component.translatable("immersive_metallurgy.defect." + archetype.key())
                    .append(Component.literal(" " + percent(severity))));
            worst = Math.max(worst, severity);
        }
        ChatFormatting colour = worst >= 0.66 ? ChatFormatting.RED : ChatFormatting.GOLD;
        lines.add(translated("tooltip.flawed", names).withStyle(colour));
        lines.add(translated("tooltip.structure", percent(profile.durabilityFactor())).withStyle(ChatFormatting.GRAY));

        if (profiles != null) {
            addIfChanged(lines, "tooltip.damage", profiles.damageFactor(profile));
            addIfChanged(lines, "tooltip.mining", profiles.speedFactor(profile));
            addIfChanged(lines, "tooltip.blunting", profiles.dullFactor(profile));
            float chipping = profiles.chipChance(profile);
            if (chipping > 0.001F) {
                lines.add(translated("tooltip.chipping", percent(chipping)).withStyle(ChatFormatting.GRAY));
            }
        }
        return lines;
    }

    /** Only the stats a defect actually moves, so a purely brittle tool does not claim to be soft. */
    private static void addIfChanged(List<Component> lines, String key, float factor) {
        if (Math.abs(factor - 1F) < 0.005F) {
            return;
        }
        boolean harmful = factor < 1F;
        lines.add(translated(key, (harmful ? "-" : "+") + percent(Math.abs(factor - 1F)))
                .withStyle(harmful ? ChatFormatting.RED : ChatFormatting.GREEN));
    }

    private static MutableComponent translated(String key, Object value) {
        return Component.translatable("immersive_metallurgy." + key,
                value instanceof Component component ? component : Component.literal(value.toString()));
    }

    private static String percent(float fraction) {
        return Math.round(100 * fraction) + "%";
    }
}
