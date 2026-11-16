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
 * Independent video geometry and per-console scaling preferences.
 *
 * Rendering only; the original frame, Libretro core, SRAM, RTC, states,
 * keyboard mappings and Cloud Save are never modified.
 */
public final class DesktopVideoScaling {
    public enum Mode {
        AUTO("Automático (compatible con configuración anterior)"),
        FIT("Ajustar · sin recortar"),
        INTEGER("Pixel Perfect · escala entera"),
        FILL("Llenar · recorta bordes"),
        STRETCH("Estirar · deforma la imagen");

        public final String label;
        Mode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    /** Coordinates relative to the game's video panel; may be negative in FILL. */
    public record Frame(int x, int y, int width, int height,
                        boolean cropped, boolean distorted) {
        public static Frame empty() { return new Frame(0, 0, 0, 0, false, false); }
        public boolean drawable() { return width > 0 && height > 0; }
    }

    private final Path file;
    private final EnumMap<DesktopLibrary.Console, Mode> scaling =
            new EnumMap<>(DesktopLibrary.Console.class);

    public DesktopVideoScaling(Path file) throws IOException {
        if (file == null) throw new IOException("Ruta de ajustes de imagen ausente.");
        this.file = file.toAbsolutePath().normalize();
        for (DesktopLibrary.Console system : DesktopLibrary.Console.values())
            scaling.put(system, Mode.AUTO);
        load();
    }

    public static Path defaultFile() {
        return DesktopLibrary.defaultSettingsFile().getParent()
                .resolve("cinema-scaling-v1.properties");
    }

    public synchronized Mode mode(DesktopLibrary.Console console) {
        return scaling.get(console);
    }

    public synchronized void setMode(DesktopLibrary.Console console, Mode value)
            throws IOException {
        if (console == null || value == null)
            throw new IOException("Modo de escalado no válido.");
        Mode previous = scaling.put(console, value);
        try { save(); }
        catch (IOException failure) {
            scaling.put(console, previous);
            throw failure;
        }
    }

    /**
     * AUTO preserves the meaning of the old Pixel Perfect video filter.
     * Explicit choices override that legacy sizing without deleting it.
     */
    public static Mode effective(Mode selected, DesktopPlaySettings.Filter filter) {
        if (selected != Mode.AUTO) return selected;
        return filter == DesktopPlaySettings.Filter.INTEGER ? Mode.INTEGER : Mode.FIT;
    }

    public static Mode next(Mode current) {
        return switch (current) {
            case AUTO, FIT -> Mode.INTEGER;
            case INTEGER -> Mode.FILL;
            case FILL -> Mode.STRETCH;
            case STRETCH -> Mode.FIT;
        };
    }

    /**
     * Calculate centered, aspect-ratio aware video without touching frames.
     * FIT: contain/letterbox. INTEGER: nearest whole-number upscaling,
     * downscale by FIT only when source doesn't fit. FILL: cover, crop
     * symmetrically with negative coordinates, clip at the paint viewport.
     * STRETCH: fill the viewport without crop but distort aspect ratio.
     */
    public static Frame calculate(int sourceWidth, int sourceHeight,
                                  int viewportWidth, int viewportHeight,
                                  Mode selected, DesktopPlaySettings.Filter filter) {
        if (sourceWidth <= 0 || sourceHeight <= 0
                || viewportWidth <= 0 || viewportHeight <= 0
                || selected == null)
            return Frame.empty();

        Mode mode = effective(selected, filter);
        if (mode == Mode.STRETCH) {
            // "Deforma" describes the selected algorithm, whether the
            // current viewport happens to share the native aspect or not.
            boolean distorted =
                    (long)sourceWidth * viewportHeight
                    != (long)sourceHeight * viewportWidth;
            return new Frame(0, 0, viewportWidth, viewportHeight, false, distorted);
        }

        double widthScale = viewportWidth / (double)sourceWidth;
        double heightScale = viewportHeight / (double)sourceHeight;
        double scale = mode == Mode.FILL
                ? Math.max(widthScale, heightScale)
                : Math.min(widthScale, heightScale);
        if (mode == Mode.INTEGER && scale >= 1.0) scale = Math.floor(scale);

        // Double math is bounded by the viewport and input frame dimensions
        // for any realistic monitor; clamp to integer range against malformed
        // frame sizes from a native core.
        double scaledWidth = sourceWidth * scale;
        double scaledHeight = sourceHeight * scale;
        int videoWidth = (int)Math.min(Integer.MAX_VALUE,
                Math.max(1, mode == Mode.FILL
                        ? Math.ceil(scaledWidth) : Math.floor(scaledWidth)));
        int videoHeight = (int)Math.min(Integer.MAX_VALUE,
                Math.max(1, mode == Mode.FILL
                        ? Math.ceil(scaledHeight) : Math.floor(scaledHeight)));
        if (mode != Mode.FILL) {
            videoWidth = Math.min(viewportWidth, videoWidth);
            videoHeight = Math.min(viewportHeight, videoHeight);
        }
        // Long intermediate arithmetic avoids overflow in centering.
        int x = (int)(((long)viewportWidth - videoWidth) / 2L);
        int y = (int)(((long)viewportHeight - videoHeight) / 2L);
        return new Frame(x, y, videoWidth, videoHeight,
                videoWidth > viewportWidth || videoHeight > viewportHeight, false);
    }

    public static String warning(Mode mode) {
        return switch (mode) {
            case FILL -> "AVISO: llena la pantalla y recorta parte de los bordes del juego.";
            case STRETCH -> "AVISO: estirar cambia las proporciones originales del juego.";
            case INTEGER -> "Pixel Perfect evita escalas fraccionarias; puede dejar bandas negras.";
            case AUTO -> "Respeta tu filtro anterior y la proporción original.";
            case FIT -> "Imagen completa y proporciones correctas, con posibles bandas negras.";
        };
    }

    private void load() throws IOException {
        if (!Files.isRegularFile(file)) return;
        Properties data = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            data.load(in);
        }
        for (DesktopLibrary.Console system : DesktopLibrary.Console.values()) {
            String value = data.getProperty(system.name() + ".scale");
            if (value == null) continue;
            try { scaling.put(system, Mode.valueOf(value)); }
            catch (IllegalArgumentException ignored) {
                // Unknown versions or corruption safely use compatible AUTO.
            }
        }
    }

    private synchronized void save() throws IOException {
        Path parent = file.getParent();
        Files.createDirectories(parent);
        Properties data = new Properties();
        for (DesktopLibrary.Console system : DesktopLibrary.Console.values())
            data.setProperty(system.name() + ".scale", scaling.get(system).name());

        Path temp = Files.createTempFile(parent, ".okubi-scaling-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temp)) {
                data.store(out, "OKUBI video scaling per console; no ROM/save data");
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
