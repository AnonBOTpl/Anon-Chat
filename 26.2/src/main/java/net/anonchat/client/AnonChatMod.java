package net.anonchat.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.anonchat.client.chat.ChatMessage;
import net.anonchat.client.chat.ChatRouter;
import net.anonchat.client.chatlog.ChatLogger;
import net.anonchat.client.config.ChatConfig;
import net.anonchat.client.config.ChatMacro;
import net.anonchat.client.gui.ChatOverlay;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AnonChatMod implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("AnonChat");

    private static ChatRouter router;

    private static final Set<Integer> pressedMacroKeys = new HashSet<>();

    public static ChatRouter getRouter() {
        return router;
    }

    public static void reloadEverything() {
        if (router != null) {
            router.reload();
            ChatOverlay.getInstance().reloadFromRouter(router);
        }
    }

    private static String sanitize(final String raw) {
        if (raw == null) return raw;
        return raw.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
    }

    @Override
    public void onInitializeClient() {
        System.out.println("[AnonChat] Initializing...");

        try {
            ChatConfig.initialize();
            System.out.println("[AnonChat] Config loaded from: " + ChatConfig.getConfigPath());
            // Initialise daily chat logger using the same config directory
            if (ChatConfig.getConfigPath() != null) {
                ChatLogger.initialize(Path.of(ChatConfig.getConfigPath()).getParent());
            }
        } catch (final Exception e) {
            System.err.println("[AnonChat] Failed to load config: " + e.getMessage());
        }

        router = new ChatRouter();
        router.loadConfiguration();

        final ChatOverlay overlay = ChatOverlay.getInstance();
        overlay.reloadFromRouter(router);
        overlay.register();
        System.out.println("[AnonChat] Overlay initialised with " + overlay.getWindowCount() + " window(s)");

        // ── ALLOW_CHAT event ────────────────────────────────────────
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMsg, sender, params, timestamp) -> {
            if (message == null) return false;

            final String text = message.getString();
            if (text == null || text.isEmpty()) return false;

            final String clean = sanitize(text);
            LOGGER.info("[CHAT] {}", clean);

            if (router != null) {
                final ChatMessage chatMsg = new ChatMessage(message, clean,
                    sender != null ? sender.id() : null);
                router.dispatchMessage(chatMsg);
            }

            return false;
        });

        // ── ALLOW_GAME event ────────────────────────────────────────
        ClientReceiveMessageEvents.ALLOW_GAME.register((Component message, boolean isOverlay) -> {
            if (message == null) return false;
            final String text = message.getString();
            if (text == null || text.isEmpty()) return false;

            final String clean = sanitize(text);
            LOGGER.info("[GAME] {}", clean);

            if (router != null) {
                final ChatMessage chatMsg = new ChatMessage(message, clean);
                router.dispatchMessage(chatMsg);
            }

            return false;
        });

        // ── Autotext (Macros) ──────────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            final ChatConfig config = ChatConfig.getInstance();
            if (config == null) return;
            final List<ChatMacro> macros = config.getMacros();
            if (macros.isEmpty()) return;
            final Minecraft mc = Minecraft.getInstance();
            if (mc.getWindow() == null) return;
            final long handle = mc.getWindow().handle();
            final boolean canSend = mc.gui.screen() == null
                && mc.player != null && mc.getConnection() != null;
            for (final ChatMacro m : macros) {
                final int code = m.getKeyCode();
                if (code < 0) continue;
                final boolean down = GLFW.glfwGetKey(handle, code) == GLFW.GLFW_PRESS;
                if (down && !pressedMacroKeys.contains(code)) {
                    if (canSend && !m.getText().isEmpty()) {
                        final String t = m.getText();
                        if (m.isCommand() || t.startsWith("/")) {
                            final String cmd = t.startsWith("/") ? t.substring(1) : t;
                            mc.player.connection.sendCommand(cmd);
                        } else {
                            mc.getConnection().sendChat(t);
                        }
                    }
                    pressedMacroKeys.add(code);
                } else if (!down) {
                    pressedMacroKeys.remove(code);
                }
            }
        });

        System.out.println("[AnonChat] Hook registered. Ready!");
    }
}
