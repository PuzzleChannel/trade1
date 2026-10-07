/* Modified from Rvhoyos/simple-trading for Minecraft 26.2 Fabric. */
package mc.simpletrading.economy;

import net.minecraft.world.SimpleContainer;

/** Shared container used by a trade session. */
public final class TradeMenuContainer extends SimpleContainer {
    private final TradeSession session;
    private boolean updatingButtons;

    public TradeMenuContainer(int size, TradeSession session) {
        super(size);
        this.session = session;
    }

    public TradeSession getSession() {
        return session;
    }

    public void setUpdatingButtons(boolean updatingButtons) {
        this.updatingButtons = updatingButtons;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (!updatingButtons && session != null) {
            session.onContainerChanged();
        }
    }
}
