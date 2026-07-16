package net.anonchat.client.chat;

import net.minecraft.network.chat.Component;
import java.util.UUID;

public class ChatMessage {

    private final UUID messageId;
    private final long timestamp;
    private final Component component;
    private final String plainText;
    private final UUID senderUniqueId;

    public ChatMessage(final Component component, final String plainText, final UUID senderUniqueId) {
        this.messageId = UUID.randomUUID();
        this.timestamp = System.currentTimeMillis();
        this.component = component;
        this.plainText = plainText;
        this.senderUniqueId = senderUniqueId;
    }

    public ChatMessage(final Component component, final String plainText) {
        this(component, plainText, null);
    }

    public UUID getMessageId() { return messageId; }
    public long getTimestamp() { return timestamp; }
    public Component getComponent() { return component; }
    public String getPlainText() { return plainText; }
    public UUID getSenderUniqueId() { return senderUniqueId; }
}
