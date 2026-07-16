package net.anonchat.client.chat;

import net.anonchat.client.config.ChatTabConfig;
import net.anonchat.client.config.ChatWindowSettings;
import java.util.List;

public interface ChatWindow {

    ChatWindowSettings getSettings();
    List<ChatTab> getTabs();
    ChatTab getActiveTab();
    void switchToTab(ChatTab tab);
    void deleteTab(ChatTab tab);
    ChatTab initializeTab(ChatTabConfig config);

    default boolean isMainWindow() {
        return getTabs().stream().anyMatch(t -> t.getConfig().isServerTab());
    }
}
