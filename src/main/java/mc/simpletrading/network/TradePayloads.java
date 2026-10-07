package mc.simpletrading.network;

import mc.simpletrading.SimpleTradingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server payloads used by Simple Trading. */
public final class TradePayloads {
    private TradePayloads() {
    }

    public record RequestTradePayload(int targetEntityId) implements CustomPacketPayload {
        private static final Identifier ID = Identifier.fromNamespaceAndPath(
                SimpleTradingMod.MOD_ID, "request_trade");
        public static final Type<RequestTradePayload> TYPE = new Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestTradePayload> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.INT,
                        RequestTradePayload::targetEntityId,
                        RequestTradePayload::new
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
