package net.anonchat.client.chatlog;

import net.anonchat.client.config.ChatConfig;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Logs chat messages to daily text files in a {@code chatlog/} subdirectory
 * next to {@code chat.json}. Each day gets its own file: {@code YYYY-MM-DD.txt}.
 * Only writes when {@link ChatConfig.FontSettings#isChatlogEnabled()} is true.
 */
public final class ChatLogger {

    private static Path logDir;
    private static PrintWriter writer;
    private static String currentDate = "";

    private ChatLogger() {}

    /** Call once during mod initialisation with the config directory path. */
    public static void initialize(final Path configDir) {
        logDir = configDir.resolve("chatlog");
    }

    /** Log a single line of chat text. Thread-safe-ish (called from MC render thread). */
    public static void log(final String message) {
        if (ChatConfig.getInstance() == null) return;
        if (!ChatConfig.getInstance().getFontSettings().isChatlogEnabled()) return;
        if (logDir == null) return;
        if (message == null || message.isEmpty()) return;

        final String today = LocalDate.now().toString();
        if (!today.equals(currentDate)) {
            close();
            currentDate = today;
            try {
                Files.createDirectories(logDir);
                final Path file = logDir.resolve(today + ".txt");
                writer = new PrintWriter(new FileWriter(file.toFile(), true));
            } catch (final IOException e) {
                writer = null;
            }
        }

        if (writer != null) {
            final String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            writer.println("[" + time + "] " + message);
            writer.flush();
        }
    }

    /** Flush and close the current log file. */
    public static void close() {
        if (writer != null) {
            writer.flush();
            writer.close();
            writer = null;
        }
    }
}
