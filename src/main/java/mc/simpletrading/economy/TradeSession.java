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
    private boolean completed;

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

    public void onContainerChanged() {
        if (completed) {
            return;
        }

        boolean changed = playerAReady || playerBReady;
        playerAReady = false;
        playerBReady = false;

        if (changed) {
            updateGuiButtons();
            sendMessage(playerA, "§eПредметы изменены. Подтверждение сброшено.");
            sendMessage(playerB, "§eПредметы изменены. Подтверждение сброшено.");
        }
    }

    public void toggleReady(ServerPlayer player) {
        if (completed) {
            return;
        }

        if (player == playerA) {
            playerAReady = !playerAReady;
        } else if (player == playerB) {
            playerBReady = !playerBReady;
        } else {
            return;
        }

        updateGuiButtons();

        if (playerAReadyFor(player)) {
            sendMessage(player, "Вы утвердили готовность к обмену");
        }

        if (playerAReady && playerBReady) {
            completed = true;
            executeTrade();
        }
    }

    private boolean playerAReadyFor(ServerPlayer player) {
        return player == playerA && playerAReady || player == playerB && playerBReady;
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
                container.setItem(i, ItemStack.EMPTY);
            } else if (col >= 5 && col <= 8) {
                itemsFromB.add(stack.copy());
                container.setItem(i, ItemStack.EMPTY);
            }
        }

        for (ItemStack stack : itemsFromB) {
            giveItem(playerA, stack);
        }
        for (ItemStack stack : itemsFromA) {
            giveItem(playerB, stack);
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

    public void cancelTrade() {
        if (completed) {
            return;
        }
        completed = true;

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
        sendMessage(playerA, "§cОбмен отменён.");
        sendMessage(playerB, "§cОбмен отменён.");
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
