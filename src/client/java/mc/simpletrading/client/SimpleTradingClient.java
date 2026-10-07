package mc.simpletrading.client;

import com.mojang.blaze3d.platform.InputConstants;
import mc.simpletrading.SimpleTradingMod;
import mc.simpletrading.economy.SimpleTradingMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.gui.screens.MenuScreens;
import org.lwjgl.glfw.GLFW;
import net.minecraft.resources.Identifier;

/** Client keybind, HUD prompt and vanilla-layout trade screen. */
public final class SimpleTradingClient implements ClientModInitializer {
    private static final Identifier CATEGORY_ID = Identifier.fromNamespaceAndPath(
            SimpleTradingMod.MOD_ID, "trade");
    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath(
            SimpleTradingMod.MOD_ID, "trade_prompt");

    private static KeyMapping tradeKey;

    @Override
    public void onInitializeClient() {
        MenuScreens.register(SimpleTradingMenus.TRADE, TradeScreen::new);

        KeyMapping.Category category = KeyMapping.Category.register(CATEGORY_ID);
        tradeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.simpletrading.trade_target",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(SimpleTradingClient::onClientTick);
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, HUD_ID,
                SimpleTradingClient::renderTradePrompt);

        SimpleTradingMod.LOGGER.info("Simple Trading client initialized; trade key registered");
    }

    private static void onClientTick(Minecraft client) {
        if (tradeKey == null) {
            return;
        }

        while (tradeKey.consumeClick()) {
            if (client.player == null || client.level == null || client.gui.screen() != null) {
                continue;
            }

            Player target = getTargetPlayer(client);
            if (target == null || target == client.player || client.getConnection() == null) {
                continue;
            }

            client.getConnection().sendCommand("trade " + target.getGameProfile().name());
        }
    }

    private static void renderTradePrompt(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (tradeKey == null || client.player == null || client.level == null || client.gui.screen() != null) {
            return;
        }

        Player target = getTargetPlayer(client);
        if (target == null || target == client.player) {
            return;
        }

        Component label = Component.empty()
                .append(tradeKey.getTranslatedKeyMessage())
                .append(Component.literal(" - Обмен"));

        graphics.centeredText(client.font, label,
                graphics.guiWidth() / 2, graphics.guiHeight() / 2 + 10, 0xFFFFFFFF);
    }

    private static Player getTargetPlayer(Minecraft client) {
        Entity target = client.crosshairPickEntity;
        if (target instanceof Player player && player != client.player
                && client.player.distanceToSqr(player) <= 36.0D) {
            return player;
        }
        return null;
    }
}
