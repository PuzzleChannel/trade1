/* Modified from Rvhoyos/simple-trading for Minecraft 26.2 Fabric. */
package mc.simpletrading.fabric;

import mc.simpletrading.SimpleTradingMod;
import mc.simpletrading.economy.SimpleTradingMenus;
import mc.simpletrading.economy.TradeManager;
import mc.simpletrading.network.TradePayloads;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Fabric server/common entry point. */
public final class SimpleTradingFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SimpleTradingMod.init();
        SimpleTradingMenus.init();

        PayloadTypeRegistry.serverboundPlay().register(
                TradePayloads.RequestTradePayload.TYPE,
                TradePayloads.RequestTradePayload.CODEC
        );

        ServerPlayNetworking.registerGlobalReceiver(
                TradePayloads.RequestTradePayload.TYPE,
                (payload, context) -> {
                    ServerPlayerLookup.handleTradeRequest(payload, context);
                }
        );

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
                TradeManager.getInstance().handlePlayerDamage(player);
            }
            return true;
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                TradeManager.getInstance().handlePlayerLogout(handler.getPlayer()));

        ServerTickEvents.END_SERVER_TICK.register(server ->
                TradeManager.getInstance().tick());

        SimpleTradingMod.LOGGER.info("Simple Trading server initialized");
    }

    private static final class ServerPlayerLookup {
        private static void handleTradeRequest(
                TradePayloads.RequestTradePayload payload,
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
            net.minecraft.server.level.ServerPlayer requester = context.player();
            net.minecraft.world.entity.Entity entity =
                    requester.level().getEntity(payload.targetEntityId());

            if (entity instanceof net.minecraft.server.level.ServerPlayer target) {
                TradeManager.getInstance().requestTrade(requester, target);
            }
        }
    }
}
