package net.anonchat.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class ChatConfig {

    private static volatile ChatConfig INSTANCE;
    private static String configPath;

    private final transient Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @SerializedName("windows")
    private List<ChatWindowSettings> windows;

    @SerializedName("macros")
    private List<ChatMacro> macros;

    @SerializedName("configVersion")
    private int configVersion = 1;

    @SerializedName("currentProfile")
    private String currentProfile = "default";

    @SerializedName("fontSettings")
    private FontSettings fontSettings;

    public static final class FontSettings {
        @SerializedName("lineSpacing") private int lineSpacing = 10;
        @SerializedName("messageSpacing") private int messageSpacing = 0;
        @SerializedName("textOpacity") private int textOpacity = 100;
        @SerializedName("textAlignment") private String textAlignment = "LEFT";
        @SerializedName("leftMargin") private int leftMargin = 2;
        @SerializedName("shadow") private boolean shadow = true;
        @SerializedName("pingEnabled") private boolean pingEnabled = true;
        @SerializedName("pingColor") private int pingColor = 0xFFFFAA00;
        @SerializedName("pingSound") private String pingSound = "pling";
        @SerializedName("chatlogEnabled") private boolean chatlogEnabled = true;
        @SerializedName("showChatOnAllScreens") private boolean showChatOnAllScreens = false;

        public boolean isShowChatOnAllScreens() { return showChatOnAllScreens; }
        public void setShowChatOnAllScreens(final boolean v) { this.showChatOnAllScreens = v; }

        public int getLineSpacing() { return lineSpacing; }
        public void setLineSpacing(final int s) { this.lineSpacing = Math.max(6, Math.min(24, s)); }
        public int getMessageSpacing() { return messageSpacing; }
        public void setMessageSpacing(final int s) { this.messageSpacing = Math.max(0, Math.min(20, s)); }
        public int getTextOpacity() { return textOpacity; }
        public void setTextOpacity(final int o) { this.textOpacity = Math.max(30, Math.min(100, o)); }
        public String getTextAlignment() { return textAlignment; }
        public void setTextAlignment(final String a) { this.textAlignment = a; }
        public int getLeftMargin() { return leftMargin; }
        public void setLeftMargin(final int m) { this.leftMargin = Math.max(0, Math.min(12, m)); }
        public boolean isShadow() { return shadow; }
        public void setShadow(final boolean s) { this.shadow = s; }
        public boolean isPingEnabled() { return pingEnabled; }
        public void setPingEnabled(final boolean e) { this.pingEnabled = e; }
        public boolean isChatlogEnabled() { return chatlogEnabled; }
        public void setChatlogEnabled(final boolean e) { this.chatlogEnabled = e; }
        public int getPingColor() { return pingColor; }
        public void setPingColor(final int c) { this.pingColor = c; }
        public String getPingSound() { return pingSound; }
        public void setPingSound(final String s) { this.pingSound = s != null ? s : "pling"; }
    }

    public FontSettings getFontSettings() {
        if (fontSettings == null) fontSettings = new FontSettings();
        return fontSettings;
    }

    public static void initialize() {
        configPath = detectConfigPath();
        final File configFile = new File(configPath);

        if (configFile.exists()) {
            try (final FileReader reader = new FileReader(configFile)) {
                final ChatConfig loaded = new Gson().fromJson(reader, ChatConfig.class);
                INSTANCE = loaded;
            } catch (final IOException e) {
                INSTANCE = createDefault();
            }
        } else {
            INSTANCE = createDefault();
            INSTANCE.save();
        }
    }

    public static ChatConfig getInstance() { return INSTANCE; }
    public static String getConfigPath() { return configPath; }

    public List<ChatWindowSettings> getWindows() {
        return windows != null ? windows : Collections.emptyList();
    }

    public List<ChatMacro> getMacros() {
        if (macros == null) macros = new ArrayList<>();
        return macros;
    }

    public void setMacros(final List<ChatMacro> macros) { this.macros = macros; }

    public String getCurrentProfile() { return currentProfile; }
    public void setCurrentProfile(final String name) { this.currentProfile = name; }

    /** Get the profiles directory (creates if missing). */
    public Path getProfilesDirectory() {
        final Path base = Path.of(getConfigPath()).getParent();
        final Path dir = base.resolve("profiles");
        try { Files.createDirectories(dir); } catch (final IOException ignored) {}
        return dir;
    }

    /** List all profile names (filenames without .json). */
    public List<String> listProfiles() {
        final Path dir = getProfilesDirectory();
        try {
            return Files.list(dir)
                .filter(p -> p.toString().endsWith(".json"))
                .map(p -> p.getFileName().toString().replace(".json", ""))
                .sorted()
                .collect(Collectors.toList());
        } catch (final IOException e) {
            return new ArrayList<>();
        }
    }

    /** Save current config as a named profile. */
    public void saveProfile(final String name) {
        final Path file = getProfilesDirectory().resolve(name + ".json");
        try (final FileWriter writer = new FileWriter(file.toFile())) {
            gson.toJson(this, writer);
        } catch (final IOException ignored) {}
    }

    /** Load a named profile, replacing current config. */
    public void loadProfile(final String name) {
        final Path file = getProfilesDirectory().resolve(name + ".json");
        if (!Files.exists(file)) {
            // If "default" has no saved file yet, just mark current profile as default.
            if ("default".equals(name)) {
                this.currentProfile = "default";
                save();
            }
            return;
        }
        try (final FileReader reader = new FileReader(file.toFile())) {
            final ChatConfig loaded = new Gson().fromJson(reader, ChatConfig.class);
            if (loaded != null) {
                this.windows = loaded.windows;
                this.macros = loaded.macros;
                this.fontSettings = loaded.fontSettings;
                this.configVersion = loaded.configVersion;
                this.currentProfile = name;
                save();
            }
        } catch (final IOException ignored) {}
    }

    /** Delete a named profile. Returns false for "default". */
    public boolean deleteProfile(final String name) {
        if ("default".equals(name)) return false;
        final Path file = getProfilesDirectory().resolve(name + ".json");
        try { return Files.deleteIfExists(file); } catch (final IOException e) { return false; }
    }

    public void save() {
        if (configPath == null) return;
        final File configFile = new File(configPath);
        final File parent = configFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (final FileWriter writer = new FileWriter(configFile)) {
            gson.toJson(this, writer);
        } catch (final IOException ignored) {}

        // Auto-save to current profile
        if (currentProfile != null && !currentProfile.isEmpty()) {
            saveProfile(currentProfile);
        }
    }

    public ChatWindowSettings getMainWindow() {
        for (final ChatWindowSettings w : getWindows()) {
            for (final ChatTabConfig tab : w.getTabs()) {
                if (tab.isServerTab()) return w;
            }
        }
        final ChatWindowSettings def = ChatWindowSettings.createDefault();
        if (windows == null) windows = new ArrayList<>();
        windows.add(def);
        return def;
    }

    public List<ChatWindowSettings> getSecondaryWindows() {
        final List<ChatWindowSettings> result = new ArrayList<>();
        for (final ChatWindowSettings w : getWindows()) {
            boolean isMain = false;
            for (final ChatTabConfig tab : w.getTabs()) {
                if (tab.isServerTab()) { isMain = true; break; }
            }
            if (!isMain) result.add(w);
        }
        return result;
    }

    private static ChatConfig createDefault() {
        final ChatConfig config = new ChatConfig();
        config.configVersion = 1;
        final ChatTabProperties mainProps = new ChatTabProperties("Main");
        final ChatTabConfig mainTab = ChatTabConfig.createServerTab();
        mainTab.setProperties(mainProps);
        final ChatWindowSettings mainWindow = ChatWindowSettings.createDefault();
        mainWindow.setTabs(new ArrayList<>());
        mainWindow.getTabs().add(mainTab);
        config.windows = new ArrayList<>();
        config.windows.add(mainWindow);
        return config;
    }

    private static String detectConfigPath() {
        final String os = System.getProperty("os.name").toLowerCase();
        final Path basePath;
        if (os.contains("win")) {
            basePath = Path.of(System.getenv("APPDATA"), "AnonChatMC");
        } else if (os.contains("mac")) {
            basePath = Path.of(System.getProperty("user.home"), "Library", "Application Support", "AnonChatMC");
        } else {
            final String xdgData = System.getenv("XDG_DATA_HOME");
            if (xdgData != null && !xdgData.isEmpty()) {
                basePath = Path.of(xdgData, "AnonChatMC");
            } else {
                basePath = Path.of(System.getProperty("user.home"), ".local", "share", "AnonChatMC");
            }
        }
        return basePath.resolve("chat.json").toString();
    }
}
