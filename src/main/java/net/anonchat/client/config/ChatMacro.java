package net.anonchat.client.config;

import com.google.gson.annotations.SerializedName;

/**
 * A user-defined chat macro (Autotext): a named action bound to a key that
 * sends a fixed text or command to chat when the key is pressed in-game.
 */
public class ChatMacro {

    @SerializedName("name")
    private String name = "";

    /** GLFW key code, or -1 when unbound. */
    @SerializedName("keyCode")
    private int keyCode = -1;

    /** Text or command (starting with {@code /}) to send. */
    @SerializedName("text")
    private String text = "";

    /** When true, send as a command (stripped of a leading {@code /}). */
    @SerializedName("command")
    private boolean command = false;

    public ChatMacro() {}

    public ChatMacro(final String name, final int keyCode, final String text) {
        this.name = name;
        this.keyCode = keyCode;
        this.text = text;
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
