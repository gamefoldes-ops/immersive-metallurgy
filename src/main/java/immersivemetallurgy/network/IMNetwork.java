package immersivemetallurgy.network;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.event.IMHoning;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Minecraft 1.21.1 does not put the right mouse button in its input packet, so the server cannot see a
 * player letting go of a stone. This is the one ping that says "still holding it", and it is only sent while
 * the button is actually down, so a session that stops being fed simply dies.
 */
@EventBusSubscriber(modid = ImmersiveMetallurgy.MODID)
public final class IMNetwork {
    private IMNetwork() {
    }

    public record HoldingTheStone() implements CustomPacketPayload {
        public static final Type<HoldingTheStone> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ImmersiveMetallurgy.MODID, "holding_the_stone"));

        public static final StreamCodec<ByteBuf, HoldingTheStone> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public HoldingTheStone decode(ByteBuf buffer) {
                return new HoldingTheStone();
            }

            @Override
            public void encode(ByteBuf buffer, HoldingTheStone value) {
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(HoldingTheStone.TYPE, HoldingTheStone.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> IMHoning.keepHoning(context.player())));
    }
}
