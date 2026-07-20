package net.anonchat.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.anonchat.client.AnonChatMod;
import net.anonchat.client.chat.ChatTab;
import net.anonchat.client.chat.ChatWindow;
import net.anonchat.client.config.ChatConfig;
import net.anonchat.client.config.ChatFilter;
import net.anonchat.client.config.ChatMacro;
import net.anonchat.client.config.ChatTabProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.awt.Color;
import net.minecraft.client.input.MouseButtonEvent;

public final class AnonChatConfigScreen extends Screen {

    private static final int TREE_WIDTH = 160;
    private static final int INDENT = 12;

    private final Screen parent;
    private SelectionType selection = SelectionType.GLOBAL;
    private int selWindow = -1;
    private int selTab = -1;
    private int selFilter = -1;
    private boolean dirty = false;

    private EditBox tabNameField;
    private EditBox chatLimitField;
    private EditBox filterNameField;
    private EditBox addTagField;
    private EditBox addExcludeTagField;
    private EditBox bgColorField;
    private EditBox unfocusedBgColorField;

    // Color picker state: 0=closed, 1=bgColor, 2=unfocusedBgColor



    private int expandedColorPicker = 0;
    private boolean expandedPingPicker = false;
    private EditBox timeoutField;

    // Color picker Y positions (for slider drawing)
    private int bgSwatchY = 0;
    private int unfocusedBgSwatchY = 0;
    private int pingSwatchY = 0;

    private enum SelectionType {
        GLOBAL, TAB, FILTER_LIST, FILTER_DETAIL, MACROS,        FONT,
        PROFILES
    }

