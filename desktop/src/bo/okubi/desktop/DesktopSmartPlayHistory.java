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
 * User-confirmed per-game observations and device-specific launch advice.
 *
 * Never reads game contents, emulator settings, save states or firmware.
 * It DOES NOT measure FPS or infer compatibility from a short session.
 * A different hardware fingerprint starts fresh without removing past data.
 */
public final class DesktopSmartPlayHistory {
    public enum Result {
        UNTESTED("Sin probar"),
        GOOD("Probado: funciona bien"),
        SLOW("Probado: rendimiento lento"),
        FAILED("Probado: no inició");
        public final String label;
        Result(String label) { this.label = label; }
    }
    public record Observation(Result result, long recordedAt) { }
    private static final int MAX_RECORDS = 1000;
    private final Path file;
    private final Properties props = new Properties();

    public DesktopSmartPlayHistory(Path file) {
        this.file = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(this.file, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(this.file)) return;
        try (InputStream input = Files.newInputStream(this.file)) {
            props.load(input);
        } catch (IOException | IllegalArgumentException ignored) {
            // Fail closed to UNTESTED; do not rewrite corrupted settings.
            props.clear();
        }
    }

    public static Path defaultFile() {
        String appData = System.getenv("APPDATA");
        Path root = appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".okubi")
                : Path.of(appData);
        return root.resolve("OKUBI").resolve("smart-play-history-v1.properties");
    }

    public static String hardwareId(DesktopSmartHardware.Snapshot hardware) {
        if (hardware == null || !hardware.graphicsKnown()
                || hardware.memoryBytes() <= 0) return "";
        String normalized = hardware.processor().trim().toLowerCase(Locale.ROOT)
                + "|" + hardware.graphicsAdapter().trim().toLowerCase(Locale.ROOT)
                + "|" + hardware.logicalCores()
                + "|" + Math.round(hardware.memoryGiB());
        return digest(normalized);
    }

    private static String gameKey(DesktopMulticonsoleCatalog.Game game,
                                  DesktopSmartHardware.Snapshot hardware) {
        if (game == null || game.file() == null) return "";
        String machine = hardwareId(hardware);
        if (machine.isBlank()) return "";
        return digest(machine + "|" + game.console().name() + "|"
                + game.file().toAbsolutePath().normalize()
                        .toString().toLowerCase(Locale.ROOT));
    }

    private static String digest(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 no disponible", impossible);
        }
    }

    public synchronized Observation observation(DesktopMulticonsoleCatalog.Game game,
                                                 DesktopSmartHardware.Snapshot hardware) {
        String key = gameKey(game, hardware);
        if (key.isBlank()) return new Observation(Result.UNTESTED, 0L);
        String value = props.getProperty("result." + key, "");
        Result result;
        try { result = Result.valueOf(value); }
        catch (IllegalArgumentException invalid) { result = Result.UNTESTED; }
        long time = 0;
        try { time = Math.max(0, Long.parseLong(props.getProperty("time." + key, "0"))); }
        catch (NumberFormatException ignored) { }
        return new Observation(result, time);
    }

    public synchronized DesktopSmartPerformance.Mode effectiveMode(
            DesktopMulticonsoleCatalog.Game game,
            DesktopSmartHardware.Snapshot hardware) {
        if (game == null || hardware == null)
            return DesktopSmartPerformance.Mode.ECO;
        DesktopSmartPerformance.Mode selected = game.mode();
        if (selected != DesktopSmartPerformance.Mode.AUTO) return selected;
        Result observed = observation(game, hardware).result();
        if (observed == Result.SLOW || observed == Result.FAILED)
            return DesktopSmartPerformance.Mode.ECO;
        // GOOD does not blindly raise resolution or force QUALITY; the
        // existing hardware rating still determines a conservative mode.
        return DesktopSmartPerformance.Mode.AUTO;
    }

    public synchronized void confirm(DesktopMulticonsoleCatalog.Game game,
                                     DesktopSmartHardware.Snapshot hardware,
                                     Result result) throws IOException {
        String key = gameKey(game, hardware);
        if (key.isBlank() || result == null || result == Result.UNTESTED)
            throw new IOException("Detecta correctamente tu PC y selecciona un juego.");
        if (!props.containsKey("result." + key)
                && props.stringPropertyNames().stream()
                        .filter(k -> k.startsWith("result.")).count() >= MAX_RECORDS)
            throw new IOException("Máximo de resultados alcanzado; ninguna partida se modificó.");
        props.setProperty("result." + key, result.name());
        props.setProperty("time." + key, Long.toString(System.currentTimeMillis()));
        persist();
    }

    private void persist() throws IOException {
        Path parent = file.getParent();
        if (parent == null || Files.isSymbolicLink(file))
            throw new IOException("Ubicación de resultados no segura.");
        Files.createDirectories(parent);
        Path tmp = Files.createTempFile(parent, ".okubi-smart-play-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(tmp)) {
                props.store(output, "OKUBI PC game feedback only: NO ROMs, save games, paths or BIOS");
            }
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException fallback) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
