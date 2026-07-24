package net.anonchat.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.anonlauncher.chatmod.AnonChatMod;
import net.anonchat.client.chat.ChatMessageWrapper;
import net.anonchat.client.chat.ChatRouter;
import net.anonchat.client.chat.ChatTab;
import net.anonchat.client.chat.ChatTabImpl;
import net.anonchat.client.chat.ChatWindow;
import net.anonchat.client.chat.DefaultChatWindow;
import net.anonchat.client.config.ChatConfig;
import net.anonchat.client.config.ChatTabConfig;
import net.anonchat.client.config.ChatWindowSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class ChatWindowWidget {

    private static final int TAB_BAR_HEIGHT = 14;
    private static final int PADDING = 2;
    private static final int TITLE_BAR_COLOR = 0x80000000;
    private static final int BACKGROUND_COLOR = 0x40000000;
    private static final int BORDER_COLOR = 0xFF555555;
    private static final int HAMBURGER_SIZE = 10;
    private static final int LOCK_SIZE = 10;
    private static final int DROPDOWN_WIDTH = 110;
    private static final int DROPDOWN_ITEM_HEIGHT = 12;

    private static final int RESIZE_THRESHOLD = 5;
    private static final int RESIZE_THRESHOLD_TOP = 3;
    private static final int MIN_WIDTH = 120;
    private static final int MIN_HEIGHT = 80;
    private static final int EDGE_LEFT   = 1;
    private static final int EDGE_RIGHT  = 2;
    private static final int EDGE_TOP    = 4;
    private static final int EDGE_BOTTOM = 8;

    private static final String RESIZE_ICON = "\u21F2"; // ⇲ bottom-right corner arrow

    private final ChatWindow chatWindow;
    private final ChatWindowSettings settings;
    private final ChatOverlay overlay;
    private final ChatMessagesWidget messagesWidget;
    private final List<ChatTabWidget> tabWidgets = new ArrayList<>();

    private int x, y, width, height;
    private boolean dragging;
    private boolean hamburgerOpen = false;
    private int hoveredEdge = 0;

    private int resizeEdge;
    private int resizeStartMouseX;
    private int resizeStartMouseY;
    private int resizeStartX;
    private int resizeStartY;
    private int resizeStartWidth;
    private int resizeStartHeight;

    public ChatWindowWidget(
        final ChatWindow chatWindow,
        final ChatWindowSettings settings,
        final ChatOverlay overlay
    ) {
        this.chatWindow = chatWindow;
        this.settings = settings;
        this.overlay = overlay;
        this.messagesWidget = new ChatMessagesWidget(chatWindow);
        for (final ChatTab tab : chatWindow.getTabs()) {
            if (tab instanceof ChatTabImpl) {
                tabWidgets.add(new ChatTabWidget((ChatTabImpl) tab, this));
            }
        }
        calculatePosition();
    }

    private void calculatePosition() {
        final MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() == null) { x = 0; y = 0; width = 300; height = 150; return; }
        final int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        width = (int) (settings.getWidth() != 0 ? settings.getWidth() : 300);
        height = (int) (settings.getHeight() != 0 ? settings.getHeight() : 150);

        if ("RIGHT".equalsIgnoreCase(settings.getHorizontalAnchor())) x = sw - width - (int) settings.getX();
        else if ("CENTER".equalsIgnoreCase(settings.getHorizontalAnchor())) x = (sw - width) / 2 + (int) settings.getX();
        else x = (int) settings.getX();

        if ("BOTTOM".equalsIgnoreCase(settings.getVerticalAnchor())) y = sh - height - (int) settings.getY();
        else if ("CENTER".equalsIgnoreCase(settings.getVerticalAnchor())) y = (sh - height) / 2 + (int) settings.getY();
        else y = (int) settings.getY();

        x = Math.max(0, Math.min(x, sw - 50));
        y = Math.max(0, Math.min(y, sh - 50));
    }

    public void render(final DrawContext context) {
        this.render(context, false);
    }

    public void render(final DrawContext context, final boolean focused) {
        if (!dragging && resizeEdge == 0) calculatePosition();
        final ChatTab active = chatWindow.getActiveTab();
        if (active == null) return;

        final int activeBg;
        final boolean drawBg;
        if (active.getConfig().getProperties() != null) {
            final var p = active.getConfig().getProperties();
            drawBg = focused ? p.isBackground() : p.isUnfocusedBackground();
            activeBg = focused ? p.getBackgroundColor() : p.getUnfocusedBgColor();
        } else {
            drawBg = true;
            activeBg = BACKGROUND_COLOR;
        }
        final int bgTop = focused ? y : (y + TAB_BAR_HEIGHT + PADDING + PADDING);
        if (drawBg) {
            context.fill(x, bgTop, x + width, y + height, activeBg);
        }

        if (focused) {
            context.fill(x, y, x + width, y + 1, BORDER_COLOR);
            context.fill(x, y + height - 1, x + width, y + height, BORDER_COLOR);
            context.fill(x, y, x + 1, y + height, BORDER_COLOR);
            context.fill(x + width - 1, y, x + width, y + height, BORDER_COLOR);

            final int tabBarBottom = y + TAB_BAR_HEIGHT + PADDING;
            context.fill(x + 1, y + 1, x + width - 1, tabBarBottom, TITLE_BAR_COLOR);

            final TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
            int tabX = x + PADDING + 1;
            final int tabY = y + PADDING + 1;
            for (final ChatTabWidget tw : tabWidgets) {
                final boolean ia = tw.getTab() == chatWindow.getActiveTab();
                final int twidth = textRenderer.getWidth(tw.getTab().getName()) + 8;
                tw.render(context, tabX, tabY, twidth, TAB_BAR_HEIGHT - PADDING, ia);
                tabX += twidth + 2;
            }

            // Lock icon (left of hamburger)
            final int hx = x + width - HAMBURGER_SIZE - 4;
            final int hy = y + (TAB_BAR_HEIGHT + PADDING - HAMBURGER_SIZE) / 2;
            final int lockX = hx - LOCK_SIZE - 4;
            final int lockY = y + (TAB_BAR_HEIGHT + PADDING - LOCK_SIZE) / 2 + 1;
            final boolean locked = settings.isPositionLocked();
            final int lockCol = locked ? 0xFFFFAA00 : 0xFF888888;
            // Draw padlock: small rectangle (body) + small arc (shackle)
            final int bw = 6, bh = 5; // body width/height
            final int bx = lockX + (LOCK_SIZE - bw) / 2;
            final int by = lockY + 4;
            context.fill(bx, by, bx + bw, by + bh, lockCol); // body
            // Shackle (arc): two vertical bars + horizontal bar
            final int sw = 2; // shackle width
            final int shackleTop = by - 3;
            if (locked) {
                context.fill(bx + 1, shackleTop, bx + 1 + sw, by, lockCol); // left bar
                context.fill(bx + bw - 1 - sw, shackleTop, bx + bw - 1, by, lockCol); // right bar
                context.fill(bx + 1, shackleTop, bx + bw - 1, shackleTop + sw, lockCol); // top bar
            } else {
                // Draw a gap in the shackle (unlocked)
                context.fill(bx + 1, shackleTop, bx + 1 + sw, by - 1, lockCol);
                context.fill(bx + bw - 1 - sw, shackleTop, bx + bw - 1, by - 1, lockCol);
                context.fill(bx + 1, shackleTop, bx + bw - 1, shackleTop + sw, lockCol);
            }

            // Hamburger icon
            final int hCol = hamburgerOpen ? 0xFFFFFFAA : 0xFFCCCCCC;
            for (int i = 0; i < 3; i++)
                context.fill(hx, hy + i * 3, hx + 8, hy + i * 3 + 1, hCol);

            if (hamburgerOpen) renderDropdown(context);
        }

        // Messages
        final int mt = y + TAB_BAR_HEIGHT + PADDING + PADDING;
        messagesWidget.render(context, x + PADDING, mt, width - PADDING * 2,
            y + height - PADDING - mt, focused, drawBg);

        // ── Resize handle ──
        if (focused) {
            final TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
            final int iconX = x + width - textRenderer.getWidth(RESIZE_ICON) - 2;
            final int iconY = y + height - textRenderer.fontHeight - 1;
            context.drawText(textRenderer, Text.literal(RESIZE_ICON), iconX, iconY, BORDER_COLOR, false);
        }

        // ── Resize-edge indicator (subtle highlight bar on the border) ──
        if (focused) {
            final int indicator = resizeEdge != 0 ? resizeEdge : hoveredEdge;
            if (indicator != 0) {
                final int barLen = 14;
                final int barWid = 2;
                final int c = 0xAAFFFFFF;
                if ((indicator & EDGE_LEFT) != 0)
                    context.fill(x, y + height / 2 - barLen / 2, x + barWid, y + height / 2 + barLen / 2, c);
                if ((indicator & EDGE_RIGHT) != 0)
                    context.fill(x + width - barWid, y + height / 2 - barLen / 2, x + width, y + height / 2 + barLen / 2, c);
                if ((indicator & EDGE_TOP) != 0)
                    context.fill(x + width / 2 - barLen / 2, y, x + width / 2 + barLen / 2, y + barWid, c);
                if ((indicator & EDGE_BOTTOM) != 0)
                    context.fill(x + width / 2 - barLen / 2, y + height - barWid, x + width / 2 + barLen / 2, y + height, c);
            }
        }
    }

    public void setHoveredEdge(final int edge) { this.hoveredEdge = edge; }

    private int getDropdownX() {
        final MinecraftClient mc = MinecraftClient.getInstance();
        final int sw = mc.getWindow() != null ? mc.getWindow().getScaledWidth() : 0;
        final boolean roomRight = x + width + DROPDOWN_WIDTH + 6 < sw;
        final boolean roomLeft = x > DROPDOWN_WIDTH + 2;
        if (roomRight) return x + width + 2;
        if (roomLeft)  return x - DROPDOWN_WIDTH - 2;
        return x + width - DROPDOWN_WIDTH - 4;
    }

    private String[] getDropdownItems() {
        final ChatTab active = chatWindow.getActiveTab();
        final boolean isCustomTab = active != null && !active.getConfig().isServerTab();
        final boolean isSecondary = !chatWindow.isMainWindow();
        if (isCustomTab && isSecondary) return new String[]{"new_tab", "new_window", "delete_tab", "delete_window", "settings"};
        if (isCustomTab) return new String[]{"new_tab", "new_window", "delete_tab", "settings"};
        if (isSecondary) return new String[]{"new_tab", "new_window", "delete_window", "settings"};
        return new String[]{"new_tab", "new_window", "settings"};
    }

    private void renderDropdown(final DrawContext context) {
        final String[] items = getDropdownItems();
        final int ddX = getDropdownX();
        final int ddY = y + TAB_BAR_HEIGHT + PADDING + 1;
        final int ddH = items.length * DROPDOWN_ITEM_HEIGHT + 2;

        context.fill(ddX, ddY, ddX + DROPDOWN_WIDTH, ddY + ddH, 0xCC000000);
        context.fill(ddX, ddY, ddX + DROPDOWN_WIDTH, ddY + 1, 0xFF555555);
        context.fill(ddX, ddY + ddH - 1, ddX + DROPDOWN_WIDTH, ddY + ddH, 0xFF555555);
        context.fill(ddX, ddY, ddX + 1, ddY + ddH, 0xFF555555);
        context.fill(ddX + DROPDOWN_WIDTH - 1, ddY, ddX + DROPDOWN_WIDTH, ddY + ddH, 0xFF555555);

        final TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        for (int i = 0; i < items.length; i++) {
            final int iy = ddY + 1 + i * DROPDOWN_ITEM_HEIGHT;
            final boolean isDanger = items[i].startsWith("delete");
            final boolean isSettings = "settings".equals(items[i]);
            final String label = isSettings
                ? "> " + Text.translatable("key.anonchat.dropdown." + items[i]).getString()
                : Text.translatable("key.anonchat.dropdown." + items[i]).getString();
            if (isSettings) {
                context.fill(ddX + 4, iy - 1, ddX + DROPDOWN_WIDTH - 4, iy, 0xFF555555);
            }
            context.fill(ddX + 1, iy, ddX + DROPDOWN_WIDTH - 1, iy + DROPDOWN_ITEM_HEIGHT, 0x40000000);
            context.drawText(textRenderer, label, ddX + 4, iy + (DROPDOWN_ITEM_HEIGHT - textRenderer.fontHeight) / 2,
                isSettings ? 0xFFFFAA00 : (isDanger ? 0xFFFF5555 : 0xFFE0E0E0), false);
        }
    }

    public void updateHover(final double mx, final double my) {
        final TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int tx = x + PADDING + 1;
        final int ty = y + PADDING + 1;
        for (final ChatTabWidget tw : tabWidgets) {
            final int twidth = textRenderer.getWidth(tw.getTab().getName()) + 8;
            tw.setHovered(mx >= tx && mx <= tx + twidth && my >= ty && my <= ty + TAB_BAR_HEIGHT - PADDING);
            tx += twidth + 2;
        }
    }

    public boolean isInside(final double mx, final double my) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }

    public int getResizeEdge(final double mx, final double my) {
        int edge = 0;
        if (Math.abs(mx - x) < RESIZE_THRESHOLD) edge |= EDGE_LEFT;
        if (Math.abs(mx - (x + width)) < RESIZE_THRESHOLD) edge |= EDGE_RIGHT;
        if (Math.abs(my - y) < RESIZE_THRESHOLD_TOP) edge |= EDGE_TOP;
        if (Math.abs(my - (y + height)) < RESIZE_THRESHOLD) edge |= EDGE_BOTTOM;
        return edge;
    }

    public boolean isResizing() { return resizeEdge != 0; }

    public void startResize(final int edge, final double mouseX, final double mouseY) {
        if (settings.isPositionLocked()) return;
        this.resizeEdge = edge;
        this.resizeStartMouseX = (int) Math.round(mouseX);
        this.resizeStartMouseY = (int) Math.round(mouseY);
        this.resizeStartX = x;
        this.resizeStartY = y;
        this.resizeStartWidth = width;
        this.resizeStartHeight = height;
    }

    public void updateResize(final double mouseX, final double mouseY) {
        if (resizeEdge == 0) return;
        int dx = (int) Math.round(mouseX) - resizeStartMouseX;
        int dy = (int) Math.round(mouseY) - resizeStartMouseY;
        int newX = resizeStartX, newY = resizeStartY;
        int newW = resizeStartWidth, newH = resizeStartHeight;

        if ((resizeEdge & EDGE_RIGHT) != 0) newW = Math.max(MIN_WIDTH, resizeStartWidth + dx);
        if ((resizeEdge & EDGE_LEFT) != 0) {
            newW = Math.max(MIN_WIDTH, resizeStartWidth - dx);
            newX = resizeStartX + resizeStartWidth - newW;
        }
        if ((resizeEdge & EDGE_BOTTOM) != 0) newH = Math.max(MIN_HEIGHT, resizeStartHeight + dy);
        if ((resizeEdge & EDGE_TOP) != 0) {
            newH = Math.max(MIN_HEIGHT, resizeStartHeight - dy);
            newY = resizeStartY + resizeStartHeight - newH;
        }

        x = newX; y = newY; width = newW; height = newH;
    }

    public void commitResize() {
        resizeEdge = 0;
        settings.setWidth(width);
        settings.setHeight(height);
        // Invalidate font-split cache for all messages (width changed)
        for (final ChatTab tab : chatWindow.getTabs()) {
            if (tab instanceof ChatTabImpl) {
                for (final ChatMessageWrapper msg : ((ChatTabImpl) tab).getMessages()) {
                    msg.invalidateCache();
                }
            }
        }
        final MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() != null) {
            final int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
            switch (settings.getHorizontalAnchor().toUpperCase()) {
                case "RIGHT":  settings.setX(sw - x - width); break;
                case "CENTER": settings.setX(x - (sw - width) / 2.0f); break;
                default:       settings.setX(x); break;
            }
            switch (settings.getVerticalAnchor().toUpperCase()) {
                case "BOTTOM": settings.setY(sh - y - height); break;
                case "CENTER": settings.setY(y - (sh - height) / 2.0f); break;
                default:       settings.setY(y); break;
            }
        }
    }

    private int getTitleBarBottom() { return y + TAB_BAR_HEIGHT + PADDING; }

    public boolean isInTitleBar(final double mx, final double my) {
        if (mx < x || mx > x + width || my < y || my > getTitleBarBottom()) return false;
        final TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int tx = x + PADDING + 1;
        final int ty = y + PADDING + 1;
        for (final ChatTabWidget tw : tabWidgets) {
            final int twidth = textRenderer.getWidth(tw.getTab().getName()) + 8;
            if (mx >= tx && mx <= tx + twidth && my >= ty && my <= ty + TAB_BAR_HEIGHT - PADDING) return false;
            tx += twidth + 2;
        }
        final int hx = x + width - HAMBURGER_SIZE - 4;
        final int hy = y + (TAB_BAR_HEIGHT + PADDING - HAMBURGER_SIZE) / 2;
        final int lockX = hx - LOCK_SIZE - 4;
        final int lockY = y + (TAB_BAR_HEIGHT + PADDING - LOCK_SIZE) / 2 + 1;
        // Exclude lock icon area
        if (mx >= lockX && mx <= lockX + LOCK_SIZE && my >= lockY && my <= lockY + LOCK_SIZE) return false;
        return mx < hx || mx > hx + HAMBURGER_SIZE || my < hy || my > hy + HAMBURGER_SIZE;
    }

    public boolean clickHamburger(final double mx, final double my) {
        final int hx = x + width - HAMBURGER_SIZE - 4;
        final int hy = y + (TAB_BAR_HEIGHT + PADDING - HAMBURGER_SIZE) / 2;
        final int lockX = hx - LOCK_SIZE - 4;
        final int lockY = y + (TAB_BAR_HEIGHT + PADDING - LOCK_SIZE) / 2 + 1;
        // Lock icon click
        if (mx >= lockX && mx <= lockX + LOCK_SIZE && my >= lockY && my <= lockY + LOCK_SIZE) {
            settings.setPositionLocked(!settings.isPositionLocked());
            saveConfig();
            return true;
        }
        if (mx >= hx && mx <= hx + HAMBURGER_SIZE && my >= hy && my <= hy + HAMBURGER_SIZE) {
            hamburgerOpen = !hamburgerOpen;
            return true;
        }
        if (hamburgerOpen && !isInside(mx, my) && !isMouseOverDropdown(mx, my)) hamburgerOpen = false;
        return false;
    }

    public boolean clickDropdown(final double mx, final double my) {
        if (!hamburgerOpen) return false;
        final String[] items = getDropdownItems();
        final int ddX = getDropdownX();
        final int ddY = y + TAB_BAR_HEIGHT + PADDING + 1;
        if (mx < ddX || mx > ddX + DROPDOWN_WIDTH || my < ddY || my > ddY + items.length * DROPDOWN_ITEM_HEIGHT + 2) return false;

        final int index = (int) ((my - ddY - 1) / DROPDOWN_ITEM_HEIGHT);
        if (index < 0 || index >= items.length) return false;

        hamburgerOpen = false;
        switch (items[index]) {
            case "new_tab": addNewTab(); break;
            case "new_window": addNewWindow(); break;
            case "delete_tab": deleteActiveTab(); break;
            case "delete_window": deleteThisWindow(); break;
            case "settings":
                MinecraftClient.getInstance().setScreen(new AnonChatConfigScreen(null));
                break;
        }
        return true;
    }

    public void closeMenus() { hamburgerOpen = false; }
    public boolean isMouseOverDropdown(final double mx, final double my) {
        if (!hamburgerOpen) return false;
        final int ddX = getDropdownX();
        final int ddY = y + TAB_BAR_HEIGHT + PADDING + 1;
        final int cnt = getDropdownItems().length;
        return mx >= ddX && mx <= ddX + DROPDOWN_WIDTH && my >= ddY && my <= ddY + cnt * DROPDOWN_ITEM_HEIGHT + 2;
    }
    public boolean isDropdownOpen() { return hamburgerOpen; }

    public boolean clickTab(final double mx, final double my) {
        if (!isInside(mx, my)) return false;
        final TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int tx = x + PADDING + 1;
        final int ty = y + PADDING + 1;
        for (final ChatTabWidget tw : tabWidgets) {
            final int twidth = textRenderer.getWidth(tw.getTab().getName()) + 8;
            if (mx >= tx && mx <= tx + twidth && my >= ty && my <= ty + TAB_BAR_HEIGHT - PADDING) {
                tw.onClick();
                return true;
            }
            tx += twidth + 2;
        }
        return false;
    }

    public void clickMessages(final double mx, final double my) {
        if (!isInside(mx, my)) return;
        if (my > getTitleBarBottom() + PADDING) messagesWidget.mouseClicked(mx, my, 0);
    }

    private void deleteActiveTab() {
        final ChatTab active = chatWindow.getActiveTab();
        if (active == null || active.getConfig().isServerTab()) return;
        chatWindow.deleteTab(active);
        refreshTabs();
        if (chatWindow.getTabs().isEmpty() && !chatWindow.isMainWindow()) {
            overlay.getWindows().remove(this);
            if (ChatConfig.getInstance() != null) ChatConfig.getInstance().getWindows().remove(settings);
        }
        saveConfig();
    }

    private void deleteThisWindow() {
        overlay.getWindows().remove(this);
        if (ChatConfig.getInstance() != null) ChatConfig.getInstance().getWindows().remove(settings);
        saveConfig();
    }

    private void addNewTab() {
        final String name = "Tab " + (chatWindow.getTabs().size() + 1);
        final ChatTabConfig tabConfig = ChatTabConfig.createCustomTab(name);
        final ChatTabImpl newTab = (ChatTabImpl) chatWindow.initializeTab(tabConfig);
        if (newTab != null) {
            chatWindow.switchToTab(newTab);
            tabWidgets.add(new ChatTabWidget(newTab, this));
        }
        saveConfig();
    }

    private void addNewWindow() {
        final String name = "Window " + (overlay.getWindowCount() + 1);
        final ChatWindowSettings newSettings = ChatWindowSettings.createSecondary(name);
        if (ChatConfig.getInstance() != null) {
            ChatConfig.getInstance().getWindows().add(newSettings);
            saveConfig();
        }
        // Add new window directly without clearing existing messages
        final ChatRouter router = AnonChatMod.getRouter();
        final DefaultChatWindow chatWindow = new DefaultChatWindow(newSettings);
        router.getWindows().add(chatWindow);
        overlay.getWindows().add(new ChatWindowWidget(chatWindow, newSettings, overlay));
    }

    private void saveConfig() { try { ChatConfig.getInstance().save(); } catch (final Exception ignored) {} }

    public void scrollMessages(final double delta) { messagesWidget.mouseScrolled(delta); }
    public void resetMessagesScroll() { messagesWidget.resetScroll(); }
    public void setPosition(final int newX, final int newY) {
        if (settings.isPositionLocked()) return;
        x = newX; y = newY; dragging = true;
    }

    public void commitPosition() {
        dragging = false;
        final MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() == null) return;
        final int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();

        final int snapPx = 10;
        if (x < snapPx) x = 0;
        else if (x + width > sw - snapPx) x = sw - width;
        if (y < snapPx) y = 0;
        else if (y + height > sh - snapPx) y = sh - height;

        switch (settings.getHorizontalAnchor().toUpperCase()) {
            case "RIGHT":  settings.setX(sw - x - width); break;
            case "CENTER": settings.setX(x - (sw - width) / 2.0f); break;
            default:       settings.setX(x); break;
        }
        switch (settings.getVerticalAnchor().toUpperCase()) {
            case "BOTTOM": settings.setY(sh - y - height); break;
            case "CENTER": settings.setY(y - (sh - height) / 2.0f); break;
            default:       settings.setY(y); break;
        }
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void refreshTabs() {
        tabWidgets.clear();
        for (final ChatTab tab : chatWindow.getTabs()) {
            if (tab instanceof ChatTabImpl) tabWidgets.add(new ChatTabWidget((ChatTabImpl) tab, this));
        }
    }

    public ChatWindow getChatWindow() { return chatWindow; }
    public ChatWindowSettings getSettings() { return settings; }


}
