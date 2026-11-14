package bo.okubi.desktop;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.SwingUtilities;

/**
 * Phase 77: secure, opt-in *repository preview* artwork for PC ports.
 *
 * GitHub-generated social cards may contain only a project name/avatar, not
 * gameplay artwork; users may override them with their own image. No ROM,
 * account, path, personal file, save or token is ever transmitted.
 */
public final class DesktopPortArtworkService implements AutoCloseable {
    public static final int MAX_BYTES = 2_000_000;
    public static final int MAX_SIDE = 4096;
    private static final int MAX_PIXELS = 9_000_000;
    private static final long MISS_TTL_MILLIS = Duration.ofHours(6).toMillis();

    public record Ref(String owner, String repository) {
        public Ref {
            if (owner == null || repository == null
                    || !owner.matches("[A-Za-z0-9][A-Za-z0-9-]{0,99}")
                    || !repository.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")
                    || repository.equals(".") || repository.equals(".."))
                throw new IllegalArgumentException("Invalid GitHub repo identity");
        }
        public String fullName() { return owner + "/" + repository; }
        public URI preview() {
            return URI.create("https://opengraph.githubassets.com/1/"
                    + owner + "/" + repository);
        }
    }

    public static Ref forPort(DesktopPortsCatalog.Port port) {
        return new Ref(port.owner(), port.repository());
    }

    public static Ref forCandidate(DesktopGitHubPortsSearch.Candidate port) {
        return new Ref(port.owner(), port.repository());
    }

    /** Returns null for anything other than a stored, curated or discovered PC port. */
    public static Ref forLibraryId(String id) {
        if (id == null || !id.startsWith("pc:")) return null;
        String key = id.substring(3);
        if (key.startsWith("port.")) {
            DesktopPortsCatalog.Port port = DesktopPortsCatalog.byId(key.substring(5));
            return port == null ? null : forPort(port);
        }
        if (key.startsWith("github.")) {
            String tail = key.substring(7);
            int slash = tail.indexOf('/');
            if (slash <= 0 || slash == tail.length() - 1
                    || tail.indexOf('/', slash + 1) >= 0) return null;
            try { return new Ref(tail.substring(0, slash), tail.substring(slash + 1)); }
            catch (IllegalArgumentException error) { return null; }
        }
        return null;
    }

