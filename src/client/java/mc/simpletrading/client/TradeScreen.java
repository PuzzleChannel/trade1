package mc.simpletrading.client;

import mc.simpletrading.economy.TradeChestMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;

/** Vanilla-looking 4x3 trade screen with synchronized readiness state. */
public final class TradeScreen extends AbstractContainerScreen<TradeChestMenu> {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace(
            "textures/gui/container/generic_54.png");

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 222;
    private static final int SLOT_LEFT = 8;
    private static final int SLOT_TOP = 14;
    private static final int CELL = 18;

    private static final int LEFT_TRADE_START_COL = 0;
    private static final int RIGHT_TRADE_START_COL = 5;
    private static final int TRADE_COLUMNS = 4;
    private static final int TRADE_ROWS = 3;

    private static final int READY_LEFT_COL = 3;
    private static final int READY_WIDTH_COLS = 3;
    private static final int READY_ROW = 4;
    private static final int HEADER_Y_OFFSET = 8;
    private static final int READY_Y_OFFSET = 10;

    // Same dark-gray used by vanilla container title text.
    private static final int TEXT = 0xFF404040;
    private static final int PANEL = 0xFFC6C6C6;

    private final String playerAName;
    private final String playerBName;

    public TradeScreen(TradeChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, GUI_WIDTH, GUI_HEIGHT);

