package net.anonchat.client.config;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatTabProperties {

    @SerializedName("name") private String name = "";
    @SerializedName("filters") private List<ChatFilter> filters;
    @SerializedName("global") private boolean global = true;
    @SerializedName("chatLimit") private int chatLimit = 100;
    @SerializedName("combineChatMessages") private boolean combineChatMessages;
    @SerializedName("antiChatClear") private boolean antiChatClear;
    @SerializedName("chatTrust") private boolean chatTrust = true;
    @SerializedName("shadow") private boolean shadow = true;
    @SerializedName("background") private boolean background = true;
    @SerializedName("unfocusedBackground") private boolean unfocusedBackground = true;
    @SerializedName("backgroundColor") private int backgroundColor = 0x40000000;
    @SerializedName("unfocusedBgColor") private int unfocusedBgColor = 0x30000000;
    // messageBgColor REMOVED in v2 — use only window bg (active + inactive)
    @SerializedName("messageTimeout") private int messageTimeout;
    // excludeFromMainWindow REMOVED — always exclude from main when caught by custom tab
    @SerializedName("overrideFont") private boolean overrideFont;
    @SerializedName("lineSpacing") private int lineSpacing = 12;
    @SerializedName("messageSpacing") private int messageSpacing = 2;
    @SerializedName("textOpacity") private int textOpacity = 100;
    @SerializedName("textAlignment") private String textAlignment = "LEFT";
    @SerializedName("leftMargin") private int leftMargin = 2;
    @SerializedName("configVersion") private int configVersion = 1;

    public ChatTabProperties() {}
    public ChatTabProperties(final String name) { this.name = name; }

    public String getName() { return name; }
    public List<ChatFilter> getFilters() { return filters != null ? filters : Collections.emptyList(); }
    public boolean isGlobal() { return global; }
    public int getChatLimit() { return chatLimit; }
    public boolean isCombineChatMessages() { return combineChatMessages; }
    public boolean isAntiChatClear() { return antiChatClear; }
    public boolean isChatTrust() { return chatTrust; }
    public boolean isShadow() { return shadow; }
    public boolean isBackground() { return background; }
    public boolean isUnfocusedBackground() { return unfocusedBackground; }
    public int getBackgroundColor() { return backgroundColor; }
    public int getUnfocusedBgColor() { return unfocusedBgColor; }
    public int getMessageTimeout() { return messageTimeout; }
    public boolean isOverrideFont() { return overrideFont; }
    public int getLineSpacing() { return lineSpacing; }
    public int getMessageSpacing() { return messageSpacing; }
    public int getTextOpacity() { return textOpacity; }
    public String getTextAlignment() { return textAlignment; }
    public int getLeftMargin() { return leftMargin; }

    public ChatFilter findMatchingFilter(final String message) {
        for (final ChatFilter filter : getFilters()) {
            if (filter.matches(message)) return filter;
        }
        return null;
    }

    public boolean hasAnyFilterCriteria() {
        for (final ChatFilter filter : getFilters()) {
            if (filter.hasIncludeCriteria()) return true;
        }
        return false;
    }

    public void setName(final String name) { this.name = name; }
    public void setFilters(final List<ChatFilter> filters) { this.filters = filters; }
    public void setGlobal(final boolean global) { this.global = global; }
    public void setChatLimit(final int limit) { this.chatLimit = Math.max(1, limit); }
    public void setCombineChatMessages(final boolean combine) { this.combineChatMessages = combine; }
    public void setAntiChatClear(final boolean antiClear) { this.antiChatClear = antiClear; }
    public void setChatTrust(final boolean trust) { this.chatTrust = trust; }
    public void setShadow(final boolean shadow) { this.shadow = shadow; }
    public void setBackground(final boolean background) { this.background = background; }
    public void setUnfocusedBackground(final boolean unfocused) { this.unfocusedBackground = unfocused; }
    public void setBackgroundColor(final int color) { this.backgroundColor = color; }
    public void setUnfocusedBgColor(final int color) { this.unfocusedBgColor = color; }
    public void setMessageTimeout(final int sec) { this.messageTimeout = sec; }
    public void setOverrideFont(final boolean override) { this.overrideFont = override; }
    public void setLineSpacing(final int ls) { this.lineSpacing = Math.max(6, Math.min(24, ls)); }
    public void setMessageSpacing(final int ms) { this.messageSpacing = Math.max(0, Math.min(20, ms)); }
    public void setTextOpacity(final int op) { this.textOpacity = Math.max(30, Math.min(100, op)); }
    public void setTextAlignment(final String align) { this.textAlignment = align; }
    public void setLeftMargin(final int lm) { this.leftMargin = Math.max(0, Math.min(12, lm)); }
}
