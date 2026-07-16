package net.anonchat.client.config;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ChatFilter {

    @SerializedName("id") private String id;
    @SerializedName("tabId") private String tabId;
    @SerializedName("name") private String name = "Filter";
    @SerializedName("includeTags") private List<String> includeTags;
    @SerializedName("excludeTags") private List<String> excludeTags;
    @SerializedName("includeWords") private String includeWords;
    @SerializedName("excludeWords") private String excludeWords;
    @SerializedName("includeRegEx") private String includeRegEx;
    @SerializedName("excludeRegEx") private String excludeRegEx;
    @SerializedName("shouldPlaySound") private boolean shouldPlaySound;
    @SerializedName("shouldChangeBackground") private boolean shouldChangeBackground;
    @SerializedName("backgroundColor") private int backgroundColor = 0xFF000000;
    @SerializedName("shouldFilterTooltip") private boolean shouldFilterTooltip;
    @SerializedName("caseSensitive") private boolean caseSensitive;
    @SerializedName("advanced") private boolean advanced;
    @SerializedName("configVersion") private int configVersion = 1;

    public ChatFilter() {
        this.id = UUID.randomUUID().toString();
        this.tabId = UUID.randomUUID().toString();
    }

    public boolean matches(final String message) {
        if (message == null || message.isEmpty()) return false;
        final String text = caseSensitive ? message : message.toUpperCase();
        final boolean hasInclude = hasIncludeCriteria();
        final boolean hasExclude = hasExcludeCriteria();
        boolean excludeMatched = false;

        if (excludeTags != null && !excludeTags.isEmpty()) {
            for (final String tag : excludeTags) {
                final String search = caseSensitive ? tag : tag.toUpperCase();
                if (!search.isEmpty() && text.contains(search)) { excludeMatched = true; break; }
            }
        }
        if (!excludeMatched && excludeWords != null && !excludeWords.isEmpty()) {
            final String words = caseSensitive ? excludeWords : excludeWords.toUpperCase();
            for (final String word : words.split(";")) {
                final String trimmed = word.trim();
                if (!trimmed.isEmpty() && text.contains(trimmed)) { excludeMatched = true; break; }
            }
        }
        if (!excludeMatched && excludeRegEx != null && !excludeRegEx.isEmpty()) {
            try {
                final Pattern pattern = caseSensitive
                    ? Pattern.compile(excludeRegEx)
                    : Pattern.compile(excludeRegEx, Pattern.CASE_INSENSITIVE);
                if (pattern.matcher(text).find()) excludeMatched = true;
            } catch (final PatternSyntaxException ignored) {}
        }
        if (excludeMatched) return !hasInclude;

        if (hasInclude) {
            boolean includeMatched = false;
            if (includeTags != null && !includeTags.isEmpty()) {
                for (final String tag : includeTags) {
                    final String search = caseSensitive ? tag : tag.toUpperCase();
                    if (!search.isEmpty() && text.contains(search)) { includeMatched = true; break; }
                }
            }
            if (!includeMatched && includeWords != null && !includeWords.isEmpty()) {
                final String words = caseSensitive ? includeWords : includeWords.toUpperCase();
                for (final String word : words.split(";")) {
                    final String trimmed = word.trim();
                    if (!trimmed.isEmpty() && text.contains(trimmed)) { includeMatched = true; break; }
                }
            }
            if (!includeMatched && includeRegEx != null && !includeRegEx.isEmpty()) {
                try {
                    final Pattern pattern = caseSensitive
                        ? Pattern.compile(includeRegEx)
                        : Pattern.compile(includeRegEx, Pattern.CASE_INSENSITIVE);
                    if (pattern.matcher(text).find()) includeMatched = true;
                } catch (final PatternSyntaxException ignored) {}
            }
            return includeMatched;
        }
        return !hasInclude && !hasExclude;
    }

    public boolean hasIncludeCriteria() {
        return (includeTags != null && !includeTags.isEmpty())
            || (includeWords != null && !includeWords.isEmpty())
            || (includeRegEx != null && !includeRegEx.isEmpty());
    }

    public boolean hasExcludeCriteria() {
        return (excludeTags != null && !excludeTags.isEmpty())
            || (excludeWords != null && !excludeWords.isEmpty())
            || (excludeRegEx != null && !excludeRegEx.isEmpty());
    }

    public String getId() { return id; }
    public String getTabId() { return tabId; }
    public String getName() { return name; }
    public List<String> getIncludeTags() { return includeTags != null ? includeTags : Collections.emptyList(); }
    public List<String> getExcludeTags() { return excludeTags != null ? excludeTags : Collections.emptyList(); }
    public String getIncludeWords() { return includeWords; }
    public String getExcludeWords() { return excludeWords; }
    public String getIncludeRegEx() { return includeRegEx; }
    public String getExcludeRegEx() { return excludeRegEx; }
    public boolean isShouldPlaySound() { return shouldPlaySound; }
    public boolean isShouldChangeBackground() { return shouldChangeBackground; }
    public int getBackgroundColor() { return backgroundColor; }
    public boolean isShouldFilterTooltip() { return shouldFilterTooltip; }
    public boolean isCaseSensitive() { return caseSensitive; }
    public boolean isAdvanced() { return advanced; }

    public void setId(final String id) { this.id = id; }
    public void setTabId(final String tabId) { this.tabId = tabId; }
    public void setName(final String name) { this.name = name; }
    public void setIncludeTags(final List<String> tags) { this.includeTags = tags; }
    public void setExcludeTags(final List<String> tags) { this.excludeTags = tags; }
    public void setIncludeWords(final String words) { this.includeWords = words; }
    public void setExcludeWords(final String words) { this.excludeWords = words; }
    public void setIncludeRegEx(final String regex) { this.includeRegEx = regex; }
    public void setExcludeRegEx(final String regex) { this.excludeRegEx = regex; }
    public void setShouldPlaySound(final boolean play) { this.shouldPlaySound = play; }
    public void setShouldChangeBackground(final boolean change) { this.shouldChangeBackground = change; }
    public void setBackgroundColor(final int color) { this.backgroundColor = color; }
    public void setShouldFilterTooltip(final boolean filter) { this.shouldFilterTooltip = filter; }
    public void setCaseSensitive(final boolean cs) { this.caseSensitive = cs; }
    public void setAdvanced(final boolean adv) { this.advanced = adv; }
}
