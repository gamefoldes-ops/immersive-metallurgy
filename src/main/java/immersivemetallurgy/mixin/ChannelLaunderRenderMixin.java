package immersivemetallurgy.mixin;

import immersivemetallurgy.block.LaunderChannelBlock;
import immersivemetallurgy.client.IMLaunder;
import immersivemetallurgy.registry.IMBlocks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.dries007.tfc.client.RenderHelpers;
import net.dries007.tfc.client.render.blockentity.ChannelBlockEntityRenderer;
import net.dries007.tfc.common.blockentities.ChannelBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The trough stream on the combined launder-and-channel cell.
 *
 * That cell keeps TFC's own channel entity and type so the flow below joins every channel beside it, which means
 * TFC's channel renderer draws it. The metal running down the trough and falling into the channel is ours, so it
 * is laid on top here whenever the channel below is actually carrying something — read straight off the entity,
 * so a bend or a side branch shows its stream just like a straight run. Only for our block, never a plain channel.
 */
@Mixin(ChannelBlockEntityRenderer.class)
public class ChannelLaunderRenderMixin {
    private static final String RENDER = "render(Lnet/dries007/tfc/common/blockentities/ChannelBlockEntity;FLcom/mojang/"
            + "blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V";

    /** Whether the channel being drawn this frame is our combined launder cell. */
    private boolean immersiveMetallurgy$combined;
    private Level immersiveMetallurgy$level;
    private BlockPos immersiveMetallurgy$pos;

    @Inject(method = RENDER, at = @At("HEAD"))
    private void immersiveMetallurgy$rememberCell(ChannelBlockEntity channel, float partialTick, PoseStack pose,
                                                  MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        immersiveMetallurgy$combined = channel.getBlockState().is(IMBlocks.LAUNDER_CHANNEL.get());
        immersiveMetallurgy$level = channel.getLevel();
        immersiveMetallurgy$pos = channel.getBlockPos();
    }

    /**
     * The arm TFC draws from a channel back to whatever feeds it. When that feeder is the furnace duct, the arm is a
     * stream straight from the pipe down (or across) into the gutter — a link the trough above already carries, so
     * it must not also be painted at channel height. Skip only that duct-fed arm; every channel-to-channel and
     * launder-to-channel link stays TFC's own.
     */
    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/dries007/tfc/client/RenderHelpers;"
            + "renderChannelFlow(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;"
            + "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;IIILorg/apache/commons/lang3/tuple/Pair;Z)V"))
    private void immersiveMetallurgy$skipDuctLink(PoseStack pose, VertexConsumer buffer, TextureAtlasSprite sprite,
                                                  int first, int second, int third, Pair<Direction, Byte> source,
                                                  boolean renderFlowSource) {
        Level level = immersiveMetallurgy$level;
        if (level != null && source != null
                && level.getBlockState(immersiveMetallurgy$pos.relative(source.getLeft()))
                        .is(IMBlocks.REFINING_DUCT.get())) {
            return;
        }
        RenderHelpers.renderChannelFlow(pose, buffer, sprite, first, second, third, source, renderFlowSource);
    }

    /**
     * The groove pool TFC paints in the channel half of the combined cell sits directly under the trough floor and
     * reads as a second stream leaking out below the clay, while the trough run above and the fall into the channel
     * below already show the metal. Skip just that pool for our cell; the arms stay TFC's.
     */
    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/dries007/tfc/client/RenderHelpers;"
            + "renderChannelFlowCenter(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;"
            + "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;III)V"))
    private void immersiveMetallurgy$skipGroovePool(PoseStack pose, VertexConsumer buffer, TextureAtlasSprite sprite,
                                                    int first, int second, int third) {
        if (immersiveMetallurgy$combined) {
            return;
        }
        RenderHelpers.renderChannelFlowCenter(pose, buffer, sprite, first, second, third);
    }

    @Inject(method = RENDER, at = @At("RETURN"))
    private void immersiveMetallurgy$drawTroughStream(ChannelBlockEntity channel, float partialTick, PoseStack pose,
                                                      MultiBufferSource buffers, int light, int overlay,
                                                      CallbackInfo ci) {
        if (!channel.getBlockState().is(IMBlocks.LAUNDER_CHANNEL.get()) || !channel.hasFlow()) {
            return;
        }
        Level level = channel.getLevel();
        if (level == null) {
            return;
        }
        Fluid fluid = BuiltInRegistries.FLUID.get(channel.getFluid());
        FluidStack stack = new FluidStack(fluid, 1);
        if (stack.isEmpty()) {
            return;
        }
        Direction side = channel.getBlockState().getValue(LaunderChannelBlock.FACING);
        boolean intoTable = IMLaunder.tableBelow(level, channel.getBlockPos());
        IMLaunder.drawStack(pose, buffers, stack, side, intoTable, LightTexture.FULL_BRIGHT, overlay);
    }
}