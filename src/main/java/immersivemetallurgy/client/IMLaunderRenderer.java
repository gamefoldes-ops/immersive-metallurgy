package immersivemetallurgy.client;

import immersivemetallurgy.block.LaunderBlock;
import immersivemetallurgy.block.RefiningDuctBlock;
import immersivemetallurgy.block.entity.LaunderBlockEntity;
import immersivemetallurgy.registry.IMFluids;

import com.mojang.blaze3d.vertex.PoseStack;
import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The stream on a launder that stands on its own, before any table is added under it. The trough itself is the
 * block's model; what is drawn here is only what runs through it — the metal or slag the duct behind it is
 * carrying, and the drop into a crucible waiting a block below.
 */
public class IMLaunderRenderer implements BlockEntityRenderer<LaunderBlockEntity> {
    public IMLaunderRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(LaunderBlockEntity launder, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (launder.getLevel() == null) {
            return;
        }
        Direction side = launder.getBlockState().getValue(LaunderBlock.FACING);
        int flow = IMLaunder.flowBehind(launder.getLevel(), launder.getBlockPos(), side);
        FluidStack fluid = null;
        if (flow == RefiningDuctBlock.FLOW_SLAG) {
            fluid = new FluidStack(IMFluids.SLAG.fluid(), 1);
        } else if (flow == RefiningDuctBlock.FLOW_METAL) {
            fluid = IMLaunder.metalAhead(launder.getLevel(), launder.getBlockPos(), side.getOpposite());
        }
        if (fluid == null) {
            return;
        }
        // A plain TFC channel below or ahead already draws the duct's metal at its own groove height, the way any
        // channel fed by a source does. Painting our trough stream over that link shows the same metal twice at two
        // heights, so the trough animation is left to the combined launder-and-channel cell and to sinks.
        BlockPos here = launder.getBlockPos();
        if (launder.getLevel().getBlockState(here.below()).is(TFCBlocks.CHANNEL.get())
                || launder.getLevel().getBlockState(here.relative(side.getOpposite())).is(TFCBlocks.CHANNEL.get())) {
            return;
        }
        boolean drop = launder.getLevel().getBlockEntity(here.below())
                instanceof CrucibleBlockEntity;
        boolean conduit = IMLaunder.conduitBelow(launder.getLevel(), here);
        boolean intoTable = IMLaunder.tableBelow(launder.getLevel(), here);
        IMLaunder.drawNode(pose, buffers, fluid, side, drop, intoTable, conduit, LightTexture.FULL_BRIGHT, overlay);
    }
}