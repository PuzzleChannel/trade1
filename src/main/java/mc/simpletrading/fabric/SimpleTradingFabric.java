/* Modified from Rvhoyos/simple-trading for Minecraft 26.2 Fabric. */
package mc.simpletrading.fabric;

import mc.simpletrading.SimpleTradingMod;
import mc.simpletrading.commands.TradeCommand;
import mc.simpletrading.economy.TradeManager;
import mc.simpletrading.economy.SimpleTradingMenus;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/** Fabric server/common entry point. */
public final class SimpleTradingFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SimpleTradingMod.init();
        SimpleTradingMenus.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            SimpleTradingMod.LOGGER.info("Registering /trade command");
            TradeCommand.register(dispatcher);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                TradeManager.getInstance().handlePlayerLogout(handler.getPlayer()));

        ServerTickEvents.END_SERVER_TICK.register(server ->
                TradeManager.getInstance().tick());

        SimpleTradingMod.LOGGER.info("Simple Trading server initialized");
    }
}
