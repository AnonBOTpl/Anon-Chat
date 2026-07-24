package net.anonchat.client.config;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Position, size and anchor settings of a single chat window.
 */

public class ChatWindowSettings {

    @SerializedName("x")
    private float x;

    @SerializedName("y")
    private float y;

    @SerializedName("width")
    private float width = 300f;

    @SerializedName("height")
    private float height = 150f;

    @SerializedName("verticalAnchor")
    private String verticalAnchor = "BOTTOM";

    @SerializedName("horizontalAnchor")
    private String horizontalAnchor = "LEFT";

    @SerializedName("boundsPosition")
    private String boundsPosition = "OUTSIDE";    @SerializedName("focusedTab") private int focusedTab;
    @SerializedName("positionLocked") private boolean positionLocked = false;
    @SerializedName("tabs") private List<ChatTabConfig> tabs;

    @SerializedName("configVersion")
    private int configVersion = 1;


    /**
     * Create a default main window with one SERVER tab.
     */
    public static ChatWindowSettings createDefault() {
        final ChatWindowSettings settings = new ChatWindowSettings();
        settings.x = 2f;
        settings.y = 40f;
        settings.width = 300f;
        settings.height = 150f;
        settings.verticalAnchor = "BOTTOM";
        settings.horizontalAnchor = "LEFT";
        settings.boundsPosition = "OUTSIDE";
        settings.tabs = new ArrayList<>();
        settings.tabs.add(ChatTabConfig.createServerTab());
        return settings;
    }

    /**
     * Create a secondary window positioned at the bottom-right.
     */
    public static ChatWindowSettings createSecondary(final String tabName) {
        final ChatWindowSettings settings = new ChatWindowSettings();
        settings.x = 2f;
        settings.y = 20f;
        settings.width = 200f;
        settings.height = 170f;
        settings.verticalAnchor = "BOTTOM";
        settings.horizontalAnchor = "RIGHT";
        settings.boundsPosition = "OUTSIDE";
        settings.tabs = new ArrayList<>();
        settings.tabs.add(ChatTabConfig.createCustomTab(tabName));
        return settings;
    }


    // ── Position helpers ────────────────────────────────────────────

    /**
     * Set the position using relative values.
     *
     * @param x  horizontal offset from anchor
     * @param y  vertical offset from anchor
     * @param horizontalAnchor  LEFT, CENTER or RIGHT
     * @param verticalAnchor    TOP, CENTER or BOTTOM
     */
    public void setPosition(final float x, final float y,
                            final String horizontalAnchor,
                            final String verticalAnchor) {
        this.x = x;
        this.y = y;
        this.horizontalAnchor = horizontalAnchor;
        this.verticalAnchor = verticalAnchor;
    }


    // ── Getters ─────────────────────────────────────────────────────

    public float getX() { return x; }
    public float getY() { return y; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }
    public String getVerticalAnchor() { return verticalAnchor; }
    public String getHorizontalAnchor() { return horizontalAnchor; }
    public String getBoundsPosition() { return boundsPosition; }    public int getFocusedTab() { return focusedTab; }
    public boolean isPositionLocked() { return positionLocked; }
    public void setPositionLocked(final boolean v) { this.positionLocked = v; }
    public List<ChatTabConfig> getTabs() {
        return tabs != null ? tabs : Collections.emptyList();
    }


    // ── Setters ─────────────────────────────────────────────────────

    public void setX(final float x) { this.x = x; }
    public void setY(final float y) { this.y = y; }
    public void setWidth(final float w) { this.width = w; }
    public void setHeight(final float h) { this.height = h; }
    public void setVerticalAnchor(final String anchor) { this.verticalAnchor = anchor; }
    public void setHorizontalAnchor(final String anchor) { this.horizontalAnchor = anchor; }
    public void setBoundsPosition(final String pos) { this.boundsPosition = pos; }
    public void setFocusedTab(final int index) { this.focusedTab = index; }
    public void setTabs(final List<ChatTabConfig> tabs) { this.tabs = tabs; }
}
