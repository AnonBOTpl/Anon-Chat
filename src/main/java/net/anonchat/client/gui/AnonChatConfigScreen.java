package net.anonchat.client.gui;

import net.anonchat.client.chat.ChatTab;
import net.anonchat.client.chat.ChatWindow;
import net.anonchat.client.chat.DefaultChatWindow;
import net.anonchat.client.config.ChatConfig;
import net.anonchat.client.config.ChatFilter;
import net.anonchat.client.config.ChatMacro;
import net.anonchat.client.config.ChatTabConfig;
import net.anonchat.client.config.ChatTabProperties;
import net.anonchat.client.config.ChatWindowSettings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;
import net.minecraft.client.gui.tooltip.Tooltip;

import java.util.ArrayList;
import java.util.List;
import java.awt.Color;

/**
 * AnonChat configuration screen with a Windows-Explorer-style split layout:
 * <ul>
 *   <li><b>Left tree panel</b> — windows as collapsible folders, tabs as items, global settings at bottom</li>
 *   <li><b>Right details panel</b> — settings for the currently selected node</li>
 * </ul>
 */
public final class AnonChatConfigScreen extends Screen {

    // ── Layout constants ──────────────────────────────────────────
    private static final int TREE_WIDTH = 180;
    private static final int INDENT = 12;
    private static final int TREE_INDENT = 22;

    // ── State ─────────────────────────────────────────────────────
    private final Screen parent;
    private SelectionType selection = SelectionType.GLOBAL;
    private int selWindow = -1;
    private int selTab = -1;
    private int selFilter = -1;
    /** Which windows are expanded in the tree. */
    private boolean[] expandedWindows = new boolean[0];

    // ── Deferred rebuild (avoids StackOverflow from init() inside mouse callbacks) ──
    private boolean dirty = false;

    // ── Widget references for auto-save ──
    private TextFieldWidget tabNameField;
    private TextFieldWidget chatLimitField;
    private TextFieldWidget filterNameField;
    private TextFieldWidget addTagField;
    private TextFieldWidget addExcludeTagField;
    private TextFieldWidget bgColorField;
    private TextFieldWidget unfocusedBgColorField;
    private TextFieldWidget timeoutField;

    // Color picker state: 0=closed, 1=bgColor, 2=unfocusedBgColor
    private int expandedColorPicker = 0;
    private boolean expandedPingPicker = false;
    private int bgSwatchY = 0;
    private int unfocusedBgSwatchY = 0;
    private int pingSwatchY = 0;

    private enum SelectionType {
        GLOBAL,
        TAB,
        FILTER_LIST,
        FILTER_DETAIL,
        MACROS,
        FONT,
        PROFILES
    }

    private static Text tr(final String key, final Object... args) {
        return Text.translatable(key, args);
    }

