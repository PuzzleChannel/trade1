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
    private static final int TRADE_SLOT_Y_OFFSET = -4;

    private static final int READY_LEFT_COL = 3;
    private static final int READY_RIGHT_COL = 5;
    private static final int READY_ROW = 4;

    private final TradeMenuContainer tradeContainer;
    private final ServerOwner owner;
    private final DataSlot readyAData;
    private final DataSlot readyBData;
    private final DataSlot countdownData;
    private boolean clientPlayerIsA;
    private boolean clientSideLayoutInitialized;

    public TradeChestMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new TradeMenuContainer(TRADE_SLOTS, null), null,
                DataSlot.standalone(), DataSlot.standalone(), DataSlot.standalone());
    }

    public TradeChestMenu(int containerId, Inventory inventory, Container container, ServerOwner owner) {
        this(containerId, inventory, container, owner,
                createReadyDataSlot(container, 0),
                createReadyDataSlot(container, 1),
                createCountdownDataSlot(container));
    }

    private TradeChestMenu(int containerId, Inventory inventory, Container container, ServerOwner owner,
                           DataSlot readyAData, DataSlot readyBData, DataSlot countdownData) {
        super(SimpleTradingMenus.TRADE, containerId, inventory, container, TRADE_ROWS);
        if (!(container instanceof TradeMenuContainer tradeContainer)) {
            throw new IllegalArgumentException("TradeChestMenu requires TradeMenuContainer");
        }
        this.tradeContainer = tradeContainer;
        this.owner = owner;
        this.readyAData = readyAData;
        this.readyBData = readyBData;
        this.countdownData = countdownData;
        addDataSlot(this.readyAData);
        addDataSlot(this.readyBData);
        addDataSlot(this.countdownData);
        replaceInactiveSlots();
    }

    private static DataSlot createCountdownDataSlot(Container container) {
        TradeMenuContainer tradeContainer = (TradeMenuContainer) container;
        TradeSession session = tradeContainer.getSession();
        return session == null
                ? DataSlot.standalone()
                : DataSlot.shared(session.getCountdownData(), 0);
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

    public int getCountdownSeconds() {
        return this.countdownData.get();
    }

    private boolean playerIsA() {
        return owner != null ? owner.playerIsA() : clientPlayerIsA;
    }

    private boolean isServerOwner(Player player) {
        return owner != null && player == owner.player();
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        if (owner != null && !isServerOwner(player)) {
            return;
        }

        if (input == ContainerInput.PICKUP_ALL) {
            return;
        }

        if (slotId >= 0 && slotId < TRADE_SLOTS) {
            int column = slotId % 9;
            int row = slotId / 9;

            if (owner != null
                    && row == READY_ROW
                    && column >= READY_LEFT_COL
                    && column <= READY_RIGHT_COL
                    && button == 0
                    && input == ContainerInput.PICKUP) {
                TradeSession session = tradeContainer.getSession();
                if (session != null) {
                    session.toggleReady((ServerPlayer) player);
                }
                return;
            }

            Slot clickedSlot = this.slots.get(slotId);
            if (!(clickedSlot instanceof TradeOfferSlot offerSlot)
                    || !offerSlot.isLocalOffer()
                    || isLocalPlayerReady()) {
                return;
            }

            // Use vanilla click processing for the actual offer slot. This keeps
            // client prediction, cursor handling, quick-craft and item components
            // identical on client and server.
            super.clicked(slotId, button, input, player);
            return;
        }

        super.clicked(slotId, button, input, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= this.slots.size()) {
            return ItemStack.EMPTY;
        }

        if (owner != null && !isServerOwner(player)) {
            return ItemStack.EMPTY;
        }

        if (slotIndex < TRADE_SLOTS) {
            Slot source = this.slots.get(slotIndex);
            if (!(source instanceof TradeOfferSlot offerSlot)
                    || !offerSlot.isLocalOffer()
                    || isLocalPlayerReady()) {
                return ItemStack.EMPTY;
            }

            ItemStack original = source.getItem().copy();
            if (original.isEmpty()) {
                return ItemStack.EMPTY;
            }

            ItemStack moving = original.copy();
            if (!moveItemStackTo(moving, TRADE_SLOTS, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }

            source.set(moving);
            source.onTake(player, original);
            broadcastChanges();
            return original;
        }

        if (isLocalPlayerReady()) {
            return ItemStack.EMPTY;
        }

        Slot source = this.slots.get(slotIndex);
        ItemStack original = source.getItem().copy();
        if (original.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack moving = original.copy();
        if (!insertIntoLocalOfferSlots(moving)) {
            return ItemStack.EMPTY;
        }

        source.set(moving);
        source.onTake(player, original);
        player.getInventory().setChanged();
        broadcastChanges();
        return original;
    }

    private boolean insertIntoLocalOfferSlots(ItemStack moving) {
        boolean movedAny = false;

        for (Slot target : this.slots) {
            if (!(target instanceof TradeOfferSlot offerSlot) || !offerSlot.isLocalOffer()) {
                continue;
            }

            ItemStack existing = target.getItem();
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(moving, existing)) {
                continue;
            }

            int max = Math.min(target.getMaxStackSize(existing), moving.getMaxStackSize());
            int space = max - existing.getCount();
            if (space <= 0) {
                continue;
            }

            int amount = Math.min(space, moving.getCount());
            existing.grow(amount);
            moving.shrink(amount);
            target.setChanged();
            movedAny = true;

            if (moving.isEmpty()) {
                return true;
            }
        }

        for (Slot target : this.slots) {
            if (!(target instanceof TradeOfferSlot offerSlot) || !offerSlot.isLocalOffer()) {
                continue;
            }

            if (!target.getItem().isEmpty() || !target.mayPlace(moving)) {
                continue;
            }

            int amount = Math.min(moving.getCount(), target.getMaxStackSize(moving));
            ItemStack placed = moving.copy();
            placed.setCount(amount);
            target.set(placed);
            moving.shrink(amount);
            movedAny = true;

            if (moving.isEmpty()) {
                return true;
            }
        }

        return movedAny;
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
