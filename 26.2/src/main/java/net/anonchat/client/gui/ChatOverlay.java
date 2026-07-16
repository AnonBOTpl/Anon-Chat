package net.anonchat.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.anonchat.client.chat.ChatRouter;
import net.anonchat.client.chat.ChatWindow;
import net.anonchat.client.config.ChatConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;

import org.lwjgl.glfw.GLFW;

public final class ChatOverlay {

    private static ChatOverlay instance;

    private final List<ChatWindowWidget> windows = new ArrayList<>();
    private boolean registered = false;
    private boolean chatFocused = false;

    public boolean isChatFocused() { return chatFocused; }

    private ChatWindowWidget draggedWindow;
    private ChatWindowWidget resizeWindow;
    private boolean wasLeftPressed = false;
    private double dragStartMouseX;
    private double dragStartMouseY;
    private int dragStartWinX;
    private int dragStartWinY;

    private double mouseX;
    private double mouseY;
    private double scrollAccumulator = 0.0;
    private boolean scrollHookRegistered = false;

    private ChatOverlay() {}

    public static ChatOverlay getInstance() {
        if (instance == null) instance = new ChatOverlay();
        return instance;
    }

    public void register() {
        if (registered) return;
        registered = true;

        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("anonchat", "overlay"),
            new HudElement() {
                @Override
                public void extractRenderState(GuiGraphicsExtractor extractor, DeltaTracker tickCounter) {
                    ChatOverlay.this.render(extractor);
                }
            }
        );

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            ensureScrollHook();
            updateMouseCoords();
            handleMouse();
            handleScroll();
        });

        ensureScrollHook();

        System.out.println("[AnonChat] Overlay registered (render + mouse + scroll)");
    }

    private void ensureScrollHook() {
        if (scrollHookRegistered) return;
        final Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null || mc.getWindow().handle() == 0) return;

        final long handle = mc.getWindow().handle();
        final var prev = GLFW.glfwSetScrollCallback(handle, null);
        GLFW.glfwSetScrollCallback(handle, (window, xoffset, yoffset) -> {
            scrollAccumulator += yoffset;
            if (prev != null) prev.invoke(window, xoffset, yoffset);
        });
        scrollHookRegistered = true;
    }

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

    private void render(final GuiGraphicsExtractor context) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!isOverlayVisible(mc)) return;
        if (mc.gui.hud.isHidden()) return;

        this.chatFocused = mc.gui.screen() instanceof ChatScreen;

        for (final ChatWindowWidget window : windows) {
            window.render(context, chatFocused);
        }
    }

    private static boolean isOverlayVisible(final Minecraft mc) {
        return mc.gui.screen() == null || mc.gui.screen() instanceof ChatScreen;
    }

    private void updateMouseCoords() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!isOverlayVisible(mc)) return;
        if (mc.gui.hud.isHidden()) return;

        final var window = mc.getWindow();
        if (window == null || window.getWidth() == 0 || window.getHeight() == 0) return;
        if (window.handle() == 0) return;

        final double[] rawX = new double[1];
        final double[] rawY = new double[1];
        GLFW.glfwGetCursorPos(window.handle(), rawX, rawY);
        final double scaleX = (double) window.getGuiScaledWidth() / window.getWidth();
        final double scaleY = (double) window.getGuiScaledHeight() / window.getHeight();

        this.mouseX = rawX[0] * scaleX;
        this.mouseY = rawY[0] * scaleY;
    }

    private void handleMouse() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!isOverlayVisible(mc)) return;
        if (mc.gui.hud.isHidden()) return;

        final long handle = mc.getWindow().handle();
        final boolean leftPressed = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        for (final ChatWindowWidget w : windows) {
            w.updateHover(mouseX, mouseY);
        }

        if (leftPressed && !wasLeftPressed) {
            for (final ChatWindowWidget w : windows) {
                if (w.isDropdownOpen() && !w.isInside(mouseX, mouseY) && !w.isMouseOverDropdown(mouseX, mouseY)) {
                    w.closeMenus();
                }
            }
        }

        if (leftPressed && !wasLeftPressed) {
            draggedWindow = null;
            resizeWindow = null;
            for (int i = windows.size() - 1; i >= 0; i--) {
                final ChatWindowWidget w = windows.get(i);
                if (!w.isInside(mouseX, mouseY) && !w.isMouseOverDropdown(mouseX, mouseY)) continue;

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

        for (final ChatWindowWidget w : windows) {
            w.setHoveredEdge(w.getResizeEdge(mouseX, mouseY));
        }

        if (leftPressed && draggedWindow != null) {
            final int dx = (int) Math.round(mouseX - dragStartMouseX);
            final int dy = (int) Math.round(mouseY - dragStartMouseY);
            draggedWindow.setPosition(dragStartWinX + dx, dragStartWinY + dy);
        }

        if (leftPressed && resizeWindow != null) {
            resizeWindow.updateResize(mouseX, mouseY);
        }

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

    private void handleScroll() {
        final double delta = scrollAccumulator;
        scrollAccumulator = 0;
        if (delta == 0) return;
        if (windows.isEmpty()) return;

        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!isOverlayVisible(mc)) return;
        if (mc.gui.hud.isHidden()) return;

        for (int i = windows.size() - 1; i >= 0; i--) {
            final ChatWindowWidget w = windows.get(i);
            if (w.isInside(mouseX, mouseY) && !w.isDropdownOpen()) {
                w.scrollMessages(delta);
                break;
            }
        }
    }

    public List<ChatWindowWidget> getWindows() { return windows; }
    public int getWindowCount() { return windows.size(); }
    public boolean isActive() { return !windows.isEmpty(); }
}
