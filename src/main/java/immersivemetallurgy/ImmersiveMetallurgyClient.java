package immersivemetallurgy;

import immersivemetallurgy.event.IMHoning;
import immersivemetallurgy.network.IMNetwork;
import immersivemetallurgy.registry.IMDataComponents;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.network.PacketDistributor;

@Mod(value = ImmersiveMetallurgy.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = ImmersiveMetallurgy.MODID, value = Dist.CLIENT)
public class ImmersiveMetallurgyClient {
    public ImmersiveMetallurgyClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        ImmersiveMetallurgy.LOGGER.info("Immersive Metallurgy client setup, player: {}", Minecraft.getInstance().getUser().getName());
    }

    /** The server is blind to the mouse on this version, so while it is held at a stone the client keeps saying so. */
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !minecraft.options.keyUse.isDown()
                || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !minecraft.player.getMainHandItem().has(IMDataComponents.SHARPNESS.get())
                || !IMHoning.isStation(minecraft.level, hit.getBlockPos(), minecraft.player)) {
            return;
        }
        PacketDistributor.sendToServer(new IMNetwork.HoldingTheStone());
    }
}
