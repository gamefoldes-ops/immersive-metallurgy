package immersivemetallurgy.event;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.component.MeltComposition;
import immersivemetallurgy.recipe.SlagSkimming;
import immersivemetallurgy.registry.IMFluids;
import immersivemetallurgy.registry.IMItems;
import immersivemetallurgy.registry.IMTags;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
import net.dries007.tfc.util.data.FluidHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.items.IItemHandler;

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
        if (level.isClientSide || !player.isShiftKeyDown() || event.getHand() != InteractionHand.MAIN_HAND
                || !bowl.is(IMTags.SLAG_SKIMMER)) {
            return;
        }
        if (!(level.getBlockEntity(event.getPos()) instanceof CrucibleBlockEntity crucible)) {
            return;
        }
        SlagSkimming rules = SlagSkimming.of(level.getRecipeManager());
        MeltComposition container = (MeltComposition) (Object) crucible.getAlloy();
        Object2DoubleMap<Fluid> contents = crucible.getAlloy().getContent();
        double slag = contents.getDouble(IMFluids.SLAG.fluid());
        if (rules == null || slag < 1 || !isMolten(crucible)) {
            return;
        }

        boolean fluxed = consumeFlux(level, event.getPos(), rules);
        int removed = container.immersiveMetallurgyTake(IMFluids.SLAG.fluid(),
                (int) Math.floor(fluxed ? slag : slag * rules.slagShare()));
        if (removed <= 0) {
            return;
        }
        if (!fluxed) {
            takeMetal(crucible, container, rules);
        }
        if (level.random.nextFloat() < rules.bowlBreakChance()) {
            breakBowl(player, bowl);
        }

        int dust = (int) Math.floor(removed * rules.dustPerMilliBucket());
        if (dust > 0) {
            give(level, player, new ItemStack(IMItems.SLAG_DUST.get(), dust));
        }
        level.playSound(null, event.getPos(), SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.7F, 0.9F);
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

    private static boolean consumeFlux(Level level, BlockPos pos, SlagSkimming rules) {
        IItemHandler contents = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (contents == null) {
            return false;
        }
        for (int slot = 0; slot < contents.getSlots(); slot++) {
            ItemStack stack = contents.getStackInSlot(slot);
            if (!stack.isEmpty() && rules.flux().test(stack)) {
                contents.extractItem(slot, 1, false);
                return true;
            }
        }
        return false;
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
