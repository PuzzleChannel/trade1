/* Modified from Rvhoyos/simple-trading for Minecraft 26.2 Fabric. */
package mc.simpletrading.economy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative invitation and trade-session manager. */
public final class TradeManager {
    public static final Identifier ACCEPT_CLICK = Identifier.fromNamespaceAndPath("simpletrading", "trade_accept");
    public static final Identifier REJECT_CLICK = Identifier.fromNamespaceAndPath("simpletrading", "trade_reject");

    private static final TradeManager INSTANCE = new TradeManager();
    private static final long REQUEST_COOLDOWN_MILLIS = 5_000L;
    private static final long REQUEST_TIMEOUT_MILLIS = 60_000L;
    private static final double START_TRADE_DISTANCE_SQR = 36.0D;

    private final Map<UUID, TradeSession> activeSessions = new HashMap<>();
    private final Map<UUID, Long> requestCooldowns = new HashMap<>();
    private final Map<UUID, PendingRequest> pendingRequests = new HashMap<>();

    private TradeManager() {}

    public static TradeManager getInstance() {
        return INSTANCE;
    }

    /** Sends an invitation; this never creates a session or opens a screen. */
    public void requestTrade(ServerPlayer requester, ServerPlayer target) {
        if (requester == null || target == null || requester == target
                || requester.hasDisconnected() || target.hasDisconnected()
                || requester.level() != target.level()
                || requester.distanceToSqr(target) > START_TRADE_DISTANCE_SQR) {
            if (requester != null && !requester.hasDisconnected()) {
                requester.sendSystemMessage(Component.literal("§cИгрок должен находиться рядом с вами."));
            }
            return;
        }

        long now = System.currentTimeMillis();
        Long lastRequest = requestCooldowns.get(requester.getUUID());
        if (lastRequest != null && now - lastRequest < REQUEST_COOLDOWN_MILLIS) {
            long seconds = (REQUEST_COOLDOWN_MILLIS - (now - lastRequest) + 999L) / 1000L;
            requester.sendSystemMessage(Component.literal("§cСлишком часто. Повторите через §f" + seconds + "§c сек."));
            return;
        }

        if (hasActiveSession(requester) || hasActiveSession(target)) {
            requester.sendSystemMessage(Component.literal("§cОдин из игроков уже участвует в обмене."));
            return;
        }

        PendingRequest existing = pendingRequests.get(target.getUUID());
        if (existing != null && existing.expiresAt() > now) {
            requester.sendSystemMessage(Component.literal("§cУ этого игрока уже есть ожидающий запрос на обмен."));
            return;
        }

        requestCooldowns.put(requester.getUUID(), now);
        pendingRequests.put(target.getUUID(),
                new PendingRequest(requester.getUUID(), target.getUUID(), now + REQUEST_TIMEOUT_MILLIS));

        Component accept = Component.literal("[ПРИНЯТЬ]").withStyle(style -> style
                .withColor(ChatFormatting.GREEN).withUnderlined(true)
                .withClickEvent(new ClickEvent.Custom(ACCEPT_CLICK, java.util.Optional.empty()))
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
                        Component.literal("Принять предложение обмена"))));
        Component reject = Component.literal("[ОТКЛОНИТЬ]").withStyle(style -> style
                .withColor(ChatFormatting.RED).withUnderlined(true)
                .withClickEvent(new ClickEvent.Custom(REJECT_CLICK, java.util.Optional.empty()))
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
                        Component.literal("Отклонить предложение обмена"))));

        target.sendSystemMessage(Component.empty()
                .append(Component.literal("§eИгрок §f" + requester.getName().getString() + " §eпредлагает вам обмен. "))
                .append(accept).append(Component.literal(" ")).append(reject));
        requester.sendSystemMessage(Component.literal(
                "§aЗапрос на обмен отправлен игроку §f" + target.getName().getString() + "§a. Ожидайте подтверждения."));
    }

    /** Called only by the custom chat-click handler; identity comes from the authenticated connection. */
    public void handleChatClick(ServerPlayer target, Identifier action) {
        if (target == null || target.hasDisconnected()) return;
        if (ACCEPT_CLICK.equals(action)) {
            acceptTrade(target);
        } else if (REJECT_CLICK.equals(action)) {
            rejectTrade(target);
        }
    }

    public boolean acceptTrade(ServerPlayer target) {
        PendingRequest request = pendingRequests.get(target.getUUID());
        if (request == null) {
            target.sendSystemMessage(Component.literal("§cУ вас нет ожидающих запросов на обмен."));
            return false;
        }

        long now = System.currentTimeMillis();
        if (request.expiresAt() <= now) {
            pendingRequests.remove(target.getUUID(), request);
            target.sendSystemMessage(Component.literal("§cСрок действия запроса на обмен истёк."));
            return false;
        }

        MinecraftServer server = target.level().getServer();
        ServerPlayer requester = server == null ? null
                : server.getPlayerList().getPlayer(request.requesterId());
        if (requester == null || requester.hasDisconnected()) {
            pendingRequests.remove(target.getUUID(), request);
            target.sendSystemMessage(Component.literal("§cИгрок, отправивший запрос, больше не в сети."));
            return false;
        }

        if (requester.level() != target.level()
                || requester.distanceToSqr(target) > START_TRADE_DISTANCE_SQR) {
            pendingRequests.remove(target.getUUID(), request);
            target.sendSystemMessage(Component.literal("§cИгрок должен находиться рядом с вами."));
            requester.sendSystemMessage(Component.literal("§cОбмен не начат: игроки слишком далеко друг от друга."));
            return false;
        }

        if (hasActiveSession(requester) || hasActiveSession(target)) {
            pendingRequests.remove(target.getUUID(), request);
            target.sendSystemMessage(Component.literal("§cОдин из игроков уже участвует в обмене."));
            return false;
        }

        if (!pendingRequests.remove(target.getUUID(), request)) return false;
        TradeSession session = new TradeSession(requester, target);
        activeSessions.put(requester.getUUID(), session);
        activeSessions.put(target.getUUID(), session);
        TradeGui.open(session);

        requester.sendSystemMessage(Component.literal(
                "§aИгрок §f" + target.getName().getString() + " §aпринял ваш запрос. Обмен открыт."));
        target.sendSystemMessage(Component.literal(
                "§aВы приняли запрос на обмен от игрока §f" + requester.getName().getString() + "§a."));
        return true;
    }

    public boolean rejectTrade(ServerPlayer target) {
        PendingRequest request = pendingRequests.remove(target.getUUID());
        if (request == null) {
            target.sendSystemMessage(Component.literal("§cУ вас нет ожидающих запросов на обмен."));
            return false;
        }
        MinecraftServer server = target.level().getServer();
        ServerPlayer requester = server == null ? null
                : server.getPlayerList().getPlayer(request.requesterId());
        target.sendSystemMessage(Component.literal("§eВы отклонили запрос на обмен."));
        if (requester != null && !requester.hasDisconnected()) {
            requester.sendSystemMessage(Component.literal(
                    "§cИгрок §f" + target.getName().getString() + " §cотклонил ваш запрос на обмен."));
        }
        return true;
    }

    public boolean hasActiveSession(ServerPlayer player) {
        return activeSessions.containsKey(player.getUUID());
    }

    public void removeActiveSession(TradeSession session) {
        activeSessions.remove(session.getPlayerA().getUUID(), session);
        activeSessions.remove(session.getPlayerB().getUUID(), session);
    }

    public void tick(MinecraftServer server) {
        for (TradeSession session : new HashSet<>(activeSessions.values())) session.tick();

        long now = System.currentTimeMillis();
        pendingRequests.entrySet().removeIf(entry -> {
            PendingRequest request = entry.getValue();
            if (request.expiresAt() > now) return false;

            ServerPlayer target = server.getPlayerList().getPlayer(request.targetId());
            ServerPlayer requester = server.getPlayerList().getPlayer(request.requesterId());
            if (target != null) target.sendSystemMessage(Component.literal("§7Запрос на обмен истёк."));
            if (requester != null) requester.sendSystemMessage(Component.literal("§7Ваш запрос на обмен истёк."));
            return true;
        });
    }

    public void handlePlayerDamage(ServerPlayer player) {
        TradeSession session = activeSessions.get(player.getUUID());
        if (session != null) session.cancelTrade("§cОбмен отменён: игрок получил урон.");
    }

    public void handlePlayerLogout(ServerPlayer player) {
        TradeSession session = activeSessions.get(player.getUUID());
        if (session != null) session.cancelTrade();

        pendingRequests.remove(player.getUUID());
        pendingRequests.entrySet().removeIf(entry -> entry.getValue().requesterId().equals(player.getUUID()));
        requestCooldowns.remove(player.getUUID());
    }

    private record PendingRequest(UUID requesterId, UUID targetId, long expiresAt) {}
}