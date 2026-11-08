package immersivemetallurgy.mixin;

import immersivemetallurgy.client.IMVesselShapes;
import immersivemetallurgy.registry.IMBlocks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.dries007.tfc.client.RenderHelpers;
import net.dries007.tfc.client.render.blockentity.MoldBlockEntityRenderer;
import net.dries007.tfc.common.blockentities.MoldTableBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Laying the furnace's own vessels on the mold table.
 *
 * TFC draws what sits on a table from shapes it baked for its own mould kinds, so a vessel that is not one of
 * those kinds is a real, working, fillable object the table has no picture of — the player sees an empty table
 * under a full mould and concludes the machine is broken. The item itself renders everywhere else, which is what
 * localised this to the table's renderer rather than to the model or the texture.
 */
@Mixin(MoldBlockEntityRenderer.class)
public class MoldTableVesselMixin {
    /**
     * What fraction of the surrounding light the drawn spout keeps, out of sixteen. The item path hands every
     * vertex the light it is given and no ambient occlusion at all, while TFC's channel is drawn as a block and
     * comes back shaded by what stands next to it — same clay, two brightnesses, and the spout read as burnt
     * beside the table it belongs to. Dimming the light is the honest half of a fix: one knob, no re-tuned shape,
     * and it does not pretend this path can ask for real AO.
     */
    private static final int CLAY_SHADE = 12;
    private static final int CLAY_SHADE_OF = 16;

    /** The same packed sky and block light, pulled down to the shade a block of clay would sit in. */
    private static int shaded(int light) {
        int sky = (light >> 16) * CLAY_SHADE / CLAY_SHADE_OF;
        int block = (light & 0xFFFF) * CLAY_SHADE / CLAY_SHADE_OF;
        return (sky << 16) | block;
    }

    /**
     * One pixel, for the eight-ingot tray only.
     *
     * The tray was authored to stand at y 1..2 so that TFC's own pour animation, which tops out at y 1.95, filled
     * its beds flush. Standing that close to the board made it look like a channel run into the table could reach
     * the mould, when the reason it cannot is right there in the heights: a channel's floor is at y 2 and the
     * metal in it never rises above that floor. Lifting the tray a pixel shows the difference — and only pays for
     * it by lifting the drawn metal the same pixel, which is what the two redirects below do. Nothing else on the
     * table moves: TFC's own moulds, and our slag dish, are drawn at the heights the game gives them.
     */
    private static final float RAISE = 1F / 16F;
    private static boolean raised;

    /**
     * The tap's stream, at the height of the launder it runs in.
     *
     * TFC fills a channel with a four-pixel bar along the way the metal came and a four-pixel square in its
     * middle, both at y 1..4 of the channel's own cell. Our launder is that same channel, drawn five pixels above
     * the board, so the metal is those same two boxes lifted by the same five — and then it goes on down through
     * the hole the channel leaves in its bottom when it points at a table. A table fed by anything else, TFC's own
     * channels included, keeps the stream exactly as the game draws it.
     */
    private static final float PIXEL = 1F / 16F;
    /** Five pixels of air between the board and the launder's own floor, which is where the channel stands. */
    private static final float LAUNDER_LIFT = 5 * PIXEL;
    /** The bar and the middle square together: from six pixels into the cell to six past the wall, y 6..9. */
    private static final AABB LAUNDER_RUN = new AABB(6 * PIXEL, 6 * PIXEL, 6 * PIXEL, 22 * PIXEL, 9 * PIXEL,
            10 * PIXEL);
    /** The same middle square, through the open bottom and down to the board. */
    private static final AABB LAUNDER_FALL = new AABB(6 * PIXEL, PIXEL, 6 * PIXEL, 10 * PIXEL, 7 * PIXEL, 10 * PIXEL);
    private static boolean launder;

