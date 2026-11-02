package immersivemetallurgy.client;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.registry.IMFluids;

import net.dries007.tfc.client.extensions.FluidRendererExtension;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Our molten fluids reuse TFC's molten metal sprites and TFC's own colour for the metal, because the table paints
 * a pour by multiplying that sprite by the fluid's tint: any other number makes the same metal look like a
 * different one depending on which mould it stands in.
 *
 * The extension is TFC's own {@link FluidRendererExtension}, built with the same four arguments TFC builds its
 * molten ones with in {@code ClientEventHandler} — the sprite pair and two null overlays — rather than an
 * anonymous class of ours that overrode only the two accessors it thought of. A fluid whose colour comes from a
 * different class than a metal's does not answer the same questions, and the pour in an inventory icon is drawn by
 * a model that asks all of them.
 */
@EventBusSubscriber(modid = ImmersiveMetallurgy.MODID, value = Dist.CLIENT)
public final class IMClientFluidExtensions {
    private static final ResourceLocation MOLTEN_STILL = ResourceLocation.fromNamespaceAndPath("tfc", "block/molten_still");
    private static final ResourceLocation MOLTEN_FLOWING = ResourceLocation.fromNamespaceAndPath("tfc", "block/molten_flow");

    private IMClientFluidExtensions() {
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        for (IMFluids.Molten molten : IMFluids.all()) {
            event.registerFluidType(new FluidRendererExtension(0xFF000000 | molten.tint(), MOLTEN_STILL,
                    MOLTEN_FLOWING, null, null), molten.typeHolder());
        }
    }
}
