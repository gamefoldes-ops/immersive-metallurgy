package immersivemetallurgy.client;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.registry.IMItems;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.model.DynamicFluidContainerModel;

/**
 * The colour of the metal a vessel is drawn with.
 *
 * A fluid container's icon does not carry its colour in the model: the pour layer is baked with tint index 1 and
 * the colour is asked of an {@link net.minecraft.client.color.item.ItemColor} at draw time. TFC registers one for
 * every item of its own namespace and none for anybody else's, so our moulds were drawn with the tint index
 * unanswered — which is white, whatever metal was standing in them. The table was never affected, because it reads
 * the fluid's colour itself rather than going through the item.
 */
@EventBusSubscriber(modid = ImmersiveMetallurgy.MODID, value = Dist.CLIENT)
public final class IMItemColours {
    private IMItemColours() {
    }

    @SubscribeEvent
    public static void onRegisterItemColours(RegisterColorHandlersEvent.Item event) {
        event.register(new DynamicFluidContainerModel.Colors(), IMItems.REFINING_MOLD.get(),
                IMItems.CLAY_REFINING_MOLD.get());
    }
}
