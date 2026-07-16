package net.anonchat.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.anonchat.client.chat.ChatRouter;
import net.anonchat.client.chat.ChatWindow;
import net.anonchat.client.config.ChatConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;

import org.lwjgl.glfw.GLFW;

/**
 * Main chat overlay — renders custom chat windows on the HUD.
 *
 * <p>Handles mouse input for dragging windows, switching tabs,
 * scrolling messages, and hover effects.
 *
 * <p>Rendering via {@link HudRenderCallback.EVENT} (no mixins needed).
 * Mouse + scroll handling via {@link ClientTickEvents.START_CLIENT_TICK}
 * and GLFW callbacks. Keyboard input and configuration handled by
 * vanilla {@link ChatScreen} and ModMenu {@link AnonChatConfigScreen}.
 */
public final class ChatOverlay {

    private static ChatOverlay instance;

    private final List<ChatWindowWidget> windows = new ArrayList<>();
    private boolean registered = false;

    // ── Focus state (chat open = ChatScreen visible) ──────────────────────

    private boolean chatFocused = false;

    public boolean isChatFocused() { return chatFocused; }

    // ── Drag / Resize state ────────────────────────────────────────────────

    private ChatWindowWidget draggedWindow;
    private ChatWindowWidget resizeWindow;
    private boolean wasLeftPressed = false;
    private double dragStartMouseX;
    private double dragStartMouseY;
    private int dragStartWinX;
    private int dragStartWinY;

    // ── Mouse coords (updated every tick) ─────────────────────────────────

    private double mouseX;
    private double mouseY;

    // ── Scroll accumulator ────────────────────────────────────────────────

    private double scrollAccumulator = 0.0;

    // ── GLFW scroll hook (registered lazily once the window is ready) ──

    private boolean scrollHookRegistered = false;

    private ChatOverlay() {
    }

    public static ChatOverlay getInstance() {
        if (instance == null) instance = new ChatOverlay();
        return instance;
    }

