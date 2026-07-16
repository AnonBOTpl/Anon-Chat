package net.anonchat.client.chat;

import net.anonchat.client.chatlog.ChatLogger;
import net.anonchat.client.config.ChatConfig;
import net.anonchat.client.config.ChatFilter;
import net.anonchat.client.config.ChatTabProperties;
import net.anonchat.client.config.ChatWindowSettings;
import java.util.ArrayList;
import java.util.List;

public class ChatRouter {

    private final List<ChatWindow> windows = new ArrayList<>();

    public void loadConfiguration() {
        windows.clear();
        final ChatConfig config = ChatConfig.getInstance();
        if (config == null) return;
        for (final ChatWindowSettings settings : config.getWindows()) {
            if (settings == null) continue;
            windows.add(new DefaultChatWindow(settings));
        }
    }

    public boolean dispatchMessage(final ChatMessage message) {
        if (message == null || message.getPlainText() == null) return false;
        final String text = message.getPlainText();

        // Log raw message to daily chat log file
        ChatLogger.log(text);

        boolean caughtByCustomTab = false;
        for (final ChatWindow window : windows) {
            for (final ChatTab tab : window.getTabs()) {
                if (tab.getConfig().isServerTab()) continue;
                if (!(tab instanceof ChatTabImpl)) continue;
                final ChatTabProperties props = tab.getConfig().getProperties();
                if (props == null) continue;
                for (final ChatFilter filter : props.getFilters()) {
                    if (filter != null && filter.hasIncludeCriteria() && filter.matches(text)) {
                        caughtByCustomTab = true;
                        break;
                    }
                }
                if (caughtByCustomTab) break;
            }
            if (caughtByCustomTab) break;
        }

        // Check if the main window itself has a filter that matches
        boolean caughtByMain = false;
        for (final ChatWindow window : windows) {
            if (!window.isMainWindow()) continue;
            for (final ChatTab tab : window.getTabs()) {
                if (!tab.getConfig().isServerTab()) continue;
                if (!(tab instanceof ChatTabImpl)) continue;
                final ChatTabProperties props = tab.getConfig().getProperties();
                if (props == null) continue;
                for (final ChatFilter filter : props.getFilters()) {
                    if (filter != null && filter.hasIncludeCriteria() && filter.matches(text)) {
                        caughtByMain = true;
                        break;
                    }
                }
                if (caughtByMain) break;
            }
            if (caughtByMain) break;
        }

        boolean accepted = false;
        for (final ChatWindow window : windows) {
            for (final ChatTab tab : window.getTabs()) {
                if (!(tab instanceof ChatTabImpl)) continue;
                final ChatTabImpl tabImpl = (ChatTabImpl) tab;
                // If custom tab caught the message AND main tab doesn't have its own include → exclude from main
                // If main tab also has include that matches → show in both (don't exclude)
                final boolean exclude = tab.getConfig().isServerTab() && caughtByCustomTab && !caughtByMain;

                final ChatMessageWrapper wrapper = ChatMessageWrapper.of(message);
                if (tabImpl.handleInput(wrapper, exclude)) {
                    accepted = true;
                }
            }
        }
        return accepted;
    }

    public void reload() { loadConfiguration(); }

    public List<ChatWindow> getWindows() { return windows; }

    public ChatWindow findWindow(final ChatWindowSettings settings) {
        for (final ChatWindow w : windows) {
            if (w.getSettings() == settings) return w;
        }
        return null;
    }
}
