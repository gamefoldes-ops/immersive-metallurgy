package immersivemetallurgy.event;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.component.DefectProfile;
import immersivemetallurgy.component.MeltCarries;
import immersivemetallurgy.recipe.FlawProfiles;
import immersivemetallurgy.registry.IMDataComponents;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.function.ToDoubleFunction;

/**
 * Dulling and its price. An edge loses sharpness on every block mined and every hit landed, a blunt edge
 * cuts and digs slower, and flawed metal goes blunt faster and chips instead. Structural damage stays where
 * TFC put it, in the vanilla durability, so a flawed tool is simply a shorter one.
 */
@EventBusSubscriber(modid = ImmersiveMetallurgy.MODID)
public final class IMWearOnUse {
    private IMWearOnUse() {
    }

    @SubscribeEvent
    public static void onMiningSpeed(PlayerEvent.BreakSpeed event) {
        ItemStack tool = event.getEntity().getMainHandItem();
        Float sharpness = tool.get(IMDataComponents.SHARPNESS.get());
        if (sharpness == null) {
            return;
        }
        FlawProfiles profiles = FlawProfiles.of(event.getEntity().level().getRecipeManager());
        if (profiles == null) {
            return;
        }
        DefectProfile defect = tool.get(IMDataComponents.DEFECT_PROFILE.get());
        float dull = Mth.lerp(sharpness / MeltCarries.FULL_SHARPNESS, profiles.wear().dullSpeedFloor(), 1F);
        float flawed = defect == null ? 1F : profiles.speedFactor(defect);
        event.setNewSpeed(event.getNewSpeed() * dull * flawed);
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        Level level = player.level();
        dull(player.getMainHandItem(), level, player, FlawProfiles.of(level.getRecipeManager()),
                profiles -> profiles.wear().sharpnessPerBlock(), null);
    }

    @SubscribeEvent
    public static void onAttackLanded(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Player attacker)) {
            return;
        }
        ItemStack weapon = attacker.getWeaponItem();
        if (weapon.isEmpty()) {
            return;
        }
        FlawProfiles profiles = FlawProfiles.of(attacker.level().getRecipeManager());
        if (profiles == null) {
            return;
        }
        DefectProfile defect = weapon.get(IMDataComponents.DEFECT_PROFILE.get());
        Float sharpness = weapon.get(IMDataComponents.SHARPNESS.get());
        float dull = sharpness == null ? 1F
                : Mth.lerp(sharpness / MeltCarries.FULL_SHARPNESS, profiles.wear().dullDamageFloor(), 1F);
        float flawed = defect == null ? 1F : profiles.damageFactor(defect);
        event.setAmount(event.getAmount() * dull * flawed);
        dull(weapon, attacker.level(), attacker, profiles, profile -> profile.wear().sharpnessPerHit(), defect);
    }

    /**
     * Losing sharpness is what makes an edge need a whetstone; chipping is the same defect seen from the
     * durability side, and only flawed metal does it. Tools made before this mod was loaded have no
     * sharpness to track, so they keep TFC's own behaviour until they are sharpened once.
     */
    private static void dull(ItemStack tool, Level level, LivingEntity user, FlawProfiles profiles,
                             ToDoubleFunction<FlawProfiles> dropPerUse, DefectProfile defect) {
        if (profiles == null || level.isClientSide || !tool.has(IMDataComponents.SHARPNESS.get())) {
            return;
        }
        float sharpness = MeltCarries.sharpnessOf(tool);
        float multiplier = defect == null ? 1F : profiles.dullFactor(defect);
        float next = Math.max(0F, (float) (sharpness - dropPerUse.applyAsDouble(profiles) * multiplier));
        tool.set(IMDataComponents.SHARPNESS.get(), next);

        if (defect != null && level.getRandom().nextFloat() < profiles.chipChance(defect) * (1F - next / MeltCarries.FULL_SHARPNESS)) {
            EquipmentSlot slot = user.getEquipmentSlotForItem(tool);
            tool.hurtAndBreak(1 + level.getRandom().nextInt(2), user, slot);
        }
    }
}