    private final Path root;
    private final HttpClient http;
    private final ThreadPoolExecutor workers;
    private final Map<String, BufferedImage> memory =
            new LinkedHashMap<>(64, .75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> x) {
                    return size() > 48;
                }
            };
    private final java.util.concurrent.ConcurrentHashMap<String, Long> failedUntil =
            new java.util.concurrent.ConcurrentHashMap<>();

    public DesktopPortArtworkService(Path root) {
        this.root = root.toAbsolutePath().normalize();
        http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(64), r -> {
                    Thread thread = new Thread(r, "OKUBI-Port-Artwork");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    public static Path defaultDirectory() {
        String appData = System.getenv("APPDATA");
        Path profile = appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".okubi")
                : Path.of(appData, "OKUBI");
        return profile.resolve("port-artwork-v1");
    }

    static String key(Ref ref) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(ref.fullName().toLowerCase(java.util.Locale.ROOT)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    Path manualPath(Ref ref) { return root.resolve("manual").resolve(key(ref) + ".png"); }

    /** Fast metadata-only hint for CINEMA cards; never loads an image on Swing EDT. */
    public boolean hasManualChoice(Ref ref) {
        Path file = manualPath(ref);
        try {
            return !Files.isSymbolicLink(file)
                    && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    && Files.size(file) > 0 && Files.size(file) <= MAX_BYTES;
        } catch (IOException | SecurityException unavailable) {
            return false;
        }
    }

    Path cachePath(Ref ref) {
        // New, versioned official image namespace never trusts previously
        // downloaded GitHub social preview cards as gameplay imagery.
        return root.resolve("official-screenshots-v2")
                .resolve(key(ref) + ".png");
    }

    /** Extra image has a URL-derived, per-game cache key, never shared with a different title. */
    static String galleryKey(Ref ref, int index) {
        java.util.List<URI> images = DesktopOfficialPortArtwork.gallery(ref);
        if (index < 1 || index >= images.size())
            throw new IllegalArgumentException("Unapproved gallery image index");
        try {
            byte[] data = (key(ref) + "|" + images.get(index)).getBytes(
                    java.nio.charset.StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    Path galleryCachePath(Ref ref, int index) {
        return root.resolve("official-gallery-v1").resolve(galleryKey(ref, index) + ".png");
    }

    /** Offline gallery lookup; index 0 retains user's manual image priority. */
    public BufferedImage localGallery(Ref ref, int index) {
        if (index == 0) return local(ref);
        if (index < 1 || index >= DesktopOfficialPortArtwork.gallery(ref).size()) return null;
        String hash = galleryKey(ref, index);
        synchronized (memory) {
            BufferedImage hit = memory.get(hash);
            if (hit != null) return hit;
        }
        BufferedImage image = readFile(galleryCachePath(ref, index));
        if (image != null) synchronized (memory) { memory.put(hash, image); }
        return image;
    }

    /** One requested screenshot at a time; never prefetches or changes the main cover. */
    public void loadGallery(Ref ref, int index, Consumer<BufferedImage> callback) {
        if (index == 0) {
            load(ref, callback);
            return;
        }
        java.util.List<URI> urls = DesktopOfficialPortArtwork.gallery(ref);
        if (index < 1 || index >= urls.size()) {
            publish(callback, null);
            return;
        }
        URI approved = urls.get(index);
        String hash = galleryKey(ref, index);
        submit(() -> {
            BufferedImage image = localGallery(ref, index);
            if (image == null && failedUntil.getOrDefault(hash, 0L)
                    < System.currentTimeMillis()) {
                try {
                    image = fetchImage(approved);
                    synchronized (memory) { memory.put(hash, image); }
                    try { savePng(galleryCachePath(ref, index), image); }
                    catch (IOException ignored) { /* cache optional */ }
                } catch (IOException | InterruptedException failed) {
                    if (failed instanceof InterruptedException)
                        Thread.currentThread().interrupt();
                    failedUntil.put(hash, System.currentTimeMillis() + MISS_TTL_MILLIS);
                }
            }
            publish(callback, image);
        });
    }

    private static BufferedImage safeDecode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES)
            throw new IOException("Image size exceeds limit");
        try (ImageInputStream stream = ImageIO.createImageInputStream(
                new ByteArrayInputStream(bytes))) {
            if (stream == null) throw new IOException("Image stream invalid");
            Iterator<ImageReader> formats = ImageIO.getImageReaders(stream);
            if (!formats.hasNext()) throw new IOException("Image format is unsupported");
            ImageReader reader = formats.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_SIDE
                        || height > MAX_SIDE
                        || (long) width * height > MAX_PIXELS)
                    throw new IOException("Image dimensions exceed limit");
                BufferedImage input = reader.read(0);
                if (input == null) throw new IOException("Unable to decode image");
                double factor = Math.min(1.0,
                        Math.min(1100.0 / input.getWidth(), 700.0 / input.getHeight()));
                int w = Math.max(1, (int) Math.round(input.getWidth() * factor));
                int h = Math.max(1, (int) Math.round(input.getHeight() * factor));
                BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = scaled.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.setColor(new java.awt.Color(17, 14, 29));
                    graphics.fillRect(0, 0, w, h);
                    graphics.drawImage(input, 0, 0, w, h, null);
                } finally { graphics.dispose(); }
                return scaled;
            } finally { reader.dispose(); }
        } catch (RuntimeException corrupt) {
            throw new IOException("Invalid image", corrupt);
        }
    }

    private static BufferedImage readFile(Path file) {
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(file) || Files.size(file) > MAX_BYTES)
                return null;
            return safeDecode(Files.readAllBytes(file));
        } catch (IOException | RuntimeException ignored) { return null; }
    }

    private static void savePng(Path target, BufferedImage image) throws IOException {
        Path dir = target.getParent();
        if (Files.isSymbolicLink(dir))
            throw new IOException("Artwork folder must not be a symlink");
        Files.createDirectories(dir);
        if (Files.isSymbolicLink(dir) || Files.isSymbolicLink(target))
            throw new IOException("Symlinked cache target denied");
        Path temp = Files.createTempFile(dir, "okubi-art-", ".tmp");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", bytes) || bytes.size() > MAX_BYTES)
                throw new IOException("Artwork file exceeds limit");
            Files.write(temp, bytes.toByteArray());
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }

    /** Read-only offline lookup, manual > cache; safe to test without internet. */
    public BufferedImage local(Ref ref) {
        BufferedImage manual = readFile(manualPath(ref));
        if (manual != null) return manual;
        synchronized (memory) {
            BufferedImage hit = memory.get(key(ref));
            if (hit != null) return hit;
        }
        BufferedImage cached = readFile(cachePath(ref));
        if (cached != null) synchronized (memory) { memory.put(key(ref), cached); }
        return cached;
    }

    /**
     * Copies a user-chosen PNG/JPEG/GIF to an independent OKUBI cache.
     * Original file stays untouched; no uploading. Method is called on a worker.
     */
    public BufferedImage chooseManual(Ref ref, Path selected) throws IOException {
        if (selected == null) throw new IOException("No image selected");
        Path file = selected.toAbsolutePath().normalize();
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(file) || Files.size(file) > MAX_BYTES)
            throw new IOException("Select an image below 2 MB, not a shortcut");
        BufferedImage image = safeDecode(Files.readAllBytes(file));
        savePng(manualPath(ref), image);
        failedUntil.remove(key(ref));
        return image;
    }

    public void clearManual(Ref ref) throws IOException {
        Files.deleteIfExists(manualPath(ref));
    }

    private BufferedImage fetchOnline(Ref ref) throws IOException, InterruptedException {
        // Fail closed: a GitHub social card is not a screenshot of a game.
        // Never substitute an unrelated or unreviewed image for another port.
        URI official = DesktopOfficialPortArtwork.source(ref);
        if (official == null)
            throw new IOException("No approved gameplay screenshot for this port");
        return fetchImage(official);
    }

    private BufferedImage fetchImage(URI uri) throws IOException, InterruptedException {
        if (!DesktopOfficialPortArtwork.permitted(uri))
            throw new IOException("Artwork origin not approved");
        HttpRequest req = HttpRequest.newBuilder(uri).GET()
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", "OKUBI-Ports-Artwork/1.0")
                .header("Accept", "image/png, image/jpeg")
                .build();
        HttpResponse<InputStream> response = http.send(
                req, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream input = response.body()) {
            if (response.statusCode() != 200)
                throw new IOException("Project image unavailable");
            String type = response.headers().firstValue("content-type")
                    .orElse("").toLowerCase(java.util.Locale.ROOT);
            if (!type.startsWith("image/png") && !type.startsWith("image/jpeg"))
                throw new IOException("Response is not PNG/JPEG artwork");
            if (response.headers().firstValueAsLong("content-length").orElse(0)
                    > MAX_BYTES) throw new IOException("Artwork too large");
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            return safeDecode(bytes);
        }
    }

    private void submit(Runnable action) {
        try { workers.execute(action); }
        catch (RejectedExecutionException ignored) {
            // Never freeze the EDT when many cards are created.
        }
    }

    private void publish(Consumer<BufferedImage> callback, BufferedImage image) {
        if (callback != null)
            SwingUtilities.invokeLater(() -> callback.accept(image));
    }

    /** Fully async: manual > disk/memory cache > official GitHub preview > UI fallback. */
    public void load(Ref ref, Consumer<BufferedImage> callback) {
        submit(() -> {
            BufferedImage image = local(ref);
            if (image == null && failedUntil.getOrDefault(key(ref), 0L)
                    < System.currentTimeMillis()) {
                try {
                    image = fetchOnline(ref);
                    if (image != null) {
                        synchronized (memory) { memory.put(key(ref), image); }
                        try { savePng(cachePath(ref), image); }
                        catch (IOException ignored) { /* cache is optional */ }
                    }
                } catch (IOException | InterruptedException error) {
                    if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                    failedUntil.put(key(ref), System.currentTimeMillis() + MISS_TTL_MILLIS);
                }
            }
            publish(callback, image);
        });
    }

    /** Explicit user refresh: never destroys manual overrides. */
    public void refresh(Ref ref, Consumer<BufferedImage> callback) {
        submit(() -> {
            if (readFile(manualPath(ref)) == null) {
                synchronized (memory) { memory.remove(key(ref)); }
                try { Files.deleteIfExists(cachePath(ref)); }
                catch (IOException ignored) { }
            }
            failedUntil.remove(key(ref));
            BufferedImage manual = readFile(manualPath(ref));
            if (manual != null) {
                publish(callback, manual);
                return;
            }
            try {
                BufferedImage image = fetchOnline(ref);
                if (image != null) {
                    synchronized (memory) { memory.put(key(ref), image); }
                    try { savePng(cachePath(ref), image); }
                    catch (IOException ignored) { }
                }
                publish(callback, image);
            } catch (IOException | InterruptedException error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                failedUntil.put(key(ref), System.currentTimeMillis() + MISS_TTL_MILLIS);
                publish(callback, local(ref));
            }
        });
    }

    /** Changes only the artwork override; never deletes any saved game. */
    public void chooseManualAsync(Ref ref, Path localPicture,
                                  Consumer<BufferedImage> callback,
                                  Consumer<String> onError) {
        submit(() -> {
            try { publish(callback, chooseManual(ref, localPicture)); }
            catch (IOException error) {
                if (onError != null)
                    SwingUtilities.invokeLater(() -> onError.accept(error.getMessage()));
            }
        });
    }

    @Override public void close() { workers.shutdownNow(); }
}