        ParsedNames names = parseNames(title.getString());
        this.playerAName = names == null ? "Игрок" : names.playerA();
        this.playerBName = names == null ? "Игрок" : names.playerB();
    }

    @Override
    protected void init() {
        super.init();

        Minecraft client = Minecraft.getInstance();
        boolean playerA = client.player != null
                && client.player.getName().getString().equals(playerAName);
        menu.setClientPlayerIsA(playerA);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && isReadyButtonHovered(event.x(), event.y())) {
            toggleReady();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void toggleReady() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null) {
            return;
        }

        client.gameMode.handleContainerInput(
                menu.containerId,
                TradeChestMenu.READY_SLOT,
                0,
                ContainerInput.PICKUP,
                client.player
        );
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);

        // Keep the standard vanilla container for the inventory/hotbar area.
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                BACKGROUND,
                this.leftPos,
                this.topPos,
                0.0F,
                0.0F,
                this.imageWidth,
                this.imageHeight,
                256,
                256
        );

        // Cover the complete 6x9 chest area first. Only the 24 real trade cells
        // are drawn again below, so no blocked slot wells remain visible anywhere,
        // including beneath the ready button.
        int panelLeft = this.leftPos + SLOT_LEFT - 1;
        int panelTop = this.topPos + SLOT_TOP - 1;
        int panelWidth = 9 * CELL + 2;
        int panelHeight = 6 * CELL + 5;
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, PANEL);

        // Redraw only the 24 real trading slots using the exact same 18x18 texture
        // region as the normal Minecraft inventory slots.
        drawTradeSlotBackgrounds(graphics);

        // The left grid is always the local player's offer.  The right grid is
        // always the other player's offer.  Readiness is therefore mirrored per
        // viewer instead of using the fixed A/B screen positions.
        if (menu.isLocalPlayerReady()) {
            drawReadyTint(graphics, LEFT_TRADE_START_COL);
        }
        if (menu.isOtherPlayerReady()) {
            drawReadyTint(graphics, RIGHT_TRADE_START_COL);
        }

        drawArrows(graphics);

        int buttonLeft = SLOT_LEFT + READY_LEFT_COL * CELL;
        int buttonTop = SLOT_TOP + READY_ROW * CELL + READY_Y_OFFSET;
        int buttonRight = buttonLeft + READY_WIDTH_COLS * CELL;
        int buttonBottom = buttonTop + CELL;
        boolean hovered = isReadyButtonHovered(mouseX, mouseY);

        drawReadyButton(
                graphics,
                this.leftPos + buttonLeft,
                this.topPos + buttonTop,
                this.leftPos + buttonRight,
                this.topPos + buttonBottom,
                menu.isLocalPlayerReady(),
                hovered,
                menu.getCountdownSeconds()
        );
    }

    private void drawTradeSlotBackgrounds(GuiGraphicsExtractor graphics) {
        for (int row = 1; row <= 3; row++) {
            for (int col = 0; col < 4; col++) {
                drawVanillaSlotBackground(graphics, col, row);
            }
            for (int col = 5; col < 9; col++) {
                drawVanillaSlotBackground(graphics, col, row);
            }
        }
    }

    private void drawVanillaSlotBackground(GuiGraphicsExtractor graphics, int column, int row) {
        int x = this.leftPos + SLOT_LEFT - 1 + column * CELL;
        int y = this.topPos + SLOT_TOP - 1 + row * CELL;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                BACKGROUND,
                x,
                y,
                7.0F,
                17.0F,
                CELL,
                CELL,
                256,
                256
        );
    }

    private void drawReadyTint(GuiGraphicsExtractor graphics, int startCol) {
        for (int row = 1; row <= 3; row++) {
            for (int col = startCol; col < startCol + TRADE_COLUMNS; col++) {
                int x = this.leftPos + SLOT_LEFT + col * CELL;
                int y = this.topPos + SLOT_TOP + row * CELL;
                graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x3F4D8E5A);
            }
        }
    }

    private void drawArrows(GuiGraphicsExtractor graphics) {
        int centerX = this.leftPos + SLOT_LEFT + 4 * CELL + CELL / 2;
        int firstY = this.topPos + SLOT_TOP + CELL + 9;
        int secondY = this.topPos + SLOT_TOP + CELL + 27;

        drawRightArrow(graphics, centerX - 8, firstY);
        drawLeftArrow(graphics, centerX - 8, secondY);
    }

    private static void drawRightArrow(GuiGraphicsExtractor graphics, int x, int y) {
        int c = 0xFF777777;
        graphics.fill(x, y + 4, x + 11, y + 7, c);
        graphics.fill(x + 8, y + 2, x + 12, y + 9, c);
        graphics.fill(x + 11, y + 3, x + 14, y + 8, c);
    }

    private static void drawLeftArrow(GuiGraphicsExtractor graphics, int x, int y) {
        int c = 0xFF777777;
        graphics.fill(x + 3, y + 4, x + 14, y + 7, c);
        graphics.fill(x + 2, y + 2, x + 6, y + 9, c);
        graphics.fill(x, y + 3, x + 3, y + 8, c);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();

        // Only names live in the top header.  Ready/not-ready status is intentionally
        // hidden here: the local button and the green offer tint are the status cues.
        int leftGridX = SLOT_LEFT;
        int rightGridX = SLOT_LEFT + RIGHT_TRADE_START_COL * CELL;
        int nameY = 3 + HEADER_Y_OFFSET;

        drawCentered(graphics, client, "Вы", leftGridX, nameY, TRADE_COLUMNS * CELL, TEXT);
        drawCentered(graphics, client, fitName(
                menu.isClientPlayerA() ? playerBName : playerAName, 70),
                rightGridX, nameY, TRADE_COLUMNS * CELL, TEXT);

        int statusY = nameY + 9;
        if (menu.isLocalPlayerReady()) {
            drawCentered(graphics, client, "ГОТОВ",
                    leftGridX, statusY, TRADE_COLUMNS * CELL, 0xFF4F7D4F);
        }
        if (menu.isOtherPlayerReady()) {
            drawCentered(graphics, client, "ГОТОВ",
                    rightGridX, statusY, TRADE_COLUMNS * CELL, 0xFF4F7D4F);
        }

        graphics.text(client.font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, TEXT, false);
    }

    private boolean isReadyButtonHovered(double mouseX, double mouseY) {
        double localX = mouseX - this.leftPos;
        double localY = mouseY - this.topPos;
        int left = SLOT_LEFT + READY_LEFT_COL * CELL;
        int top = SLOT_TOP + READY_ROW * CELL + READY_Y_OFFSET;
        int right = left + READY_WIDTH_COLS * CELL;
        int bottom = top + CELL;
        return localX >= left && localX < right && localY >= top && localY < bottom;
    }

    private static void drawCentered(GuiGraphicsExtractor graphics, Minecraft client,
                                     String text, int left, int top, int width, int color) {
        Component component = Component.literal(text);
        int textWidth = client.font.width(component);
        int x = left + (width - textWidth) / 2;
        graphics.text(client.font, component, x, top + 2, color, false);
    }

    private static void drawReadyButton(GuiGraphicsExtractor graphics, int left, int top,
                                        int right, int bottom, boolean ready, boolean hovered,
                                        int countdown) {
        int outer = ready ? 0xFF315C3A : 0xFF4F4F4F;
        int inner = ready ? (hovered ? 0xFF4C8758 : 0xFF416F4B)
                : (hovered ? 0xFF868686 : 0xFF747474);
        int light = ready ? 0xFF6CA477 : 0xFFADADAD;
        int dark = ready ? 0xFF23452C : 0xFF444444;

        graphics.fill(left, top, right, bottom, outer);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, inner);
        graphics.fill(left + 1, top + 1, right - 1, top + 2, light);
        graphics.fill(left + 1, bottom - 2, right - 1, bottom - 1, dark);

        String label = countdown > 0
                ? "ОБМЕН " + countdown
                : ready ? "ОТМЕНИТЬ" : "ГОТОВ";
        Minecraft client = Minecraft.getInstance();
        Component component = Component.literal(label);
        int textWidth = client.font.width(component);
        int textX = (left + right - textWidth) / 2;
        graphics.text(client.font, component, textX, top + 5, TEXT, false);
    }

    private static String fitName(String name, int maxWidth) {
        Minecraft client = Minecraft.getInstance();
        if (client.font.width(name) <= maxWidth) {
            return name;
        }

        String suffix = "…";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            String candidate = out.toString() + name.charAt(i) + suffix;
            if (client.font.width(candidate) > maxWidth) {
                break;
            }
            out.append(name.charAt(i));
        }
        return out.toString() + suffix;
    }

    private static ParsedNames parseNames(String title) {
        String prefix = "Обмен: ";
        if (!title.startsWith(prefix)) {
            return null;
        }
        String body = title.substring(prefix.length());
        int separator = body.indexOf(" ↔ ");
        if (separator <= 0 || separator + 3 >= body.length()) {
            return null;
        }
        return new ParsedNames(body.substring(0, separator), body.substring(separator + 3));
    }

    private record ParsedNames(String playerA, String playerB) {
    }
}
