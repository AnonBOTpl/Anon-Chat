package net.anonchat.client.chat;

import net.minecraft.network.chat.Component;
import java.util.HashMap;
import java.util.Map;

public class ChatMessageWrapper {

    private final ChatMessage chatMessage;
    private final Map<String, Object> metadata = new HashMap<>();
    private boolean hidden;

    public ChatMessageWrapper(final ChatMessage chatMessage) {
        this.chatMessage = chatMessage;
    }

    public ChatMessageWrapper(final String text) {
        this.chatMessage = new ChatMessage(Component.literal(text), text);
    }

    public static ChatMessageWrapper of(final ChatMessage msg) {
        return new ChatMessageWrapper(msg);
    }

    public static ChatMessageWrapper of(final String text) {
        return new ChatMessageWrapper(text);
    }

    public ChatMessage getChatMessage() { return chatMessage; }
    public long getTimestamp() { return chatMessage.getTimestamp(); }
    public Component getComponent() { return chatMessage.getComponent(); }
    public String getPlainText() { return chatMessage.getPlainText(); }

    public Map<String, Object> getMetadata() { return metadata; }

    @SuppressWarnings("unchecked")
    public <T> T getMetadata(final String key) {
        return (T) metadata.get(key);
    }

    public void setMetadata(final String key, final Object value) {
        metadata.put(key, value);
    }

    private int repeatCount;

    public int getRepeatCount() { return repeatCount; }
    public void setRepeatCount(final int count) { this.repeatCount = count; }
    public void incrementRepeat() { this.repeatCount++; }

    public boolean isHidden() { return hidden; }
    public void setHidden(final boolean hidden) { this.hidden = hidden; }
    public void hide() { this.hidden = true; }
    public boolean isVisible() { return !hidden; }
}
