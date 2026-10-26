package immersivemetallurgy.event;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.component.MeltComposition;
import immersivemetallurgy.recipe.SlagSkimming;
import immersivemetallurgy.registry.IMAlloys;
import immersivemetallurgy.registry.IMFluids;
import immersivemetallurgy.registry.IMItems;
import immersivemetallurgy.registry.IMTags;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
import net.dries007.tfc.util.data.FluidHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.common.util.TriState;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Skimming slag off a hot crucible with a ceramic bowl. The dirt floats, so it can be pushed aside by hand,
 * but a bare skim is viscous work and carries droplets of metal away with it; throw a flux in first and the
 * slag goes thin and cohesive, and comes off alone. Sneaking keeps the crucible's own menu reachable, so the
 * gesture is shift and right click with a bowl in hand.
 */
@EventBusSubscriber(modid = ImmersiveMetallurgy.MODID)
public final class IMSlagSkim {
    private IMSlagSkim() {
    }

    @SubscribeEvent
    public static void onSkim(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        Player player = event.getEntity();
        ItemStack bowl = event.getItemStack();
        boolean skimmer = bowl.is(IMTags.SLAG_SKIMMER);
        if (level.isClientSide || !player.isShiftKeyDown() || event.getHand() != InteractionHand.MAIN_HAND
                || (!skimmer && !bowl.is(IMTags.BURNS_IN_CRUCIBLE))) {
            return;
        }
        if (!(level.getBlockEntity(event.getPos()) instanceof CrucibleBlockEntity crucible)) {
            return;
        }
        ImmersiveMetallurgy.LOGGER.debug("Skim attempted at {} with {} (skimmer={})", event.getPos(),
                bowl.getHoverName().getString(), skimmer);
        if (!isMolten(crucible)) {
            if (skimmer) {
                ImmersiveMetallurgy.LOGGER.debug("Skim refused at {}: nothing is molten at {} degrees",
                        event.getPos(), crucible.getTemperature());
            }
            return;
        }
        if (!skimmer) {
            // Wood against a bath of molten metal does not skim anything, it just stops being a bowl.
            bowl.shrink(1);
            level.playSound(null, event.getPos(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1F, 1.4F);
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            event.setCancellationResult(InteractionResult.CONSUME);
            return;
        }

        SlagSkimming rules = SlagSkimming.of(level.getRecipeManager());
        MeltComposition container = (MeltComposition) (Object) crucible.getAlloy();
        Object2DoubleMap<Fluid> contents = crucible.getAlloy().getContent();
        double slag = contents.getDouble(IMFluids.SLAG.fluid());
        if (rules == null || slag < 1) {
            ImmersiveMetallurgy.LOGGER.debug("Skim refused at {}: rules={}, slag={}, contents={}", event.getPos(),
                    rules == null ? "missing recipe" : "loaded", slag, crucible.getAlloy().getContent());
            return;
        }

        // A skim always lifts something, however little dirt is left: a percentage of a thin layer rounds to
        // zero and the player is left clicking a crucible that refuses to get any cleaner.
        float fluxPower = container.immersiveMetallurgyFluxPower();
        int wanted = fluxPower > 0F ? Math.max(1, (int) Math.ceil(slag * fluxPower))
                : Math.max(1, (int) Math.floor(slag * rules.slagShare()));
        int removed = container.immersiveMetallurgyTake(IMFluids.SLAG.fluid(), wanted);
        if (removed <= 0) {
            ImmersiveMetallurgy.LOGGER.debug("Skim took nothing at {}: slag was {} mB", event.getPos(), slag);
            return;
        }
        if (fluxPower <= 0F) {
            takeMetal(crucible, container, rules);
        }
        if (level.random.nextFloat() < rules.bowlBreakChance()) {
            breakBowl(player, bowl);
        }

        double expected = removed * rules.dustPerMilliBucket();
        int dust = (int) Math.floor(expected);
        if (level.random.nextFloat() < expected - dust) {
            dust++;
        }
        if (dust > 0) {
            // The dust is named for the metal it was dirty in, which is also what it can be worked for later.
            IMAlloys bath = IMFluids.alloyOf(crucible.getAlloyResult().getFluid());
            give(level, player, new ItemStack(bath == null ? IMItems.SLAG_DUST.get() : IMItems.slagDust(bath), dust));
        }
        // A flux is spent on the layer it was thrown onto: whatever slag survives the lift needs new flux,
        // otherwise the [skim ready] mark promises a clean skim that can no longer happen.
        if (fluxPower > 0F || slag - removed < 1) {
            container.immersiveMetallurgySetFluxPower(0F);
        }
        level.playSound(null, event.getPos(), SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.7F, 0.9F);

        // The bowl has a salad interface of its own and TFC opens it on this exact gesture, so the skim has
        // to claim the click; otherwise the GUI pops up over the top of the work and it reads as nothing
        // happening. Only a skim that actually took slag claims it, so an empty bowl still makes a salad.
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCancellationResult(InteractionResult.CONSUME);
        crucible.markForSync();
        crucible.setChanged();
        BlockPos pos = event.getPos();
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_ALL);
    }

    /** Slag floats in a liquid bath, so a cold crucible has nothing to take off the top of. */
    private static boolean isMolten(CrucibleBlockEntity crucible) {
        FluidHeat heat = FluidHeat.get(crucible.getAlloyResult().getFluid());
        return heat != null && crucible.getTemperature() >= heat.meltTemperature();
    }

    /** Without flux the skim is dirty work: it lifts the metal's share of the loss along with the dirt. */
    private static void takeMetal(CrucibleBlockEntity crucible, MeltComposition container, SlagSkimming rules) {
        Object2DoubleMap<Fluid> content = crucible.getAlloy().getContent();
        double metal = 0;
        for (Object2DoubleMap.Entry<Fluid> entry : content.object2DoubleEntrySet()) {
            if (entry.getKey() != IMFluids.SLAG.fluid()) {
                metal += entry.getDoubleValue();
            }
        }
        int budget = (int) Math.round(metal * rules.metalLoss());
        if (budget <= 0) {
            return;
        }
        for (Fluid fluid : content.keySet().toArray(new Fluid[0])) {
            if (fluid == IMFluids.SLAG.fluid()) {
                continue;
            }
            container.immersiveMetallurgyTake(fluid, (int) Math.floor(budget * content.getDouble(fluid) / metal));
        }
    }

    private static void breakBowl(Player player, ItemStack bowl) {
        if (bowl.isDamageableItem()) {
            bowl.hurtAndBreak(1, player, player.getEquipmentSlotForItem(bowl));
        } else {
            bowl.shrink(1);
        }
    }

    private static void give(Level level, Player player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !level.isClientSide) {
            BlockPos at = player.blockPosition();
            level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level,
                    at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, stack));
        }
    }
}
