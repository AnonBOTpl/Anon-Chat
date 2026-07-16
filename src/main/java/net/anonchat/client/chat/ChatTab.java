package net.anonchat.client.chat;

import net.anonchat.client.config.ChatTabConfig;

/**
 * Abstract base class for a chat tab.
 */

public abstract class ChatTab {

    private final ChatWindow window;
    private final ChatTabConfig config;
    protected int unread;

    protected ChatTab(final ChatWindow window, final ChatTabConfig config) {
        this.window = window;
        this.config = config;
    }

    /** The window this tab belongs to. */
    public ChatWindow getWindow() { return window; }

    /** The root config (uniqueId, type SERVER/CUSTOM, index). */
    public ChatTabConfig getConfig() { return config; }

    /** Convenience: tab display name from config. */
    public String getName() {
        final String name = config.getProperties() != null ? config.getProperties().getName() : null;
        return (name != null && !name.trim().isEmpty()) ? name.trim() : getDefaultName();
    }

    /** Fallback name when no name is configured. */
    public String getDefaultName() { return "Tab"; }

    /** Unread message counter. */
    public int getUnread() { return unread; }
    public void resetUnread() { this.unread = 0; }

    /** Handle an incoming message – main entry point for filtering. */
    public abstract boolean handleInput(ChatMessageWrapper message);
}
