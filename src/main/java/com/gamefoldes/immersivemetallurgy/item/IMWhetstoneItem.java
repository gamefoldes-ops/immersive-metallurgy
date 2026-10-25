package com.gamefoldes.immersivemetallurgy.item;

import com.gamefoldes.immersivemetallurgy.component.MeltCarries;
import com.gamefoldes.immersivemetallurgy.recipe.FlawProfiles;
import com.gamefoldes.immersivemetallurgy.registry.IMDataComponents;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A whetstone buys back sharpness but never all of it, and every stroke grinds metal off the tool, so the
 * structural ceiling drops. That is the second refining route in the same shape as skimming slag: fast, and
 * it costs you something real. Hold the stone in one hand and the tool in the other, then right-click.
 */
public class IMWhetstoneItem extends Item {
    public IMWhetstoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stone = player.getItemInHand(hand);
        ItemStack tool = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!tool.has(IMDataComponents.SHARPNESS.get())) {
            return InteractionResultHolder.pass(stone);
        }
        FlawProfiles profiles = FlawProfiles.of(level.getRecipeManager());
        if (profiles == null) {
            return InteractionResultHolder.pass(stone);
        }
        FlawProfiles.Wear wear = profiles.wear();
        float sharpness = MeltCarries.sharpnessOf(tool);
        if (sharpness >= wear.whetstoneCeiling()) {
            return InteractionResultHolder.fail(stone);
        }
        if (!level.isClientSide) {
            tool.set(IMDataComponents.SHARPNESS.get(), Math.min(wear.whetstoneCeiling(), sharpness + wear.whetstoneRestore()));
            eatStructure(tool, wear.whetstoneStructureCost());
            stone.hurtAndBreak(1, player, player.getEquipmentSlotForItem(stone));
        }
        return InteractionResultHolder.sidedSuccess(stone, level.isClientSide);
    }

    /** Grinding an edge removes metal, so the tool's lifetime shortens a little every time it is sharpened. */
    private static void eatStructure(ItemStack tool, float shareOfMax) {
        int max = tool.getMaxDamage();
        if (max <= 1) {
            return;
        }
        int shrunk = Math.max(1, Math.round(max * (1F - shareOfMax)));
        tool.set(DataComponents.MAX_DAMAGE, shrunk);
        if (tool.getDamageValue() >= shrunk) {
            tool.setDamageValue(shrunk - 1);
        }
    }
}
