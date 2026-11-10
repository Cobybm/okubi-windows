package bo.okubi.desktop;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/**
 * Optional, automatic boxart lookup from Libretro's documented HTTPS server.
 *
 * No Google scraping, account, token, ROM upload or save-state access.
 * Requests are serialized off the Swing event thread. A cached PNG is used
 * offline; misses are throttled for two weeks. No assets ship with the EXE.
 */
public final class OnlineCoverService {
    private static final int MAX_HTML_BYTES = 6_000_000;
    private static final int MAX_PNG_BYTES = 2_000_000;
    private static final int MAX_IMAGE_DIMENSION = 2048;
    private static final Duration MISSING_TTL = Duration.ofDays(14);
    private static final int MAX_PENDING = 128;

    private final Path root;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final java.util.concurrent.ExecutorService worker =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "OKUBI-Online-Covers");
                thread.setDaemon(true);
                return thread;
            });
    private final Object lock = new Object();
    private final Map<DesktopLibrary.Console, String> indexes =
            new EnumMap<>(DesktopLibrary.Console.class);
    private final Map<String, List<Consumer<BufferedImage>>> waiting = new HashMap<>();
    private final Map<String, BufferedImage> memory =
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override protected boolean removeEldestEntry(
                        Map.Entry<String, BufferedImage> oldest) {
                    return size() > 96;
                }
            };

    public OnlineCoverService(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public static Path defaultDirectory() {
        String appData = System.getenv("APPDATA");
        Path home = appData != null && !appData.isBlank()
                ? Path.of(appData) : Path.of(System.getProperty("user.home"), ".okubi");
        return home.resolve("OKUBI").resolve("cover-art-v1");
    }

    private static String cacheKey(DesktopLibrary.Console console, Path rom) {
        String value = console.name() + "|" + rom.toAbsolutePath().normalize();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public Path cachePath(DesktopLibrary.Console console, Path rom) {
        return root.resolve(console.name()).resolve(cacheKey(console, rom) + ".png");
    }

    /**
     * Always returns on Swing EDT; null means keep procedural fallback.
     * No external code needs to handle threads or HTTP errors.
     */
    public void load(DesktopLibrary.Console console, Path rom, String title,
                     Consumer<BufferedImage> callback) {
        if (console == null || rom == null || callback == null) return;
        String key = console.name() + "|" + cacheKey(console, rom);
        BufferedImage cached;
        synchronized (lock) {
            cached = memory.get(key);
            if (cached == null) {
                List<Consumer<BufferedImage>> listeners = waiting.get(key);
                if (listeners != null) {
                    listeners.add(callback);
                    return;
                }
                if (waiting.size() >= MAX_PENDING) return;
                List<Consumer<BufferedImage>> first = new ArrayList<>();
                first.add(callback);
                waiting.put(key, first);
            }
        }
        if (cached != null) {
            SwingUtilities.invokeLater(() -> callback.accept(cached));
            return;
        }
        worker.execute(() -> {
            BufferedImage cover = null;
            try {
                cover = resolve(console, rom, title);
            } catch (IOException | InterruptedException ignored) {
                if (ignored instanceof InterruptedException)
                    Thread.currentThread().interrupt();
                // Disconnected or unavailable server: do not corrupt cache.
            } catch (RuntimeException ignored) {
                // Invalid external image or storage permission: fallback.
            }
            List<Consumer<BufferedImage>> listeners;
            synchronized (lock) {
                if (cover != null) memory.put(key, cover);
                listeners = waiting.remove(key);
            }
            final BufferedImage ready = cover;
            if (listeners != null) SwingUtilities.invokeLater(() -> {
                for (Consumer<BufferedImage> listener : listeners)
                    listener.accept(ready);
            });
        });
    }

    /** User-invoked retry after a prior "no cover found" result. */
    public void retryMissing(Runnable onFinished) {
        worker.execute(() -> {
            for (DesktopLibrary.Console console : DesktopLibrary.Console.values()) {
                Path directory = root.resolve(console.name());
                if (!Files.isDirectory(directory)) continue;
                try (java.util.stream.Stream<Path> files = Files.list(directory)) {
                    files.filter(path -> path.getFileName().toString().endsWith(".missing"))
                            .forEach(path -> {
                                try { Files.deleteIfExists(path); }
                                catch (IOException ignored) { }
                            });
                } catch (IOException ignored) { }
            }
            if (onFinished != null) SwingUtilities.invokeLater(onFinished);
        });
    }

    private BufferedImage resolve(DesktopLibrary.Console console, Path rom,
                                  String title) throws IOException, InterruptedException {
        Path file = cachePath(console, rom);
        BufferedImage custom = localCover(rom);
        if (custom != null) return custom;

        BufferedImage disk = readLocal(file);
        if (disk != null) return disk;

        Path missing = file.resolveSibling(file.getFileName() + ".missing");
        if (Files.isRegularFile(missing)
                && Files.getLastModifiedTime(missing).toInstant()
                        .plus(MISSING_TTL).isAfter(Instant.now())) return null;

        String html = indexes.get(console);
        if (html == null) {
            html = getText(URI.create(LibretroCoverCatalog.directoryUrl(console)));
            indexes.put(console, html);
        }
        String found = LibretroCoverCatalog.bestMatch(html, title);
        if (found == null) {
            markMissing(missing);
            return null;
        }
        byte[] bytes = getBytes(
                URI.create(LibretroCoverCatalog.coverUrl(console, found)), MAX_PNG_BYTES);
        BufferedImage image = decode(bytes);
        if (image == null) {
            markMissing(missing);
            return null;
        }
        persist(file, bytes);
        Files.deleteIfExists(missing);
        return image;
    }

    /** Custom artwork beside a ROM takes precedence but never alters its bytes. */
    private static BufferedImage localCover(Path rom) {
        Path filename = rom.getFileName();
        if (filename == null || rom.getParent() == null) return null;
        String stem = LibretroCoverCatalog.gameStem(filename.toString());
        for (String ext : new String[]{".png", ".jpg", ".jpeg"}) {
            BufferedImage picture = readLocal(rom.resolveSibling(stem + ext));
            if (picture != null) return picture;
        }
        return null;
    }

    private static BufferedImage readLocal(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_PNG_BYTES) return null;
            BufferedImage picture = ImageIO.read(file.toFile());
            if (picture == null
                    || picture.getWidth() > MAX_IMAGE_DIMENSION
                    || picture.getHeight() > MAX_IMAGE_DIMENSION) return null;
            return picture;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static BufferedImage decode(byte[] bytes) {
        if (bytes == null || bytes.length < 8 || bytes.length > MAX_PNG_BYTES
                || (bytes[0] & 0xFF) != 137 || bytes[1] != 80
                || bytes[2] != 78 || bytes[3] != 71) return null;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0
                    || image.getWidth() > MAX_IMAGE_DIMENSION
                    || image.getHeight() > MAX_IMAGE_DIMENSION) return null;
            return image;
        } catch (IOException | RuntimeException corrupt) {
            return null;
        }
    }

    private static void persist(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "okubi-cover-", ".tmp");
        try {
            Files.write(temp, bytes);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException fallback) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void markMissing(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "No matching Libretro cover; retry after 14 days.\n",
                StandardCharsets.UTF_8);
    }

    private String getText(URI uri) throws IOException, InterruptedException {
        return new String(getBytes(uri, MAX_HTML_BYTES), StandardCharsets.UTF_8);
    }

    private byte[] getBytes(URI uri, int maximum) throws IOException, InterruptedException {
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"thumbnails.libretro.com".equalsIgnoreCase(uri.getHost()))
            throw new IOException("Only official Libretro thumbnail source allowed");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET().header("User-Agent", "OKUBI-Desktop/0.9.7")
                .timeout(Duration.ofSeconds(18)).build();
        HttpResponse<InputStream> response =
                http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream stream = response.body()) {
            if (response.statusCode() != 200)
                throw new IOException("Thumbnail HTTP " + response.statusCode());
            byte[] bytes = stream.readNBytes(maximum + 1);
            if (bytes.length > maximum)
                throw new IOException("Thumbnail exceeds permitted size");
            return bytes;
        }
    }
}
