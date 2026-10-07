/* Modified from Rvhoyos/simple-trading for Minecraft 26.2 Fabric. */
package mc.simpletrading.economy;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Active player-to-player trade session. */
public final class TradeSession {
    private final ServerPlayer playerA;
    private final ServerPlayer playerB;
    private final TradeMenuContainer container;
    private boolean playerAReady;
    private boolean playerBReady;
    private final int[] readyStateData = new int[2];
    private final int[] countdownData = new int[1];
    private int countdownTicks;
    private boolean completed;

    private static final int COUNTDOWN_TICKS = 60;
    private static final double MAX_TRADE_DISTANCE_SQR = 100.0D;

    public TradeSession(ServerPlayer playerA, ServerPlayer playerB) {
        this.playerA = playerA;
        this.playerB = playerB;
        this.container = new TradeMenuContainer(54, this);
    }

    public ServerPlayer getPlayerA() {
        return playerA;
    }

    public ServerPlayer getPlayerB() {
        return playerB;
    }

    public TradeMenuContainer getContainer() {
        return container;
    }

    public boolean isPlayerAReady() {
        return playerAReady;
    }

    public boolean isPlayerBReady() {
        return playerBReady;
    }

    public boolean isCompleted() {
        return completed;
    }

    public int[] getReadyStateData() {
        return readyStateData;
    }

    public int[] getCountdownData() {
        return countdownData;
    }

    public void onContainerChanged() {
        if (completed) {
            return;
        }

        boolean hadReadyState = playerAReady || playerBReady || countdownTicks > 0;
        playerAReady = false;
        playerBReady = false;
        countdownTicks = 0;
        countdownData[0] = 0;

        if (hadReadyState) {
            updateGuiButtons();
            sendMessage(playerA, "§eПредметы изменены. Подтверждение сброшено.");
            sendMessage(playerB, "§eПредметы изменены. Подтверждение сброшено.");
        }
    }

    public void toggleReady(ServerPlayer player) {
        if (completed || (player != playerA && player != playerB)) {
            return;
        }

        boolean wasReady = player == playerA ? playerAReady : playerBReady;

        if (player == playerA) {
            playerAReady = !playerAReady;
        } else {
            playerBReady = !playerBReady;
        }

        if (wasReady) {
            countdownTicks = 0;
            countdownData[0] = 0;
            updateGuiButtons();
            sendMessage(player, "§eГотовность отменена.");
            return;
        }

        updateGuiButtons();
        sendMessage(player, "§aВы подтвердили готовность к обмену.");

        if (playerAReady && playerBReady && countdownTicks <= 0) {
            countdownTicks = COUNTDOWN_TICKS;
            countdownData[0] = 3;
            broadcastMenus();

            sendMessage(playerA, "§aОба игрока готовы. Обмен начнётся через §f3§a...");
            sendMessage(playerB, "§aОба игрока готовы. Обмен начнётся через §f3§a...");
        }
    }

    void broadcastMenus() {
        if (playerA.containerMenu instanceof TradeChestMenu menuA) {
            menuA.broadcastChanges();
        }
        if (playerB.containerMenu instanceof TradeChestMenu menuB) {
            menuB.broadcastChanges();
        }
    }

    private void updateGuiButtons() {
        readyStateData[0] = playerAReady ? 1 : 0;
        readyStateData[1] = playerBReady ? 1 : 0;
        broadcastMenus();
    }

    public void tick() {
        if (completed) {
            return;
        }

        if (playerA.hasDisconnected() || playerB.hasDisconnected()
                || !playerA.isAlive() || !playerB.isAlive()
                || playerA.level() != playerB.level()
                || playerA.distanceToSqr(playerB) > MAX_TRADE_DISTANCE_SQR
                || !(playerA.containerMenu instanceof TradeChestMenu menuA)
                || menuA.getTradeSession() != this
                || !(playerB.containerMenu instanceof TradeChestMenu menuB)
                || menuB.getTradeSession() != this) {
            cancelTrade("§cОбмен отменён: игроки должны оставаться рядом и в окне обмена.");
            return;
        }

        if (!playerAReady || !playerBReady) {
            countdownTicks = 0;
            countdownData[0] = 0;
            return;
        }

        if (countdownTicks <= 0) {
            executeTrade();
            return;
        }

        int previousSeconds = countdownData[0];
        countdownTicks--;

        int seconds = countdownTicks <= 0 ? 0 : (countdownTicks + 19) / 20;
        if (seconds != previousSeconds) {
            countdownData[0] = seconds;
            broadcastMenus();
        }

        if (countdownTicks <= 0) {
            executeTrade();
        }
    }

    private void executeTrade() {
        List<ItemStack> itemsFromA = new ArrayList<>();
        List<ItemStack> itemsFromB = new ArrayList<>();

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            int col = i % 9;
            if (col >= 0 && col <= 3) {
                itemsFromA.add(stack.copy());
            } else if (col >= 5 && col <= 8) {
                itemsFromB.add(stack.copy());
            }
        }

