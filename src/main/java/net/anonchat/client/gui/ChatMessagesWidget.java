package net.anonchat.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.anonchat.client.chat.ChatMessageWrapper;
import net.anonchat.client.chat.ChatTabImpl;
import net.anonchat.client.chat.ChatWindow;
import net.anonchat.client.config.ChatConfig;
import net.anonchat.client.config.ChatConfig.FontSettings;
import net.anonchat.client.config.ChatTabProperties;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

public final class ChatMessagesWidget {

    private static final int SCROLLBAR_WIDTH = 3;
    private static final int SCROLLBAR_BG = 0x40FFFFFF;
    private static final int SCROLLBAR_FG = 0xAAFFFFFF;

    private final ChatWindow chatWindow;
    private int lineOffset = 0;
    private int lastTotalLines = 0;

    public ChatMessagesWidget(final ChatWindow chatWindow) {
        this.chatWindow = chatWindow;
    }

    private FontSettings fs() {
        return ChatConfig.getInstance() != null
            ? ChatConfig.getInstance().getFontSettings()
            : new ChatConfig.FontSettings(); // fallback defaults
    }

    public void render(
        final DrawContext context,
        final int areaX,
        final int areaY,
        final int areaWidth,
        final int areaHeight,
        final boolean focused
    ) {
        render(context, areaX, areaY, areaWidth, areaHeight, focused, true);
    }