    /**
     * Hanging TFC's own channel over the table as the tap's launder.
     *
     * The furnace already borrows TFC's trough for the slag line, and the tap wanted the same thing worn
     * honestly — so this draws the channel's own baked pieces rather than a shape of ours. They belong to a cell,
     * and the launder sits in the table's cell, five pixels above its floor, which is exactly where a block cannot
     * also be, one cell holding one block. So nothing is placed and nothing is stored: the duct behind the table
     * carries which line it runs on in its own state, and this is the renderer that already draws things in that
     * cell. Drawn at HEAD, before TFC's own render has committed to whether it will bail on a table holding a
     * foreign vessel, so a bare tap table still shows its launder.
     */
    @Inject(method = "render(Lnet/dries007/tfc/common/blockentities/MoldTableBlockEntity;FLcom/mojang/blaze3d/"
            + "vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V", at = @At("HEAD"),
            require = 0)
    private void immersiveMetallurgy$drawTapSpout(MoldTableBlockEntity table, float partialTick, PoseStack pose,
                                                  MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        // Decided here, at the top, because this is the only point in the draw that still holds the table: the
        // redirects below are aimed at static helpers that are given heights, not vessels.
        raised = IMVesselShapes.isTray(table.getMoldStack());
        launder = false;
        Level level = table.getLevel();
        if (level == null) {
            return;
        }
        Direction side = IMVesselShapes.tapSide(level, table.getBlockPos());
        launder = side != null;
        if (side == null) {
            return;
        }
        BakedModel base = IMVesselShapes.channel(IMVesselShapes.CHANNEL_BASE);
        BakedModel joint = IMVesselShapes.channel(IMVesselShapes.CHANNEL_CONNECTION);
        BakedModel stop = IMVesselShapes.channel(IMVesselShapes.CHANNEL_STOP);
        if (base == null || joint == null || stop == null) {
            return;
        }
        VertexConsumer sink = buffers.getBuffer(RenderType.solid());
        // The stack is only what the model is asked for quads about; a launder is not an item and has no overrides,
        // so a duct in the hand answers the same as anything else. Built per draw on purpose: a static stack here
        // was once a crash, because client classes are loaded before items exist.
        ItemStack probe = new ItemStack(IMBlocks.REFINING_DUCT.get());
        int clay = shaded(light);
        drawPiece(pose, sink, base, probe, clay, overlay, 0F);
        drawPiece(pose, sink, joint, probe, clay, overlay, IMVesselShapes.pieceYaw(side));
        for (Direction other : Direction.Plane.HORIZONTAL) {
            if (other != side) {
                drawPiece(pose, sink, stop, probe, clay, overlay, IMVesselShapes.pieceYaw(other));
            }
        }
        // The bottom piece is left out on purpose: without it the channel is the variant that has a hole in its
        // floor, which is the one TFC draws when the cell below is where the metal is going.
    }

    /**
     * One of TFC's channel pieces, standing in this cell and turned about its centre.
     *
     * The channel is authored on the floor of its own cell, so the launder is the same pieces lifted five pixels
     * and turned by the same angles its multipart states use.
     */
    private static void drawPiece(PoseStack pose, VertexConsumer sink, BakedModel piece, ItemStack probe, int light,
                                  int overlay, float yaw) {
        pose.pushPose();
        pose.translate(0.5F, LAUNDER_LIFT, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-0.5F, 0.0F, -0.5F);
        Minecraft.getInstance().getItemRenderer().renderModelLists(piece, probe, light, overlay, pose, sink);
        pose.popPose();
    }

