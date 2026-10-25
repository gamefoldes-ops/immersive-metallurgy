package immersivemetallurgy.event;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.component.MeltCarries;
import immersivemetallurgy.recipe.FlawProfiles;
import immersivemetallurgy.registry.IMDataComponents;
import immersivemetallurgy.registry.IMTags;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Honing an edge is a five second appointment with a stone, not a menu: hold the tool, right click the
 * station, wait it out with sparks and the grinding sound, and get the edge back at the price of a little of
 * the tool's structure. The timer lives here rather than on the player because it is meaningless after a
 * reload, and nothing about an edge is worth persisting half a stroke for.
 *
 * The same numbers drive the portable whetstone, so the two routes differ only in what they cost: the stone
 * wears out, the station takes your time.
 */
@EventBusSubscriber(modid = ImmersiveMetallurgy.MODID)
public final class IMHoning {
    private record Session(int slot, Item tool, long endsAt) {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private IMHoning() {
    }

    @SubscribeEvent
    public static void onRightClickStation(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        Player player = event.getEntity();
        ItemStack tool = event.getItemStack();
        if (level.isClientSide || event.getHand() != InteractionHand.MAIN_HAND
                || !tool.has(IMDataComponents.SHARPNESS.get())
                || !isStation(level, event, player)) {
            return;
        }
        FlawProfiles profiles = FlawProfiles.of(level.getRecipeManager());
        if (profiles == null || SESSIONS.containsKey(player.getUUID())
                || MeltCarries.sharpnessOf(tool) >= profiles.wear().honingCeiling()) {
            return;
        }

        SESSIONS.put(player.getUUID(), new Session(player.getInventory().selected, tool.getItem(),
                level.getGameTime() + profiles.wear().honingTicks()));
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCancellationResult(InteractionResult.CONSUME);
        grind(level, player);
    }

    /** An anvil has to be asked sneaking, so that its own menu stays reachable the normal way. */
    private static boolean isStation(Level level, PlayerInteractEvent.RightClickBlock event, Player player) {
        var state = level.getBlockState(event.getPos());
        return state.is(IMTags.HONING_STATION)
                || (player.isShiftKeyDown() && state.is(IMTags.HONING_STATION_SNEAK));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || player.level().isClientSide) {
            return;
        }
        Level level = player.level();
        if (level.getGameTime() < session.endsAt()) {
            if ((level.getGameTime() - session.endsAt()) % 12 == 0) {
                grind(level, player);
            }
            return;
        }
        SESSIONS.remove(player.getUUID());

        ItemStack held = player.getInventory().getItem(session.slot());
        if (held.getItem() != session.tool() || !held.has(IMDataComponents.SHARPNESS.get())) {
            return;
        }
        FlawProfiles profiles = FlawProfiles.of(level.getRecipeManager());
        if (profiles == null) {
            return;
        }
        hone(held, profiles.wear());
        grind(level, player);
    }

    /** Restores the edge, and grinds metal off the tool while doing it, which lowers its structural ceiling. */
    public static void hone(ItemStack tool, FlawProfiles.Wear wear) {
        float sharpness = MeltCarries.sharpnessOf(tool);
        tool.set(IMDataComponents.SHARPNESS.get(), Math.min(wear.honingCeiling(), sharpness + wear.honingRestore()));
        eatStructure(tool, wear.honingStructureCost());
    }

    /**
     * A finished edge at the station's ceiling, rather than the hundred it came off the anvil with: honing
     * recovers an edge that has been used, it does not return metal that was ground away.
     */
    public static boolean canHone(ItemStack tool, FlawProfiles.Wear wear) {
        return tool.has(IMDataComponents.SHARPNESS.get()) && MeltCarries.sharpnessOf(tool) < wear.honingCeiling();
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

    private static void grind(Level level, Player player) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GRINDSTONE_USE,
                SoundSource.PLAYERS, 1F, 1F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getEyeY() + 0.4,
                    player.getZ(), 6, 0.25, 0.2, 0.25, 0.02);
        }
    }
}
