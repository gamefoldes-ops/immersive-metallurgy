package immersivemetallurgy.event;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.registry.IMItems;

import net.dries007.tfc.common.blockentities.IngotPileBlockEntity;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.devices.IngotPileBlock;
import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.InteractionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * TFC's ingot and double ingot piles accept the concrete items they were registered with, which are TFC's own
 * ingots - so a flawed ingot had nowhere in the world to be stored, even though the pile keeps whatever stack
 * it is handed and gives it back unchanged. This registers our two families against the same blocks, and the
 * one thing TFC cannot do is reach a list it never saw.
 */
@EventBusSubscriber(modid = ImmersiveMetallurgy.MODID)
public final class IMIngotPiles {
    private IMIngotPiles() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            InteractionManager.register(ourItems("metal/ingot/flawed_"), InteractionManager.Target.BOTH,
                    (stack, context) -> pileOnto(stack, context, TFCBlocks.INGOT_PILE.get()));
            InteractionManager.register(ourItems("metal/double_ingot/flawed_"), InteractionManager.Target.BOTH,
                    (stack, context) -> pileOnto(stack, context, TFCBlocks.DOUBLE_INGOT_PILE.get()));
        });
    }

    private static Ingredient ourItems(String pathPrefix) {
        List<Item> items = new ArrayList<>();
        for (var holder : IMItems.ITEMS.getEntries()) {
            if (holder.getId().getPath().startsWith(pathPrefix)) {
                items.add(holder.value());
            }
        }
        return Ingredient.of(items.toArray(new Item[0]));
    }

    /** Sneaking at a pile with an ingot in hand, exactly the way TFC's own ingots are stored. */
    private static InteractionResult pileOnto(ItemStack stack, UseOnContext context, Block pile) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (level.isClientSide || player == null || !player.mayBuild() || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        IntegerProperty count = ((IngotPileBlock) pile).getCountProperty();
        int limit = count.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(1);
        BlockPos clicked = context.getClickedPos();
        BlockPos target = level.getBlockState(clicked).is(pile) ? clicked : clicked.above();
        BlockState state = level.getBlockState(target);
        if (state.is(pile)) {
            int held = state.getValue(count);
            return held >= limit ? InteractionResult.PASS : store(level, player, target, state.setValue(count, held + 1), stack);
        }
        BlockState fresh = pile.defaultBlockState().setValue(count, 1);
        if (!state.isAir() || !fresh.canSurvive(level, target)) {
            return InteractionResult.PASS;
        }
        return store(level, player, target, fresh, stack);
    }

    /** One ingot leaves the hand and becomes an entry of the pile, which is the whole point: the pile keeps stacks. */
    private static InteractionResult store(Level level, Player player, BlockPos pos, BlockState state, ItemStack stack) {
        ItemStack one = stack.split(1);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof IngotPileBlockEntity pile) {
            pile.addIngot(one);
        }
        Helpers.playPlaceSound(player, level, pos, state);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
