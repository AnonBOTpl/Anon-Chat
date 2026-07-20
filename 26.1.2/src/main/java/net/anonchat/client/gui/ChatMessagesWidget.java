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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;

public final class ChatMessagesWidget {

    // ── Styled run tracking for click/hover events ────────────────

    private static final class StyledRun {
        final int x, y, width, height;
        final Style style;

        StyledRun(final int x, final int y, final int width, final int height, final Style style) {
            this.x = x; this.y = y; this.width = width; this.height = height; this.style = style;
        }

        boolean contains(final double mx, final double my) {
            return mx >= x && mx <= x + width && my >= y && my <= y + height;
        }
    }

    private static final int SCROLLBAR_WIDTH = 3;
    private static final int SCROLLBAR_BG = 0x40FFFFFF;
    private static final int SCROLLBAR_FG = 0xAAFFFFFF;

    private final ChatWindow chatWindow;
    private int lineOffset = 0;
    private int lastTotalLines = 0;

    private final List<StyledRun> styledRuns = new ArrayList<>();
    private double mouseX;
    private double mouseY;

    public ChatMessagesWidget(final ChatWindow chatWindow) {
        this.chatWindow = chatWindow;
    }

    public void setMousePos(final double mx, final double my) {
        this.mouseX = mx;
        this.mouseY = my;
    }

    private FontSettings fs() {
        return ChatConfig.getInstance() != null
            ? ChatConfig.getInstance().getFontSettings()
            : new ChatConfig.FontSettings(); // fallback defaults
    }

    public void render(
        final GuiGraphicsExtractor context,
        final int areaX,
        final int areaY,
        final int areaWidth,
        final int areaHeight,
        final boolean focused
    ) {
        render(context, areaX, areaY, areaWidth, areaHeight, focused, true);
    }

