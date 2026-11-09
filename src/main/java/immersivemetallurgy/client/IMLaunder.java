package immersivemetallurgy.client;

import immersivemetallurgy.block.RefiningDuctBlock;
import immersivemetallurgy.furnace.Launder;
import immersivemetallurgy.registry.IMBlocks;
import immersivemetallurgy.registry.IMDataComponents;
import immersivemetallurgy.registry.IMFluids;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.dries007.tfc.client.RenderHelpers;
import net.dries007.tfc.common.blockentities.MoldTableBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * The furnace's launder over a mold table, in either of its two shapes.
 *
 * Both the tap and the slag hole hang a trough over the table standing in front of them, and both are the same
 * drawing: TFC's own channel, its pieces turned toward the hole and lifted five pixels off the board, with metal
 * running along it and falling through the hole the channel leaves in its floor. What differs is only what falls.
 * The tap's stream is TFC's own, driven by the table's source, and this class hands it the same two boxes; the
 * slag line has no source to speak of, so its stream — slag, or the metal that ran over the dam — is drawn here.
 */
public final class IMLaunder {
    private static final float PIXEL = 1F / 16F;
    /** Five pixels of air between the board and the launder's floor, which is where the channel stands. */
    private static final float LIFT = 5 * PIXEL;
    /** From six pixels into the cell to six past the wall, four high, filling the four-pixel trough. */
    public static final AABB RUN = box(6, 6, 6, 22, 9, 10);
    /** The trough's own middle square, dropped through the open floor to the board. */
    public static final AABB FALL = box(6, 1, 6, 10, 7, 10);

    /**
     * What fraction of the surrounding light the drawn clay keeps, out of sixteen. The item path hands every vertex
     * the light it is given and no ambient occlusion at all, while TFC's channel is drawn as a block and comes back
     * shaded by what stands next to it — same clay, two brightnesses.
     */
    private static final int CLAY_SHADE = 12;
    private static final int CLAY_SHADE_OF = 16;

    /** Which hole this table stands in front of. */
    public enum Line { NONE, TAP, SLAG }

    /** The line this cell belongs to and the side its hole stands on; {@link Line#NONE} for a plain table. */
    public record On(Line line, @Nullable Direction side) {
        static final On NONE = new On(Line.NONE, null);

        public boolean isReal() {
            return line != Line.NONE && side != null;
        }
    }

    private IMLaunder() {
    }

    public static On of(Level level, MoldTableBlockEntity table) {
        if (level == null) {
            return On.NONE;
        }
        Direction tap = Launder.lineSide(level, table.getBlockPos(),
                RefiningDuctBlock.LINE_TAP);
        if (tap != null) {
            return new On(Line.TAP, tap);
        }
        Direction slag = Launder.lineSide(level, table.getBlockPos(),
                RefiningDuctBlock.LINE_SLAG);
        return slag == null ? On.NONE : new On(Line.SLAG, slag);
    }

    /**
     * What is falling out of this launder right now, or null when the hole is quiet. Slag is the easy answer;
     * metal is whatever the dish under it is being filled with, read off the plate the table is holding.
     */
    @Nullable
    public static FluidStack flowing(Level level, MoldTableBlockEntity table, On on) {
        int flow = Launder.flow(level, table.getBlockPos(), on.side());
        if (flow == RefiningDuctBlock.FLOW_SLAG) {
            return new FluidStack(IMFluids.SLAG.fluid(), 1);
        }
        if (flow != RefiningDuctBlock.FLOW_METAL) {
            return null;
        }
        ItemStack vessel = table.getMoldStack();
        String id = vessel.get(IMDataComponents.SKIMMING_FLUID.get());
        Integer mb = vessel.get(IMDataComponents.SKIMMING_MB.get());
        if (id == null || mb == null || mb <= 0) {
            return null;
        }
        Fluid metal = BuiltInRegistries.FLUID.get(ResourceLocation.parse(id));
        return metal == null ? null : new FluidStack(metal, 1);
    }

    /** The clay: TFC's channel, turned toward the hole, with its floor left open. */
    public static void drawClay(PoseStack pose, MultiBufferSource buffers, On on, int light, int overlay) {
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
        piece(pose, sink, base, probe, clay, overlay, 0F);
        piece(pose, sink, joint, probe, clay, overlay, IMVesselShapes.pieceYaw(on.side()));
        for (Direction other : Direction.Plane.HORIZONTAL) {
            if (other != on.side()) {
                piece(pose, sink, stop, probe, clay, overlay, IMVesselShapes.pieceYaw(other));
            }
        }
    }

    /** The slag line's stream, drawn from the fluid itself. */
    public static void drawStream(PoseStack pose, MultiBufferSource buffers, FluidStack fluid, On on, int light,
                                  int overlay) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(RenderHelpers.BLOCKS_ATLAS)
                .apply(IClientFluidTypeExtensions.of(fluid.getFluidType()).getStillTexture());
        drawStream(pose, buffers.getBuffer(RenderType.cutout()), sprite, RenderHelpers.getFluidColor(fluid),
                on.side(), light, overlay);
    }

    /**
     * The two boxes the metal is drawn with, for whoever already holds a sprite: the tap's stream comes from TFC's
     * own renderer, which has worked the texture and the colour out and hands them over.
     */
    public static void drawStream(PoseStack pose, VertexConsumer sink, TextureAtlasSprite sprite, int color,
                                  Direction side, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(IMVesselShapes.pieceYaw(side)));
        pose.translate(-0.5F, 0.0F, -0.5F);
        RenderHelpers.renderTexturedCuboid(pose, sink, sprite, light, overlay, RUN, color);
        RenderHelpers.renderTexturedCuboid(pose, sink, sprite, light, overlay, FALL, color);
        pose.popPose();
    }

    /** One channel piece, standing in this cell and turned about its centre. */
    private static void piece(PoseStack pose, VertexConsumer sink, BakedModel model, ItemStack probe, int light,
                              int overlay, float yaw) {
        pose.pushPose();
        pose.translate(0.5F, LIFT, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-0.5F, 0.0F, -0.5F);
        Minecraft.getInstance().getItemRenderer().renderModelLists(model, probe, light, overlay, pose, sink);
        pose.popPose();
    }

    /** The same packed sky and block light, pulled down to the shade a block of clay would sit in. */
    private static int shaded(int light) {
        int sky = (light >> 16) * CLAY_SHADE / CLAY_SHADE_OF;
        int block = (light & 0xFFFF) * CLAY_SHADE / CLAY_SHADE_OF;
        return (sky << 16) | block;
    }

    private static AABB box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return new AABB(minX * PIXEL, minY * PIXEL, minZ * PIXEL, maxX * PIXEL, maxY * PIXEL, maxZ * PIXEL);
    }
}
