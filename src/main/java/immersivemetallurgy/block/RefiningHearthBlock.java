package immersivemetallurgy.block;

import immersivemetallurgy.block.entity.RefiningFurnaceBlockEntity;
import immersivemetallurgy.furnace.FurnaceShape;
import immersivemetallurgy.recipe.RefiningFurnaceStats;
import immersivemetallurgy.registry.IMBlockEntities;

import com.mojang.serialization.MapCodec;
import immersivemetallurgy.ImmersiveMetallurgy;
import net.dries007.tfc.common.items.TFCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.ItemAbilities;

/**
 * The door of the furnace, and the only part of it a player works at: the charge is thrown in here, the fire is
 * lit here with a firestarter, the lining is scraped off here, and the state of the whole stack is read here.
 *
 * It is also the anchor the shape is measured from, so which way it faces decides where the shaft is — one block
 * in front of it — and how tall the stack is, which is whatever the player built upward in whole modules.
 */
public class RefiningHearthBlock extends BaseEntityBlock {
    public static final MapCodec<RefiningHearthBlock> CODEC = simpleCodec(RefiningHearthBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public RefiningHearthBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        RefiningFurnaceBlockEntity furnace = level.getBlockEntity(pos, IMBlockEntities.HEARTH.get()).orElse(null);
        RefiningFurnaceStats stats = RefiningFurnaceStats.of(level.getRecipeManager());
        if (furnace == null || stats == null || !player.mayBuild()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (isIgniter(held)) {
            return ignite(furnace, stats, level, pos, player);
        }
        if (!furnace.isLit() && furnace.lining() > 0 && held.is(ItemTags.PICKAXES)
                && furnace.scrapeLining(player, held, stats) > 0) {
            level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1F, 0.8F);
            return ItemInteractionResult.CONSUME;
        }
        if (held.is(ItemTags.SHOVELS) && furnace.quench()) {
            level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1F, 1F);
            player.displayClientMessage(Component.translatable("screen." + ImmersiveMetallurgy.MODID
                    + ".furnace.quenched"), true);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        // Nothing is fed to this furnace by hand. A click that ate an item cost a stack to a player who meant to
        // mine the block, so fuel, powder and metal all go in the way they go into a real shaft furnace: dropped
        // down the open throat, where what is not wanted simply keeps falling rather than vanishing.
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Anything the game lets a fire catch with. TFC's firestarter carries the igniter ability in a field the
     * tag does not name, its flint-and-pyrite is a plain item, and vanilla's flint-and-steel answers through
     * NeoForge's ability — so the ability plus that one item is the whole list of ways to light this fire.
     */
    private static boolean isIgniter(ItemStack held) {
        return held.canPerformAction(ItemAbilities.FIRESTARTER_LIGHT) || held.is(TFCItems.FLINT_AND_PYRITE.asItem());
    }

    /** Lighting, with the reason a firestarter was refused said out loud instead of eaten. */
    private static ItemInteractionResult ignite(RefiningFurnaceBlockEntity furnace, RefiningFurnaceStats stats,
                                                Level level, BlockPos pos, Player player) {
        String why = furnace.lightRefusal(stats);
        if (!why.equals("none")) {
            Component reason = why.equals("shape")
                    ? Component.translatable("screen." + ImmersiveMetallurgy.MODID + ".furnace.fault." + faultKey(furnace))
                    : Component.translatable("screen." + ImmersiveMetallurgy.MODID + ".furnace.cannot_light." + why,
                            furnace.fuelRequired(stats), furnace.fuelCount());
            player.displayClientMessage(reason, true);
            return ItemInteractionResult.FAIL;
        }
        level.playSound(null, pos, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1F, 0.8F);
        return furnace.light(stats) ? ItemInteractionResult.CONSUME : ItemInteractionResult.FAIL;
    }

    private static String faultKey(RefiningFurnaceBlockEntity furnace) {
        return FurnaceShape.Fault.values()[furnace.faultOrdinal()].key();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        level.getBlockEntity(pos, IMBlockEntities.HEARTH.get()).ifPresent(player::openMenu);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replaced, boolean moving) {
        if (!replaced.is(state.getBlock())) {
            RefiningFurnaceStats stats = RefiningFurnaceStats.of(level.getRecipeManager());
            level.getBlockEntity(pos, IMBlockEntities.HEARTH.get()).ifPresent(furnace -> furnace.eject(stats));
        }
        super.onRemove(state, level, pos, replaced, moving);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from,
                                   boolean isMoving) {
        // The stack is measured from here, so anything changing beside it may have changed what it is.
        level.getBlockEntity(pos, IMBlockEntities.HEARTH.get()).ifPresent(RefiningFurnaceBlockEntity::requestRescan);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The hearth looks at the furnace, not at the player standing in front of it.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RefiningFurnaceBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, IMBlockEntities.HEARTH.get(), RefiningFurnaceBlockEntity::serverTick);
    }
}