    public AnonChatConfigScreen(final Screen parent) {
        super(tr("key.anonchat.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // 26.x: Screen.init(int,int) [final] calls this empty init() on first open.
        // Without this override, widgets are NEVER created -> blank screen.
        clearWidgets();
        rebuildAll();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        rebuildAll();
    }

    private void markDirty() { this.dirty = true; }

    @Override
    public void tick() {
        super.tick();
        if (dirty) { dirty = false; rebuildWidgets(); return; }

        if (selection == SelectionType.TAB && tabNameField != null && tabNameField.isFocused()) {
            final ChatTabProperties p = tabProps(); if (p != null) { p.setName(tabNameField.getValue()); save(); }
        }
        if (selection == SelectionType.TAB && chatLimitField != null && chatLimitField.isFocused()) {
            final String t = chatLimitField.getValue();
            if (!t.isEmpty()) try {
                final ChatTabProperties p = tabProps(); if (p != null) { p.setChatLimit(Integer.parseInt(t)); save(); }
            } catch (final NumberFormatException ignored) {}
        }
        if (selection == SelectionType.TAB && bgColorField != null && bgColorField.isFocused()) {
            final ChatTabProperties p = tabProps(); if (p != null) {
                try { p.setBackgroundColor((int) Long.parseLong(bgColorField.getValue(), 16)); save(); }
                catch (final NumberFormatException ignored) {}
            }
        }
        if (selection == SelectionType.TAB && unfocusedBgColorField != null && unfocusedBgColorField.isFocused()) {
            final ChatTabProperties p = tabProps(); if (p != null) {
                try { p.setUnfocusedBgColor((int) Long.parseLong(unfocusedBgColorField.getValue(), 16)); save(); }
                catch (final NumberFormatException ignored) {}
            }
        }
        if (selection == SelectionType.TAB && timeoutField != null && timeoutField.isFocused()) {
            final ChatTabProperties p = tabProps(); if (p != null) {
                try { p.setMessageTimeout(Integer.parseInt(timeoutField.getValue())); save(); }
                catch (final NumberFormatException ignored) {}
            }
        }
        if (selection == SelectionType.FILTER_DETAIL && filterNameField != null && filterNameField.isFocused()) {
            final ChatFilter f = currentFilter(); if (f != null) { f.setName(filterNameField.getValue()); save(); }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mx, int my, float delta) {
        final Font font = Minecraft.getInstance().font;
        context.text(font, this.getTitle(), (width - font.width(this.getTitle())) / 2, 6, 0xFFFFFF, true);
        context.fill(0, 0, TREE_WIDTH + 1, height, 0x20FFFFFF);
        context.fill(TREE_WIDTH, 24, TREE_WIDTH + 1, height - 4, 0xFF555555);
        super.extractRenderState(context, mx, my, delta);

        // Always draw swatch colors (even when picker is closed)
        if (selection == SelectionType.TAB) {
            final ChatTabProperties p = tabProps();
            if (p != null && bgColorField != null && unfocusedBgColorField != null) {
                final int px = TREE_WIDTH + 16;
                // BG color swatch
                context.fill(px + 196, bgSwatchY - 2, px + 214, bgSwatchY + 16, p.getBackgroundColor());
                // Unfocused BG color swatch
                context.fill(px + 196, unfocusedBgSwatchY - 2, px + 214, unfocusedBgSwatchY + 16, p.getUnfocusedBgColor());
            }
        }

        // Draw ping color swatch
        if (selection == SelectionType.FONT) {
            final ChatConfig.FontSettings fs = fs();
            final int px = TREE_WIDTH + 16;
            context.fill(px + 156, pingSwatchY, px + 174, pingSwatchY + 18, fs.getPingColor());
        }

        // Draw expanded color picker sliders (over widgets)
        if (expandedColorPicker > 0 && selection == SelectionType.TAB) {
            drawColorSliders(context);
        }
        // Draw ping color picker
        if (expandedPingPicker && selection == SelectionType.FONT) {
            drawPingSliders(context);
        }
    }

    @Override
    public boolean mouseClicked(final MouseButtonEvent event, final boolean bool) {
        if (super.mouseClicked(event, bool)) return true;
        if (expandedColorPicker > 0 && selection == SelectionType.TAB) {
            return handleSliderClick(event.x(), event.y());
        }
        if (expandedPingPicker && selection == SelectionType.FONT) {
            return handlePingSliderClick(event.x(), event.y());
        }
        return false;
    }

    // ── BUILDERS ───────────────────────────────────────────────────

    private void rebuildAll() {
        addRenderableWidget(Button.builder(
            tr("key.anonchat.button.done"), btn -> Minecraft.getInstance().setScreen(parent)
        ).bounds(width - 110, height - 28, 100, 20).build());

        buildTree();
        buildPanel();
    }

    private static Component tr(final String key, final Object... args) {
        return Component.translatable(key, args);
    }

    private StringWidget lbl(final String text, final int x, final int y) {
        final Font font = Minecraft.getInstance().font;
        return new StringWidget(x, y, font.width(text), 12, Component.literal(text), font);
    }

    private Button backBtn(final String label, final Runnable onBack) {
        return Button.builder(
            Component.literal("\u25C0 " + label), btn -> { onBack.run(); markDirty(); }
        ).bounds(TREE_WIDTH + 16, 30, 120, 20).build();
    }

    private void chk(final String key, final boolean initial,
                     final int x, final int y, final int w,
                     final java.util.function.Consumer<Boolean> onChange) {
        final Button btn = Button.builder(
            Component.literal(initial ? "\u2611 " : "\u2610 ").append(Component.translatable(key)),
            b -> {
                final String msg = b.getMessage().getString();
                final boolean now = !msg.startsWith("\u2611");
                onChange.accept(now);
                b.setMessage(Component.literal(now ? "\u2611 " : "\u2610 ").append(Component.translatable(key)));
            }
        ).bounds(x, y, w, 20).build();
        addRenderableWidget(btn);
    }

    private void tagPill(final String tag, final int x, final int y, final Runnable onRemove) {
        final Font font = Minecraft.getInstance().font;
        final int tw = font.width("[" + tag + "] \u2715") + 10;
        addRenderableWidget(Button.builder(
            Component.literal("[" + tag + "] \u2715"),
            btn -> { onRemove.run(); save(); markDirty(); }
        ).bounds(x, y, tw, 16).build());
    }

    // ── LEFT TREE (flat tab list + macros) ──────────────────────────

    private void buildTree() {
        final List<TabRef> allTabs = collectAllTabs();
        int y = 26;

        addRenderableWidget(lbl(tr("key.anonchat.section.tabs").getString(), INDENT, y));
        y += 16;

        for (int i = 0; i < allTabs.size(); i++) {
            final TabRef ref = allTabs.get(i);
            final boolean isSel = selWindow == ref.wi && selTab == ref.ti
                && selection == SelectionType.TAB;
            final String prefix = isSel ? "\u2192" : " ";
            final String suffix = ref.tab.getConfig().isServerTab() ? " (MAIN)" : "";

            final int wi = ref.wi;
            final int ti = ref.ti;
            addRenderableWidget(Button.builder(
                Component.literal(prefix + " " + ref.tab.getName() + suffix),
                btn -> {
                    selection = SelectionType.TAB; selWindow = wi; selTab = ti;
                    markDirty();
                }
            ).bounds(INDENT, y, TREE_WIDTH - INDENT, 16).build());
            y += 18;
        }

        if (allTabs.isEmpty()) {
            addRenderableWidget(lbl(tr("key.anonchat.no_tabs").getString(), INDENT, y));
            y += 18;
        }

        y += 6;

        // Autotext (Macros)
        final boolean isMacro = selection == SelectionType.MACROS;
        addRenderableWidget(Button.builder(
            Component.literal((isMacro ? "\u2192" : " ") + tr("key.anonchat.section.autotext").getString()),
            btn -> { selection = SelectionType.MACROS; markDirty(); }
        ).bounds(INDENT, y, TREE_WIDTH - INDENT, 16).build());
        y += 20;

        // Profile section
        final boolean isProf = selection == SelectionType.PROFILES;
        addRenderableWidget(Button.builder(
            Component.literal((isProf ? "\u2192" : " ") + tr("key.anonchat.section.profiles").getString()),
            btn -> { selection = SelectionType.PROFILES; markDirty(); }
        ).bounds(INDENT, y, TREE_WIDTH - INDENT, 16).build());
        y += 20;

        // Appearance section (font + ping)
        final boolean isFont = selection == SelectionType.FONT;
        addRenderableWidget(Button.builder(
            Component.literal((isFont ? "\u2192" : " ") + tr("key.anonchat.section.appearance").getString()),
            btn -> { selection = SelectionType.FONT; markDirty(); }
        ).bounds(INDENT, y, TREE_WIDTH - INDENT, 16).build());
    }

    // ── RIGHT PANEL ─────────────────────────────────────────────────

    private void buildPanel() {
        final int px = TREE_WIDTH + 16;
        switch (selection) {
            case GLOBAL       -> buildGlobalPlaceholder(px);
            case TAB          -> buildTabPanel(px);
            case FILTER_LIST  -> buildFilterListPanel(px);
            case FILTER_DETAIL -> buildFilterDetailPanel(px);
            case MACROS       -> buildMacrosPanel(px);
            case FONT         -> buildFontPanel(px);
            case PROFILES     -> buildProfilesPanel(px);
        }
    }

    private void buildGlobalPlaceholder(final int px) {
        addRenderableWidget(lbl(tr("key.anonchat.global.title").getString(), px, 26));
        addRenderableWidget(lbl(tr("key.anonchat.global.hint1").getString(), px, 48));
        addRenderableWidget(lbl(tr("key.anonchat.global.hint2").getString(), px, 64));
    }

    // ── FONT ────────────────────────────────────────────────────

    /** Renders a stepper: label [◀] value [▶] with range hint. */
    private void addStepper(final String labelKey, final int val, final int min, final int max,
                            final int x, final int y,
                            final java.util.function.Consumer<Integer> onChange) {
        final Font font = Minecraft.getInstance().font;
        final String label = tr(labelKey).getString();
        final String range = " (" + min + "-" + max + ")";
        final int labelW = font.width(label + range);
        addRenderableWidget(lbl(label + range, x, y));

        final int btnSize = 16;
        final int valW = 24;
        final int valX = x + labelW + 4;

        final String valStr = String.valueOf(val);
        addRenderableWidget(lbl(valStr, valX, y));

        // ◀
        addRenderableWidget(Button.builder(
            Component.literal("\u25C0"),
            b -> { onChange.accept((val > min) ? val - 1 : val); markDirty(); }
        ).bounds(valX + valW + 2, y, btnSize, btnSize).build());
        // ▶
        addRenderableWidget(Button.builder(
            Component.literal("\u25B6"),
            b -> { onChange.accept((val < max) ? val + 1 : val); markDirty(); }
        ).bounds(valX + valW + 2 + btnSize + 2, y, btnSize, btnSize).build());
    }

    private void buildFontPanel(final int px) {
        final ChatConfig.FontSettings fs = ChatConfig.getInstance() != null
            ? ChatConfig.getInstance().getFontSettings()
            : new ChatConfig.FontSettings();

        addRenderableWidget(lbl(tr("key.anonchat.appearance.title").getString(), px, 26));
        final int cw = width - px - 20;
        final Font font = Minecraft.getInstance().font;
        int y = 48;

        // ── Chatlog section ──────────────────────────────────────────
        chk("key.anonchat.appearance.chatlog", fs.isChatlogEnabled(), px, y, cw, v -> { fs.setChatlogEnabled(v); save(); });
        y += 20;

        // Open chat logs folder button
        addRenderableWidget(Button.builder(
            Component.literal("\uD83D\uDCC2 " + tr("key.anonchat.appearance.open_logs").getString()),
            btn -> openLogsFolder()
        ).bounds(px + 20, y, 180, 18).build());
        y += 24;

        // ── Ping section ───────────────────────────────────────────
        chk("key.anonchat.appearance.ping.enabled", fs.isPingEnabled(), px, y, cw, v -> { fs.setPingEnabled(v); save(); });
        y += 22;

        // Ping color (with expandable picker)
        addRenderableWidget(lbl(tr("key.anonchat.appearance.ping.color").getString(), px, y));
        pingSwatchY = y;
        final EditBox pingColorField = new EditBox(font, px + 80, y - 2, 70, 18, Component.literal("FFAA00"));
        pingColorField.setValue(String.format("%06X", fs.getPingColor() & 0xFFFFFF));
        pingColorField.setMaxLength(6);
        pingColorField.setResponder(s -> {
            try {
                final int c = 0xFF000000 | (int) Long.parseLong(s, 16);
                fs.setPingColor(c); save();
            } catch (final NumberFormatException ignored) {}
        });
        addRenderableWidget(pingColorField);
        // Color swatch button (toggles the hue picker)
        addRenderableWidget(Button.builder(
            Component.literal(""),
            b -> { expandedPingPicker = !expandedPingPicker; markDirty(); }
        ).bounds(px + 156, y, 18, 18).build());
        y += expandedPingPicker ? 32 : 22;

        // Ping sound
        final String soundLabel = tr("key.anonchat.appearance.ping.sound").getString();
        final String soundDisplay = net.anonchat.client.chat.ChatTabImpl.getSoundDisplayName(fs.getPingSound());
        addRenderableWidget(lbl(soundLabel + " " + soundDisplay, px, y));
        final int soundLblW = font.width(soundLabel + " " + soundDisplay);
        // ◀
        addRenderableWidget(Button.builder(
            Component.literal("\u25C0"),
            b -> { fs.setPingSound(net.anonchat.client.chat.ChatTabImpl.prevSound(fs.getPingSound())); markDirty(); save(); }
        ).bounds(px + soundLblW + 4, y, 16, 16).build());
        // ▶
        addRenderableWidget(Button.builder(
            Component.literal("\u25B6"),
            b -> { fs.setPingSound(net.anonchat.client.chat.ChatTabImpl.nextSound(fs.getPingSound())); markDirty(); save(); }
        ).bounds(px + soundLblW + 4 + 18, y, 16, 16).build());
        y += 24;

        // Separator
        addRenderableWidget(lbl("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500", px, y));
        y += 16;

        // ── Font section ───────────────────────────────────────────
        // Shadow
        chk("key.anonchat.font.shadow", fs.isShadow(), px, y, cw, v -> { fs.setShadow(v); save(); });
        y += 26;

        // Line spacing
        addStepper("key.anonchat.font.line_spacing", fs.getLineSpacing(), 6, 24, px, y,
            v -> { fs.setLineSpacing(v); save(); });
        y += 26;

        // Message spacing
        addStepper("key.anonchat.font.msg_spacing", fs.getMessageSpacing(), 0, 20, px, y,
            v -> { fs.setMessageSpacing(v); save(); });
        y += 26;

        // Text opacity
        addStepper("key.anonchat.font.opacity", fs.getTextOpacity(), 30, 100, px, y,
            v -> { fs.setTextOpacity(v); save(); });
        y += 26;

        // Alignment
        addRenderableWidget(lbl(tr("key.anonchat.font.alignment").getString(), px, y));
        final String curAlign = fs.getTextAlignment();
        final int btnW = (Math.min(cw, 200) - 8) / 3;
        addRenderableWidget(Button.builder(
            Component.literal("LEFT".equals(curAlign) ? "\u2611 " : "\u2610 ").append(tr("key.anonchat.font.align_left")),
            b -> { fs.setTextAlignment("LEFT"); markDirty(); save(); }
        ).bounds(px + 80, y, btnW, 18).build());
        addRenderableWidget(Button.builder(
            Component.literal("CENTER".equals(curAlign) ? "\u2611 " : "\u2610 ").append(tr("key.anonchat.font.align_center")),
            b -> { fs.setTextAlignment("CENTER"); markDirty(); save(); }
        ).bounds(px + 80 + btnW + 4, y, btnW, 18).build());
        addRenderableWidget(Button.builder(
            Component.literal("RIGHT".equals(curAlign) ? "\u2611 " : "\u2610 ").append(tr("key.anonchat.font.align_right")),
            b -> { fs.setTextAlignment("RIGHT"); markDirty(); save(); }
        ).bounds(px + 80 + btnW * 2 + 8, y, btnW, 18).build());
        y += 26;

        // Left margin
        addStepper("key.anonchat.font.margin", fs.getLeftMargin(), 0, 12, px, y,
            v -> { fs.setLeftMargin(v); save(); });
        y += 30;

        // ── Config folder section ───────────────────────────────────────
        addRenderableWidget(lbl("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500", px, y));
        y += 16;

        addRenderableWidget(Button.builder(
            Component.literal("\uD83D\uDCC1 " + tr("key.anonchat.appearance.open_config").getString()),
            btn -> openConfigFolder()
        ).bounds(px, y, 200, 18).tooltip(Tooltip.create(tr("key.anonchat.tooltip.open_config"))).build());
    }

    // ── TAB PANEL ───────────────────────────────────────────────────

    private void buildTabPanel(final int px) {
        final ChatTabProperties props = tabProps();
        final ChatTab tab = currentTab();
        if (props == null || tab == null) {
            selection = SelectionType.GLOBAL; markDirty();
            return;
        }

        addRenderableWidget(lbl(tr("key.anonchat.tab.title").getString(), px, 26));
        addRenderableWidget(lbl(tab.getName(), px, 40));

        int y = 56;
        final int cw = width - px - 20;
        final Font font = Minecraft.getInstance().font;

        // Name + Edit Filters on same row
        addRenderableWidget(lbl(tr("key.anonchat.label.name").getString(), px, y));
        tabNameField = new EditBox(font, px + 50, y - 2, 150, 18, Component.literal("Name"));
        tabNameField.setValue(props.getName());
        tabNameField.setMaxLength(32);
        addRenderableWidget(tabNameField);
        addRenderableWidget(Button.builder(
            tr("key.anonchat.tab.edit_filters", props.getFilters().size()),
            btn -> { selection = SelectionType.FILTER_LIST; selFilter = -1; markDirty(); }
        ).bounds(px + 210, y - 2, 140, 18).tooltip(Tooltip.create(tr("key.anonchat.tooltip.edit_filters"))).build());
        y += 26;

        chk("key.anonchat.tab.combine", props.isCombineChatMessages(), px, y, cw, v -> { props.setCombineChatMessages(v); save(); });
        y += 22;
        chk("key.anonchat.tab.background", props.isBackground(), px, y, cw, v -> { props.setBackground(v); save(); });
        y += 22;
        chk("key.anonchat.tab.unfocused_bg", props.isUnfocusedBackground(), px, y, cw, v -> { props.setUnfocusedBackground(v); save(); });
        y += 26;

        // Message Limit
        addRenderableWidget(lbl(tr("key.anonchat.tab.limit").getString(), px, y));
        chatLimitField = new EditBox(font, px + 80, y - 2, 50, 18, Component.literal("Limit"));
        chatLimitField.setValue(String.valueOf(props.getChatLimit()));
        chatLimitField.setMaxLength(4);
        addRenderableWidget(chatLimitField);
        y += 26;

        // ── Window BG Color (active) with picker ──
        addRenderableWidget(lbl(tr("key.anonchat.tab.window_bg").getString(), px, y));
        bgColorField = new EditBox(font, px + 120, y - 2, 72, 18, Component.literal("40000000"));
        bgColorField.setValue(String.format("%08X", props.getBackgroundColor()));
        bgColorField.setMaxLength(8);
        addRenderableWidget(bgColorField);
        bgSwatchY = y;
        // Swatch button (toggles picker)
        addRenderableWidget(Button.builder(
            Component.literal(""),
            b -> { expandedColorPicker = (expandedColorPicker == 1) ? 0 : 1; markDirty(); }
        ).bounds(px + 196, y - 2, 18, 18).build());
        y += (expandedColorPicker == 1) ? 90 : 22;

        // ── Window BG Color (inactive) with picker ──
        addRenderableWidget(lbl(tr("key.anonchat.tab.unfocused_bg_color").getString(), px, y));
        unfocusedBgColorField = new EditBox(font, px + 120, y - 2, 72, 18, Component.literal("30000000"));
        unfocusedBgColorField.setValue(String.format("%08X", props.getUnfocusedBgColor()));
        unfocusedBgColorField.setMaxLength(8);
        addRenderableWidget(unfocusedBgColorField);
        unfocusedBgSwatchY = y;
        // Swatch button
        addRenderableWidget(Button.builder(
            Component.literal(""),
            b -> { expandedColorPicker = (expandedColorPicker == 2) ? 0 : 2; markDirty(); }
        ).bounds(px + 196, y - 2, 18, 18).build());
        y += (expandedColorPicker == 2) ? 90 : 22;

        // Message timeout
        addRenderableWidget(lbl(tr("key.anonchat.tab.timeout").getString(), px, y));
        timeoutField = new EditBox(font, px + 110, y - 2, 40, 18, Component.literal("0"));
        timeoutField.setValue(String.valueOf(props.getMessageTimeout()));
        timeoutField.setMaxLength(5);
        addRenderableWidget(timeoutField);
        y += 26;

        chk("key.anonchat.tab.override_font", props.isOverrideFont(), px, y, cw, v -> { props.setOverrideFont(v); save(); markDirty(); });
        y += 22;

        if (props.isOverrideFont()) {
            final int fontPx = px + 16;

            // Shadow
            chk("key.anonchat.font.shadow", props.isShadow(), fontPx, y, cw - 16, v -> { props.setShadow(v); save(); });
            y += 26;

            // Line spacing
            addStepper("key.anonchat.font.line_spacing", props.getLineSpacing(), 6, 24, fontPx, y,
                v -> { props.setLineSpacing(v); save(); });
            y += 26;

            // Message spacing
            addStepper("key.anonchat.font.msg_spacing", props.getMessageSpacing(), 0, 20, fontPx, y,
                v -> { props.setMessageSpacing(v); save(); });
            y += 26;

            // Text opacity
            addStepper("key.anonchat.font.opacity", props.getTextOpacity(), 30, 100, fontPx, y,
                v -> { props.setTextOpacity(v); save(); });
            y += 26;

            // Alignment
            addRenderableWidget(lbl(tr("key.anonchat.font.alignment").getString(), fontPx, y));
            final String curAlign = props.getTextAlignment();
            final int alignBtnW = (Math.min(cw - 16, 200) - 8) / 3;
            addRenderableWidget(Button.builder(
                Component.literal("LEFT".equals(curAlign) ? "\u2611 " : "\u2610 ").append(tr("key.anonchat.font.align_left")),
                b -> { props.setTextAlignment("LEFT"); markDirty(); save(); }
            ).bounds(fontPx + 80, y, alignBtnW, 18).build());
            addRenderableWidget(Button.builder(
                Component.literal("CENTER".equals(curAlign) ? "\u2611 " : "\u2610 ").append(tr("key.anonchat.font.align_center")),
                b -> { props.setTextAlignment("CENTER"); markDirty(); save(); }
            ).bounds(fontPx + 80 + alignBtnW + 4, y, alignBtnW, 18).build());
            addRenderableWidget(Button.builder(
                Component.literal("RIGHT".equals(curAlign) ? "\u2611 " : "\u2610 ").append(tr("key.anonchat.font.align_right")),
                b -> { props.setTextAlignment("RIGHT"); markDirty(); save(); }
            ).bounds(fontPx + 80 + alignBtnW * 2 + 8, y, alignBtnW, 18).build());
            y += 26;

            // Left margin
            addStepper("key.anonchat.font.margin", props.getLeftMargin(), 0, 12, fontPx, y,
                v -> { props.setLeftMargin(v); save(); });
        }
    }

    // ── FILTER LIST ─────────────────────────────────────────────────

    private void buildFilterListPanel(final int px) {
        final ChatTabProperties props = tabProps();
        if (props == null) { selection = SelectionType.GLOBAL; markDirty(); return; }

        addRenderableWidget(backBtn(tr("key.anonchat.filter.back").getString(), () -> selection = SelectionType.TAB));
        addRenderableWidget(lbl(tr("key.anonchat.filter.title", props.getName()).getString(), px, 26));

        final List<ChatFilter> raw = props.getFilters();
        final List<ChatFilter> filters = raw.isEmpty() ? new ArrayList<>() : raw;
        if (raw.isEmpty()) { props.setFilters(filters); }

        int y = 54;
        for (int i = 0; i < filters.size(); i++) {
            final ChatFilter f = filters.get(i);
            final int fi = i;
            addRenderableWidget(Button.builder(
                Component.literal(f.getName() + " (inc:" + f.getIncludeTags().size()
                    + " exc:" + f.getExcludeTags().size() + ")"),
                btn -> { selFilter = fi; selection = SelectionType.FILTER_DETAIL; markDirty(); }
            ).bounds(px + 8, y, width - px - 70, 18).build());

            addRenderableWidget(Button.builder(
                Component.literal("\u2715"),
                btn -> { filters.remove(fi); save(); markDirty(); }
            ).bounds(width - 54, y, 22, 18).build());
            y += 22;
        }

        y += 4;
        addRenderableWidget(Button.builder(
            tr("key.anonchat.filter.add"),
            btn -> {
                final ChatFilter nf = new ChatFilter();
                nf.setName("Filter " + (filters.size() + 1));
                filters.add(nf);
                selFilter = filters.size() - 1;
                selection = SelectionType.FILTER_DETAIL; save(); markDirty();
            }
        ).bounds(px, y, 130, 20).build());
    }

    // ── FILTER DETAIL ───────────────────────────────────────────────

    private void buildFilterDetailPanel(final int px) {
        final ChatFilter filter = currentFilter();
        if (filter == null) { selection = SelectionType.FILTER_LIST; markDirty(); return; }

        addRenderableWidget(backBtn(tr("key.anonchat.filter.back_list").getString(), () -> selection = SelectionType.FILTER_LIST));
        addRenderableWidget(lbl(tr("key.anonchat.filter.editor").getString(), px, 26));
        addRenderableWidget(lbl(filter.getName(), px, 52));

        int y = 68;
        final int cw = width - px - 20;
        final Font font = Minecraft.getInstance().font;

        // Name
        addRenderableWidget(lbl(tr("key.anonchat.label.name").getString(), px, y));
        filterNameField = new EditBox(font, px + 50, y - 2, 180, 18, Component.literal("Filter name"));
        filterNameField.setValue(filter.getName());
        filterNameField.setMaxLength(32);
        addRenderableWidget(filterNameField);
        y += 26;

        // Include Tags
        addRenderableWidget(lbl(tr("key.anonchat.filter.include").getString(), px, y));
        y += 16;
        int tagX = px + 12;
        int tagRowY = y;
        for (int i = 0; i < filter.getIncludeTags().size(); i++) {
            final int ti = i;
            final String tag = filter.getIncludeTags().get(i);
            final int tw = font.width("[" + tag + "] \u2715") + 14;
            if (tagX + tw > width - 20) { tagX = px + 12; tagRowY += 20; }
            tagPill(tag, tagX, tagRowY, () -> filter.getIncludeTags().remove(ti));
            tagX += tw + 8;
        }
        y = Math.max(y + 16, tagRowY + (filter.getIncludeTags().isEmpty() ? 0 : 22));

        addTagField = new EditBox(font, px + 12, y, 140, 16, Component.literal(""));
        addTagField.setMaxLength(64);
        addTagField.setResponder(s -> {}); // placeholder text handled via setValue on focus? we skip placeholder
        addRenderableWidget(addTagField);
        addRenderableWidget(Button.builder(
            Component.literal("+"),
            btn -> doAddTag(filter, true)
        ).bounds(px + 158, y, 22, 16).tooltip(Tooltip.create(tr("key.anonchat.tooltip.include_tag"))).build());
        y += 22;

        // Exclude Tags
        addRenderableWidget(lbl(tr("key.anonchat.filter.exclude").getString(), px, y));
        y += 16;
        int excX = px + 12;
        int excRowY = y;
        for (int i = 0; i < filter.getExcludeTags().size(); i++) {
            final int ti = i;
            final String tag = filter.getExcludeTags().get(i);
            final int tw = font.width("[" + tag + "] \u2715") + 14;
            if (excX + tw > width - 20) { excX = px + 12; excRowY += 20; }
            tagPill(tag, excX, excRowY, () -> filter.getExcludeTags().remove(ti));
            excX += tw + 8;
        }
        y = Math.max(y + 16, excRowY + (filter.getExcludeTags().isEmpty() ? 0 : 22));

        addExcludeTagField = new EditBox(font, px + 12, y, 140, 16, Component.literal(""));
        addExcludeTagField.setMaxLength(64);
        addExcludeTagField.setResponder(s -> {});
        addRenderableWidget(addExcludeTagField);
        addRenderableWidget(Button.builder(
            Component.literal("+"),
            btn -> doAddTag(filter, false)
        ).bounds(px + 158, y, 22, 16).tooltip(Tooltip.create(tr("key.anonchat.tooltip.exclude_tag"))).build());
        y += 24;

        // Options
        chk("key.anonchat.filter.sound", filter.isShouldPlaySound(), px, y, cw, v -> { filter.setShouldPlaySound(v); save(); });
        y += 22;
        chk("key.anonchat.filter.change_bg", filter.isShouldChangeBackground(), px, y, cw, v -> { filter.setShouldChangeBackground(v); save(); });
        y += 28;

        // Delete
        addRenderableWidget(Button.builder(
            tr("key.anonchat.filter.delete"),
            btn -> {
                final ChatTabProperties p = tabProps();
                if (p != null) { p.getFilters().remove(selFilter); save();
                    selection = SelectionType.FILTER_LIST; markDirty(); }
            }
        ).bounds(px, y, 120, 20).build());
    }

    private static ChatConfig.FontSettings fs() {
        return ChatConfig.getInstance() != null
            ? ChatConfig.getInstance().getFontSettings()
            : new ChatConfig.FontSettings();
    }

    // ── MACROS ─────────────────────────────────────────────────────

    private int capturingMacroIndex = -1;

    private void buildMacrosPanel(final int px) {
        addRenderableWidget(lbl(tr("key.anonchat.macro.title").getString(), px, 26));
        addRenderableWidget(lbl(tr("key.anonchat.macro.hint").getString(), px, 42));

        final List<ChatMacro> macros = ChatConfig.getInstance() != null
            ? ChatConfig.getInstance().getMacros() : new ArrayList<>();

        int y = 64;
        final int cw = width - px - 20;
        final Font font = Minecraft.getInstance().font;

        for (int i = 0; i < macros.size(); i++) {
            final int fi = i;
            final ChatMacro m = macros.get(i);

            addRenderableWidget(lbl(tr("key.anonchat.label.name").getString(), px, y));
            final EditBox nameField = new EditBox(font, px + 50, y - 2, 150, 18, Component.literal("Name"));
            nameField.setValue(m.getName());
            nameField.setMaxLength(32);
            nameField.setResponder(s -> { m.setName(s); save(); });
            addRenderableWidget(nameField);
            y += 22;

            // Key capture
            addRenderableWidget(Button.builder(
                Component.literal(tr("key.anonchat.macro.key",
                    capturingMacroIndex == fi ? tr("key.anonchat.macro.press_key").getString() : keyName(m.getKeyCode())).getString()),
                b -> { capturingMacroIndex = fi; setFocused(null); markDirty(); }
            ).bounds(px, y, 150, 18).build());
            addRenderableWidget(Button.builder(
                Component.literal("\u2715"),
                b -> { m.setKeyCode(-1); save(); markDirty(); }
            ).bounds(px + 155, y, 20, 18).build());
            y += 22;

            // Text
            addRenderableWidget(lbl(tr("key.anonchat.macro.text").getString(), px, y));
            final EditBox textField = new EditBox(font, px + 50, y - 2, cw - 50, 18, Component.literal("Text"));
            textField.setValue(m.getText());
            textField.setMaxLength(256);
            textField.setResponder(s -> { m.setText(s); save(); });
            addRenderableWidget(textField);
            y += 22;

            // Command toggle
            chk("key.anonchat.macro.command", m.isCommand(), px, y, cw, v -> { m.setCommand(v); save(); });
            y += 22;

            // Delete
            addRenderableWidget(Button.builder(
                tr("key.anonchat.macro.delete"),
                b -> { macros.remove(fi); save(); markDirty(); }
            ).bounds(px, y, 120, 18).build());
            y += 28;
        }

        addRenderableWidget(Button.builder(
            tr("key.anonchat.macro.add"),
            b -> {
                macros.add(new ChatMacro("Macro " + (macros.size() + 1), -1, ""));
                save(); markDirty();
            }
        ).bounds(px, y, 130, 20).build());
    }

    // ── PROFILES ─────────────────────────────────────────────────────

    private EditBox profileNameField;

    private void buildProfilesPanel(final int px) {
        addRenderableWidget(lbl(tr("key.anonchat.profiles.title").getString(), px, 26));
        final int cw = width - px - 20;
        final Font font = Minecraft.getInstance().font;
        int y = 46;

        // ── Profile list ──────────────────────────────────────────────
        final ChatConfig config = ChatConfig.getInstance();
        final List<String> profiles = config != null ? config.listProfiles() : new ArrayList<>();
        // Always include "default"
        if (!profiles.contains("default")) profiles.add(0, "default");

        for (final String name : profiles) {
            final boolean isDefault = "default".equals(name);
            final boolean isCurrent = (config != null && name.equals(config.getCurrentProfile()));

            final String label = (isCurrent ? "\u2192" : " ") + "\u2630 " + name;
            final int lblW = font.width(label) + 10;
            addRenderableWidget(lbl(label, px, y));

            // Load button
            final String finName = name;
            addRenderableWidget(Button.builder(
                tr("key.anonchat.profiles.load"),
                btn -> {
                    if (ChatConfig.getInstance() != null) {
                        ChatConfig.getInstance().loadProfile(finName);
                        net.anonchat.client.AnonChatMod.reloadEverything();
                        markDirty();
                    }
                }
            ).bounds(px + lblW + 4, y, 60, 16).tooltip(Tooltip.create(tr("key.anonchat.tooltip.profile_load"))).build());

            // Delete button (not for "default")
            if (!isDefault) {
                addRenderableWidget(Button.builder(
                    Component.literal("\u2715"),
                    btn -> {
                        if (ChatConfig.getInstance() != null) {
                            ChatConfig.getInstance().deleteProfile(finName);
                            markDirty();
                        }
                    }
                ).bounds(px + lblW + 4 + 64, y, 20, 16).tooltip(Tooltip.create(tr("key.anonchat.tooltip.profile_delete"))).build());
            }

            y += 20;
        }

        y += 6;

        // ── Separator ──
        addRenderableWidget(lbl("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500", px, y));
        y += 16;

        // ── Save as ──
        addRenderableWidget(lbl(tr("key.anonchat.profiles.save_as").getString(), px, y));
        profileNameField = new EditBox(font, px + 100, y - 2, 150, 18, Component.literal(""));
        profileNameField.setMaxLength(32);
        addRenderableWidget(profileNameField);
        addRenderableWidget(Button.builder(
            tr("key.anonchat.profiles.save_btn"),
            btn -> {
                if (ChatConfig.getInstance() == null) return;
                String name = profileNameField.getValue().trim();
                if (name.isEmpty()) {
                    name = ChatConfig.getInstance().getCurrentProfile();
                }
                if (name.isEmpty()) name = "default";
                final String oldProfile = ChatConfig.getInstance().getCurrentProfile();
                if (oldProfile != null && !oldProfile.isEmpty() && !oldProfile.equals(name)) {
                    ChatConfig.getInstance().saveProfile(oldProfile);
                }
                ChatConfig.getInstance().saveProfile(name);
                ChatConfig.getInstance().setCurrentProfile(name);
                profileNameField.setValue("");
                markDirty();
            }
        ).bounds(px + 260, y - 2, 80, 18).tooltip(Tooltip.create(tr("key.anonchat.tooltip.profile_save"))).build());
        y += 24;

        // Hint
        addRenderableWidget(lbl(tr("key.anonchat.profiles.save_hint").getString(), px, y));
    }

    private static String keyName(final int code) {
        if (code < 0) return "-";
        final String name = GLFW.glfwGetKeyName(code, 0);
        if (name != null && !name.isEmpty()) return name.toUpperCase(java.util.Locale.ROOT);
        return "Key#" + code;
    }

    // ── KEYBOARD ────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(final KeyEvent event) {
        final int keyCode = event.key();
        if (capturingMacroIndex >= 0) {
            final List<ChatMacro> macros = ChatConfig.getInstance() != null
                ? ChatConfig.getInstance().getMacros() : null;
            if (macros != null && capturingMacroIndex < macros.size()) {
                macros.get(capturingMacroIndex).setKeyCode(keyCode);
                save();
            }
            capturingMacroIndex = -1;
            markDirty();
            return true;
        }
        if ((keyCode == 257 || keyCode == 335) && selection == SelectionType.FILTER_DETAIL) {
            final ChatFilter f = currentFilter();
            if (f != null) {
                if (addTagField != null && addTagField.isFocused()) { doAddTag(f, true); return true; }
                if (addExcludeTagField != null && addExcludeTagField.isFocused()) { doAddTag(f, false); return true; }
            }
        }
        return super.keyPressed(event);
    }

    // ── TAG HELPERS ─────────────────────────────────────────────────

    private void doAddTag(final ChatFilter filter, final boolean include) {
        final EditBox src = include ? addTagField : addExcludeTagField;
        if (src == null) return;
        final String t = src.getValue().trim();
        if (t.isEmpty()) return;
        if (include) {
            if (filter.getIncludeTags().isEmpty()) filter.setIncludeTags(new ArrayList<>());
            filter.getIncludeTags().add(t);
        } else {
            if (filter.getExcludeTags().isEmpty()) filter.setExcludeTags(new ArrayList<>());
            filter.getExcludeTags().add(t);
        }
        src.setValue(""); save(); markDirty();
    }

    // ── COLOR PICKER ──────────────────────────────────────────────

    /** Draw expanded color picker sliders (hue bar + alpha bar) over widgets. */
    private void drawColorSliders(final GuiGraphicsExtractor context) {
        final ChatTabProperties p = tabProps();
        if (p == null) return;

        final int argb = expandedColorPicker == 1 ? p.getBackgroundColor() : p.getUnfocusedBgColor();
        final int swatchY = expandedColorPicker == 1 ? bgSwatchY : unfocusedBgSwatchY;
        final int px = TREE_WIDTH + 16;

        // Swatches already drawn in extractRenderState (always visible)
        // Position of sliders (relative to the selected swatch)
        final int sliderX = px + 120;
        final int sliderW = 94;
        final int sliderH = 10;
        final int hueY = swatchY + 18;
        final int satY = swatchY + 32;
        final int briY = swatchY + 46;
        final int alphaY = swatchY + 60;

        // ── Hue slider ──
        final int steps = 18;
        for (int i = 0; i < steps; i++) {
            final float hue = (float) i / steps;
            final int col = 0xFF000000 | Color.HSBtoRGB(hue, 1.0f, 1.0f);
            final int sx = sliderX + i * sliderW / steps;
            final int ex = sliderX + (i + 1) * sliderW / steps;
            context.fill(sx, hueY, ex, hueY + sliderH, col);
        }
        // Border
        context.fill(sliderX, hueY, sliderX + sliderW, hueY + 1, 0xFF666666);
        context.fill(sliderX, hueY + sliderH - 1, sliderX + sliderW, hueY + sliderH, 0xFF666666);
        context.fill(sliderX, hueY, sliderX + 1, hueY + sliderH, 0xFF666666);
        context.fill(sliderX + sliderW - 1, hueY, sliderX + sliderW, hueY + sliderH, 0xFF666666);

        // Hue marker
        final float[] hsb = new float[3];
        Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, hsb);
        final int markerX = Math.min(sliderX + sliderW - 2, sliderX + (int) (hsb[0] * sliderW));
        context.fill(markerX - 1, hueY - 2, markerX + 2, hueY, 0xFFFFFFFF);
        context.fill(markerX - 1, hueY + sliderH, markerX + 2, hueY + sliderH + 2, 0xFFFFFFFF);

        // ── Saturation slider ──
        for (int i = 0; i < steps; i++) {
            final float sat = (float) i / (steps - 1);
            final int col = 0xFF000000 | Color.HSBtoRGB(hsb[0], sat, hsb[2]);
            final int sx = sliderX + i * sliderW / steps;
            final int ex = sliderX + (i + 1) * sliderW / steps;
            context.fill(sx, satY, ex, satY + sliderH, col);
        }
        context.fill(sliderX, satY, sliderX + sliderW, satY + 1, 0xFF666666);
        context.fill(sliderX, satY + sliderH - 1, sliderX + sliderW, satY + sliderH, 0xFF666666);
        context.fill(sliderX, satY, sliderX + 1, satY + sliderH, 0xFF666666);
        context.fill(sliderX + sliderW - 1, satY, sliderX + sliderW, satY + sliderH, 0xFF666666);
        final int satMarkerX = Math.min(sliderX + sliderW - 2, sliderX + (int) (hsb[1] * sliderW));
        context.fill(satMarkerX - 1, satY - 2, satMarkerX + 2, satY, 0xFFFFFFFF);
        context.fill(satMarkerX - 1, satY + sliderH, satMarkerX + 2, satY + sliderH + 2, 0xFFFFFFFF);

        // ── Brightness slider ──
        for (int i = 0; i < steps; i++) {
            final float bri = (float) i / (steps - 1);
            final int col = 0xFF000000 | Color.HSBtoRGB(hsb[0], hsb[1], bri);
            final int sx = sliderX + i * sliderW / steps;
            final int ex = sliderX + (i + 1) * sliderW / steps;
            context.fill(sx, briY, ex, briY + sliderH, col);
        }
        context.fill(sliderX, briY, sliderX + sliderW, briY + 1, 0xFF666666);
        context.fill(sliderX, briY + sliderH - 1, sliderX + sliderW, briY + sliderH, 0xFF666666);
        context.fill(sliderX, briY, sliderX + 1, briY + sliderH, 0xFF666666);
        context.fill(sliderX + sliderW - 1, briY, sliderX + sliderW, briY + sliderH, 0xFF666666);
        final int briMarkerX = Math.min(sliderX + sliderW - 2, sliderX + (int) (hsb[2] * sliderW));
        context.fill(briMarkerX - 1, briY - 2, briMarkerX + 2, briY, 0xFFFFFFFF);
        context.fill(briMarkerX - 1, briY + sliderH, briMarkerX + 2, briY + sliderH + 2, 0xFFFFFFFF);

        // ── Alpha slider ──
        final int alpha = (argb >> 24) & 0xFF;
        final int colorNoAlpha = argb & 0xFFFFFF;
        // Checkerboard pattern for transparency reference
        final int checkSize = 4;
        for (int i = 0; i < sliderW; i += checkSize * 2) {
            for (int j = 0; j < sliderH; j += checkSize * 2) {
                context.fill(sliderX + i, alphaY + j, Math.min(sliderX + i + checkSize, sliderX + sliderW), alphaY + j + checkSize, 0xFF333333);
                context.fill(sliderX + i + checkSize, alphaY + j + checkSize, Math.min(sliderX + i + checkSize * 2, sliderX + sliderW), alphaY + j + checkSize * 2, 0xFF333333);
            }
        }
        // Gradient on top of checkerboard
        for (int i = 0; i < steps; i++) {
            final int a = i * 255 / (steps - 1);
            final int col = (a << 24) | colorNoAlpha;
            final int sx = sliderX + i * sliderW / steps;
            final int ex = sliderX + (i + 1) * sliderW / steps;
            context.fill(sx, alphaY, ex, alphaY + sliderH, col);
        }
        // Border
        context.fill(sliderX, alphaY, sliderX + sliderW, alphaY + 1, 0xFF666666);
        context.fill(sliderX, alphaY + sliderH - 1, sliderX + sliderW, alphaY + sliderH, 0xFF666666);
        context.fill(sliderX, alphaY, sliderX + 1, alphaY + sliderH, 0xFF666666);
        context.fill(sliderX + sliderW - 1, alphaY, sliderX + sliderW, alphaY + sliderH, 0xFF666666);

        // Alpha marker
        final int alphaMarkerX = Math.min(sliderX + sliderW - 2, sliderX + (int) ((alpha / 255.0f) * sliderW));
        context.fill(alphaMarkerX - 1, alphaY - 2, alphaMarkerX + 2, alphaY, 0xFFFFFFFF);
        context.fill(alphaMarkerX - 1, alphaY + sliderH, alphaMarkerX + 2, alphaY + sliderH + 2, 0xFFFFFFFF);
    }

    /** Handle mouse click on expanded color picker sliders. */
    private boolean handleSliderClick(final double mx, final double my) {
        final ChatTabProperties p = tabProps();
        if (p == null) return false;

        final int swatchY = expandedColorPicker == 1 ? bgSwatchY : unfocusedBgSwatchY;
        final int px = TREE_WIDTH + 16;
        final int sliderX = px + 120;
        final int sliderW = 94;
        final int sliderH = 10;
        final int hueY = swatchY + 18;
        final int satY = swatchY + 32;
        final int briY = swatchY + 46;
        final int alphaY = swatchY + 60;

        final int argb = expandedColorPicker == 1 ? p.getBackgroundColor() : p.getUnfocusedBgColor();
        final int oldAlpha = (argb >> 24) & 0xFF;
        final int oldRGB = argb & 0xFFFFFF;
        final float[] hsb = new float[3];
        Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, hsb);

        int newArg = 0;
        boolean changed = false;

        // Check hue slider
        if (mx >= sliderX && mx <= sliderX + sliderW && my >= hueY && my <= hueY + sliderH) {
            final float newHue = (float) Math.max(0, Math.min(1, (mx - sliderX) / (double) sliderW));
            float sat = hsb[1];
            float bri = hsb[2];
            if (sat < 0.1f) sat = 0.8f;
            if (bri < 0.1f) bri = 0.8f;
            final int newRGB = Color.HSBtoRGB(newHue, sat, bri);
            newArg = (oldAlpha << 24) | (newRGB & 0xFFFFFF);
            changed = true;
        }
        // Check saturation slider
        else if (mx >= sliderX && mx <= sliderX + sliderW && my >= satY && my <= satY + sliderH) {
            final float newSat = (float) Math.max(0, Math.min(1, (mx - sliderX) / (double) sliderW));
            final int newRGB = Color.HSBtoRGB(hsb[0], newSat, hsb[2]);
            newArg = (oldAlpha << 24) | (newRGB & 0xFFFFFF);
            changed = true;
        }
        // Check brightness slider
        else if (mx >= sliderX && mx <= sliderX + sliderW && my >= briY && my <= briY + sliderH) {
            final float newBri = (float) Math.max(0, Math.min(1, (mx - sliderX) / (double) sliderW));
            final int newRGB = Color.HSBtoRGB(hsb[0], hsb[1], newBri);
            newArg = (oldAlpha << 24) | (newRGB & 0xFFFFFF);
            changed = true;
        }
        // Check alpha slider
        else if (mx >= sliderX && mx <= sliderX + sliderW && my >= alphaY && my <= alphaY + sliderH) {
            final int newAlpha = (int) Math.max(0, Math.min(255, ((mx - sliderX) / (double) sliderW) * 255));
            newArg = (newAlpha << 24) | oldRGB;
            changed = true;
        }

        if (changed) {
            if (expandedColorPicker == 1) {
                p.setBackgroundColor(newArg);
                bgColorField.setValue(String.format("%08X", newArg));
            } else {
                p.setUnfocusedBgColor(newArg);
                unfocusedBgColorField.setValue(String.format("%08X", newArg));
            }
            save();
            return true;
        }
        return false;
    }

