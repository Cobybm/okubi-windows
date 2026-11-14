package bo.okubi.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Properties;

/**
 * Per-console HUD preferences. Independent of ROM, SRAM/RTC, quick states,
 * cloud backups, keyboard bindings and filter settings.
 */
public final class DesktopHudSettings {
    public enum Mode {
        AUTO("Automático · se oculta a los 3 segundos"),
        FULL("Completo · siempre visible"),
        COMPACT("Compacto · barra superior pequeña"),
        HIDDEN("Oculto · F1 para mostrarlo");

        public final String label;

        Mode(String label) { this.label = label; }

        @Override public String toString() { return label; }
    }

    /** What is visible in the player. Pure, easily regression-tested logic. */
    public record View(boolean header, boolean footer, boolean compactHeader) { }

    private final Path file;
    private final EnumMap<DesktopLibrary.Console, Mode> modes =
            new EnumMap<>(DesktopLibrary.Console.class);

    public DesktopHudSettings(Path file) throws IOException {
        if (file == null) throw new IOException("Falta la ruta de ajustes HUD.");
        this.file = file.toAbsolutePath().normalize();
        for (DesktopLibrary.Console console : DesktopLibrary.Console.values())
            modes.put(console, Mode.AUTO);
        load();
    }

    public static Path defaultFile() {
        return DesktopLibrary.defaultSettingsFile().getParent()
                .resolve("cinema-hud-v1.properties");
    }

    public synchronized Mode mode(DesktopLibrary.Console console) {
        return modes.get(console);
    }

    public synchronized void setMode(DesktopLibrary.Console console, Mode mode)
            throws IOException {
        if (console == null || mode == null)
            throw new IOException("Modo de pantalla desconocido.");
        Mode previous = modes.put(console, mode);
        try {
            save();
        } catch (IOException failure) {
            modes.put(console, previous);
            throw failure;
        }
    }

    /**
     * Menu controls are on a Swing glass pane, always separately available
     * via ESC, even when the chrome is invisible. Never hide the game itself.
     */
    public static View view(Mode mode, boolean revealed, boolean menuVisible) {
        if (menuVisible) return new View(false, false, false);
        return switch (mode) {
            case FULL -> new View(true, true, false);
            case COMPACT -> new View(true, false, true);
            case AUTO, HIDDEN -> new View(revealed, revealed, false);
        };
    }

    public static boolean autoHide(Mode mode) {
        return mode == Mode.AUTO || mode == Mode.HIDDEN;
    }

    public static boolean revealOnMouse(Mode mode) {
        // Hidden really means hidden: F1 or Esc is its explicit escape hatch.
        return mode == Mode.AUTO;
    }

    private void load() throws IOException {
        if (!Files.isRegularFile(file)) return;
        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(file)) {
            properties.load(stream);
        }
        for (DesktopLibrary.Console console : DesktopLibrary.Console.values()) {
            String stored = properties.getProperty(console.name() + ".hud");
            if (stored == null) continue;
            try { modes.put(console, Mode.valueOf(stored)); }
            catch (IllegalArgumentException ignored) {
                // Corrupted or newer setting: safe AUTO fallback.
            }
        }
    }

    private synchronized void save() throws IOException {
        Path folder = file.getParent();
        Files.createDirectories(folder);
        Properties properties = new Properties();
        for (DesktopLibrary.Console console : DesktopLibrary.Console.values())
            properties.setProperty(console.name() + ".hud", modes.get(console).name());

        Path temp = Files.createTempFile(folder, ".okubi-hud-", ".tmp");
        try {
            try (OutputStream stream = Files.newOutputStream(temp)) {
                properties.store(stream, "OKUBI CINEMA HUD; no saves or ROM paths");
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
}
