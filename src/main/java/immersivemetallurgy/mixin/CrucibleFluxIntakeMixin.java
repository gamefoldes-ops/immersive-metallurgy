package immersivemetallurgy.mixin;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.component.MeltComposition;
import immersivemetallurgy.recipe.SlagSkimming;
import immersivemetallurgy.registry.IMFluids;

import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
import net.dries007.tfc.util.data.FluidHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Decides what flux thrown into a crucible is for, while it is still sitting in a slot: it joins the bath
 * the moment the dirt underneath it is molten, so the player sees the slag become skim-cleanable straight
 * away instead of finding out when they next try the bowl. Flux dropped into a cold or already clean crucible
 * is left alone, because there is nothing here for it to do yet.
 *
 * Checked on the crucible's own tick rather than on the click that inserted the item, since items arrive from
 * a hopper or a belt as well as from a hand.
 */
@Mixin(CrucibleBlockEntity.class)
public class CrucibleFluxIntakeMixin {
    @Inject(method = "serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/dries007/tfc/common/blockentities/CrucibleBlockEntity;)V", at = @At("HEAD"))
    private static void immersiveMetallurgy$fluxTheBath(Level level, BlockPos pos, BlockState state,
                                                        CrucibleBlockEntity crucible, CallbackInfo ci) {
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        MeltComposition container = (MeltComposition) (Object) crucible.getAlloy();
        double slag = crucible.getAlloy().getContent().getDouble(IMFluids.SLAG.fluid());
        if (container.immersiveMetallurgyFluxed()) {
            if (slag < 1) {
                container.immersiveMetallurgySetFluxed(false);
            }
            return;
        }
        if (slag < 1) {
            return;
        }
        FluidHeat heat = FluidHeat.get(crucible.getAlloyResult().getFluid());
        if (heat == null || crucible.getTemperature() < heat.meltTemperature()) {
            return;
        }
        SlagSkimming rules = SlagSkimming.of(level.getRecipeManager());
        IItemHandler contents = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (rules == null || contents == null) {
            return;
        }
        for (int slot = 0; slot < contents.getSlots(); slot++) {
            ItemStack stack = contents.getStackInSlot(slot);
            if (!stack.isEmpty() && rules.flux().test(stack)) {
                contents.extractItem(slot, 1, false);
                container.immersiveMetallurgySetFluxed(true);
                level.playSound(null, pos, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.6F, 1.3F);
                ImmersiveMetallurgy.LOGGER.debug("Flux from slot {} joined the bath at {}: {} mB of slag is now "
                        + "skim-cleanable", slot, pos, slag);
                return;
            }
        }
    }
}
