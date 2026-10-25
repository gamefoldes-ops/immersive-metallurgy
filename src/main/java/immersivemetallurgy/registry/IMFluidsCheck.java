package immersivemetallurgy.registry;

import immersivemetallurgy.ImmersiveMetallurgy;

import net.dries007.tfc.util.data.FluidHeat;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * TFC reads molten temperatures from datapacks, so a missing {@code tfc:fluid_heat} file only
 * shows up as a null lookup deep inside melting maths. This reports it once per server start.
 */
@EventBusSubscriber(modid = ImmersiveMetallurgy.MODID)
public final class IMFluidsCheck {
    private IMFluidsCheck() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        for (IMFluids.Molten molten : IMFluids.all()) {
            FluidHeat heat = FluidHeat.get(molten.fluid());
            ImmersiveMetallurgy.LOGGER.info("Molten {} fluid_heat: {}", molten.id(), heat == null ? "MISSING" : heat);
        }
    }
}
