package com.imalloys.imalloys_tfc;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = ImAlloysTFC.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = ImAlloysTFC.MODID, value = Dist.CLIENT)
public class ImAlloysTFCClient {
    public ImAlloysTFCClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        ImAlloysTFC.LOGGER.info("Im Alloys TFC client setup, player: {}", Minecraft.getInstance().getUser().getName());
    }
}
