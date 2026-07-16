package net.anonchat.client.config;

import com.google.gson.annotations.SerializedName;
import java.util.UUID;

public class ChatTabConfig {

    @SerializedName("uniqueId") private String uniqueId;
    @SerializedName("config") private ChatTabProperties properties;
    @SerializedName("type") private String type = "SERVER";
    @SerializedName("index") private int index;
    @SerializedName("configVersion") private int configVersion = 2;

    public ChatTabConfig() {
        this.uniqueId = UUID.randomUUID().toString();
        this.properties = new ChatTabProperties("");
    }

    public ChatTabConfig(final int index, final String type, final ChatTabProperties properties) {
        this.uniqueId = UUID.randomUUID().toString();
        this.index = index; this.type = type; this.properties = properties;
    }

    public static ChatTabConfig createServerTab() { return new ChatTabConfig(0, "SERVER", new ChatTabProperties("")); }
    public static ChatTabConfig createCustomTab(final String name) { return new ChatTabConfig(0, "CUSTOM", new ChatTabProperties(name)); }

    public String getUniqueId() { return uniqueId; }
    public ChatTabProperties getProperties() { return properties; }
    public String getType() { return type; }
    public int getIndex() { return index; }
    public boolean isServerTab() { return "SERVER".equalsIgnoreCase(type); }
    public boolean isCustomTab() { return "CUSTOM".equalsIgnoreCase(type); }

    public void setUniqueId(final String id) { this.uniqueId = id; }
    public void setProperties(final ChatTabProperties props) { this.properties = props; }
    public void setType(final String type) { this.type = type; }
    public void setIndex(final int index) { this.index = index; }
}
