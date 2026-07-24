package net.anonchat.client.chat;

import net.anonchat.client.config.ChatFilter;
import net.anonchat.client.config.ChatConfig;
import net.anonchat.client.config.ChatTabProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import java.util.ArrayList;
import java.util.List;

public class ChatTabImpl extends ChatTab {

    private static final String[] SOUND_IDS = {"pling", "bell", "chime", "xporb", "click", "none"};

    private final List<ChatMessageWrapper> messages = new ArrayList<>();
    private int counter;

    public ChatTabImpl(final ChatWindow window, final net.anonchat.client.config.ChatTabConfig config) {
        super(window, config);
    }

    @Override
    public boolean handleInput(final ChatMessageWrapper message) {
        return handleInput(message, false);
    }

    public boolean handleInput(final ChatMessageWrapper message, final boolean suppressIfServerCatchAll) {
        if (message == null) return false;
        if (suppressIfServerCatchAll) return false;

        final String text = message.getPlainText();
        if (text == null || text.isEmpty()) return false;

        // Ping detection — set metadata early so it persists through filter processing
        final Minecraft mc = Minecraft.getInstance();
        final String playerName = mc.player != null ? mc.player.getName().getString() : null;
        if (playerName != null && !playerName.isEmpty()) {
            if (text.toLowerCase(java.util.Locale.ROOT).contains(playerName.toLowerCase(java.util.Locale.ROOT))
                && !text.toLowerCase(java.util.Locale.ROOT).startsWith("<" + playerName.toLowerCase(java.util.Locale.ROOT) + ">")) {
                message.setMetadata("ping", true);
            }
        }

        final ChatTabProperties props = getConfig().getProperties();

        if (props != null && props.isAntiChatClear() && text.trim().isEmpty()) {
            return false;
        }

        final List<ChatFilter> filters = props != null ? props.getFilters() : null;
        final boolean hasFilters = filters != null && !filters.isEmpty();
        boolean filterMatched = false;

        if (hasFilters) {
            for (final ChatFilter filter : filters) {
                if (filter == null) continue;
                if (filter.matches(text)) {
                    if (filter.hasIncludeCriteria()) {
                        filterMatched = true;
                    }
                    if (filter.isShouldPlaySound()) {
                        playNotificationSound();
                    }
                    if (filter.isShouldChangeBackground()) {
                        message.setMetadata("custom_background", filter.getBackgroundColor());
                    }
                    if (filter.isHideMessage()) {
                        message.hide();
                    }
                    break;
                }
            }
        }

        // Play ping sound if message is visible and ping is enabled
        if (Boolean.TRUE.equals(message.getMetadata("ping"))
            && !message.isHidden()
            && ChatConfig.getInstance() != null
            && ChatConfig.getInstance().getFontSettings().isPingEnabled()) {
            playNotificationSound();
        }

        if (!getConfig().isServerTab() && !filterMatched) {
            return false;
        }

        if (props != null && props.isCombineChatMessages() && !messages.isEmpty()) {
            final ChatMessageWrapper latest = messages.get(0);
            if (latest != null && text.equals(latest.getPlainText())) {
                latest.incrementRepeat();
                counter++;
                unread++;
                return true;
            }
        }

        final int limit = Math.max(1, props != null ? props.getChatLimit() : 100);
        while (messages.size() >= limit && !messages.isEmpty()) {
            messages.remove(messages.size() - 1);
        }

        messages.add(0, message);
        counter++;
        unread++;

        return true;
    }

    public List<ChatMessageWrapper> getMessages() { return messages; }
    public int getCounter() { return counter; }

    public boolean removeMessage(final java.util.UUID messageId) {
        return messages.removeIf(m -> m.getChatMessage().getMessageId().equals(messageId));
    }

    private void playNotificationSound() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        final String soundId = ChatConfig.getInstance() != null
            ? ChatConfig.getInstance().getFontSettings().getPingSound()
            : "pling";
        final SoundEvent sound = getSoundById(soundId);
        if (sound != null) {
            mc.player.playSound(sound, 1.0f, 1.0f);
        }
    }

    private static SoundEvent getSoundById(final String id) {
        return switch (id) {
            case "pling" -> SoundEvents.NOTE_BLOCK_CHIME.value();
            case "bell"  -> SoundEvents.NOTE_BLOCK_BELL.value();
            case "chime" -> SoundEvents.AMETHYST_BLOCK_CHIME;
            case "xporb" -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case "click" -> SoundEvents.UI_BUTTON_CLICK.value();
            default      -> null;
        };
    }

    public static String getSoundDisplayName(final String id) {
        return switch (id) {
            case "pling" -> "\u266A Ding";
            case "bell"  -> "\u237F Bell";
            case "chime" -> "\u266B Chime";
            case "xporb" -> "\u2726 XP Orb";
            case "click" -> "\u25A1 Click";
            default      -> "\u2715 Off";
        };
    }

    public static int getSoundIndex(final String id) {
        for (int i = 0; i < SOUND_IDS.length; i++) {
            if (SOUND_IDS[i].equals(id)) return i;
        }
        return 0;
    }

    public static String nextSound(final String current) {
        final int idx = getSoundIndex(current);
        return SOUND_IDS[(idx + 1) % SOUND_IDS.length];
    }

    public static String prevSound(final String current) {
        final int idx = getSoundIndex(current);
        return SOUND_IDS[(idx - 1 + SOUND_IDS.length) % SOUND_IDS.length];
    }
}
