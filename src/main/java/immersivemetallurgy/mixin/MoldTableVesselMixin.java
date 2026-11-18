package immersivemetallurgy.mixin;

import immersivemetallurgy.client.IMLaunder;
import immersivemetallurgy.client.IMSlagDish;
import immersivemetallurgy.client.IMVesselShapes;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.dries007.tfc.client.RenderHelpers;
import net.dries007.tfc.client.render.blockentity.MoldBlockEntityRenderer;
import net.dries007.tfc.common.blockentities.MoldTableBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
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
     * Whether this table stands in front of the furnace's tap. The tap's stream is drawn by TFC, from the table's
     * own source, and only needs moving into the launder — which is what the redirect below is for, and it is
     * given heights rather than vessels, so the answer has to be decided while the table is still in hand.
     */
    private static boolean launder;

    /**
     * What the slag dish standing on this table holds, or null for every other vessel. Its tank is the slag and TFC
     * paints that; the metal the hearth ran over the dam is a count and a fluid on the stack, so its surface has to
     * be drawn here — and the slag's lifted to float above it, which is what the redirect below is for.
     */
    private static IMSlagDish.Contents dish;

    /**
     * Pouring the slag line through the launder, and telling the tap's redirect below that this table is a tap.
     *
     * The trough itself is no longer drawn here: it is part of the casting table block the player places, model
     * and hood and all, so the clay stands whether or not anything is flowing. What is left for the renderer is
     * the moving metal. The tap's stream is TFC's own, driven by the table's source, and only needs moving into
     * the launder — which is what the redirect below is for, and it is given heights rather than vessels, so the
     * answer has to be decided while the table is still in hand. The slag line has no source of its own, so its
     * stream — slag, or the metal that ran over the dam — is drawn here. Drawn at HEAD, before TFC's own render
     * has committed to whether it will bail on a table holding a foreign vessel.
     */
    @Inject(method = "render(Lnet/dries007/tfc/common/blockentities/MoldTableBlockEntity;FLcom/mojang/blaze3d/"
            + "vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V", at = @At("HEAD"),
            require = 0)
    private void immersiveMetallurgy$drawLaunder(MoldTableBlockEntity table, float partialTick, PoseStack pose,
                                                 MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        raised = IMVesselShapes.isTray(table.getMoldStack());
        launder = false;
        dish = IMSlagDish.read(table.getMoldStack());
        Level level = table.getLevel();
        if (level == null) {
            return;
        }
        IMLaunder.On on = IMLaunder.of(level, table);
        if (!on.isReal()) {
            return;
        }
        launder = on.line() == IMLaunder.Line.TAP;
        if (on.line() == IMLaunder.Line.SLAG) {
            FluidStack flowing = IMLaunder.flowing(level, table, on);
            if (flowing != null) {
                IMLaunder.drawStream(pose, buffers, flowing, on, light, overlay);
            }
        } else if (launder && table.hasSource()) {
            // TFC draws the tap's stream only when a channel stands at the table's source. A table right in the
            // furnace outlet is fed from the duct, which is no channel, so isFlowing() is false and TFC draws
            // nothing while the metal still arrives — the pour the player sees land with no stream above it. Draw
            // it here from the fluid the source carries, the same two boxes the slag line uses, and tell the
            // redirect below to stand down so a channel-fed tap is not drawn twice.
            Fluid fluid = table.getFluidToRender();
            if (fluid != Fluids.EMPTY) {
                IMLaunder.drawStream(pose, buffers, new FluidStack(fluid, 1), on, light, overlay);
            }
        }
    }

    /**
     * Standing TFC's own tap stream down on a launder table.
     *
     * The HEAD inject has already drawn the stream into the trough from the table's source, whether that source
     * is a channel or the furnace duct, so TFC's channel flow here would only paint a second one over it. A table
     * that is not on a launder keeps TFC's own drawing untouched.
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
        if (launder) {
            // The HEAD inject already drew this tap's stream into the launder; TFC's own would only double it.
            return;
        }
        RenderHelpers.renderChannelFlow(pose, buffer, sprite, color, light, overlay, source, renderFlowSource);
    }

    /**
     * The height of what stands in the table's vessel, while it is still liquid.
     *
     * TFC works it out itself — `1 + 0.95 × fill`, over sixteen — and hands it as the fifth float to
     * {@link RenderHelpers#renderFluidFace}. Two things follow from that: the tray model was lifted a pixel, so the
     * pour has to follow it or it sits a pixel under the clay it is inside; and a slag dish holding metal as well
     * as dirt has to show the slag at the height of both, not of the tank alone. Both trailing ints are passed
     * straight back in the places they came from, which is why it does not matter which is overlay and which light.
     */
    @Redirect(method = "render(Lnet/dries007/tfc/common/blockentities/MoldTableBlockEntity;FLcom/mojang/blaze3d/"
            + "vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/dries007/tfc/client/RenderHelpers;renderFluidFace("
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/neoforged/neoforge/fluids/FluidStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;FFFFFII)V"))
    private void immersiveMetallurgy$raiseMoltenFace(PoseStack pose, FluidStack fluid, MultiBufferSource buffers,
                                                     float minX, float maxX, float minZ, float maxZ, float y,
                                                     int first, int second) {
        float surface = dish != null ? IMSlagDish.slagLevel(dish) : (raised ? y + RAISE : y);
        RenderHelpers.renderFluidFace(pose, fluid, buffers, minX, maxX, minZ, maxZ, surface, first, second);
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
        if (dish != null && dish.hasMetal()) {
            // The metal the hearth ran over the dam, under the slag TFC has just painted over it.
            IMSlagDish.drawMetal(pose, buffers, dish, light, overlay);
        }
    }
}
