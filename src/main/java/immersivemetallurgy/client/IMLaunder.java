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
import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
import net.dries007.tfc.common.blockentities.MoldTableBlockEntity;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.component.mold.IMold;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * The metal moving through the furnace's launder, in either of its two lines.
 *
 * The trough the metal runs along is no longer drawn here — it is part of the casting table block the player
 * places, so the clay stands on its own. What is left is the stream: two boxes, the run along the trough and the
 * fall through the open floor to the board. The tap's stream is TFC's own, driven by the table's source, and this
 * class hands it the same two boxes; the slag line has no source to speak of, so its stream — slag, or the metal
 * that ran over the dam — is drawn here from the fluid itself.
 */
public final class IMLaunder {
    private static final float PIXEL = 1F / 16F;
    /** The run along the trough, from the fall's far face to six past the wall, four high. */
    public static final AABB RUN = box(10, 6, 6, 22, 9, 10);
    /** The corner column: the trough's open mouth dropped to the board, raised to the run's own top so the two meet
     *  at x=10 edge-to-edge. Four pixels wide (one past the hole toward the open mouth) so the fall reads 4x4 and
     *  closes the hairline of empty air at the mouth's left edge. Overlapping the run instead left their side faces
     *  coplanar, which read as a ripple. */
    public static final AABB FALL = box(6, 1, 6, 10, 9, 10);
    /** A conduit launder's run: from the mouth's far face to the cell's own far face so the next cell draws its own. */
    public static final AABB RUN_THROUGH = box(10, 6, 6, 16, 9, 10);
    /** A sink launder's run: from the fall's far face to the cell's far face, edge-to-edge with the fall. */
    public static final AABB RUN_NODE = box(10, 6, 6, 16, 9, 10);
    /** The stream continuing down out of a sink launder into the crucible standing a block below it. */
    public static final AABB DROP = box(7, -8, 6, 10, 1, 10);
    /** The combined cell's fall: the same open mouth, dropped to the channel groove floor at y=2 rather than a board,
     *  and four pixels wide like {@link #FALL} so the column reads 4x4 and closes the hairline at the mouth's left. */
    public static final AABB FALL_STACK = box(6, 2, 6, 10, 9, 10);
    public static final AABB RUN_STACK = box(10, 6, 6, 16, 9, 10);
    /** The fall continuing out of the cell into a mold table standing a block below: down to that table's board,
     *  so the stream lands in the vessel instead of stopping in mid-air at the trough's own floor. */
    public static final AABB DROP_TABLE = box(7, -14, 6, 10, 1, 10);
    public static final AABB DROP_TABLE_STACK = box(7, -14, 6, 10, 2, 10);

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

