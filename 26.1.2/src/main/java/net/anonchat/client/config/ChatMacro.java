package net.anonchat.client.config;

import com.google.gson.annotations.SerializedName;

public class ChatMacro {

    @SerializedName("name") private String name = "";
    @SerializedName("keyCode") private int keyCode = -1;
    @SerializedName("text") private String text = "";
    @SerializedName("command") private boolean command = false;

    public ChatMacro() {}
    public ChatMacro(final String name, final int keyCode, final String text) {
        this.name = name; this.keyCode = keyCode; this.text = text;
    }

    public String getName() { return name; }
    public int getKeyCode() { return keyCode; }
    public String getText() { return text; }
    public boolean isCommand() { return command; }

    public void setName(final String name) { this.name = name; }
    public void setKeyCode(final int keyCode) { this.keyCode = keyCode; }
    public void setText(final String text) { this.text = text; }
    public void setCommand(final boolean command) { this.command = command; }
}
