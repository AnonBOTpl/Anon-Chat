package net.anonchat.client.chat;

import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

/**
 * Wraps a ChatMessage with per-tab metadata, visibility and render cache.
 */

public class ChatMessageWrapper {

    private final ChatMessage chatMessage;
    private final Map<String, Object> metadata = new HashMap<>();
    private boolean hidden;

    public ChatMessageWrapper(final ChatMessage chatMessage) {
        this.chatMessage = chatMessage;
    }

    public ChatMessageWrapper(final String text) {
        this.chatMessage = new ChatMessage(Text.literal(text), text);
    }

    /**
     * Factory: wrap an existing ChatMessage.
     */
    public static ChatMessageWrapper of(final ChatMessage msg) {
        return new ChatMessageWrapper(msg);
    }

    /**
     * Factory: create from plain text (for programmatic messages).
     */
    public static ChatMessageWrapper of(final String text) {
        return new ChatMessageWrapper(text);
    }


    // ── Delegates ───────────────────────────────────────────────────

    public ChatMessage getChatMessage() { return chatMessage; }
    public long getTimestamp() { return chatMessage.getTimestamp(); }
    public Text getComponent() { return chatMessage.getComponent(); }
    public String getPlainText() { return chatMessage.getPlainText(); }


    // ── Metadata ────────────────────────────────────────────────────

    public Map<String, Object> getMetadata() { return metadata; }

    @SuppressWarnings("unchecked")
    public <T> T getMetadata(final String key) {
        return (T) metadata.get(key);
    }

    public void setMetadata(final String key, final Object value) {
        metadata.put(key, value);
    }


    // ── Repeat count (Combine Equal Messages) ──────────────────────

    private int repeatCount;

    public int getRepeatCount() { return repeatCount; }
    public void setRepeatCount(final int count) { this.repeatCount = count; }
    public void incrementRepeat() { this.repeatCount++; }

    // ── Visibility ──────────────────────────────────────────────────

    public boolean isHidden() { return hidden; }
    public void setHidden(final boolean hidden) { this.hidden = hidden; }
    public void hide() { this.hidden = true; }
    public boolean isVisible() { return !hidden; }
}
