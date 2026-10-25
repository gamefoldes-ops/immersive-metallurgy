package immersivemetallurgy.client;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.registry.IMFluids;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Our molten fluids reuse TFC's molten metal sprites, tinted per metal, so a flawed alloy reads
 * as the dirty version of the metal it is flawed from.
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
            int tint = molten.tint();
            event.registerFluidType(new IClientFluidTypeExtensions() {
                @Override
                public ResourceLocation getStillTexture() {
                    return MOLTEN_STILL;
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    return MOLTEN_FLOWING;
                }

                @Override
                public int getTintColor() {
                    return tint;
                }

                @Override
                public int getTintColor(FluidStack stack) {
                    return tint;
                }
            }, molten.typeHolder());
        }
    }
}
