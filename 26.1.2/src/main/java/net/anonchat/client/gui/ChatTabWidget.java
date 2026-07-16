package net.anonchat.client.gui;

import net.anonchat.client.chat.ChatTabImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class ChatTabWidget {

    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int ACTIVE_TEXT_COLOR = 0xFFFFFFFF;
    private static final int UNREAD_COLOR = 0xFFFF5555;
    private static final int HOVER_COLOR = 0x50FFFFFF;

    private final ChatTabImpl tab;
    private final ChatWindowWidget parentWindow;
    private boolean hovered = false;

    public ChatTabWidget(final ChatTabImpl tab, final ChatWindowWidget parentWindow) {
        this.tab = tab;
        this.parentWindow = parentWindow;
    }

    public void render(
        final GuiGraphicsExtractor context,
        final int tabX,
        final int tabY,
        final int tabWidth,
        final int tabHeight,
        final boolean isActive
    ) {
        final Font font = Minecraft.getInstance().font;

        // Background
        final int bgColor = isActive ? 0x70000000 : 0x40000000;
        context.fill(tabX, tabY, tabX + tabWidth, tabY + tabHeight, bgColor);

        if (hovered && !isActive) {
            context.fill(tabX, tabY, tabX + tabWidth, tabY + tabHeight, HOVER_COLOR);
        }

        if (isActive) {
            context.fill(tabX, tabY + tabHeight - 1, tabX + tabWidth, tabY + tabHeight, 0xFFFFAA00);
        }

        // Name text
        final String name = tab.getName();
        final int textX = tabX + (tabWidth - font.width(name)) / 2;
        final int textY = tabY + (tabHeight - font.lineHeight) / 2;
        context.text(font, name, textX, textY, isActive ? ACTIVE_TEXT_COLOR : TEXT_COLOR, false);

        // Unread badge
        final int unread = tab.getUnread();
        if (unread > 0 && !isActive) {
            final String badge = unread > 99 ? "99+" : String.valueOf(unread);
            final int badgeWidth = font.width(badge) + 4;
            final int badgeX = tabX + tabWidth - badgeWidth - 2;
            final int badgeY = tabY + 1;

            context.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + font.lineHeight + 1, 0xCCFF0000);
            context.text(font, badge, badgeX + 2, badgeY + 1, 0xFFFFFFFF, false);
        }
    }

    public void onClick() {
        parentWindow.getChatWindow().switchToTab(tab);
        tab.resetUnread();
    }

    public ChatTabImpl getTab() { return tab; }
    public void setHovered(final boolean hovered) { this.hovered = hovered; }
    public boolean isHovered() { return hovered; }
}