    /** The slag line's stream, drawn from the fluid itself. */
    public static void drawStream(PoseStack pose, MultiBufferSource buffers, FluidStack fluid, On on, int light,
                                  int overlay) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(RenderHelpers.BLOCKS_ATLAS)
                .apply(IClientFluidTypeExtensions.of(fluid.getFluidType()).getStillTexture());
        drawStream(pose, buffers.getBuffer(RenderType.cutout()), sprite, RenderHelpers.getFluidColor(fluid),
                on.side(), light, overlay);
    }

    /**
     * The stream on a launder that stands on its own: the run down its trough, and the fall through the open mouth.
     * A crucible a block below draws nothing of its own, so the drop continues into it; a channel below draws its
     * own pool and up-arm, so the fall stops at the cell floor and hands over rather than painting a second column
     * over it. A conduit launder draws only its own cell, so two launders in a row never share a face.
     */
    public static void drawNode(PoseStack pose, MultiBufferSource buffers, FluidStack fluid, Direction side,
                                boolean drop, boolean intoTable, boolean conduit, int light, int overlay) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(RenderHelpers.BLOCKS_ATLAS)
                .apply(IClientFluidTypeExtensions.of(fluid.getFluidType()).getStillTexture());
        VertexConsumer sink = buffers.getBuffer(RenderType.cutout());
        int color = RenderHelpers.getFluidColor(fluid);
        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(IMVesselShapes.pieceYaw(side)));
        pose.translate(-0.5F, 0.0F, -0.5F);
        if (drop) {
            RenderHelpers.renderTexturedCuboid(pose, sink, sprite, light, overlay, FALL, color);
            RenderHelpers.renderTexturedCuboid(pose, sink, sprite, light, overlay, RUN_NODE, color);
            RenderHelpers.renderTexturedCuboid(pose, sink, sprite, light, overlay, DROP, color);
        } else if (intoTable) {
            RenderHelpers.renderTexturedCuboid(pose, sink, sprite, light, overlay, FALL, color);
            RenderHelpers.renderTexturedCuboid(pose, sink, sprite, light, overlay, RUN_NODE, color);
            RenderHelpers.renderTexturedCuboid(pose, sink, sprite, light, overlay, DROP_TABLE, color);
        } else if (conduit) {
            RenderHelpers.renderTexturedCuboid(pose, sink, sprite, light, overlay, FALL, color);
            RenderHelpers.renderTexturedCuboid(pose, sink, sprite, light, overlay, RUN_NODE, color);
        } else {
            RenderHelpers.renderTexturedCuboid(pose, sink, sprite, light, overlay, RUN_THROUGH, color);
        }
        pose.popPose();
    }

    /** The stream on a launder with a channel under it in the same cell: the run down the trough and the fall
     *  into the channel below, which draws its own flow on from there. */
    public static void drawStack(PoseStack pose, MultiBufferSource buffers, FluidStack fluid, Direction side,
                                 boolean intoTable, int light, int overlay) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(RenderHelpers.BLOCKS_ATLAS)
                .apply(IClientFluidTypeExtensions.of(fluid.getFluidType()).getStillTexture());
        VertexConsumer sink = buffers.getBuffer(RenderType.cutout());
        int color = RenderHelpers.getFluidColor(fluid);
        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(IMVesselShapes.pieceYaw(side)));
        pose.translate(-0.5F, 0.0F, -0.5F);
        RenderHelpers.renderTexturedCuboid(pose, sink, sprite, light, overlay, FALL_STACK, color);
        RenderHelpers.renderTexturedCuboid(pose, sink, sprite, light, overlay, RUN_STACK, color);
        if (intoTable) {
            RenderHelpers.renderTexturedCuboid(pose, sink, sprite, light, overlay, DROP_TABLE_STACK, color);
        }
        pose.popPose();
    }

    /** What the hole behind this launder is running, walked back along the run to the duct that feeds it. */
    public static int flowBehind(Level level, BlockPos pos, Direction side) {
        BlockPos here = pos;
        Direction dir = side;
        for (int step = 0; step < 8; step++) {
            here = here.relative(dir);
            int flow = ductFlow(level, here);
            if (flow >= 0) {
                return flow;
            }
            if (isConduit(level, here)) {
                continue;
            }
            if (isConduit(level, here.below())) {
                here = here.below();
                continue;
            }
            Direction bend = null;
            BlockPos bent = null;
            for (Direction turn : new Direction[]{dir.getClockWise(), dir.getCounterClockWise()}) {
                BlockPos cell = here.relative(turn);
                int turned = ductFlow(level, cell);
                if (turned >= 0) {
                    return turned;
                }
                if (isConduit(level, cell)) {
                    bend = turn;
                    bent = cell;
                    break;
                }
            }
            if (bend == null) {
                return RefiningDuctBlock.FLOW_NONE;
            }
            dir = bend;
            here = bent;
        }
        return RefiningDuctBlock.FLOW_NONE;
    }

    /** The duct's flow at a cell, or -1 when the cell is not a duct. */
    private static int ductFlow(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(IMBlocks.REFINING_DUCT.get()) ? state.getValue(RefiningDuctBlock.FLOW) : -1;
    }

    /** The metal a tap run is carrying, read off the sink it ends at: the dish on a table, or the pot itself. */
    @Nullable
    public static FluidStack metalAhead(Level level, BlockPos pos, Direction away) {
        BlockPos here = pos;
        Direction dir = away;
        for (int step = 0; step < 8; step++) {
            if (level.getBlockEntity(here.below()) instanceof CrucibleBlockEntity
                    || level.getBlockEntity(here.below()) instanceof MoldTableBlockEntity) {
                return metalAt(level, here.below());
            }
            BlockPos next = here.relative(dir);
            if (isSink(level, next)) {
                return metalAt(level, next);
            }
            if (isConduit(level, next)) {
                here = next;
                continue;
            }
            if (isConduit(level, here.below())) {
                here = here.below();
                continue;
            }
            Direction bend = null;
            BlockPos bent = null;
            for (Direction turn : new Direction[]{dir.getClockWise(), dir.getCounterClockWise()}) {
                BlockPos cell = here.relative(turn);
                if (isSink(level, cell)) {
                    return metalAt(level, cell);
                }
                if (isConduit(level, cell)) {
                    bend = turn;
                    bent = cell;
                    break;
                }
            }
            if (bend == null) {
                return null;
            }
            dir = bend;
            here = bent;
        }
        return null;
    }

    /** Whether a cell holds something the run ends in, full or not: a table or a crucible. */
    private static boolean isSink(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MoldTableBlockEntity
                || level.getBlockEntity(pos) instanceof CrucibleBlockEntity;
    }

    private static boolean isConduit(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(TFCBlocks.CHANNEL.get()) || state.is(IMBlocks.LAUNDER.get())
                || state.is(IMBlocks.LAUNDER_CHANNEL.get());
    }

    /** Whether a channel or launder stands on the ground under this cell, waiting to carry the stream on. */
    public static boolean conduitBelow(Level level, BlockPos pos) {
        return isConduit(level, pos.below());
    }

    /** Whether a mold table stands a block under this cell, waiting to catch the stream in its vessel. */
    public static boolean tableBelow(Level level, BlockPos pos) {
        return level.getBlockEntity(pos.below()) instanceof MoldTableBlockEntity;
    }

    /** The metal standing in a sink, or null when it holds nothing liquid. */
    @Nullable
    private static FluidStack metalAt(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MoldTableBlockEntity table) {
            ItemStack vessel = table.getMoldStack();
            String id = vessel.get(IMDataComponents.SKIMMING_FLUID.get());
            Integer mb = vessel.get(IMDataComponents.SKIMMING_MB.get());
            if (id != null && mb != null && mb > 0) {
                Fluid metal = BuiltInRegistries.FLUID.get(ResourceLocation.parse(id));
                if (metal != null) {
                    return new FluidStack(metal, 1);
                }
            }
            // An ordinary casting mould keeps its metal in its own tank, not in the dish's skimming record.
            IMold mold = IMold.get(vessel);
            if (mold != null) {
                FluidStack tank = mold.getFluidInTank(0);
                if (!tank.isEmpty()) {
                    return tank.copyWithAmount(1);
                }
            }
            return null;
        }
        if (level.getBlockEntity(pos) instanceof CrucibleBlockEntity crucible) {
            FluidStack result = crucible.getAlloyResult();
            return result.isEmpty() ? null : result;
        }
        return null;
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

    private static AABB box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return new AABB(minX * PIXEL, minY * PIXEL, minZ * PIXEL, maxX * PIXEL, maxY * PIXEL, maxZ * PIXEL);
    }
}