    public void register() {
        if (registered) return;
        registered = true;

        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            ensureScrollHook();
            updateMouseCoords();
            handleMouse();
            handleScroll();
        });

        // Try to register the GLFW scroll callback immediately; if the window
        // handle is not ready yet, ensureScrollHook() will retry every tick.
        ensureScrollHook();

        System.out.println("[AnonChat] Overlay registered (render + mouse + scroll)");
    }

    /**
     * Register the global GLFW scroll callback for the Minecraft window, but
     * only once the window handle is actually available. Retrying from the
     * client tick avoids the race where {@code onInitializeClient()} runs before
     * the window exists — which would otherwise leave scroll permanently dead.
     */
    private void ensureScrollHook() {
        if (scrollHookRegistered) return;
        final MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() == null || mc.getWindow().getHandle() == 0) return;

        final long handle = mc.getWindow().getHandle();
        final var prev = GLFW.glfwSetScrollCallback(handle, null);
        GLFW.glfwSetScrollCallback(handle, (window, xoffset, yoffset) -> {
            scrollAccumulator += yoffset;
            if (prev != null) prev.invoke(window, xoffset, yoffset);
        });
        scrollHookRegistered = true;
    }

    // ── Config reload ──────────────────────────────────────────────────────

    public void reloadFromRouter(final ChatRouter router) {
        windows.clear();
        final List<? extends ChatWindow> chatWindows = router.getWindows();
        if (chatWindows == null || chatWindows.isEmpty()) return;

        for (final ChatWindow chatWindow : chatWindows) {
            final var settings = chatWindow.getSettings();
            if (settings == null) continue;
            windows.add(new ChatWindowWidget(chatWindow, settings, this));
        }
        System.out.println("[AnonChat] Overlay reloaded: " + windows.size() + " windows");
    }

    // ── Rendering ──────────────────────────────────────────────────────────

    private void render(final DrawContext context) {
        final MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        if (!isOverlayVisible(mc)) return;
        if (mc.options.hudHidden) return;

        this.chatFocused = mc.currentScreen instanceof ChatScreen;

        for (final ChatWindowWidget window : windows) {
            window.render(context, chatFocused);
        }
    }

    /**
     * The chat overlay is shown during normal gameplay (no screen open) and
     * while the chat is focused (ChatScreen). It is intentionally hidden behind
     * other GUIs (inventory, furnace, server screens, ...).
     */
    private static boolean isOverlayVisible(final MinecraftClient mc) {
        return mc.currentScreen == null || mc.currentScreen instanceof ChatScreen;
    }

    // ── Mouse coordinate tracking ─────────────────────────────────────────

    private void updateMouseCoords() {
        final MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        if (!isOverlayVisible(mc)) return;
        if (mc.options.hudHidden) return;

        final var window = mc.getWindow();
        if (window == null || window.getWidth() == 0 || window.getHeight() == 0) return;
        if (window.getHandle() == 0) return;

        final double[] rawX = new double[1];
        final double[] rawY = new double[1];
        GLFW.glfwGetCursorPos(window.getHandle(), rawX, rawY);
        final double scaleX = (double) window.getScaledWidth() / window.getWidth();
        final double scaleY = (double) window.getScaledHeight() / window.getHeight();

        this.mouseX = rawX[0] * scaleX;
        this.mouseY = rawY[0] * scaleY;
    }

    // ── Mouse input ────────────────────────────────────────────────────────

    private void handleMouse() {
        final MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        if (!isOverlayVisible(mc)) return;
        if (mc.options.hudHidden) return;

        final long handle = mc.getWindow().getHandle();
        final boolean leftPressed = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        // Update hover on all windows
        for (final ChatWindowWidget w : windows) {
            w.updateHover(mouseX, mouseY);
        }

        // Close dropdowns when clicking outside any window
        if (leftPressed && !wasLeftPressed) {
            for (final ChatWindowWidget w : windows) {
                if (w.isDropdownOpen() && !w.isInside(mouseX, mouseY) && !w.isMouseOverDropdown(mouseX, mouseY)) {
                    w.closeMenus();
                }
            }
        }

        // Left button just pressed
        if (leftPressed && !wasLeftPressed) {
            draggedWindow = null;
            resizeWindow = null;
            for (int i = windows.size() - 1; i >= 0; i--) {
                final ChatWindowWidget w = windows.get(i);
                if (!w.isInside(mouseX, mouseY) && !w.isMouseOverDropdown(mouseX, mouseY)) continue;

                // Check resize edge first (before hamburger/tab/titlebar)
                final int edge = w.getResizeEdge(mouseX, mouseY);
                if (edge != 0 && chatFocused) {
                    resizeWindow = w;
                    w.startResize(edge, mouseX, mouseY);
                    windows.remove(i);
                    windows.add(w);
                    break;
                }

                if (w.clickHamburger(mouseX, mouseY)) break;
                if (w.clickTab(mouseX, mouseY)) break;

                if (w.isInTitleBar(mouseX, mouseY) && !w.isMouseOverDropdown(mouseX, mouseY)) {
                    draggedWindow = w;
                    dragStartMouseX = mouseX;
                    dragStartMouseY = mouseY;
                    dragStartWinX = w.getX();
                    dragStartWinY = w.getY();
                    windows.remove(i);
                    windows.add(w);
                    break;
                }

                if (w.clickDropdown(mouseX, mouseY)) break;
                w.clickMessages(mouseX, mouseY);
                break;
            }
        }

        // Update resize-edge hover indicator for every window
        for (final ChatWindowWidget w : windows) {
            w.setHoveredEdge(w.getResizeEdge(mouseX, mouseY));
        }

        // Dragging
        if (leftPressed && draggedWindow != null) {
            final int dx = (int) Math.round(mouseX - dragStartMouseX);
            final int dy = (int) Math.round(mouseY - dragStartMouseY);
            draggedWindow.setPosition(dragStartWinX + dx, dragStartWinY + dy);
        }

        // Resizing
        if (leftPressed && resizeWindow != null) {
            resizeWindow.updateResize(mouseX, mouseY);
        }

        // Left released → save config
        if (!leftPressed && wasLeftPressed) {
            if (draggedWindow != null) {
                draggedWindow.commitPosition();
                draggedWindow = null;
                try { ChatConfig.getInstance().save(); } catch (final Exception ignored) {}
            }
            if (resizeWindow != null) {
                resizeWindow.commitResize();
                resizeWindow = null;
                try { ChatConfig.getInstance().save(); } catch (final Exception ignored) {}
            }
        }

        wasLeftPressed = leftPressed;
    }

    // ── Scroll handling ───────────────────────────────────────────────────

    private void handleScroll() {
        final double delta = scrollAccumulator;
        scrollAccumulator = 0;
        if (delta == 0) return;
        if (windows.isEmpty()) return;

        final MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        if (!isOverlayVisible(mc)) return;
        if (mc.options.hudHidden) return;

        for (int i = windows.size() - 1; i >= 0; i--) {
            final ChatWindowWidget w = windows.get(i);
            if (w.isInside(mouseX, mouseY) && !w.isDropdownOpen()) {
                w.scrollMessages(delta);
                break;
            }
        }
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    public List<ChatWindowWidget> getWindows() { return windows; }
    public int getWindowCount() { return windows.size(); }
    public boolean isActive() { return !windows.isEmpty(); }
}
