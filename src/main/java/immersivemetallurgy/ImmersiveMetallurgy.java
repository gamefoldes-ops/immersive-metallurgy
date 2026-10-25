package immersivemetallurgy;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import immersivemetallurgy.registry.IMDataComponents;
import immersivemetallurgy.registry.IMFluids;
import immersivemetallurgy.registry.IMItemModifiers;
import immersivemetallurgy.registry.IMItems;
import immersivemetallurgy.registry.IMRecipes;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(ImmersiveMetallurgy.MODID)
public class ImmersiveMetallurgy {
    public static final String MODID = "immersive_metallurgy";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ImmersiveMetallurgy(IEventBus modEventBus) {
        IMDataComponents.register(modEventBus);
        IMFluids.register(modEventBus);
        IMItemModifiers.register(modEventBus);
        IMItems.register(modEventBus);
        IMRecipes.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Immersive Metallurgy common setup");
    }
}
