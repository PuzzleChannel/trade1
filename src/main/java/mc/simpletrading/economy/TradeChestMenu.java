package mc.simpletrading.economy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Trade menu with two 4x3 offer grids and inactive UI/spacing cells. */
public final class TradeChestMenu extends ChestMenu {
    public static final int TRADE_ROWS = 6;
    public static final int TRADE_SLOTS = 54;

    public static final int READY_SLOT = 39; // row 4, column 3; client-side ready button hit target

    private static final int LEFT_START_COL = 0;
    private static final int RIGHT_START_COL = 5;
    private static final int OFFER_COLUMNS = 4;
    private static final int OFFER_START_ROW = 1;
    private static final int OFFER_END_ROW = 3;
    private static final int CELL = 18;
    private static final int TRADE_SLOT_Y_OFFSET = -5;

    private static final int READY_LEFT_COL = 3;
    private static final int READY_RIGHT_COL = 5;
    private static final int READY_ROW = 4;

    private final TradeMenuContainer tradeContainer;
    private final ServerOwner owner;
    private final DataSlot readyAData;
    private final DataSlot readyBData;
    private boolean clientPlayerIsA;
    private boolean clientSideLayoutInitialized;

    public TradeChestMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new TradeMenuContainer(TRADE_SLOTS, null), null,
                DataSlot.standalone(), DataSlot.standalone());
    }

    public TradeChestMenu(int containerId, Inventory inventory, Container container, ServerOwner owner) {
        this(containerId, inventory, container, owner, createReadyDataSlot(container, 0), createReadyDataSlot(container, 1));
    }

    private TradeChestMenu(int containerId, Inventory inventory, Container container, ServerOwner owner,
                           DataSlot readyAData, DataSlot readyBData) {
        super(SimpleTradingMenus.TRADE, containerId, inventory, container, TRADE_ROWS);
        if (!(container instanceof TradeMenuContainer tradeContainer)) {
            throw new IllegalArgumentException("TradeChestMenu requires TradeMenuContainer");
        }
        this.tradeContainer = tradeContainer;
        this.owner = owner;
        this.readyAData = readyAData;
        this.readyBData = readyBData;
        addDataSlot(this.readyAData);
        addDataSlot(this.readyBData);
        replaceInactiveSlots();
    }

    private static DataSlot createReadyDataSlot(Container container, int index) {
        TradeMenuContainer tradeContainer = (TradeMenuContainer) container;
        TradeSession session = tradeContainer.getSession();
        return session == null
                ? DataSlot.standalone()
                : DataSlot.shared(session.getReadyStateData(), index);
    }

    private void replaceInactiveSlots() {
        boolean viewerIsA = owner != null ? owner.playerIsA() : clientPlayerIsA;

        for (int row = 0; row < TRADE_ROWS; row++) {
            for (int col = 0; col < 9; col++) {
                int visualIndex = row * 9 + col;
                Slot old = this.slots.get(visualIndex);

                if (isOfferSlot(row, col)) {
                    // The left 4x3 grid is ALWAYS the local player's offer on both
                    // screens. The underlying shared container remains logical:
                    // player A uses columns 0-3 and player B uses columns 5-8.
                    // For player B's menu the visual slots are therefore mapped to
                    // B's logical slots on the left and A's logical slots on the right.
                    boolean localSide = col < LEFT_START_COL + OFFER_COLUMNS;
                    boolean logicalPlayerA = viewerIsA == localSide;
                    int visualColumn = localSide
                            ? col - LEFT_START_COL
                            : col - RIGHT_START_COL;
                    int logicalColumn = logicalPlayerA
                            ? LEFT_START_COL + visualColumn
                            : RIGHT_START_COL + visualColumn;
                    int logicalContainerSlot = row * 9 + logicalColumn;

                    // Move the actual interactive trade slot to the exact position
                    // used by the custom-rendered slot well on the client.
                    // Both local and remote grids use the same baseline.
                    // Derive the Y position from the row rather than from old.y.
                    // replaceInactiveSlots() can run more than once on the client;
                    // using old.y would otherwise apply the offset repeatedly.
                    int vanillaSlotY = 18 + row * CELL;
                    int visualY = vanillaSlotY + TRADE_SLOT_Y_OFFSET;
                    this.slots.set(visualIndex, new TradeOfferSlot(
                            tradeContainer, logicalContainerSlot, old.x, visualY, localSide));
                    continue;
                }

                this.slots.set(visualIndex, new InactiveSlot(
                        tradeContainer, old.getContainerSlot(), old.x, old.y));
            }
        }
    }

    private static boolean isOfferSlot(int row, int col) {
        if (row < OFFER_START_ROW || row > OFFER_END_ROW) {
            return false;
        }
        return col >= LEFT_START_COL && col < LEFT_START_COL + OFFER_COLUMNS
                || col >= RIGHT_START_COL && col < RIGHT_START_COL + OFFER_COLUMNS;
    }

    public TradeMenuContainer getTradeContainer() {
        return tradeContainer;
    }

    public TradeSession getTradeSession() {
        return tradeContainer.getSession();
    }

    public boolean isClientPlayerA() {
        return clientPlayerIsA;
    }

    public void setClientPlayerIsA(boolean playerA) {
        if (owner != null) {
            return;
        }

        // Rebuild even on the initial false/false state. The client menu is
        // constructed with the default side before TradeScreen identifies the
        // local player. Without an unconditional first rebuild, player A keeps
        // the player-B mapping and the visible/clickable grids diverge.
        boolean changed = this.clientPlayerIsA != playerA;
        this.clientPlayerIsA = playerA;
        if (changed || !clientSideLayoutInitialized) {
            replaceInactiveSlots();
            clientSideLayoutInitialized = true;
        }
    }

    public boolean isPlayerAReady() {
        return this.readyAData.get() != 0;
    }

    public boolean isPlayerBReady() {
        return this.readyBData.get() != 0;
    }

    public boolean isLocalPlayerReady() {
        return playerIsA() ? isPlayerAReady() : isPlayerBReady();
    }

    public boolean isOtherPlayerReady() {
        return playerIsA() ? isPlayerBReady() : isPlayerAReady();
    }

    private boolean playerIsA() {
        return owner != null ? owner.playerIsA() : clientPlayerIsA;
    }

    private boolean isServerOwner(Player player) {
        return owner != null && player == owner.player();
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        if (owner == null) {
            super.clicked(slotId, button, input, player);
            return;
        }

        if (!(player instanceof ServerPlayer serverPlayer) || !isServerOwner(serverPlayer)) {
            return;
        }

        if (input == ContainerInput.PICKUP_ALL) {
            return;
        }

        if (slotId >= 0 && slotId < TRADE_SLOTS) {
            int column = slotId % 9;
            int row = slotId / 9;

            // The ready button occupies the central 3x1 area.
            if (row == READY_ROW && column >= READY_LEFT_COL && column <= READY_RIGHT_COL
                    && button == 0 && input == ContainerInput.PICKUP) {
                tradeContainer.getSession().toggleReady(serverPlayer);
                return;
            }

            Slot clickedSlot = this.slots.get(slotId);

            // Own offer slots are handled explicitly on the server because the
            // left/right visual sides are remapped to different logical columns.
            // The client still uses the normal menu click prediction; the server
            // applies the same pickup rules against the authoritative shared container.
            if (clickedSlot instanceof TradeOfferSlot offerSlot) {
                if (offerSlot.isLocalOffer() && input == ContainerInput.PICKUP) {
                    handleTradePickup(serverPlayer, offerSlot, button);
                }
                return;
            }

            // Every other slot in the 6x9 trade canvas is decorative/view-only.
            return;
        }

        if (slotId >= TRADE_SLOTS && input == ContainerInput.QUICK_MOVE) {
            TradeGui.handleQuickMove(serverPlayer, tradeContainer, slotId, owner.playerIsA());
            return;
        }

        super.clicked(slotId, button, input, player);
    }

    private void handleTradePickup(ServerPlayer player, TradeOfferSlot slot, int button) {
        if (button != 0 && button != 1) {
            return;
        }

        ItemStack carried = getCarried().copy();
        ItemStack inSlot = slot.getItem().copy();

        if (button == 0) {
            if (carried.isEmpty()) {
                if (inSlot.isEmpty()) {
                    return;
                }

                ItemStack taken = inSlot.copy();
                slot.set(ItemStack.EMPTY);
                setCarried(taken);
                slot.onTake(player, taken);
            } else if (inSlot.isEmpty()) {
                int amount = Math.min(carried.getCount(), slot.getMaxStackSize(carried));
                ItemStack placed = carried.copy();
                placed.setCount(amount);
                slot.set(placed);
                carried.shrink(amount);
                setCarried(carried);
            } else if (ItemStack.isSameItemSameComponents(carried, inSlot)) {
                int space = Math.min(slot.getMaxStackSize(inSlot), inSlot.getMaxStackSize()) - inSlot.getCount();
                if (space <= 0) {
                    return;
                }
                int amount = Math.min(space, carried.getCount());
                inSlot.grow(amount);
                carried.shrink(amount);
                slot.set(inSlot);
                setCarried(carried);
            } else {
                // Left-click with a different item swaps the cursor and the slot.
                if (!slot.mayPlace(carried) || !slot.mayPickup(player)) {
                    return;
                }
                slot.set(carried);
                setCarried(inSlot);
            }
        } else {
            if (carried.isEmpty()) {
                if (inSlot.isEmpty()) {
                    return;
                }
                int amount = (inSlot.getCount() + 1) / 2;
                ItemStack taken = inSlot.copy();
                taken.setCount(amount);
                inSlot.shrink(amount);
                slot.set(inSlot);
                setCarried(taken);
                slot.onTake(player, taken);
            } else if (inSlot.isEmpty()) {
                ItemStack placed = carried.copy();
                placed.setCount(1);
                slot.set(placed);
                carried.shrink(1);
                setCarried(carried);
            } else if (ItemStack.isSameItemSameComponents(carried, inSlot)) {
                int max = Math.min(slot.getMaxStackSize(inSlot), inSlot.getMaxStackSize());
                if (inSlot.getCount() >= max) {
                    return;
                }
                inSlot.grow(1);
                carried.shrink(1);
                slot.set(inSlot);
                setCarried(carried);
            }
        }

        // Synchronize both viewers immediately and let the session reset readiness if
        // an offer changed. This keeps the shared trade container authoritative.
        TradeSession session = tradeContainer.getSession();
        if (session != null) {
            session.broadcastMenus();
        } else {
            broadcastChanges();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        // Never shift-click items out of the trade area. Shift-clicking is reserved
        // for moving items from the player's inventory into their own offer grid.
        if (slotIndex < TRADE_SLOTS) {
            return ItemStack.EMPTY;
        }

        if (owner != null && player instanceof ServerPlayer serverPlayer
                && isServerOwner(serverPlayer)) {
            TradeGui.handleQuickMove(serverPlayer, tradeContainer, slotIndex, owner.playerIsA());
            return ItemStack.EMPTY;
        }

        return super.quickMoveStack(player, slotIndex);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        if (slot.getContainerSlot() < TRADE_SLOTS) {
            return slot instanceof TradeOfferSlot offerSlot && offerSlot.isLocalOffer();
        }
        return true;
    }

    @Override
    public boolean canDragTo(Slot slot) {
        if (slot.getContainerSlot() < TRADE_SLOTS) {
            return slot instanceof TradeOfferSlot offerSlot && offerSlot.isLocalOffer();
        }
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (owner != null && player == owner.player()) {
            TradeSession session = tradeContainer.getSession();
            if (session != null) {
                session.cancelTrade();
            }
        }
    }

    private static final class TradeOfferSlot extends Slot {
        private final boolean localOffer;

        private TradeOfferSlot(Container container, int containerSlot, int x, int y,
                               boolean localOffer) {
            super(container, containerSlot, x, y);
            this.localOffer = localOffer;
        }

        /** True only for the four-by-three offer area on the viewer's LEFT side. */
        private boolean isLocalOffer() {
            return localOffer;
        }

        @Override
        public boolean isActive() {
            // Both sides must remain active so the other player's items render and
            // can show tooltips. Placement/pickup is restricted separately below.
            return true;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return localOffer;
        }

        @Override
        public boolean mayPickup(Player player) {
            return localOffer;
        }

        @Override
        public boolean isHighlightable() {
            return localOffer;
        }
    }

    private static final class InactiveSlot extends Slot {
        private InactiveSlot(Container container, int containerSlot, int x, int y) {
            super(container, containerSlot, x, y);
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean isHighlightable() {
            return false;
        }
    }

    public record ServerOwner(ServerPlayer player, boolean playerIsA) {
    }
}
