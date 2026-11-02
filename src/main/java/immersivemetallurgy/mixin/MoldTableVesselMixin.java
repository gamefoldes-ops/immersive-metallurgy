package immersivemetallurgy.mixin;

import immersivemetallurgy.registry.IMItems;

import com.mojang.blaze3d.vertex.PoseStack;
import net.dries007.tfc.client.render.blockentity.MoldBlockEntityRenderer;
import net.dries007.tfc.common.blockentities.MoldTableBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Laying the furnace's own trays on the mold table.
 *
 * TFC draws what sits on a table from shapes it baked for its own mould kinds, so a vessel that is not one of
 * those kinds is a real, working, fillable object that the table has no picture of — the player sees an empty
 * table under a full mould and concludes the machine is broken. The item itself renders everywhere else, which
 * is what localised this to the table's renderer rather than to the model or the texture.
 */
@Mixin(MoldBlockEntityRenderer.class)
public class MoldTableVesselMixin {
    @Inject(method = "render(Lnet/dries007/tfc/common/blockentities/MoldTableBlockEntity;FLcom/mojang/blaze3d/"
            + "vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V", at = @At("RETURN"),
            require = 0)
    private void immersiveMetallurgy$drawOurVessel(MoldTableBlockEntity table, float partialTick, PoseStack pose,
                                                  MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        ItemStack vessel = table.getMoldStack();
        if (!isOurs(vessel)) {
            return;
        }
        Level level = table.getLevel();
        pose.pushPose();
        // GROUND already lays an item flat on its back and turns it face-up, so the pose only centres it on the
        // board and lifts it clear of the table's own top face — anything lower is hidden by the board it sits on,
        // which is what made the first attempt draw a tray nobody could see.
        pose.translate(0.5F, 0.07F, 0.5F);
        pose.scale(1.7F, 1.7F, 1.7F);
        Minecraft.getInstance().getItemRenderer().renderStatic(vessel, ItemDisplayContext.FIXED, light, overlay,
                pose, buffers, level == null ? Minecraft.getInstance().level : level, 0);
        pose.popPose();
    }

    private static boolean isOurs(ItemStack stack) {
        return stack.is(IMItems.SLAG_PLATE.get()) || stack.is(IMItems.REFINING_MOLD.get())
                || stack.is(IMItems.CLAY_REFINING_MOLD.get());
    }
}
