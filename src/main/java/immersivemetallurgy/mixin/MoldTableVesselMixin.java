package immersivemetallurgy.mixin;

import immersivemetallurgy.client.IMVesselShapes;

import com.mojang.blaze3d.vertex.PoseStack;
import net.dries007.tfc.client.render.blockentity.MoldBlockEntityRenderer;
import net.dries007.tfc.common.blockentities.MoldTableBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
        // own moulds sit the same way on every table in the world.
        pose.translate(0.5F, 0.5F, 0.5F);
        Minecraft.getInstance().getItemRenderer().render(vessel, ItemDisplayContext.NONE, false, pose, buffers,
                light, overlay, shape);
        pose.popPose();
    }
}
