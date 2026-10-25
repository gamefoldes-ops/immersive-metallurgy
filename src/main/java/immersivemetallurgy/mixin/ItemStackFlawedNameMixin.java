package immersivemetallurgy.mixin;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.component.DefectProfile;
import immersivemetallurgy.registry.IMDataComponents;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A tool forged from flawed metal is TFC's own item, so its name reads exactly like a clean one and the
 * defect is invisible until you hold F3+H. Marking the name is what makes it feel like a different object;
 * our own ingots already carry the word in their item name, so they are left alone.
 */
@Mixin(ItemStack.class)
public class ItemStackFlawedNameMixin {
    @Inject(method = "getHoverName()Lnet/minecraft/network/chat/Component;", at = @At("TAIL"), cancellable = true)
    private void immersiveMetallurgy$markFlawed(CallbackInfoReturnable<Component> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (stack.isEmpty()) {
            return;
        }
        DefectProfile profile = stack.get(IMDataComponents.DEFECT_PROFILE.get());
        if (profile == null || profile.isClean()) {
            return;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (ImmersiveMetallurgy.MODID.equals(itemId.getNamespace())) {
            return;
        }
        cir.setReturnValue(Component.translatable("immersive_metallurgy.name.flawed", cir.getReturnValue()));
    }
}
