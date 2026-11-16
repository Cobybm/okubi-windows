package bo.okubi.desktop;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Read-only game media and save presence for GAME HUB PRO.
 *
 * Call inspect() off the Swing event thread: SHA-256 reads the ROM and
 * screenshot decoding may access a slower disk. No ROM, screenshot or save
 * is written, moved, restored or deleted by this class.
 */
public final class DesktopGameHubMedia {
    private static final int MAX_CAPTURES = 3;
    private static final int MAX_SCANNED_FILES = 600;
    private static final long MAX_CAPTURE_BYTES = 8L * 1024 * 1024;
    private static final int MAX_DIMENSION = 4096;
    private static final long MAX_PIXELS = 12_000_000L;

    public record Capture(BufferedImage image, String filename) { }

    public record Snapshot(boolean hasBattery, boolean hasRtc,
                           List<Capture> captures, String note) {
        public Snapshot {
            captures = List.copyOf(captures);
        }
    }

    private DesktopGameHubMedia() { }

    public static Snapshot inspect(DesktopLibrary.Game game, Path savesRoot) {
        if (game == null || savesRoot == null || !game.isAvailable())
            return new Snapshot(false, false, List.of(),
                    "La ROM no está disponible. No se modificó ningún guardado.");
        try {
            DesktopSaves saves = new DesktopSaves(savesRoot, game.console, game.rom);
            boolean battery = Files.isRegularFile(saves.ramFile, LinkOption.NOFOLLOW_LINKS);
            boolean rtc = Files.isRegularFile(saves.rtcFile, LinkOption.NOFOLLOW_LINKS);
            Path screenshots = saves.directory.resolve("screenshots");
            if (!Files.isDirectory(screenshots, LinkOption.NOFOLLOW_LINKS))
                return new Snapshot(battery, rtc, List.of(),
                        "Aún no hay capturas de este juego.");
            String hashPrefix = saves.ramFile.getFileName().toString().substring(0, 12) + "-";
            List<Path> candidates;
            try (var files = Files.list(screenshots)) {
                candidates = files.filter(file ->
                        Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        && file.getFileName().toString().startsWith(hashPrefix)
                        && file.getFileName().toString()
                                .toLowerCase(Locale.ROOT).endsWith(".png"))
                        .limit(MAX_SCANNED_FILES).toList();
            }
            // Existing screenshots only, newest first. No generated/fake art.
            candidates = new ArrayList<>(candidates);
            candidates.sort(Comparator.comparingLong(DesktopGameHubMedia::timestamp)
                    .reversed());
            List<Capture> captures = new ArrayList<>();
            for (Path file : candidates) {
                if (captures.size() >= MAX_CAPTURES) break;
                BufferedImage image = readBoundedPng(file);
                if (image != null)
                    captures.add(new Capture(image, file.getFileName().toString()));
            }
            return new Snapshot(battery, rtc, captures, captures.isEmpty()
                    ? "Aún no hay capturas válidas para este juego."
                    : "Capturas propias guardadas por OKUBI. Solo lectura.");
        } catch (IOException | RuntimeException error) {
            return new Snapshot(false, false, List.of(),
                    "No se pudo comprobar el almacenamiento; tus archivos siguen intactos.");
        }
    }

    private static long timestamp(Path path) {
        try { return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis(); }
        catch (IOException error) { return 0L; }
    }

    private static BufferedImage readBoundedPng(Path file) {
        try {
            long bytes = Files.size(file);
            if (bytes <= 0 || bytes > MAX_CAPTURE_BYTES) return null;
            try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
                if (input == null) return null;
                var readers = ImageIO.getImageReadersByFormatName("png");
                while (readers.hasNext()) {
                    ImageReader reader = readers.next();
                    try {
                        reader.setInput(input, true, true);
                        int width = reader.getWidth(0), height = reader.getHeight(0);
                        if (width <= 0 || height <= 0
                                || width > MAX_DIMENSION || height > MAX_DIMENSION
                                || (long) width * height > MAX_PIXELS) return null;
                        return reader.read(0);
                    } finally {
                        reader.dispose();
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Corrupted or oversized old screenshot: skip it without modifying.
        }
        return null;
    }
}