    public AnonChatConfigScreen(final Screen parent) {
        super(tr("key.anonchat.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.clearChildren();
        final int winCount = configWindows().size();
        if (expandedWindows.length != winCount) expandedWindows = new boolean[winCount];
        rebuildAll();
    }

    private void markDirty() { this.dirty = true; }

    @Override
    public void tick() {
        super.tick();
        if (dirty) { dirty = false; init(); return; }

        // Auto-save focused text fields
        if (selection == SelectionType.TAB && tabNameField != null && tabNameField.isFocused()) {
            final ChatTabProperties p = tabProps(); if (p != null) { p.setName(tabNameField.getText()); save(); }
        }
        if (selection == SelectionType.TAB && chatLimitField != null && chatLimitField.isFocused()) {
            final String t = chatLimitField.getText();
            if (!t.isEmpty()) try {
                final ChatTabProperties p = tabProps(); if (p != null) { p.setChatLimit(Integer.parseInt(t)); save(); }
            } catch (final NumberFormatException ignored) {}
        }
        if (selection == SelectionType.TAB && bgColorField != null && bgColorField.isFocused()) {
            final ChatTabProperties p = tabProps(); if (p != null) {
                try { p.setBackgroundColor((int) Long.parseLong(bgColorField.getText(), 16)); save(); }
                catch (final NumberFormatException ignored) {}
            }
        }
        if (selection == SelectionType.TAB && unfocusedBgColorField != null && unfocusedBgColorField.isFocused()) {
            final ChatTabProperties p = tabProps(); if (p != null) {
                try { p.setUnfocusedBgColor((int) Long.parseLong(unfocusedBgColorField.getText(), 16)); save(); }
                catch (final NumberFormatException ignored) {}
            }
        }
        if (selection == SelectionType.TAB && timeoutField != null && timeoutField.isFocused()) {
            final ChatTabProperties p = tabProps(); if (p != null) {
                try { p.setMessageTimeout(Integer.parseInt(timeoutField.getText())); save(); }
                catch (final NumberFormatException ignored) {}
            }
        }
        if (selection == SelectionType.FILTER_DETAIL && filterNameField != null && filterNameField.isFocused()) {
            final ChatFilter f = currentFilter(); if (f != null) { f.setName(filterNameField.getText()); save(); }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  BUILDERS
    // ══════════════════════════════════════════════════════════════════

    private void rebuildAll() {
        // Done button
        this.addDrawableChild(ButtonWidget.builder(
            tr("key.anonchat.button.done"), btn -> close()
        ).dimensions(width - 110, height - 28, 100, 20).build());

        buildTree();
        buildPanel();
    }

    // ── Helpers ───────────────────────────────────────────────────

    /** Non-interactive label. */
    private TextWidget lbl(final String text, final int x, final int y) {
        return new TextWidget(x, y, textRenderer.getWidth(text), 12,
            Text.literal(text), textRenderer);
    }

    /** Back button in the right panel. */
    private ButtonWidget backBtn(final String label, final Runnable onBack) {
        return ButtonWidget.builder(
            Text.literal("\u25C0 " + label), btn -> { onBack.run(); markDirty(); }
        ).dimensions(TREE_WIDTH + 16, 30, 120, 20).build();
    }

    /** Toggle checkbox as a ButtonWidget. */
    private void chk(final String key, final boolean initial,
                     final int x, final int y, final int w,
                     final java.util.function.Consumer<Boolean> onChange) {
        final ButtonWidget btn = ButtonWidget.builder(
            Text.literal(initial ? "\u2611 " : "\u2610 ").append(Text.translatable(key)),
            b -> {
                final String msg = b.getMessage().getString();
                final boolean now = !msg.startsWith("\u2611");
                onChange.accept(now);
                b.setMessage(Text.literal(now ? "\u2611 " : "\u2610 ").append(Text.translatable(key)));
            }
        ).dimensions(x, y, w, 20).build();
        this.addDrawableChild(btn);
    }

    /** Pill tag button like {@code [OP_CRATE] ✕}. */
    private void tagPill(final String tag, final int x, final int y, final Runnable onRemove) {
        final int tw = textRenderer.getWidth("[" + tag + "] \u2715") + 10;
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("[" + tag + "] \u2715"),
            btn -> { onRemove.run(); save(); markDirty(); }
        ).dimensions(x, y, tw, 16).build());
    }

    // ══════════════════════════════════════════════════════════════════
    //  LEFT TREE PANEL
    // ══════════════════════════════════════════════════════════════════

    private void buildTree() {
        final List<ChatWindowSettings> wins = configWindows();
        int y = 26;

        // ── Section header ──
        this.addDrawableChild(lbl(tr("key.anonchat.section.windows").getString(), INDENT, y));
        y += 16;

        for (int wi = 0; wi < wins.size(); wi++) {
            final boolean isExp = wi < expandedWindows.length && expandedWindows[wi];
            final boolean hasServerTab = wins.get(wi).getTabs().stream().anyMatch(ChatTabConfig::isServerTab);
            final boolean isMain = wins.size() == 1 || hasServerTab;
            final String icon = isExp ? "\u25BC" : "\u25B6";
            final boolean isSelected = selWindow == wi && selection == SelectionType.GLOBAL; // window selected

            // ── Window header ──
            final int wiFin = wi;
            final String header = icon + " " + tr("key.anonchat.window.label", wi + 1).getString();
            this.addDrawableChild(ButtonWidget.builder(
                Text.literal(header),
                btn -> {
                    expandedWindows[wiFin] = !expandedWindows[wiFin];
                    if (expandedWindows[wiFin]) {
                        // Expanding: select first tab
                        final ChatWindow win = findWindow(wins.get(wiFin));
                        if (win != null && !win.getTabs().isEmpty()) {
                            selection = SelectionType.TAB; selWindow = wiFin; selTab = 0;
                        } else {
                            selection = SelectionType.GLOBAL; selWindow = wiFin;
                        }
                    } else {
                        selection = SelectionType.GLOBAL; selWindow = -1;
                    }
                    markDirty();
                }
            ).dimensions(INDENT, y, TREE_WIDTH - 56, 18).build());

            // ── Delete window button (secondary only) ──
            if (!isMain) {
                this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("\u2715"),
                    btn -> {
                        wins.remove(wiFin);
                        if (selWindow == wiFin) { selWindow = -1; selection = SelectionType.GLOBAL; }
                        else if (selWindow > wiFin) selWindow--;
                        save();
                        net.anonlauncher.chatmod.AnonChatMod.reloadEverything();
                        markDirty();
                    }
                ).dimensions(TREE_WIDTH - 34, y, 22, 18).build());
            }

            y += 22;

            // ── Expanded tabs ──
            if (isExp) {
                final ChatWindow win = findWindow(wins.get(wi));
                if (win != null) {
                    for (int ti = 0; ti < win.getTabs().size(); ti++) {
                        final ChatTab tab = win.getTabs().get(ti);
                        final boolean isServ = tab.getConfig().isServerTab();
                        final String tabName = tab.getName();
                        final boolean isTabSel = selection != SelectionType.GLOBAL
                            && selWindow == wiFin && selTab == ti;

                        final int tiFin = ti;
                        this.addDrawableChild(ButtonWidget.builder(
                            Text.literal((isTabSel ? "\u2192" : " " ) + tabName + (isServ ? " (MAIN)" : "")),
                            btn -> {
                                selection = SelectionType.TAB; selWindow = wiFin; selTab = tiFin;
                                markDirty();
                            }
                        ).dimensions(INDENT + 10, y, TREE_WIDTH - 64, 18).build());

                        // Delete tab (CUSTOM only)
                        if (!isServ) {
                            final int tiDel = ti;
                            this.addDrawableChild(ButtonWidget.builder(
                                Text.literal("\u2715"),
                            btn -> {
                                win.deleteTab(tab);
                                if (selWindow == wiFin && selTab == tiDel) {
                                    selTab = -1; selection = SelectionType.GLOBAL;
                                }
                                save();
                                net.anonlauncher.chatmod.AnonChatMod.reloadEverything();
                                markDirty();
                            }
                            ).dimensions(TREE_WIDTH - 32, y, 20, 18).build());
                        }

                        y += 20;
                    }
                }

                // + New Tab inline
                final int wiAdd = wi;
                this.addDrawableChild(ButtonWidget.builder(
                    tr("key.anonchat.tab.new_tab_inline"),
                    btn -> {
                        final ChatWindow w = findWindow(wins.get(wiAdd));
                        if (w != null) {
                            w.initializeTab(ChatTabConfig.createCustomTab("Tab " + (w.getTabs().size() + 1)));
                            selWindow = wiAdd; selTab = w.getTabs().size() - 1;
                            selection = SelectionType.TAB;
                            save();
                            net.anonlauncher.chatmod.AnonChatMod.reloadEverything();
                            markDirty();
                        }
                    }
                ).dimensions(INDENT + 10, y, TREE_WIDTH - 40, 18).build());
                y += 20;
            }
        }

        // ── Add Window button ──
        y += 4;
        this.addDrawableChild(ButtonWidget.builder(
            tr("key.anonchat.window.add"),
            btn -> {
                final ChatWindowSettings nw = ChatWindowSettings.createSecondary("Tab 1");
                wins.add(nw);
                selWindow = wins.size() - 1; selTab = 0;
                selection = SelectionType.TAB;
                save();
                net.anonlauncher.chatmod.AnonChatMod.reloadEverything();
                markDirty();
            }
        ).dimensions(INDENT, y, TREE_WIDTH - INDENT, 18).build());
        y += 20;

        // ── Spacer ──
        y += 4;

        // ── Autotext (Macros) ──
        final boolean isMacro = selection == SelectionType.MACROS;
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal((isMacro ? "\u2192" : " ") + tr("key.anonchat.section.autotext").getString()),
            btn -> { selection = SelectionType.MACROS; markDirty(); }
        ).dimensions(INDENT, y, TREE_WIDTH - INDENT, 18).build());
        y += 24;

        // ── Profiles ──
        final boolean isProf = selection == SelectionType.PROFILES;
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal((isProf ? "\u2192" : " ") + tr("key.anonchat.section.profiles").getString()),
            btn -> { selection = SelectionType.PROFILES; markDirty(); }
        ).dimensions(INDENT, y, TREE_WIDTH - INDENT, 18).build());
        y += 22;

        // ── Appearance ──
        final boolean isFont = selection == SelectionType.FONT;
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal((isFont ? "\u2192" : " ") + tr("key.anonchat.section.appearance").getString()),
            btn -> { selection = SelectionType.FONT; markDirty(); }
        ).dimensions(INDENT, y, TREE_WIDTH - INDENT, 18).build());
    }

    // ══════════════════════════════════════════════════════════════════
    //  RIGHT DETAILS PANEL
    // ══════════════════════════════════════════════════════════════════

    private void buildPanel() {
        final int px = TREE_WIDTH + 16; // panel X start

        switch (selection) {
            case GLOBAL         -> buildGlobalPlaceholder(px);
            case TAB            -> buildTabPanel(px);
            case FILTER_LIST    -> buildFilterListPanel(px);
            case FILTER_DETAIL  -> buildFilterDetailPanel(px);
            case MACROS         -> buildMacrosPanel(px);
            case FONT           -> buildFontPanel(px);
            case PROFILES       -> buildProfilesPanel(px);
        }
    }

    // ── Global / window-selected placeholder ──────────────────────

    private void buildGlobalPlaceholder(final int px) {
        this.addDrawableChild(lbl(tr("key.anonchat.global.title").getString(), px, 26));
        this.addDrawableChild(lbl(tr("key.anonchat.global.hint1").getString(), px, 48));
        this.addDrawableChild(lbl(tr("key.anonchat.global.hint2").getString(), px, 64));
    }

    // ── Autotext (Macros) ─────────────────────────────────────────

    private void buildMacrosPanel(final int px) {
        this.addDrawableChild(lbl(tr("key.anonchat.macro.title").getString(), px, 26));
        this.addDrawableChild(lbl(tr("key.anonchat.macro.hint").getString(), px, 42));

        final List<ChatMacro> macros = ChatConfig.getInstance() != null
            ? ChatConfig.getInstance().getMacros() : new ArrayList<>();

        int y = 64;
        final int cw = width - px - 20;

        for (int i = 0; i < macros.size(); i++) {
            final int fi = i;
            final ChatMacro m = macros.get(i);

            // Name
            this.addDrawableChild(lbl(tr("key.anonchat.label.name").getString(), px, y));
            final TextFieldWidget nameField = new TextFieldWidget(textRenderer, px + 50, y - 2, 150, 18, Text.literal("Name"));
            nameField.setText(m.getName());
            nameField.setMaxLength(32);
            nameField.setChangedListener(s -> { m.setName(s); save(); });
            this.addDrawableChild(nameField);
            y += 22;

            // Key (click to capture) + clear
            this.addDrawableChild(ButtonWidget.builder(
                Text.literal(tr("key.anonchat.macro.key",
                    capturingMacroIndex == fi ? tr("key.anonchat.macro.press_key").getString() : keyName(m.getKeyCode())).getString()),
                b -> { capturingMacroIndex = fi; this.setFocused(null); markDirty(); }
            ).dimensions(px, y, 150, 18).build());
            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("\u2715"),
                b -> { m.setKeyCode(-1); save(); markDirty(); }
            ).dimensions(px + 155, y, 20, 18).build());
            y += 22;

            // Text / command
            this.addDrawableChild(lbl(tr("key.anonchat.macro.text").getString(), px, y));
            final TextFieldWidget textField = new TextFieldWidget(textRenderer, px + 50, y - 2, cw - 50, 18, Text.literal("Text"));
            textField.setText(m.getText());
            textField.setMaxLength(256);
            textField.setChangedListener(s -> { m.setText(s); save(); });
            this.addDrawableChild(textField);
            y += 22;

            // Command toggle
            chk("key.anonchat.macro.command", m.isCommand(), px, y, cw, v -> { m.setCommand(v); save(); });
            y += 22;

            // Delete macro
            this.addDrawableChild(ButtonWidget.builder(
                tr("key.anonchat.macro.delete"),
                b -> { macros.remove(fi); save(); markDirty(); }
            ).dimensions(px, y, 120, 18).build());
            y += 28;
        }

        // + Add Macro
        this.addDrawableChild(ButtonWidget.builder(
            tr("key.anonchat.macro.add"),
            b -> {
                macros.add(new ChatMacro("Macro " + (macros.size() + 1), -1, ""));
                save(); markDirty();
            }
        ).dimensions(px, y, 130, 20).build());
    }

    // ── FONT PANEL ───────────────────────────────────────────────

    /** Renders a stepper: label (range) value [◀] [▶] */
    private void addStepper(final String labelKey, final int val, final int min, final int max,
                            final int x, final int y,
                            final java.util.function.Consumer<Integer> onChange) {
        final String label = tr(labelKey).getString();
        final String range = " (" + min + "-" + max + ")";
        final int labelW = textRenderer.getWidth(label + range);
        this.addDrawableChild(lbl(label + range, x, y));

        final int btnSize = 16;
        final int valW = 24;
        final int valX = x + labelW + 4;

        this.addDrawableChild(lbl(String.valueOf(val), valX, y));

        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("\u25C0"),
            b -> { onChange.accept((val > min) ? val - 1 : val); markDirty(); }
        ).dimensions(valX + valW + 2, y, btnSize, btnSize).build());
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("\u25B6"),
            b -> { onChange.accept((val < max) ? val + 1 : val); markDirty(); }
        ).dimensions(valX + valW + 2 + btnSize + 2, y, btnSize, btnSize).build());
    }

    private void buildFontPanel(final int px) {
        final ChatConfig.FontSettings fs = ChatConfig.getInstance() != null
            ? ChatConfig.getInstance().getFontSettings()
            : new ChatConfig.FontSettings();

        this.addDrawableChild(lbl(tr("key.anonchat.appearance.title").getString(), px, 26));
        final int cw = width - px - 20;
        int y = 48;

        // ── Chatlog section ──────────────────────────────────────────
        chk("key.anonchat.appearance.chatlog", fs.isChatlogEnabled(), px, y, cw, v -> { fs.setChatlogEnabled(v); save(); });
        y += 20;

        // Open chat logs folder button
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("\uD83D\uDCC2 " + tr("key.anonchat.appearance.open_logs").getString()),
            btn -> openLogsFolder()
        ).dimensions(px + 20, y, 180, 18).build());
        y += 24;

        // ── Ping section ───────────────────────────────────────────
        chk("key.anonchat.appearance.ping.enabled", fs.isPingEnabled(), px, y, cw, v -> { fs.setPingEnabled(v); save(); });
        y += 22;

        // Ping color (with expandable picker)
        this.addDrawableChild(lbl(tr("key.anonchat.appearance.ping.color").getString(), px, y));
        pingSwatchY = y;
        final TextFieldWidget pingColorField = new TextFieldWidget(textRenderer, px + 80, y - 2, 70, 18, Text.literal("FFAA00"));
        pingColorField.setText(String.format("%06X", fs.getPingColor() & 0xFFFFFF));
        pingColorField.setMaxLength(6);
        pingColorField.setChangedListener(s -> {
            try {
                final int c = 0xFF000000 | (int) Long.parseLong(s, 16);
                fs.setPingColor(c); save();
            } catch (final NumberFormatException ignored) {}
        });
        this.addDrawableChild(pingColorField);
        // Color swatch button (toggles the hue picker)
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal(""),
            b -> { expandedPingPicker = !expandedPingPicker; markDirty(); }
        ).dimensions(px + 156, y, 18, 18).build());
        y += expandedPingPicker ? 32 : 22;

        // Ping sound
        final String soundLabel = tr("key.anonchat.appearance.ping.sound").getString();
        final String soundDisplay = net.anonchat.client.chat.ChatTabImpl.getSoundDisplayName(fs.getPingSound());
        this.addDrawableChild(lbl(soundLabel + " " + soundDisplay, px, y));
        final int soundLblW = textRenderer.getWidth(soundLabel + " " + soundDisplay);
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("\u25C0"),
            b -> { fs.setPingSound(net.anonchat.client.chat.ChatTabImpl.prevSound(fs.getPingSound())); markDirty(); save(); }
        ).dimensions(px + soundLblW + 4, y, 16, 16).build());
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("\u25B6"),
            b -> { fs.setPingSound(net.anonchat.client.chat.ChatTabImpl.nextSound(fs.getPingSound())); markDirty(); save(); }
        ).dimensions(px + soundLblW + 4 + 18, y, 16, 16).build());
        y += 24;

        // Separator
        this.addDrawableChild(lbl("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500", px, y));
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
        this.addDrawableChild(lbl(tr("key.anonchat.font.alignment").getString(), px, y));
        final String curAlign = fs.getTextAlignment();
        final int btnW = (Math.min(cw, 200) - 8) / 3;
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("LEFT".equals(curAlign) ? "\u2611 " : "\u2610 ").append(tr("key.anonchat.font.align_left")),
            b -> { fs.setTextAlignment("LEFT"); markDirty(); save(); }
        ).dimensions(px + 80, y, btnW, 18).build());
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("CENTER".equals(curAlign) ? "\u2611 " : "\u2610 ").append(tr("key.anonchat.font.align_center")),
            b -> { fs.setTextAlignment("CENTER"); markDirty(); save(); }
        ).dimensions(px + 80 + btnW + 4, y, btnW, 18).build());
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("RIGHT".equals(curAlign) ? "\u2611 " : "\u2610 ").append(tr("key.anonchat.font.align_right")),
            b -> { fs.setTextAlignment("RIGHT"); markDirty(); save(); }
        ).dimensions(px + 80 + btnW * 2 + 8, y, btnW, 18).build());
        y += 26;

        // Left margin
        addStepper("key.anonchat.font.margin", fs.getLeftMargin(), 0, 12, px, y,
            v -> { fs.setLeftMargin(v); save(); });
        y += 30;

        // ── Config folder section ───────────────────────────────────────
        this.addDrawableChild(lbl("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500", px, y));
        y += 16;

        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("\uD83D\uDCC1 " + tr("key.anonchat.appearance.open_config").getString()),
            btn -> openConfigFolder()
        ).dimensions(px, y, 200, 18).tooltip(Tooltip.of(tr("key.anonchat.tooltip.open_config"))).build());
    }

    private static String keyName(final int code) {
        if (code < 0) return "-";
        return InputUtil.fromKeyCode(code, 0).getLocalizedText().getString();
    }

    // ── Tab Settings ──────────────────────────────────────────────

    private void buildTabPanel(final int px) {
        final ChatTabProperties props = tabProps();
        final ChatTab tab = currentTab();
        if (props == null || tab == null) {
            selection = SelectionType.GLOBAL; selWindow = -1; markDirty();
            return;
        }

        this.addDrawableChild(lbl(tr("key.anonchat.tab.title").getString(), px, 26));
        this.addDrawableChild(lbl(tab.getName(), px, 40));

        int y = 56;
        final int cw = width - px - 20;

        // Name + Edit Filters on same row
        this.addDrawableChild(lbl(tr("key.anonchat.label.name").getString(), px, y));
        tabNameField = new TextFieldWidget(textRenderer, px + 50, y - 2, 150, 18, Text.literal("Name"));
        tabNameField.setText(props.getName());
        tabNameField.setMaxLength(32);
        this.addDrawableChild(tabNameField);
        this.addDrawableChild(ButtonWidget.builder(
            tr("key.anonchat.tab.edit_filters", props.getFilters().size()),
            btn -> { selection = SelectionType.FILTER_LIST; selFilter = -1; markDirty(); }
        ).dimensions(px + 210, y - 2, 140, 18).tooltip(Tooltip.of(tr("key.anonchat.tooltip.edit_filters"))).build());
        y += 26;

        chk("key.anonchat.tab.combine", props.isCombineChatMessages(), px, y, cw, v -> { props.setCombineChatMessages(v); save(); });
        y += 22;
        chk("key.anonchat.tab.background", props.isBackground(), px, y, cw, v -> { props.setBackground(v); save(); });
        y += 22;
        chk("key.anonchat.tab.unfocused_bg", props.isUnfocusedBackground(), px, y, cw, v -> { props.setUnfocusedBackground(v); save(); });
        y += 26;

        // Message Limit
        this.addDrawableChild(lbl(tr("key.anonchat.tab.limit_legacy").getString(), px, y));
        chatLimitField = new TextFieldWidget(textRenderer, px + 100, y - 2, 50, 18, Text.literal("Limit"));
        chatLimitField.setText(String.valueOf(props.getChatLimit()));
        chatLimitField.setMaxLength(4);
        chatLimitField.setTextPredicate(s -> s.matches("\\d*"));
        this.addDrawableChild(chatLimitField);
        y += 26;

        // ── Window BG Color (active) with picker ──
        this.addDrawableChild(lbl(tr("key.anonchat.tab.window_bg_legacy").getString(), px, y));
        bgColorField = new TextFieldWidget(textRenderer, px + 140, y - 2, 72, 18, Text.literal("40000000"));
        bgColorField.setText(String.format("%08X", props.getBackgroundColor()));
        bgColorField.setMaxLength(8);
        bgColorField.setTextPredicate(s -> s.matches("[0-9a-fA-F]*"));
        this.addDrawableChild(bgColorField);
        bgSwatchY = y;
        // Swatch button (toggles picker)
        this.addDrawableChild(ButtonWidget.builder(Text.literal(""),
            b -> { expandedColorPicker = (expandedColorPicker == 1) ? 0 : 1; markDirty(); })
            .dimensions(px + 216, y - 2, 18, 18).build());
        y += (expandedColorPicker == 1) ? 90 : 22;

        // ── Window BG Color (inactive) with picker ──
        this.addDrawableChild(lbl(tr("key.anonchat.tab.unfocused_bg_color").getString(), px, y));
        unfocusedBgColorField = new TextFieldWidget(textRenderer, px + 140, y - 2, 72, 18, Text.literal("30000000"));
        unfocusedBgColorField.setText(String.format("%08X", props.getUnfocusedBgColor()));
        unfocusedBgColorField.setMaxLength(8);
        unfocusedBgColorField.setTextPredicate(s -> s.matches("[0-9a-fA-F]*"));
        this.addDrawableChild(unfocusedBgColorField);
        unfocusedBgSwatchY = y;
        // Swatch button (toggles picker)
        this.addDrawableChild(ButtonWidget.builder(Text.literal(""),
            b -> { expandedColorPicker = (expandedColorPicker == 2) ? 0 : 2; markDirty(); })
            .dimensions(px + 216, y - 2, 18, 18).build());
        y += (expandedColorPicker == 2) ? 90 : 22;

        // Message timeout
        this.addDrawableChild(lbl(tr("key.anonchat.tab.timeout_legacy").getString(), px, y));
        timeoutField = new TextFieldWidget(textRenderer, px + 170, y - 2, 40, 18, Text.literal("0"));
        timeoutField.setText(String.valueOf(props.getMessageTimeout()));
        timeoutField.setMaxLength(5);
        timeoutField.setTextPredicate(s -> s.matches("\\d*"));
        this.addDrawableChild(timeoutField);
        y += 26;

        // ── Font override per-tab ──────────────────────────────────────
        chk("key.anonchat.tab.override_font", props.isOverrideFont(), px, y, cw, v -> { props.setOverrideFont(v); save(); });
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
            this.addDrawableChild(lbl(tr("key.anonchat.font.alignment").getString(), fontPx, y));
            final String curAlign = props.getTextAlignment();
            final int alignBtnW = (Math.min(cw - 16, 200) - 8) / 3;
            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("LEFT".equals(curAlign) ? "\u2611 " : "\u2610 ").append(tr("key.anonchat.font.align_left")),
                b -> { props.setTextAlignment("LEFT"); markDirty(); save(); }
            ).dimensions(fontPx + 80, y, alignBtnW, 18).build());
            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("CENTER".equals(curAlign) ? "\u2611 " : "\u2610 ").append(tr("key.anonchat.font.align_center")),
                b -> { props.setTextAlignment("CENTER"); markDirty(); save(); }
            ).dimensions(fontPx + 80 + alignBtnW + 4, y, alignBtnW, 18).build());
            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("RIGHT".equals(curAlign) ? "\u2611 " : "\u2610 ").append(tr("key.anonchat.font.align_right")),
                b -> { props.setTextAlignment("RIGHT"); markDirty(); save(); }
            ).dimensions(fontPx + 80 + alignBtnW * 2 + 8, y, alignBtnW, 18).build());
            y += 26;

            // Left margin
            addStepper("key.anonchat.font.margin", props.getLeftMargin(), 0, 12, fontPx, y,
                v -> { props.setLeftMargin(v); save(); });
            y += 26;
        }

        // Delete Tab (CUSTOM only)
        if (!tab.getConfig().isServerTab()) {
            this.addDrawableChild(ButtonWidget.builder(
                tr("key.anonchat.tab.delete_tab"),
                btn -> {
                    final ChatWindow win = currentWindow();
                    if (win != null) {
                        win.deleteTab(currentTab());
                        save();
                        net.anonlauncher.chatmod.AnonChatMod.reloadEverything();
                        selection = SelectionType.GLOBAL; markDirty();
                    }
                }
            ).dimensions(px, y, 120, 20).build());
        }
    }

    // ── Filter List Panel ─────────────────────────────────────────

    private void buildFilterListPanel(final int px) {
        final ChatTabProperties props = tabProps();
        if (props == null) { selection = SelectionType.GLOBAL; markDirty(); return; }

        this.addDrawableChild(backBtn(tr("key.anonchat.filter.back").getString(), () -> selection = SelectionType.TAB));
        this.addDrawableChild(lbl(tr("key.anonchat.filter.title", props.getName()).getString(), px, 26));

        final List<ChatFilter> raw = props.getFilters();
        final List<ChatFilter> filters = raw.isEmpty() ? new ArrayList<>() : raw;
        if (raw.isEmpty()) {
            props.setFilters(filters);
        }
        int y = 54;

        for (int i = 0; i < filters.size(); i++) {
            final ChatFilter f = filters.get(i);
            final int fi = i;
            this.addDrawableChild(ButtonWidget.builder(
                Text.literal(f.getName() + " (inc:" + f.getIncludeTags().size() + " exc:" + f.getExcludeTags().size() + ")"),
                btn -> { selFilter = fi; selection = SelectionType.FILTER_DETAIL; markDirty(); }
            ).dimensions(px + 8, y, width - px - 70, 18).build());

            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("\u2715"),
                btn -> { filters.remove(fi); save(); markDirty(); }
            ).dimensions(width - 54, y, 22, 18).build());

            y += 22;
        }

        y += 4;
        this.addDrawableChild(ButtonWidget.builder(
            tr("key.anonchat.filter.add"),
            btn -> {
                final ChatFilter nf = new ChatFilter();
                nf.setName("Filter " + (filters.size() + 1));
                filters.add(nf);
                selFilter = filters.size() - 1;
                selection = SelectionType.FILTER_DETAIL; save(); markDirty();
            }
        ).dimensions(px, y, 130, 20).build());
    }

    // ── Filter Detail Panel ───────────────────────────────────────

    private void buildFilterDetailPanel(final int px) {
        final ChatFilter filter = currentFilter();
        if (filter == null) { selection = SelectionType.FILTER_LIST; markDirty(); return; }

        this.addDrawableChild(backBtn(tr("key.anonchat.filter.back_list").getString(), () -> selection = SelectionType.FILTER_LIST));
        this.addDrawableChild(lbl(tr("key.anonchat.filter.editor").getString(), px, 26));
        this.addDrawableChild(lbl(filter.getName(), px, 52));

        int y = 68;
        final int cw = width - px - 20;

        // Name
        this.addDrawableChild(lbl(tr("key.anonchat.label.name").getString(), px, y));
        filterNameField = new TextFieldWidget(textRenderer, px + 50, y - 2, 180, 18, Text.literal("Filter name"));
        filterNameField.setText(filter.getName());
        filterNameField.setMaxLength(32);
        this.addDrawableChild(filterNameField);
        y += 26;

        // ── Include Tags ──
        this.addDrawableChild(lbl(tr("key.anonchat.filter.include").getString(), px, y));
        y += 16;

        int tagX = px + 12;
        int tagRowY = y;
        for (int i = 0; i < filter.getIncludeTags().size(); i++) {
            final int ti = i;
            final String tag = filter.getIncludeTags().get(i);
            final int tw = textRenderer.getWidth("[" + tag + "] \u2715") + 14;
            if (tagX + tw > width - 20) { tagX = px + 12; tagRowY += 20; }
            tagPill(tag, tagX, tagRowY, () -> filter.getIncludeTags().remove(ti));
            tagX += tw + 8;
        }
        y = Math.max(y + 16, tagRowY + (filter.getIncludeTags().isEmpty() ? 0 : 22));

        addTagField = new TextFieldWidget(textRenderer, px + 12, y, 140, 16, Text.literal(""));
        addTagField.setMaxLength(64);
        addTagField.setPlaceholder(tr("key.anonchat.filter.include_placeholder"));
        this.addDrawableChild(addTagField);
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("+"),
            btn -> doAddTag(filter, true)
        ).dimensions(px + 158, y, 22, 16).tooltip(Tooltip.of(tr("key.anonchat.tooltip.include_tag"))).build());
        y += 22;

        // ── Exclude Tags ──
        this.addDrawableChild(lbl(tr("key.anonchat.filter.exclude").getString(), px, y));
        y += 16;

        int excX = px + 12;
        int excRowY = y;
        for (int i = 0; i < filter.getExcludeTags().size(); i++) {
            final int ti = i;
            final String tag = filter.getExcludeTags().get(i);
            final int tw = textRenderer.getWidth("[" + tag + "] \u2715") + 14;
            if (excX + tw > width - 20) { excX = px + 12; excRowY += 20; }
            tagPill(tag, excX, excRowY, () -> filter.getExcludeTags().remove(ti));
            excX += tw + 8;
        }
        y = Math.max(y + 16, excRowY + (filter.getExcludeTags().isEmpty() ? 0 : 22));

        addExcludeTagField = new TextFieldWidget(textRenderer, px + 12, y, 140, 16, Text.literal(""));
        addExcludeTagField.setMaxLength(64);
        addExcludeTagField.setPlaceholder(tr("key.anonchat.filter.exclude_placeholder"));
        this.addDrawableChild(addExcludeTagField);
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("+"),
            btn -> doAddTag(filter, false)
        ).dimensions(px + 158, y, 22, 16).tooltip(Tooltip.of(tr("key.anonchat.tooltip.exclude_tag"))).build());
        y += 24;

        // Options
        chk("key.anonchat.filter.sound", filter.isShouldPlaySound(), px, y, cw, v -> { filter.setShouldPlaySound(v); save(); });
        y += 22;
        chk("key.anonchat.filter.change_bg", filter.isShouldChangeBackground(), px, y, cw, v -> { filter.setShouldChangeBackground(v); save(); });
        y += 28;

        // Delete
        this.addDrawableChild(ButtonWidget.builder(
            tr("key.anonchat.filter.delete"),
            btn -> {
                final ChatTabProperties p = tabProps();
                if (p != null) { p.getFilters().remove(selFilter); save();
                    selection = SelectionType.FILTER_LIST; markDirty(); }
            }
        ).dimensions(px, y, 120, 20).build());
    }

    // ── Tag helpers ───────────────────────────────────────────────

    private void doAddTag(final ChatFilter filter, final boolean include) {
        final TextFieldWidget src = include ? addTagField : addExcludeTagField;
        if (src == null) return;
        final String t = src.getText().trim();
        if (t.isEmpty()) return;
        if (include) {
            if (filter.getIncludeTags().isEmpty()) filter.setIncludeTags(new ArrayList<>());
            filter.getIncludeTags().add(t);
        } else {
            if (filter.getExcludeTags().isEmpty()) filter.setExcludeTags(new ArrayList<>());
            filter.getExcludeTags().add(t);
        }
        src.setText(""); save(); markDirty();
    }

    // ── PROFILES ─────────────────────────────────────────────────────

    private TextFieldWidget profileNameField;

    private void buildProfilesPanel(final int px) {
        this.addDrawableChild(lbl(tr("key.anonchat.profiles.title").getString(), px, 26));
        final int cw = width - px - 20;
        int y = 46;

        final ChatConfig config = ChatConfig.getInstance();
        final List<String> profiles = config != null ? config.listProfiles() : new java.util.ArrayList<>();
        if (!profiles.contains("default")) profiles.add(0, "default");

        for (final String name : profiles) {
            final boolean isDefault = "default".equals(name);
            final boolean isCurrent = (config != null && name.equals(config.getCurrentProfile()));

            final String label = (isCurrent ? "\u2192" : " ") + "\u2630 " + name;
            final int lblW = textRenderer.getWidth(label) + 10;
            this.addDrawableChild(lbl(label, px, y));

            final String finName = name;
            this.addDrawableChild(ButtonWidget.builder(
                tr("key.anonchat.profiles.load"),
                btn -> {
                    if (ChatConfig.getInstance() != null) {
                        ChatConfig.getInstance().loadProfile(finName);
                        net.anonlauncher.chatmod.AnonChatMod.reloadEverything();
                        markDirty();
                    }
                }
            ).dimensions(px + lblW + 4, y, 60, 16).tooltip(Tooltip.of(tr("key.anonchat.tooltip.profile_load"))).build());

            if (!isDefault) {
                this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("\u2715"),
                    btn -> {
                        if (ChatConfig.getInstance() != null) {
                            ChatConfig.getInstance().deleteProfile(finName);
                            markDirty();
                        }
                    }
                ).dimensions(px + lblW + 4 + 64, y, 20, 16).tooltip(Tooltip.of(tr("key.anonchat.tooltip.profile_delete"))).build());
            }

            y += 20;
        }

        y += 6;
        this.addDrawableChild(lbl("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500", px, y));
        y += 16;

        this.addDrawableChild(lbl(tr("key.anonchat.profiles.save_as").getString(), px, y));
        profileNameField = new TextFieldWidget(textRenderer, px + 100, y - 2, 150, 18, Text.literal(""));
        profileNameField.setMaxLength(32);
        this.addDrawableChild(profileNameField);
        this.addDrawableChild(ButtonWidget.builder(
            tr("key.anonchat.profiles.save_btn"),
            btn -> {
                if (ChatConfig.getInstance() == null) return;
                String name = profileNameField.getText().trim();
                if (name.isEmpty()) name = ChatConfig.getInstance().getCurrentProfile();
                if (name.isEmpty()) name = "default";
                ChatConfig.getInstance().saveProfile(name);
                ChatConfig.getInstance().setCurrentProfile(name);
                profileNameField.setText("");
                markDirty();
            }
        ).dimensions(px + 260, y - 2, 80, 18).tooltip(Tooltip.of(tr("key.anonchat.tooltip.profile_save"))).build());
        y += 24;

        this.addDrawableChild(lbl(tr("key.anonchat.profiles.save_hint").getString(), px, y));
    }

    // ── Keyboard ─────────────────────────────────────────────────

    /** Which macro is currently capturing its next key press, or -1. */
    private int capturingMacroIndex = -1;

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
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
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── COLOR PICKER ────────────────────────────────────────────

    private void drawColorSliders(final DrawContext context) {
        final ChatTabProperties p = tabProps();
        if (p == null) return;

        final int argb = expandedColorPicker == 1 ? p.getBackgroundColor() : p.getUnfocusedBgColor();
        final int swatchY = expandedColorPicker == 1 ? bgSwatchY : unfocusedBgSwatchY;
        final int px = TREE_WIDTH + 16;

        // Swatches already drawn in render() (always visible)
        // Position of sliders (relative to the selected swatch)
        final int sliderX = px + 140;
        final int sliderW = 94;
        final int sliderH = 10;
        final int hueY = swatchY + 18;
        final int satY = swatchY + 32;
        final int briY = swatchY + 46;
        final int alphaY = swatchY + 60;

        // ── Hue slider (rainbow) ──
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
                context.fill(sliderX + i, alphaY + j,
                    Math.min(sliderX + i + checkSize, sliderX + sliderW), alphaY + j + checkSize, 0xFF333333);
                context.fill(sliderX + i + checkSize, alphaY + j + checkSize,
                    Math.min(sliderX + i + checkSize * 2, sliderX + sliderW), alphaY + j + checkSize * 2, 0xFF333333);
            }
        }
        // Alpha gradient on top
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

    private boolean handleSliderClick(final double mx, final double my) {
        final ChatTabProperties p = tabProps();
        if (p == null) return false;

        final int swatchY = expandedColorPicker == 1 ? bgSwatchY : unfocusedBgSwatchY;
        final int px = TREE_WIDTH + 16;
        final int sliderX = px + 140;
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

        if (mx >= sliderX && mx <= sliderX + sliderW && my >= hueY && my <= hueY + sliderH) {
            final float newHue = (float) Math.max(0, Math.min(1, (mx - sliderX) / (double) sliderW));
            float sat = hsb[1];
            float bri = hsb[2];
            if (sat < 0.1f) sat = 0.8f;
            if (bri < 0.1f) bri = 0.8f;
            final int newRGB = Color.HSBtoRGB(newHue, sat, bri);
            newArg = (oldAlpha << 24) | (newRGB & 0xFFFFFF);
            changed = true;
        } else if (mx >= sliderX && mx <= sliderX + sliderW && my >= satY && my <= satY + sliderH) {
            final float newSat = (float) Math.max(0, Math.min(1, (mx - sliderX) / (double) sliderW));
            final int newRGB = Color.HSBtoRGB(hsb[0], newSat, hsb[2]);
            newArg = (oldAlpha << 24) | (newRGB & 0xFFFFFF);
            changed = true;
        } else if (mx >= sliderX && mx <= sliderX + sliderW && my >= briY && my <= briY + sliderH) {
            final float newBri = (float) Math.max(0, Math.min(1, (mx - sliderX) / (double) sliderW));
            final int newRGB = Color.HSBtoRGB(hsb[0], hsb[1], newBri);
            newArg = (oldAlpha << 24) | (newRGB & 0xFFFFFF);
            changed = true;
        } else if (mx >= sliderX && mx <= sliderX + sliderW && my >= alphaY && my <= alphaY + sliderH) {
            final int newAlpha = (int) Math.max(0, Math.min(255, ((mx - sliderX) / (double) sliderW) * 255));
            newArg = (newAlpha << 24) | oldRGB;
            changed = true;
        }

        if (changed) {
            if (expandedColorPicker == 1) {
                p.setBackgroundColor(newArg);
                bgColorField.setText(String.format("%08X", newArg));
            } else {
                p.setUnfocusedBgColor(newArg);
                unfocusedBgColorField.setText(String.format("%08X", newArg));
            }
            save();
            return true;
        }
        return false;
    }

    // ── PING COLOR PICKER ─────────────────────────────────────────

    /** Draw ping color picker (hue only, no alpha — ping is always 0xFF). */
    private void drawPingSliders(final DrawContext context) {
        final ChatConfig.FontSettings fs = ChatConfig.getInstance() != null
            ? ChatConfig.getInstance().getFontSettings() : new ChatConfig.FontSettings();
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
        final ChatConfig.FontSettings ffs = ChatConfig.getInstance() != null
            ? ChatConfig.getInstance().getFontSettings() : new ChatConfig.FontSettings();
        final int pingColor = ffs.getPingColor();
        final float[] hsb = new float[3];
        Color.RGBtoHSB((pingColor >> 16) & 0xFF, (pingColor >> 8) & 0xFF, pingColor & 0xFF, hsb);
        final int markerX = Math.min(sliderX + sliderW - 2, sliderX + (int) (hsb[0] * sliderW));
        context.fill(markerX - 1, hueY - 2, markerX + 2, hueY, 0xFFFFFFFF);
        context.fill(markerX - 1, hueY + sliderH, markerX + 2, hueY + sliderH + 2, 0xFFFFFFFF);
    }

    /** Handle mouse click on ping color picker. */
    private boolean handlePingSliderClick(final double mx, final double my) {
        final ChatConfig.FontSettings fs = ChatConfig.getInstance() != null
            ? ChatConfig.getInstance().getFontSettings() : new ChatConfig.FontSettings();
        final int px = TREE_WIDTH + 16;
        final int sliderX = px + 80;
        final int sliderW = 94;
        final int sliderH = 10;
        final int hueY = pingSwatchY + 22;

        if (mx >= sliderX && mx <= sliderX + sliderW && my >= hueY && my <= hueY + sliderH) {
            final float newHue = (float) Math.max(0, Math.min(1, (mx - sliderX) / (double) sliderW));
            final int newRGB = Color.HSBtoRGB(newHue, 0.9f, 1.0f);
            final int newColor = 0xFF000000 | (newRGB & 0xFFFFFF);
            fs.setPingColor(newColor);
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

    // ── Render ───────────────────────────────────────────────────

    @Override
    public void render(final DrawContext ctx, final int mx, final int my, final float delta) {
        this.renderBackground(ctx, mx, my, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, this.title, width / 2, 6, 0xFFFFFF);

        // Tree panel background (subtle)
        ctx.fill(0, 0, TREE_WIDTH + 1, height, 0x20FFFFFF);

        // Vertical divider between tree and panel
        ctx.fill(TREE_WIDTH, 24, TREE_WIDTH + 1, height - 4, 0xFF555555);

        super.render(ctx, mx, my, delta);

        // Always draw swatch colors (even when picker is closed)
        if (selection == SelectionType.TAB) {
            final ChatTabProperties p = tabProps();
            if (p != null && bgColorField != null && unfocusedBgColorField != null) {
                final int px = TREE_WIDTH + 16;
                ctx.fill(px + 216, bgSwatchY, px + 234, bgSwatchY + 16, p.getBackgroundColor());
                ctx.fill(px + 216, unfocusedBgSwatchY, px + 234, unfocusedBgSwatchY + 16, p.getUnfocusedBgColor());
            }
        }

        // Draw ping color swatch
        if (selection == SelectionType.FONT) {
            final ChatConfig.FontSettings fs = ChatConfig.getInstance() != null
                ? ChatConfig.getInstance().getFontSettings() : new ChatConfig.FontSettings();
            final int px = TREE_WIDTH + 16;
            ctx.fill(px + 156, pingSwatchY, px + 174, pingSwatchY + 18, fs.getPingColor());
        }

        // Draw expanded color picker sliders
        if (expandedColorPicker > 0 && selection == SelectionType.TAB) {
            drawColorSliders(ctx);
        }
        // Draw ping color picker
        if (expandedPingPicker && selection == SelectionType.FONT) {
            drawPingSliders(ctx);
        }
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (expandedColorPicker > 0 && selection == SelectionType.TAB) {
            return handleSliderClick(mx, my);
        }
        if (expandedPingPicker && selection == SelectionType.FONT) {
            return handlePingSliderClick(mx, my);
        }
        return false;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    // ══════════════════════════════════════════════════════════════════
    //  DATA ACCESS
    // ══════════════════════════════════════════════════════════════════

    private static List<ChatWindowSettings> configWindows() {
        final ChatConfig c = ChatConfig.getInstance();
        return c != null ? c.getWindows() : List.of();
    }

    private ChatTabProperties tabProps() {
        final ChatTab t = currentTab();
        return t != null ? t.getConfig().getProperties() : null;
    }

    private ChatTab currentTab() {
        final ChatWindow w = currentWindow();
        if (w == null || selTab < 0 || selTab >= w.getTabs().size()) return null;
        return w.getTabs().get(selTab);
    }

    private ChatWindow currentWindow() {
        final List<ChatWindowSettings> ws = configWindows();
        if (selWindow < 0 || selWindow >= ws.size()) return null;
        return findWindow(ws.get(selWindow));
    }

    private ChatFilter currentFilter() {
        final ChatTabProperties p = tabProps();
        if (p == null || selFilter < 0 || selFilter >= p.getFilters().size()) return null;
        return p.getFilters().get(selFilter);
    }

    private ChatWindow findWindow(final ChatWindowSettings s) {
        final ChatOverlay o = ChatOverlay.getInstance();
        for (final ChatWindowWidget w : o.getWindows()) {
            if (w.getSettings() == s) return w.getChatWindow();
        }
        System.err.println("[AnonChat] findWindow: no matching overlay widget for settings, creating orphan (this shouldn't happen)");
        return new DefaultChatWindow(s);
    }

    private void save() {
        try { ChatConfig.getInstance().save(); } catch (final Exception ignored) {}
    }


}