    // ── PING COLOR PICKER ─────────────────────────────────────────

    /** Draw ping color picker (hue only, no alpha — ping is always 0xFF). */
    private void drawPingSliders(final GuiGraphicsExtractor context) {
        final ChatConfig.FontSettings fs = fs();
        final int px = TREE_WIDTH + 16;
        final int sliderX = px + 80;
        final int sliderW = 94;
        final int sliderH = 10;
        final int hueY = pingSwatchY + 22;

        // ── Hue slider ──
        final int steps = 18;
        for (int i = 0; i < steps; i++) {
            final float hue = (float) i / steps;
            final int col = 0xFF000000 | Color.HSBtoRGB(hue, 1.0f, 1.0f);
            final int sx = sliderX + i * sliderW / steps;
            final int ex = sliderX + (i + 1) * sliderW / steps;
            context.fill(sx, hueY, ex, hueY + sliderH, col);
        }
        // Border
        context.fill(sliderX, hueY, sliderX + sliderW, hueY + 1, 0xFF666666);
        context.fill(sliderX, hueY + sliderH - 1, sliderX + sliderW, hueY + sliderH, 0xFF666666);
        context.fill(sliderX, hueY, sliderX + 1, hueY + sliderH, 0xFF666666);
        context.fill(sliderX + sliderW - 1, hueY, sliderX + sliderW, hueY + sliderH, 0xFF666666);

        // Hue marker
        final int pingColor = fs.getPingColor();
        final float[] hsb = new float[3];
        Color.RGBtoHSB((pingColor >> 16) & 0xFF, (pingColor >> 8) & 0xFF, pingColor & 0xFF, hsb);
        final int markerX = Math.min(sliderX + sliderW - 2, sliderX + (int) (hsb[0] * sliderW));
        context.fill(markerX - 1, hueY - 2, markerX + 2, hueY, 0xFFFFFFFF);
        context.fill(markerX - 1, hueY + sliderH, markerX + 2, hueY + sliderH + 2, 0xFFFFFFFF);
    }

