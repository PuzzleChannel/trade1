/* Modified from Rvhoyos/simple-trading for Minecraft 26.2 Fabric. */
package mc.simpletrading.economy;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Manages pending requests and active trades on the server. */
public final class TradeManager {
    private static final TradeManager INSTANCE = new TradeManager();
    private static final long REQUEST_COOLDOWN_MILLIS = 5_000L;

    private final Map<UUID, TradeSession> activeSessions = new HashMap<>();
    private final Map<UUID, TradeRequest> pendingRequests = new HashMap<>();
    private final Map<UUID, Long> requestCooldowns = new HashMap<>();

    private TradeManager() {
    }

    public static TradeManager getInstance() {
        return INSTANCE;
    }

    public void requestTrade(ServerPlayer requester, ServerPlayer target) {
        long now = System.currentTimeMillis();
        Long lastRequest = requestCooldowns.get(requester.getUUID());
        if (lastRequest != null) {
            long remaining = REQUEST_COOLDOWN_MILLIS - (now - lastRequest);
            if (remaining > 0L) {
                long seconds = (remaining + 999L) / 1000L;
                requester.sendSystemMessage(Component.literal("§cСлишком часто. Повторите через §f" + seconds + "§c сек."));
                return;
            }
        }

        if (hasActiveSession(requester) || hasActiveSession(target)) {
            requester.sendSystemMessage(Component.literal("§cОдин из игроков уже участвует в обмене."));
            return;
        }

        requestCooldowns.put(requester.getUUID(), now);

        TradeRequest oldRequest = pendingRequests.put(target.getUUID(),
                new TradeRequest(requester.getUUID(), System.currentTimeMillis()));
        if (oldRequest != null) {
            requester.sendSystemMessage(Component.literal("§eПредыдущий запрос игроку был заменён новым."));
        }

        requester.sendSystemMessage(Component.literal("§aЗапрос на обмен отправлен игроку §f"
                + target.getName().getString() + "§a."));

        MutableComponent acceptButton = Component.literal("§a§l[ПРИНЯТЬ]")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.RunCommand("/trade accept"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§aПринять обмен"))));
        MutableComponent denyButton = Component.literal("§c§l[ОТКЛОНИТЬ]")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.RunCommand("/trade deny"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§cОтклонить обмен"))));

        MutableComponent message = Component.literal("§e" + requester.getName().getString()
                + " §aпредлагает вам обмен. ")
                .append(acceptButton)
                .append(Component.literal(" "))
                .append(denyButton);

        target.sendSystemMessage(message);
    }

    public void acceptTrade(ServerPlayer target, MinecraftServer server) {
        TradeRequest request = pendingRequests.remove(target.getUUID());
        if (request == null || request.isExpired()) {
            target.sendSystemMessage(Component.literal("§cУ вас нет активных запросов на обмен."));
            return;
        }

        ServerPlayer requester = server.getPlayerList().getPlayer(request.requesterId());
        if (requester == null) {
            target.sendSystemMessage(Component.literal("§cИгрок, отправивший запрос, уже вышел с сервера."));
            return;
        }

        if (hasActiveSession(requester) || hasActiveSession(target)) {
            target.sendSystemMessage(Component.literal("§cОдин из игроков уже участвует в обмене."));
            return;
        }

        TradeSession session = new TradeSession(requester, target);
        activeSessions.put(requester.getUUID(), session);
        activeSessions.put(target.getUUID(), session);
        TradeGui.open(session);
    }

    public void denyTrade(ServerPlayer target, MinecraftServer server) {
        TradeRequest request = pendingRequests.remove(target.getUUID());
        if (request == null) {
            target.sendSystemMessage(Component.literal("§cУ вас нет активных запросов на обмен."));
            return;
        }

        target.sendSystemMessage(Component.literal("§cЗапрос на обмен отклонён."));
        ServerPlayer requester = server.getPlayerList().getPlayer(request.requesterId());
        if (requester != null) {
            requester.sendSystemMessage(Component.literal("§cИгрок " + target.getName().getString()
                    + " отклонил ваш запрос на обмен."));
        }
    }

    public boolean hasActiveSession(ServerPlayer player) {
        return activeSessions.containsKey(player.getUUID());
    }

    public void removeActiveSession(TradeSession session) {
        activeSessions.remove(session.getPlayerA().getUUID());
        activeSessions.remove(session.getPlayerB().getUUID());
    }

    public void handlePlayerLogout(ServerPlayer player) {
        TradeSession session = activeSessions.get(player.getUUID());
        if (session != null) {
            session.cancelTrade();
        }

        pendingRequests.remove(player.getUUID());
        pendingRequests.values().removeIf(request -> request.requesterId().equals(player.getUUID()));
    }

    private record TradeRequest(UUID requesterId, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 60_000L;
        }
    }
}
