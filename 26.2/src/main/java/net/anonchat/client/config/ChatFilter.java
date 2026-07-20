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

        // 1. Check include criteria first (include wins over exclude)
        if (hasInclude) {
            boolean includeMatched = checkCriteria(includeTags, includeWords, includeRegEx, text);
            if (includeMatched) return true; // include always wins
            return false; // include exists but didn't match → reject
        }

        // 2. Exclude-only filter (no include criteria) — matches for routing purposes
        if (hasExclude && checkCriteria(excludeTags, excludeWords, excludeRegEx, text)) {
            return true;
        }

        return false;
    }

    private boolean checkCriteria(final List<String> tags, final String words, final String regex, final String text) {
        if (tags != null && !tags.isEmpty()) {
            for (final String tag : tags) {
                final String search = caseSensitive ? tag : tag.toUpperCase();
                if (!search.isEmpty() && text.contains(search)) return true;
            }
        }
        if (words != null && !words.isEmpty()) {
            final String w = caseSensitive ? words : words.toUpperCase();
            for (final String word : w.split(";")) {
                final String trimmed = word.trim();
                if (!trimmed.isEmpty() && text.contains(trimmed)) return true;
            }
        }
        if (regex != null && !regex.isEmpty()) {
            try {
                final Pattern pattern = caseSensitive
                    ? Pattern.compile(regex)
                    : Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
                if (pattern.matcher(text).find()) return true;
            } catch (final PatternSyntaxException ignored) {}
        }
        return false;
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
