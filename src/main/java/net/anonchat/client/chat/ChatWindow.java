package net.anonchat.client.chat;

import net.anonchat.client.config.ChatTabConfig;
import net.anonchat.client.config.ChatWindowSettings;

import java.util.List;

/**
 * A chat window that contains one or more tabs.
 */

public interface ChatWindow {

    /** The position/size/anchor settings for this window. */
    ChatWindowSettings getSettings();

    /** All tabs in this window, ordered by index. */
    List<ChatTab> getTabs();

    /** The currently active (selected) tab. */
    ChatTab getActiveTab();

    /** Switch to a different tab. */
    void switchToTab(ChatTab tab);

    /** Remove a tab from this window. */
    void deleteTab(ChatTab tab);

    /** Create and add a new tab from its config. */
    ChatTab initializeTab(ChatTabConfig config);

    /**
     * @return true when this window has a SERVER tab (catch-all).
     */
    default boolean isMainWindow() {
        return getTabs().stream().anyMatch(t -> t.getConfig().isServerTab());
    }
}
