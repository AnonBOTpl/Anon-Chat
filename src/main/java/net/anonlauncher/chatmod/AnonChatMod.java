package net.anonlauncher.chatmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.anonchat.client.chat.ChatMessage;
import net.anonchat.client.chat.ChatRouter;
import net.anonchat.client.chatlog.ChatLogger;
import net.anonchat.client.config.ChatConfig;
import net.anonchat.client.config.ChatMacro;
import net.anonchat.client.gui.ChatOverlay;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AnonChat Mod — advanced chat filtering using Fabric API events.
 *
 * <p>Instead of ASM bytecode manipulation, this mod uses the official
 * {@link ClientReceiveMessageEvents} API from Fabric, which provides
 * hooks for all incoming chat messages without any bytecode hacks.
 */

public final class AnonChatMod implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("AnonChat");

    private static ChatRouter router;

    /** Tracks currently-held macro keys for edge-triggered sending. */
    private static final Set<Integer> pressedMacroKeys = new HashSet<>();

    /**
     * @return the shared ChatRouter instance
     */
    public static ChatRouter getRouter() {
        return router;
    }

    /**
     * Rebuild the router and overlay from the current config. Call this after
     * any change to windows/tabs/filters made at runtime so the live message
     * router and the HUD stay in sync.
     */
    public static void reloadEverything() {
        if (router != null) {
            router.reload();
            ChatOverlay.getInstance().reloadFromRouter(router);
        }
    }

    /**
     * Replace control characters that the Minecraft font cannot render (and
     * that would otherwise break word-wrapping) with spaces.
     */
    private static String sanitize(final String raw) {
        if (raw == null) return raw;
        return raw.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
    }

    @Override
    public void onInitializeClient() {
        System.out.println("[AnonChat] Initializing...");

        // Load config from disk
        try {
            ChatConfig.initialize();
            System.out.println("[AnonChat] Config loaded from: " + ChatConfig.getConfigPath());
            // Initialise daily chat logger
            if (ChatConfig.getConfigPath() != null) {
                ChatLogger.initialize(Path.of(ChatConfig.getConfigPath()).getParent());
            }
        } catch (final Exception e) {
            System.err.println("[AnonChat] Failed to load config: " + e.getMessage());
        }

        // Create and initialise the router
        router = new ChatRouter();
        router.loadConfiguration();

        // ── Initialise GUI overlay ────────────────────────────────
        final ChatOverlay overlay = ChatOverlay.getInstance();
        overlay.reloadFromRouter(router);
        overlay.register();
        System.out.println("[AnonChat] Overlay initialised with " + overlay.getWindowCount() + " window(s)");

        // ── Register ALLOW_CHAT event ──────────────────────────────
        // Fires for every player chat message.
        // Always block from vanilla chat history — our overlay handles display.
        // Vanilla ChatScreen (input field) works independently of this callback.
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMsg, sender, params, timestamp) -> {
            if (message == null) return false;

            final String text = message.getString();
            if (text == null || text.isEmpty()) return false;

            final String clean = sanitize(text);
            LOGGER.info("[CHAT] {}", clean);

            if (router != null) {
                final ChatMessage chatMsg = new ChatMessage(message, clean,
                    sender != null ? sender.getId() : null);
                router.dispatchMessage(chatMsg);
            }

            return false;
        });

        // ── Register ALLOW_GAME event ──────────────────────────────
        // Fires for game messages (e.g. "joined the game").
        ClientReceiveMessageEvents.ALLOW_GAME.register((Text message, boolean isOverlay) -> {
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
        // Fire bound macros when their key is pressed during normal gameplay.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            final ChatConfig config = ChatConfig.getInstance();
            if (config == null) return;
            final List<ChatMacro> macros = config.getMacros();
            if (macros.isEmpty()) return;
            final MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getWindow() == null) return;
            final long handle = mc.getWindow().getHandle();
            final boolean canSend = mc.currentScreen == null
                && mc.player != null && mc.getNetworkHandler() != null;
            for (final ChatMacro m : macros) {
                final int code = m.getKeyCode();
                if (code < 0) continue;
                final boolean down = InputUtil.isKeyPressed(handle, code);
                if (down && !pressedMacroKeys.contains(code)) {
                    if (canSend && !m.getText().isEmpty()) {
                        final String t = m.getText();
                        if (m.isCommand() || t.startsWith("/")) {
                            final String cmd = t.startsWith("/") ? t.substring(1) : t;
                            mc.player.networkHandler.sendCommand(cmd);
                        } else {
                            mc.getNetworkHandler().sendChatMessage(t);
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
