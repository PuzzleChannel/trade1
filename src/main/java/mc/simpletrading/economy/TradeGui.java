/* Modified from Rvhoyos/simple-trading for Minecraft 26.2 Fabric. */
package mc.simpletrading.economy;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Server-side controller for the trade menu. */
public final class TradeGui {
    private TradeGui() {
    }

    public static void open(TradeSession session) {
        populate(session.getContainer());

        String title = "Обмен: " + session.getPlayerA().getName().getString()
                + " ↔ " + session.getPlayerB().getName().getString();

        openMenuForPlayer(session.getPlayerA(), session.getContainer(), title, true);
        openMenuForPlayer(session.getPlayerB(), session.getContainer(), title, false);
    }

    private static void openMenuForPlayer(ServerPlayer player, TradeMenuContainer container,
                                          String title, boolean playerA) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal(title);
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player ignored) {
                return new TradeChestMenu(containerId, inventory, container,
                        new TradeChestMenu.ServerOwner(player, playerA));
            }
        });
    }

    private static void populate(TradeMenuContainer container) {
        container.setUpdatingButtons(true);
        try {
            for (int row = 0; row < 6; row++) {
                for (int column = 0; column < 9; column++) {
                    if (!isOfferSlot(row, column)) {
                        container.setItem(row * 9 + column, ItemStack.EMPTY);
                    }
                }
            }
        } finally {
            container.setUpdatingButtons(false);
        }
    }

    private static boolean isOfferSlot(int row, int col) {
        if (row < 1 || row > 3) {
            return false;
        }
        return col >= 0 && col <= 3 || col >= 5 && col <= 8;
    }

    static void handleQuickMove(ServerPlayer player, TradeMenuContainer container, int playerSlot, boolean playerA) {
        if (player.containerMenu == null || playerSlot < 0 || playerSlot >= player.containerMenu.slots.size()) {
            return;
        }

        ItemStack clickedStack = player.containerMenu.getSlot(playerSlot).getItem();
        if (clickedStack.isEmpty()) {
            return;
        }

        int startCol = playerA ? 0 : 5;
        int endCol = playerA ? 3 : 8;

        for (int row = 1; row <= 3; row++) {
            for (int col = startCol; col <= endCol; col++) {
                int index = row * 9 + col;
                ItemStack existing = container.getItem(index);
                if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(clickedStack, existing)) {
                    int space = existing.getMaxStackSize() - existing.getCount();
                    if (space > 0) {
                        int amount = Math.min(space, clickedStack.getCount());
                        existing.grow(amount);
                        clickedStack.shrink(amount);
                        container.setChanged();
                        if (clickedStack.isEmpty()) {
                            return;
                        }
                    }
                }
            }
        }

        for (int row = 1; row <= 3; row++) {
            for (int col = startCol; col <= endCol; col++) {
                int index = row * 9 + col;
                if (container.getItem(index).isEmpty()) {
                    container.setItem(index, clickedStack.copy());
                    clickedStack.setCount(0);
                    container.setChanged();
                    return;
                }
            }
        }
    }
}
