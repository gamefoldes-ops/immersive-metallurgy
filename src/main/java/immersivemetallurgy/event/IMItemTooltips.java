package immersivemetallurgy.event;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.component.DefectProfile;
import immersivemetallurgy.recipe.FlawProfiles;
import immersivemetallurgy.registry.IMDataComponents;

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
        // What is wrong with the piece and how sound it came out are worth a keybind-free look, because they
        // are the reason a player is holding this casting instead of the clean one. Only the numbers that
        // translate the flaw into game stats stay under F3+H, where the rest of the game hides its detail.
        boolean advanced = event.getFlags().isAdvanced();
        ItemStack stack = event.getItemStack();
        List<Component> lines = new ArrayList<>();

        Player player = event.getEntity();
        FlawProfiles profiles = player == null ? null : FlawProfiles.of(player.level().getRecipeManager());
        DefectProfile profile = stack.get(IMDataComponents.DEFECT_PROFILE.get());
        if (profile != null && !profile.isClean()) {
            lines.addAll(defectLines(profile, profiles, advanced));
        }

        Float sharpness = stack.get(IMDataComponents.SHARPNESS.get());
        if (advanced && sharpness != null) {
            // Tracked in points out of a hundred, not a fraction, so it is not put through percent().
            lines.add(translated("tooltip.sharpness", Math.round(sharpness) + "%")
                    .withStyle(sharpness < 40 ? ChatFormatting.RED : ChatFormatting.GRAY));
        }

        event.getToolTip().addAll(lines);
    }

    private static List<Component> defectLines(DefectProfile profile, FlawProfiles profiles, boolean advanced) {
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
        // The band the whole pile was cast into, so a player can see why two stacks of the same ingot differ.
        lines.add(translated("tooltip.grade", Component.translatable("immersive_metallurgy.grade." + profile.gradeKey()))
                .withStyle(colour));
        lines.add(translated("tooltip.defects", names).withStyle(colour));
        lines.add(translated("tooltip.structure", percent(profile.durabilityFactor())).withStyle(ChatFormatting.GRAY));

        if (advanced && profiles != null) {
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
        if (Math.abs(factor - 1F) < 0.001F) {
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
