package net.anonchat.client.chat;

import net.anonchat.client.config.ChatTabConfig;

public abstract class ChatTab {

    private final ChatWindow window;
    private final ChatTabConfig config;
    protected int unread;

    protected ChatTab(final ChatWindow window, final ChatTabConfig config) {
        this.window = window;
        this.config = config;
    }

    public ChatWindow getWindow() { return window; }
    public ChatTabConfig getConfig() { return config; }

    public String getName() {
        final String name = config.getProperties() != null ? config.getProperties().getName() : null;
        return (name != null && !name.trim().isEmpty()) ? name.trim() : getDefaultName();
    }

    public String getDefaultName() { return "Tab"; }

    public int getUnread() { return unread; }
    public void resetUnread() { this.unread = 0; }

    public abstract boolean handleInput(ChatMessageWrapper message);
}