    /** Handle mouse click on ping color picker. */
    private boolean handlePingSliderClick(final double mx, final double my) {
        final ChatConfig.FontSettings fs = fs();
        final int px = TREE_WIDTH + 16;
        final int sliderX = px + 80;
        final int sliderW = 94;
        final int sliderH = 10;
        final int hueY = pingSwatchY + 22;

        // Check hue slider (ping has no alpha slider)
        if (mx >= sliderX && mx <= sliderX + sliderW && my >= hueY && my <= hueY + sliderH) {
            final float newHue = (float) Math.max(0, Math.min(1, (mx - sliderX) / (double) sliderW));
            // Keep saturation and brightness at max for vibrant ping color
            final int newRGB = Color.HSBtoRGB(newHue, 0.9f, 1.0f);
            final int newColor = 0xFF000000 | (newRGB & 0xFFFFFF);
            fs.setPingColor(newColor);
            // Find and update the pingColor field
            save();
            markDirty();
            return true;
        }
        return false;
    }

    // ── OPEN FOLDERS ───────────────────────────────────────────────

    /** Open the config folder in the OS file explorer. */
    private void openConfigFolder() {
        try {
            final String path = ChatConfig.getConfigPath();
            if (path == null) return;
            final java.io.File dir = java.nio.file.Path.of(path).getParent().toFile();
            dir.mkdirs();
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(dir);
            } else {
                final String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    Runtime.getRuntime().exec(new String[]{"explorer.exe", dir.getAbsolutePath()});
                } else if (os.contains("mac")) {
                    Runtime.getRuntime().exec(new String[]{"open", dir.getAbsolutePath()});
                } else {
                    Runtime.getRuntime().exec(new String[]{"xdg-open", dir.getAbsolutePath()});
                }
            }
        } catch (final Exception e) {
            System.err.println("[AnonChat] Failed to open config folder: " + e.getMessage());
        }
    }

    /** Open the chatlog folder in the OS file explorer. */
    private void openLogsFolder() {
        try {
            final String path = ChatConfig.getConfigPath();
            if (path == null) return;
            final java.nio.file.Path logDir = java.nio.file.Path.of(path).getParent().resolve("chatlog");
            logDir.toFile().mkdirs();
            final java.io.File dir = logDir.toFile();
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(dir);
            } else {
                // Fallback: use Runtime exec
                final String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    Runtime.getRuntime().exec(new String[]{"explorer.exe", dir.getAbsolutePath()});
                } else if (os.contains("mac")) {
                    Runtime.getRuntime().exec(new String[]{"open", dir.getAbsolutePath()});
                } else {
                    Runtime.getRuntime().exec(new String[]{"xdg-open", dir.getAbsolutePath()});
                }
            }
        } catch (final Exception e) {
            System.err.println("[AnonChat] Failed to open logs folder: " + e.getMessage());
        }
    }

    // ── CLOSE ───────────────────────────────────────────────────────



    // ── DATA ACCESS ─────────────────────────────────────────────────

    private List<TabRef> collectAllTabs() {
        final List<TabRef> tabs = new ArrayList<>();
        final ChatOverlay overlay = ChatOverlay.getInstance();
        if (overlay == null) return tabs;
        int wi = 0;
        for (final ChatWindowWidget w : overlay.getWindows()) {
            final ChatWindow cw = w.getChatWindow();
            for (int ti = 0; ti < cw.getTabs().size(); ti++) {
                tabs.add(new TabRef(wi, ti, cw, cw.getTabs().get(ti)));
            }
            wi++;
        }
        return tabs;
    }

    private ChatTabProperties tabProps() {
        final ChatTab t = currentTab();
        return t != null ? t.getConfig().getProperties() : null;
    }

    private ChatTab currentTab() {
        final List<TabRef> all = collectAllTabs();
        for (final TabRef r : all) {
            if (r.wi == selWindow && r.ti == selTab) return r.tab;
        }
        return null;
    }

    private ChatFilter currentFilter() {
        final ChatTabProperties p = tabProps();
        if (p == null || selFilter < 0 || selFilter >= p.getFilters().size()) return null;
        return p.getFilters().get(selFilter);
    }

    private void save() {
        try { ChatConfig.getInstance().save(); } catch (final Exception ignored) {}
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private record TabRef(int wi, int ti, ChatWindow window, ChatTab tab) {}
}
