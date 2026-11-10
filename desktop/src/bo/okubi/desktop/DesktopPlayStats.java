package bo.okubi.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Real, local playtime for the embedded emulator, NEVER guessed from dates,
 * ROM metadata, historical launches, or games run in external emulators.
 * Pause/background time does not count. No telemetry or network.
 *
 * Uses a SHA-256 identifier of the normalized console+ROM path (not ROM data).
 * Moving a game to another path starts a new history but never deletes old data.
 */
public final class DesktopPlayStats {
    public static final class Snapshot {
        public final long seconds;
        public final long sessions;
        public final long lastSessionEpochMillis;
        private Snapshot(long seconds, long sessions, long lastSessionEpochMillis) {
            this.seconds = seconds;
            this.sessions = sessions;
            this.lastSessionEpochMillis = lastSessionEpochMillis;
        }
    }

    private static final class Record {
        long seconds;
        long sessions;
        long lastSessionEpochMillis;
    }

    private final Path file;
    private final Map<String, Record> records = new HashMap<>();

    public static Path defaultFile() {
        return DesktopLibrary.defaultSettingsFile().getParent()
                .resolve("gameplay-stats-v1.properties");
    }

    public DesktopPlayStats(Path file) throws IOException {
        this.file = file.toAbsolutePath().normalize();
        load();
    }

    /** Safe for call from EDT: only hashes the ROM's path, not large game bytes. */
    public static String id(DesktopLibrary.Console console, Path rom) {
        if (console == null || rom == null) throw new IllegalArgumentException("Game");
        String key = console.name() + "\n" + rom.toAbsolutePath().normalize();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte b : hash) result.append(String.format("%02x", b & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    public synchronized Snapshot get(DesktopLibrary.Console console, Path rom) {
        Record record = records.get(id(console, rom));
        return record == null ? new Snapshot(0, 0, 0)
                : new Snapshot(record.seconds, record.sessions, record.lastSessionEpochMillis);
    }

    public synchronized long totalSeconds() {
        long total = 0;
        for (Record record : records.values()) total = saturatingAdd(total, record.seconds);
        return total;
    }

    public synchronized long totalSessions() {
        long total = 0;
        for (Record record : records.values()) total = saturatingAdd(total, record.sessions);
        return total;
    }

    private static long saturatingAdd(long a, long b) {
        return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b;
    }

    /**
     * Call only AFTER native core and battery data were loaded successfully.
     * Every successful launch increments session count at most once.
     */
    public synchronized Session begin(DesktopLibrary.Console console, Path rom)
            throws IOException {
        String key = id(console, rom);
        boolean newRecord = !records.containsKey(key);
        Record record = records.computeIfAbsent(key, missing -> new Record());
        long previousCount = record.sessions;
        long previousStart = record.lastSessionEpochMillis;
        record.sessions = saturatingAdd(record.sessions, 1);
        record.lastSessionEpochMillis = Instant.now().toEpochMilli();
        try {
            save();
        } catch (IOException error) {
            // Do not display a session that wasn't actually saved.
            record.sessions = previousCount;
            record.lastSessionEpochMillis = previousStart;
            if (newRecord) records.remove(key);
            throw error;
        }
        return new Session(key);
    }

    public final class Session {
        private final String key;
        private long lastNano = Long.MIN_VALUE;
        private long pendingNanos;
        private boolean wasActive;
        private boolean closed;
        private Session(String key) { this.key = key; }

        /**
         * Called by emulator worker using System.nanoTime(). Do not count
         * time before the first active frame, during pauses, focus loss,
         * game menus, nor a long stalled frame (over 2 seconds).
         */
        public synchronized void tick(boolean active, long nowNanos) throws IOException {
            if (closed) return;
            if (lastNano != Long.MIN_VALUE && nowNanos >= lastNano
                    && active && wasActive) {
                long delta = nowNanos - lastNano;
                if (delta > 0 && delta <= 2_000_000_000L)
                    pendingNanos = saturatingAdd(pendingNanos, delta);
            }
            lastNano = nowNanos;
            wasActive = active;
            if (pendingNanos >= 15_000_000_000L) flush();
        }

        public synchronized void flush() throws IOException {
            if (closed) return;
            long elapsedSeconds = pendingNanos / 1_000_000_000L;
            if (elapsedSeconds == 0) return;
            synchronized (DesktopPlayStats.this) {
                Record record = records.get(key);
                if (record == null) throw new IOException("Record de partida ausente.");
                long originalSeconds = record.seconds;
                record.seconds = saturatingAdd(record.seconds, elapsedSeconds);
                try {
                    save();
                    pendingNanos %= 1_000_000_000L;
                } catch (IOException error) {
                    // Failed disk writes cannot double-count the same seconds.
                    record.seconds = originalSeconds;
                    throw error;
                }
            }
        }

        public synchronized void finish() throws IOException {
            if (closed) return;
            flush();
            closed = true;
        }
    }

    private synchronized void load() throws IOException {
        if (!Files.isRegularFile(file)) return;
        Properties p = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            p.load(input);
        }
        int count;
        try { count = Math.min(3000, Math.max(0,
                Integer.parseInt(p.getProperty("count", "0")))); }
        catch (NumberFormatException invalid) { count = 0; }
        for (int i = 0; i < count; i++) {
            String prefix = "game." + i + ".";
            String key = p.getProperty(prefix + "id", "");
            if (!key.matches("[0-9a-f]{64}")) continue;
            Record record = new Record();
            record.seconds = safeLong(p.getProperty(prefix + "seconds"));
            record.sessions = safeLong(p.getProperty(prefix + "sessions"));
            record.lastSessionEpochMillis = safeLong(p.getProperty(prefix + "last"));
            records.put(key, record);
        }
    }

    private static long safeLong(String text) {
        if (text == null) return 0;
        try { return Math.max(0, Long.parseLong(text)); }
        catch (NumberFormatException invalid) { return 0; }
    }

    private synchronized void save() throws IOException {
        Path parent = file.getParent();
        Files.createDirectories(parent);
        Properties p = new Properties();
        p.setProperty("count", Integer.toString(records.size()));
        int index = 0;
        for (Map.Entry<String, Record> entry : records.entrySet()) {
            String prefix = "game." + index++ + ".";
            Record record = entry.getValue();
            p.setProperty(prefix + "id", entry.getKey());
            p.setProperty(prefix + "seconds", Long.toString(record.seconds));
            p.setProperty(prefix + "sessions", Long.toString(record.sessions));
            p.setProperty(prefix + "last", Long.toString(record.lastSessionEpochMillis));
        }
        Path temp = Files.createTempFile(parent, ".okubi-stats-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temp)) {
                p.store(output, "OKUBI Desktop real playtime since v0.7; never retroactively guessed");
            }
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static String duration(long seconds) {
        if (seconds < 60) return seconds + " s";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours > 0 ? hours + " h " + minutes + " min" : minutes + " min";
    }
}
