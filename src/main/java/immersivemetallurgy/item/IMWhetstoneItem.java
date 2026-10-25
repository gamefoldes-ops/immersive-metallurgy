package immersivemetallurgy.item;

import immersivemetallurgy.event.IMHoning;
import immersivemetallurgy.recipe.FlawProfiles;
import immersivemetallurgy.registry.IMDataComponents;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The portable route to the same edge a honing station gives: hold the stone in one hand and the tool in the
 * other and right-click. It costs the stone's durability instead of five seconds of standing at the wheel,
 * and it grinds the same share off the tool's structure, because an edge is recovered by removing metal
 * either way.
 */
public class IMWhetstoneItem extends Item {
    public IMWhetstoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stone = player.getItemInHand(hand);
        ItemStack tool = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        FlawProfiles profiles = FlawProfiles.of(level.getRecipeManager());
        if (profiles == null || !IMHoning.canHone(tool, profiles.wear())) {
            return tool.has(IMDataComponents.SHARPNESS.get())
                    ? InteractionResultHolder.fail(stone)
                    : InteractionResultHolder.pass(stone);
        }
        if (!level.isClientSide) {
            IMHoning.hone(tool, profiles.wear());
            stone.hurtAndBreak(1, player, player.getEquipmentSlotForItem(stone));
        }
        return InteractionResultHolder.sidedSuccess(stone, level.isClientSide);
    }
}