    public void render(
        final GuiGraphicsExtractor context,
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

        final Font font = Minecraft.getInstance().font;

        final long timeoutMs = (props != null && props.getMessageTimeout() > 0)
            ? (long) props.getMessageTimeout() * 1000L : 0L;
        final long now = System.currentTimeMillis();

        final int textAlpha = (int) Math.round(0xFF * (textOpacity / 100.0));
        final int textColor = (textAlpha << 24) | 0xFFFFFF;
        final int shadowColor = (Math.min(255, textAlpha / 2) << 24) | 0x000000;

        styledRuns.clear();

        // ── Wrap lines ───────────────────────────────────────────
        final List<List<FormattedCharSequence>> wrappedLines = new ArrayList<>(messages.size());
        int totalLines = 0;
        for (final ChatMessageWrapper wrapper : messages) {
            final boolean timedOut = !focused && timeoutMs > 0L
                && (now - wrapper.getTimestamp()) > timeoutMs;
            if (wrapper == null || wrapper.isHidden() || timedOut) {
                wrappedLines.add(Collections.emptyList());
                totalLines++;
                continue;
            }
            Component component = wrapper.getComponent();
            if (component == null) {
                wrappedLines.add(Collections.emptyList());
                totalLines++;
                continue;
            }
            final int repeat = wrapper.getRepeatCount();
            if (repeat > 0) {
                final Component prefix = Component.literal("[×" + (repeat + 1) + "] ");
                component = prefix.copy().append(component);
            }
            final List<FormattedCharSequence> lines = wrapper.getOrComputeLines(font, component, areaWidth - leftMargin - 2);
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
            final List<FormattedCharSequence> lines = wrappedLines.get(mi);

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

                    final FormattedCharSequence line = lines.get(li);
                    final int textY = lineY + (lineSpacing - font.lineHeight) / 2;

                    // Ping highlight — if message contains player name, use ping color
                    final boolean isPing = pingEnabled && Boolean.TRUE.equals(wrapper.getMetadata("ping"));
                    final int lineColor = isPing ? pingColor : textColor;

                    // Alignment
                    final int textX;
                    final int lineWidth = font.width(line);
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

                    // ── Build styled runs for click/hover ────────────────────────
                    {
                        final float[] charX = {textX};
                        line.accept(new FormattedCharSink() {
                            @Override
                            public boolean accept(final int index, final Style style, final int codePoint) {
                                final int cw = font.width(Character.toString(codePoint));
                                if (style.getClickEvent() != null || style.getHoverEvent() != null) {
                                    styledRuns.add(new StyledRun((int) charX[0], textY, cw, lineSpacing, style));
                                }
                                charX[0] += cw;
                                return true;
                            }
                        });
                    }

                    if (shadow) {
                        context.text(font, line, textX + 1, textY + 1, shadowColor);
                    }
                    context.text(font, line, textX, textY, lineColor);
                }

                renderedLineIndex++;
            }
        }

        // ── Render hover tooltip ──────────────────────────────────
        if (!styledRuns.isEmpty()) {
            for (final StyledRun run : styledRuns) {
                if (run.contains(mouseX, mouseY)) {
                    final HoverEvent hover = run.style.getHoverEvent();
                    if (hover instanceof HoverEvent.ShowText showText) {
                        final Component tooltipValue = showText.value();
                        final String raw = tooltipValue.getString();
                        if (raw.contains("\n")) {
                            final List<Component> lines = new ArrayList<>();
                            for (final String line : raw.split("\n")) {
                                lines.add(Component.literal(line).setStyle(tooltipValue.getStyle()));
                            }
                            context.setComponentTooltipForNextFrame(font, lines, (int) mouseX, (int) mouseY);
                        } else {
                            context.setComponentTooltipForNextFrame(
                                font, List.of(tooltipValue), (int) mouseX, (int) mouseY);
                        }
                    }
                    break;
                }
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
        if (button != 0) return;
        final Minecraft mc = Minecraft.getInstance();
        for (final StyledRun run : styledRuns) {
            if (run.contains(mouseX, mouseY)) {
                final ClickEvent click = run.style.getClickEvent();
                if (click != null) {
                    handleClickEvent(mc, click);
                    return;
                }
            }
        }
    }

    private static void handleClickEvent(final Minecraft mc, final ClickEvent event) {
        if (event instanceof ClickEvent.OpenUrl openUrl) {
            try {
                java.net.URI uri = openUrl.uri();
                if (uri.getScheme() == null) {
                    uri = new java.net.URI("https://" + uri);
                }
                java.awt.Desktop.getDesktop().browse(uri);
            } catch (final Exception ignored) {}
        } else if (event instanceof ClickEvent.OpenFile openFile) {
            try {
                java.awt.Desktop.getDesktop().open(new java.io.File(openFile.path()));
            } catch (final Exception ignored) {}
        } else if (event instanceof ClickEvent.RunCommand runCmd) {
            String cmd = runCmd.command();
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            if (mc.getConnection() != null) {
                mc.getConnection().sendCommand(cmd);
            }            } else if (event instanceof ClickEvent.SuggestCommand suggestCmd) {
            if (mc.player != null) {
                mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen(suggestCmd.command(), false));
            }
        } else if (event instanceof ClickEvent.CopyToClipboard copy) {
            mc.keyboardHandler.setClipboard(copy.value());
        } else if (event instanceof ClickEvent.ShowDialog showDialog) {
            // Extract ClickEvent from dialog buttons and handle recursively
            try {
                final Dialog dialog = showDialog.dialog().value();
                // Check cancel action
                dialog.onCancel().ifPresent(a -> tryHandleAction(mc, a));
                // Check ConfirmationDialog buttons
                if (dialog instanceof net.minecraft.server.dialog.ConfirmationDialog confirm) {
                    tryHandleAction(mc, confirm.yesButton().action().orElse(null));
                }
                // Check ButtonListDialog exit action
                if (dialog instanceof net.minecraft.server.dialog.ButtonListDialog btnList) {
                    btnList.exitAction().ifPresent(btn ->
                        btn.action().ifPresent(a -> tryHandleAction(mc, a)));
                }
            } catch (final Exception ignored) {}
        }
    }

    private static void tryHandleAction(final Minecraft mc, final net.minecraft.server.dialog.action.Action action) {
        if (action instanceof StaticAction staticAction) {
            final ClickEvent click = staticAction.value();
            if (click != null) {
                handleClickEvent(mc, click);
            }
        }
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
