/* Modified from Rvhoyos/simple-trading for Minecraft 26.2 Fabric. */
package mc.simpletrading.economy;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Manages active trades on the server. */
public final class TradeManager {
    private static final TradeManager INSTANCE = new TradeManager();
    private static final long REQUEST_COOLDOWN_MILLIS = 5_000L;
    private static final double START_TRADE_DISTANCE_SQR = 36.0D;

    private final Map<UUID, TradeSession> activeSessions = new HashMap<>();
    private final Map<UUID, Long> requestCooldowns = new HashMap<>();

    private TradeManager() {
    }

    public static TradeManager getInstance() {
        return INSTANCE;
    }

    /**
     * Opens a trade directly from the in-world trade key.
     * The server validates the target and the maximum distance before creating a session.
     */
    public void requestTrade(ServerPlayer requester, ServerPlayer target) {
        if (requester == null || target == null || requester == target
                || requester.hasDisconnected() || target.hasDisconnected()
                || requester.level() != target.level()
                || requester.distanceToSqr(target) > START_TRADE_DISTANCE_SQR) {
            if (requester != null && !requester.hasDisconnected()) {
                requester.sendSystemMessage(Component.literal(
                        "§cИгрок должен находиться рядом с вами."));
            }
            return;
        }

        long now = System.currentTimeMillis();
        Long lastRequest = requestCooldowns.get(requester.getUUID());
        if (lastRequest != null) {
            long remaining = REQUEST_COOLDOWN_MILLIS - (now - lastRequest);
            if (remaining > 0L) {
                long seconds = (remaining + 999L) / 1000L;
                requester.sendSystemMessage(Component.literal(
                        "§cСлишком часто. Повторите через §f" + seconds + "§c сек."));
                return;
            }
        }

        if (hasActiveSession(requester) || hasActiveSession(target)) {
            requester.sendSystemMessage(Component.literal(
                    "§cОдин из игроков уже участвует в обмене."));
            return;
        }

        requestCooldowns.put(requester.getUUID(), now);

        TradeSession session = new TradeSession(requester, target);
        activeSessions.put(requester.getUUID(), session);
        activeSessions.put(target.getUUID(), session);
        TradeGui.open(session);

        requester.sendSystemMessage(Component.literal(
                "§aВы открыли обмен с игроком §f" + target.getName().getString() + "§a."));
        target.sendSystemMessage(Component.literal(
                "§aИгрок §f" + requester.getName().getString() + "§a открыл с вами обмен."));
    }

    public boolean hasActiveSession(ServerPlayer player) {
        return activeSessions.containsKey(player.getUUID());
    }

    public void removeActiveSession(TradeSession session) {
        activeSessions.remove(session.getPlayerA().getUUID());
        activeSessions.remove(session.getPlayerB().getUUID());
    }

    public void tick() {
        for (TradeSession session : new java.util.HashSet<>(activeSessions.values())) {
            session.tick();
        }
    }

    public void handlePlayerDamage(ServerPlayer player) {
        TradeSession session = activeSessions.get(player.getUUID());
        if (session != null) {
            session.cancelTrade("§cОбмен отменён: игрок получил урон.");
        }
    }

    public void handlePlayerLogout(ServerPlayer player) {
        TradeSession session = activeSessions.get(player.getUUID());
        if (session != null) {
            session.cancelTrade();
        }

        requestCooldowns.remove(player.getUUID());
    }
}