    /**
     * Replacing the stream that feeds a tap with the launder's own.
     *
     * Only the direction is read from the pair TFC was given, so the metal still arrives along the face it came
     * from, and the two boxes are the ones the game would have drawn for a channel — lifted into the trough and
     * carried on down through its hole.
     */
    @Redirect(method = "render(Lnet/dries007/tfc/common/blockentities/MoldTableBlockEntity;FLcom/mojang/blaze3d/"
            + "vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/dries007/tfc/client/RenderHelpers;renderChannelFlow("
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                    + "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;IIILorg/apache/commons/lang3/tuple/"
                    + "Pair;Z)V"))
    private void immersiveMetallurgy$drawTapStream(PoseStack pose, VertexConsumer buffer, TextureAtlasSprite sprite,
                                                   int color, int light, int overlay, Pair<Direction, Byte> source,
                                                   boolean renderFlowSource) {
        if (!launder) {
            RenderHelpers.renderChannelFlow(pose, buffer, sprite, color, light, overlay, source, renderFlowSource);
            return;
        }
        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(IMVesselShapes.pieceYaw(source.getLeft())));
        pose.translate(-0.5F, 0.0F, -0.5F);
        RenderHelpers.renderTexturedCuboid(pose, buffer, sprite, light, overlay, LAUNDER_RUN, color);
        RenderHelpers.renderTexturedCuboid(pose, buffer, sprite, light, overlay, LAUNDER_FALL, color);
        pose.popPose();
    }

    /**
     * The metal standing in a raised tray, while it is still liquid.
     *
     * TFC works the height out itself — `1 + 0.95 × fill`, over sixteen — and hands it as the fifth float to
     * {@link RenderHelpers#renderFluidFace}. The tray model was lifted a pixel, so this has to follow it or the
     * pour would sit a pixel under the clay it is inside. Both trailing ints are passed straight back in the
     * places they came from, which is why it does not matter which of them is overlay and which is light.
     */
    @Redirect(method = "render(Lnet/dries007/tfc/common/blockentities/MoldTableBlockEntity;FLcom/mojang/blaze3d/"
            + "vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/dries007/tfc/client/RenderHelpers;renderFluidFace("
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/neoforged/neoforge/fluids/FluidStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;FFFFFII)V"))
    private void immersiveMetallurgy$raiseMoltenFace(PoseStack pose, FluidStack fluid, MultiBufferSource buffers,
                                                     float minX, float maxX, float minZ, float maxZ, float y,
                                                     int first, int second) {
        RenderHelpers.renderFluidFace(pose, fluid, buffers, minX, maxX, minZ, maxZ, raised ? y + RAISE : y,
                first, second);
    }

    /** The same pixel for a cast that has already set, whose heights come from {@link RenderHelpers#getYVertices}. */
    @Redirect(method = "render(Lnet/dries007/tfc/common/blockentities/MoldTableBlockEntity;FLcom/mojang/blaze3d/"
            + "vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/dries007/tfc/client/RenderHelpers;getYVertices(FFFFFF)[[F"))
    private float[][] immersiveMetallurgy$raiseSetFace(float minX, float minY, float minZ, float maxX, float y,
                                                       float maxZ) {
        return RenderHelpers.getYVertices(minX, minY, minZ, maxX, raised ? y + RAISE : y, maxZ);
    }

    @Inject(method = "render(Lnet/dries007/tfc/common/blockentities/MoldTableBlockEntity;FLcom/mojang/blaze3d/"
            + "vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V", at = @At("RETURN"),
            require = 0)
    private void immersiveMetallurgy$drawOurVessel(MoldTableBlockEntity table, float partialTick, PoseStack pose,
                                                  MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        ItemStack vessel = table.getMoldStack();
        if (!IMVesselShapes.isOurs(vessel)) {
            return;
        }
        BakedModel shape = IMVesselShapes.baked(vessel);
        if (shape == null) {
            return;
        }
        pose.pushPose();
        // The shape is authored in this block's sixteenths standing on the board, and asking the item renderer for
        // no display transform leaves it exactly there: `render` shifts the model back by half a block itself, so
        // centring on the block is the whole of it. No size, no slide, no tilt, and no turning either — a mold
        // table is a multipart block with one boolean per side and no `facing` to read, which is also why TFC's
        // own moulds sit the same way on every table in the world. The tray goes up the one pixel its metal goes up.
        pose.translate(0.5F, 0.5F + (raised ? RAISE : 0F), 0.5F);
        Minecraft.getInstance().getItemRenderer().render(vessel, ItemDisplayContext.NONE, false, pose, buffers,
                light, overlay, shape);
        pose.popPose();
    }
}
