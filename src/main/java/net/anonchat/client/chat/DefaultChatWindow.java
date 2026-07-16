package net.anonchat.client.chat;

import net.anonchat.client.config.ChatTabConfig;
import net.anonchat.client.config.ChatWindowSettings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Default implementation of a chat window.
 */

public class DefaultChatWindow implements ChatWindow {

    private final ChatWindowSettings settings;
    private final List<ChatTab> tabs = new ArrayList<>();
    private ChatTab activeTab;

    public DefaultChatWindow(final ChatWindowSettings settings) {
        this.settings = settings;
        for (final ChatTabConfig tabConfig : settings.getTabs()) {
            initializeTab(tabConfig);
        }
        // Activate first tab by default
        if (!tabs.isEmpty()) {
            this.activeTab = tabs.get(0);
        }
    }

    @Override
    public ChatWindowSettings getSettings() { return settings; }

    @Override
    public List<ChatTab> getTabs() { return tabs; }

    @Override
    public ChatTab getActiveTab() { return activeTab; }

    @Override
    public void switchToTab(final ChatTab tab) {
        if (tabs.contains(tab)) {
            this.activeTab = tab;
            settings.setFocusedTab(tabs.indexOf(tab));
        }
    }

    @Override
    public void deleteTab(final ChatTab tab) {
        tabs.remove(tab);
        settings.getTabs().remove(tab.getConfig());

        // If active tab was removed, pick the previous or first
        if (activeTab == tab) {
            if (tabs.isEmpty()) {
                activeTab = null;
            } else {
                final int prevIndex = Math.max(0, settings.getFocusedTab() - 1);
                activeTab = tabs.get(Math.min(prevIndex, tabs.size() - 1));
            }
        }
        sortTabs();
    }

    @Override
    public ChatTab initializeTab(final ChatTabConfig config) {
        if (config == null) return null;

        // Check if already exists
        for (final ChatTab tab : tabs) {
            if (tab.getConfig().getUniqueId().equals(config.getUniqueId())) {
                return tab;
            }
        }

        final ChatTabImpl tab = new ChatTabImpl(this, config);
        tabs.add(tab);
        sortTabs();

        // Add to window settings if not already there
        if (!settings.getTabs().contains(config)) {
            settings.getTabs().add(config);
        }

        return tab;
    }

    /** Keep tabs sorted by index. */
    private void sortTabs() {
        tabs.sort(Comparator.comparingInt(t -> t.getConfig().getIndex()));
    }
}
