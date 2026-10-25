package com.gamefoldes.immersivemetallurgy;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.gamefoldes.immersivemetallurgy.registry.IMFluids;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(ImmersiveMetallurgy.MODID)
public class ImmersiveMetallurgy {
    public static final String MODID = "immersive_metallurgy";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ImmersiveMetallurgy(IEventBus modEventBus) {
        IMFluids.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Immersive Metallurgy common setup");
    }
}
