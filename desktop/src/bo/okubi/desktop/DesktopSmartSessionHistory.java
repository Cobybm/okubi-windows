package bo.okubi.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Properties;

/**
 * Phase65 SMART SESSION: records ONLY external emulator process duration,
 * exit code and user-entered FPS. No actual FPS sampling, ROM reads,
 * game save access, emulator config writes or OS tuning.
 *
 * Exit code is NOT evidence that a game ran well or was compatible.
 */
public final class DesktopSmartSessionHistory {
    public record Stats(int sessions, long seconds, int lastExitCode,
                        long lastAtEpochMs, int userReportedFps) {
        public Stats {
            if (sessions < 0 || seconds < 0 || userReportedFps < 0
                    || userReportedFps > 240)
                throw new IllegalArgumentException("Invalid session data.");
        }
        public String summary() {
            if (sessions == 0) return "Sin sesiones registradas en esta PC.";
            return "Sesiones: " + sessions + " · Tiempo: "
                    + (seconds / 60) + " min · Última salida: "
                    + lastExitCode + "\n"
                    + (userReportedFps > 0
                    ? "FPS introducidos por ti: " + userReportedFps
                            + " (NO medidos automáticamente por OKUBI)."
                    : "FPS: aún sin datos. OKUBI no inventa FPS.");
        }
    }

    private static final int MAX_KEYS = 1000;
    private static final int MAX_SIZE = 256 * 1024;
    private static final long MAX_SESSION_SECONDS = 24L * 60 * 60;
    private final Path file;
    private final Properties props = new Properties();

    public DesktopSmartSessionHistory(Path file) {
        this.file = file.toAbsolutePath().normalize();
        try {
            if (!Files.isRegularFile(this.file, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(this.file)
                    || Files.size(this.file) > MAX_SIZE) return;
            try (InputStream in = Files.newInputStream(this.file)) {
                props.load(in);
            }
        } catch (IOException | IllegalArgumentException corrupt) {
            // Unknown data fail closed. Never destroy the corrupt original.
            props.clear();
        }
    }

    public static Path defaultFile() {
        String appData = System.getenv("APPDATA");
        Path root = appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".okubi")
                : Path.of(appData);
        return root.resolve("OKUBI").resolve("smart-session-history-v1.properties");
    }

    private static String key(DesktopMulticonsoleCatalog.Game game,
                              DesktopSmartHardware.Snapshot pc) throws IOException {
        if (game == null || pc == null || game.file() == null)
            throw new IOException("Necesitas un juego y una PC detectada.");
        String pcId = DesktopSmartPlayHistory.hardwareId(pc);
        if (pcId.isEmpty()) throw new IOException("Hardware incompleto, sin estadísticas.");
        String value = pcId + "|" + game.console().name() + "|"
                + game.file().toAbsolutePath().normalize().toString()
                        .toLowerCase(Locale.ROOT);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 no disponible.", impossible);
        }
    }

    public synchronized Stats stats(DesktopMulticonsoleCatalog.Game game,
                                    DesktopSmartHardware.Snapshot pc) {
        try { return statsFor(key(game, pc)); }
        catch (IOException error) { return new Stats(0, 0, 0, 0, 0); }
    }

    private Stats statsFor(String id) {
        try {
            int count = Integer.parseInt(props.getProperty("s." + id, "0"));
            long total = Long.parseLong(props.getProperty("duration." + id, "0"));
            int exit = Integer.parseInt(props.getProperty("exit." + id, "0"));
            long when = Long.parseLong(props.getProperty("at." + id, "0"));
            int fps = Integer.parseInt(props.getProperty("fps." + id, "0"));
            if (count > 1_000_000 || total > 1_000_000_000L
                    || when < 0) return new Stats(0, 0, 0, 0, 0);
            return new Stats(count, total, exit, when, fps);
        } catch (IllegalArgumentException invalid) {
            return new Stats(0, 0, 0, 0, 0);
        }
    }

    public synchronized void completed(DesktopMulticonsoleCatalog.Game game,
                                       DesktopSmartHardware.Snapshot pc,
                                       long elapsedSeconds, int exitCode)
            throws IOException {
        String id = key(game, pc);
        if (elapsedSeconds < 0) throw new IOException("Duración negativa.");
        Stats before = statsFor(id);
        if (before.sessions() >= 1_000_000)
            throw new IOException("Se alcanzó el límite del historial.");
        requireCapacity(id);
        long bounded = Math.min(MAX_SESSION_SECONDS, elapsedSeconds);
        props.setProperty("s." + id, Integer.toString(before.sessions() + 1));
        props.setProperty("duration." + id, Long.toString(
                Math.min(1_000_000_000L, before.seconds() + bounded)));
        props.setProperty("exit." + id, Integer.toString(exitCode));
        props.setProperty("at." + id, Long.toString(System.currentTimeMillis()));
        save();
    }

    /** FPS must come from the emulator's FPS display or the user's own test. */
    public synchronized void userFps(DesktopMulticonsoleCatalog.Game game,
                                     DesktopSmartHardware.Snapshot pc,
                                     int fps) throws IOException {
        if (fps < 0 || fps > 240)
            throw new IOException("FPS deben estar entre 1 y 240; 0 los borra.");
        String id = key(game, pc);
        requireCapacity(id);
        if (fps == 0) props.remove("fps." + id);
        else props.setProperty("fps." + id, Integer.toString(fps));
        save();
    }

    private void requireCapacity(String id) throws IOException {
        if (!props.containsKey("s." + id) && !props.containsKey("fps." + id)
                && props.stringPropertyNames().stream()
                        .filter(k -> k.startsWith("s.") || k.startsWith("fps."))
                        .count() >= MAX_KEYS * 2)
            throw new IOException("El historial ha alcanzado su límite.");
    }

    private void save() throws IOException {
        if (Files.isSymbolicLink(file)
                || Files.isSymbolicLink(file.getParent()))
            throw new IOException("Ruta de historial insegura.");
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(),
                ".okubi-session-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temp)) {
                props.store(out, "OKUBI process duration / user FPS; no ROMs, paths or saves");
            }
            if (Files.size(temp) > MAX_SIZE)
                throw new IOException("Historial demasiado grande.");
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
}