    public void render(
        final DrawContext context,
        final int areaX,
        final int areaY,
        final int areaWidth,
        final int areaHeight,
        final boolean focused,
        final boolean drawBg
    ) {
        final ChatTabImpl activeTab = getActiveTabImpl();
        if (activeTab == null) return;

        final List<ChatMessageWrapper> messages = new ArrayList<>(activeTab.getMessages());
        if (messages.isEmpty()) return;

        final FontSettings fontSet = fs();
        final ChatTabProperties props = activeTab.getConfig().getProperties();

        // Per-tab font override or global defaults
        final boolean useOverride = props != null && props.isOverrideFont();
        final int lineSpacing = useOverride ? props.getLineSpacing() : fontSet.getLineSpacing();
        final int msgSpacing  = useOverride ? props.getMessageSpacing() : fontSet.getMessageSpacing();
        final int textOpacity = useOverride ? props.getTextOpacity() : fontSet.getTextOpacity();
        final String align    = useOverride ? props.getTextAlignment() : fontSet.getTextAlignment();
        final int leftMargin  = useOverride ? props.getLeftMargin() : fontSet.getLeftMargin();
        final boolean shadow  = useOverride ? props.isShadow() : fontSet.isShadow();
        final boolean pingEnabled = fontSet.isPingEnabled();
        final int pingColor = fontSet.getPingColor();

        final TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        final long timeoutMs = (props != null && props.getMessageTimeout() > 0)
            ? (long) props.getMessageTimeout() * 1000L : 0L;
        final long now = System.currentTimeMillis();

        final int textAlpha = (int) Math.round(0xFF * (textOpacity / 100.0));
        final int textColor = (textAlpha << 24) | 0xFFFFFF;
        final int shadowColor = (Math.min(255, textAlpha / 2) << 24) | 0x000000;

        // ── Wrap lines ───────────────────────────────────────────
        final List<List<OrderedText>> wrappedLines = new ArrayList<>(messages.size());
        int totalLines = 0;
        for (final ChatMessageWrapper wrapper : messages) {
            final boolean timedOut = !focused && timeoutMs > 0L
                && (now - wrapper.getTimestamp()) > timeoutMs;
            if (wrapper == null || wrapper.isHidden() || timedOut) {
                wrappedLines.add(Collections.emptyList());
                totalLines++;
                continue;
            }
            Text component = wrapper.getComponent();
            if (component == null) {
                wrappedLines.add(Collections.emptyList());
                totalLines++;
                continue;
            }
            final int repeat = wrapper.getRepeatCount();
            if (repeat > 0) {
                final Text prefix = Text.literal("[×" + (repeat + 1) + "] ");
                component = prefix.copy().append(component);
            }
            final List<OrderedText> lines = wrapper.getOrComputeLines(textRenderer, component, areaWidth - leftMargin - 2);
            wrappedLines.add(lines);
            totalLines += lines.size();
        }

        // ── Update scroll ────────────────────────────────────────
        if (this.lastTotalLines != totalLines) {
            if (this.lineOffset != 0) {
                this.lineOffset += totalLines - this.lastTotalLines;
            }
            this.lastTotalLines = totalLines;
        }

        final int maxVisibleLines = Math.max(1, areaHeight / lineSpacing);
        final int maxOffset = Math.max(0, totalLines - maxVisibleLines);
        lineOffset = Math.max(0, Math.min(lineOffset, maxOffset));

        // ── Render ───────────────────────────────────────────────
        int renderedLineIndex = 0;
        for (int mi = 0; mi < messages.size(); mi++) {
            final ChatMessageWrapper wrapper = messages.get(mi);
            final List<OrderedText> lines = wrappedLines.get(mi);

            if (lines.isEmpty()) {
                renderedLineIndex++;
                continue;
            }

            for (int li = lines.size() - 1; li >= 0; li--) {
                if (renderedLineIndex >= lineOffset
                    && renderedLineIndex < lineOffset + maxVisibleLines) {

                    // Base Y from bottom — each rendered line takes lineSpacing px
                    final int baseY = areaY + areaHeight
                        - (renderedLineIndex - lineOffset + 1) * lineSpacing;

                    // Add message spacing before the FIRST (topmost) line of each message
                    final boolean isFirstLineOfMessage = (li == lines.size() - 1);
                    final int extraSpacing = (isFirstLineOfMessage && mi > 0) ? msgSpacing : 0;
                    final int lineY = baseY - extraSpacing;

                    if (drawBg) {
                        final Object customBg = wrapper.getMetadata("custom_background");
                        if (customBg instanceof Integer) {
                            final int argb = (Integer) customBg;
                            final int lineBg = (Math.min(255, (argb >> 24) & 0xFF) << 24) | (argb & 0xFFFFFF);
                            context.fill(areaX, lineY, areaX + areaWidth - SCROLLBAR_WIDTH, lineY + lineSpacing, lineBg);
                        } else {
                            // No custom bg — draw a subtle default line background
                            context.fill(areaX, lineY, areaX + areaWidth - SCROLLBAR_WIDTH, lineY + lineSpacing, 0x18000000);
                        }
                    }

                    final OrderedText line = lines.get(li);
                    final int textY = lineY + (lineSpacing - textRenderer.fontHeight) / 2;

                    // Ping highlight — if message contains player name, use ping color
                    final boolean isPing = pingEnabled && Boolean.TRUE.equals(wrapper.getMetadata("ping"));
                    final int lineColor = isPing ? pingColor : textColor;

                    // Alignment
                    final int textX;
                    final int lineWidth = textRenderer.getWidth(line);
                    switch (align) {
                        case "CENTER":
                            textX = areaX + (areaWidth - lineWidth) / 2;
                            break;
                        case "RIGHT":
                            textX = areaX + areaWidth - lineWidth - leftMargin - SCROLLBAR_WIDTH - 1;
                            break;
                        default: // LEFT
                            textX = areaX + leftMargin;
                    }

                    if (shadow) {
                        context.drawText(textRenderer, line, textX + 1, textY + 1, shadowColor, false);
                    }
                    context.drawText(textRenderer, line, textX, textY, lineColor, false);
                }

                renderedLineIndex++;
            }
        }

        // ── Scrollbar (only if bg is drawn) ──────────────────────
        if (drawBg && totalLines > maxVisibleLines) {
            final int scrollbarHeight = (int) ((float) maxVisibleLines / totalLines * areaHeight);
            final int scrollbarY = areaY + (areaHeight - scrollbarHeight)
                - (int) ((float) lineOffset / maxOffset * (areaHeight - scrollbarHeight));
            final int scrollbarX = areaX + areaWidth - SCROLLBAR_WIDTH;
            context.fill(scrollbarX, areaY, scrollbarX + SCROLLBAR_WIDTH, areaY + areaHeight, SCROLLBAR_BG);
            context.fill(scrollbarX, scrollbarY, scrollbarX + SCROLLBAR_WIDTH, scrollbarY + scrollbarHeight, SCROLLBAR_FG);
        }
    }

    public void mouseScrolled(final double scrollDelta) {
        lineOffset += (scrollDelta > 0) ? 3 : -3;
    }

    public void mouseClicked(final double mouseX, final double mouseY, final int button) {
    }

    private ChatTabImpl getActiveTabImpl() {
        if (chatWindow.getActiveTab() instanceof ChatTabImpl) {
            return (ChatTabImpl) chatWindow.getActiveTab();
        }
        return null;
    }

    public void resetScroll() {
        lineOffset = 0;
    }

    public int getLineOffset() {
        return lineOffset;
    }
}