        if (!canFit(playerA, itemsFromB) || !canFit(playerB, itemsFromA)) {
            playerAReady = false;
            playerBReady = false;
            countdownTicks = 0;
            countdownData[0] = 0;
            updateGuiButtons();

            sendMessage(playerA, "§cОбмен остановлен: одному из игроков не хватает места в инвентаре.");
            sendMessage(playerB, "§cОбмен остановлен: одному из игроков не хватает места в инвентаре.");
            return;
        }

        List<ItemStack> snapshotA = snapshotInventory(playerA);
        List<ItemStack> snapshotB = snapshotInventory(playerB);

        completed = true;

        boolean success = addAll(playerA, itemsFromB) && addAll(playerB, itemsFromA);
        if (!success) {
            restoreInventory(playerA, snapshotA);
            restoreInventory(playerB, snapshotB);
            completed = false;
            playerAReady = false;
            playerBReady = false;
            countdownTicks = 0;
            countdownData[0] = 0;
            updateGuiButtons();

            sendMessage(playerA, "§cОбмен не выполнен: не удалось безопасно выдать предметы.");
            sendMessage(playerB, "§cОбмен не выполнен: не удалось безопасно выдать предметы.");
            return;
        }

        for (int i = 0; i < container.getContainerSize(); i++) {
            container.setItem(i, ItemStack.EMPTY);
        }

        TradeManager.getInstance().removeActiveSession(this);
        closeMenus();

        sendMessage(playerA, "§aОбмен успешно завершён!");
        sendMessage(playerB, "§aОбмен успешно завершён!");

        playerA.level().playSound(null, playerA.getX(), playerA.getY(), playerA.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 1f, 1f);
        playerB.level().playSound(null, playerB.getX(), playerB.getY(), playerB.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 1f, 1f);
    }

    private static boolean canFit(ServerPlayer player, List<ItemStack> incoming) {
        List<ItemStack> simulated = new ArrayList<>(36);
        for (int i = 0; i < 36; i++) {
            simulated.add(player.getInventory().getItem(i).copy());
        }

        for (ItemStack incomingStack : incoming) {
            int remaining = incomingStack.getCount();

            for (ItemStack existing : simulated) {
                if (remaining <= 0) {
                    break;
                }

                if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, incomingStack)) {
                    continue;
                }

                int max = Math.min(existing.getMaxStackSize(), incomingStack.getMaxStackSize());
                int space = max - existing.getCount();
                if (space <= 0) {
                    continue;
                }

                int amount = Math.min(space, remaining);
                existing.grow(amount);
                remaining -= amount;
            }

            for (int i = 0; i < simulated.size() && remaining > 0; i++) {
                if (!simulated.get(i).isEmpty()) {
                    continue;
                }

                int amount = Math.min(remaining, incomingStack.getMaxStackSize());
                ItemStack placed = incomingStack.copy();
                placed.setCount(amount);
                simulated.set(i, placed);
                remaining -= amount;
            }

            if (remaining > 0) {
                return false;
            }
        }

        return true;
    }

    private static List<ItemStack> snapshotInventory(ServerPlayer player) {
        List<ItemStack> snapshot = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            snapshot.add(player.getInventory().getItem(i).copy());
        }
        return snapshot;
    }

    private static void restoreInventory(ServerPlayer player, List<ItemStack> snapshot) {
        int limit = Math.min(snapshot.size(), player.getInventory().getContainerSize());
        for (int i = 0; i < limit; i++) {
            player.getInventory().setItem(i, snapshot.get(i).copy());
        }
        player.getInventory().setChanged();
    }

    private static boolean addAll(ServerPlayer player, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (!player.getInventory().add(stack.copy())) {
                return false;
            }
        }
        return true;
    }

    public void cancelTrade() {
        cancelTrade(null);
    }

    private void cancelTrade(String reason) {
        if (completed) {
            return;
        }

        completed = true;
        countdownTicks = 0;
        countdownData[0] = 0;
        playerAReady = false;
        playerBReady = false;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            int col = i % 9;
            if (col >= 0 && col <= 3) {
                giveItem(playerA, stack.copy());
            } else if (col >= 5 && col <= 8) {
                giveItem(playerB, stack.copy());
            }
            container.setItem(i, ItemStack.EMPTY);
        }

        TradeManager.getInstance().removeActiveSession(this);
        closeMenus();

        if (reason == null) {
            sendMessage(playerA, "§cОбмен отменён.");
            sendMessage(playerB, "§cОбмен отменён.");
        } else {
            sendMessage(playerA, reason);
            sendMessage(playerB, reason);
        }
    }

    private void closeMenus() {
        if (playerA.containerMenu instanceof TradeChestMenu) {
            playerA.closeContainer();
        }
        if (playerB.containerMenu instanceof TradeChestMenu) {
            playerB.closeContainer();
        }
    }

    private static void giveItem(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void sendMessage(ServerPlayer player, String message) {
        if (player != null && !player.hasDisconnected()) {
            player.sendSystemMessage(Component.literal(message));
        }
    }
}
