package bo.okubi.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Strict allowlisted direct downloads of explicitly authorized homebrew. */
public final class DesktopHomebrewDownloads {
    public record Entry(String title, String platform, String page,
                        String directUrl, String filename) {
        public boolean direct() { return directUrl != null && !directUrl.isBlank(); }
    }
    public record Receipt(Path file, String sha256) { }
    private static final int MAX_ROM = 16 * 1024 * 1024;
    private static final byte[] NINTENDO_LOGO = HexFormat.of().parseHex("CEED6666CC0D000B03730083000C000D0008111F8889000EDCCC6EE6DDDDD999BBBB67636E0EECCCDDDC999FBBB9333E");
    // Verified creator pages and official curated lists. Only the allowlisted
    // 2048 GB endpoint performs an in-app direct ROM download; for all others
    // the author controls the download, license and current availability.
    private static final List<Entry> ENTRIES = List.of(
        new Entry("2048 GB · descargar e importar", "GB / GBC",
            "https://hh.gbdev.io/",
            "https://hh3.gbdev.io/api/entry/2048gb/2048.gb", "2048gb.gb"),
        new Entry("Tobu Tobu Girl Deluxe · ROM gratis", "GB / GBC",
            "https://tangramgames.itch.io/tobu-tobu-girl-deluxe", null, null),
        new Entry("Opossum Country · terror retro", "GB / GBC",
            "https://benjelter.itch.io/opossum-country", null, null),
        new Entry("Homebrew Hub · muchos juegos", "GB / GBC / GBA",
            "https://hh.gbdev.io/", null, null),
        new Entry("Goodboy Galaxy · DEMO en español", "GBA",
            "https://goodboygalaxy.itch.io/goodboy-galaxy-demo", null, null),
        new Entry("Böbl · aventura gratuita", "NES",
            "https://morphcatgames.itch.io/bobl", null, null),
        new Entry("Dottie Flowers · ROM gratis", "SNES",
            "https://goldlocke.itch.io/dottie-flowers", null, null),
        new Entry("SNES · juegos homebrew para descargar", "SNES",
            "https://itch.io/games/free/tag-homebrew/tag-snes-rom", null, null),
        new Entry("PSoXide Demo Disc · BIN/CUE libre", "PS1",
            "https://bonnie-studios.itch.io/psoxide-demo-disc", null, null),
        new Entry("PS1 · catálogo homebrew gratuito", "PS1",
            "https://itch.io/games/free/tag-homebrew/tag-ps1", null, null),
        new Entry("Sega Mega Drive · homebrew gratuito", "SEGA Genesis",
            "https://itch.io/games/free/tag-homebrew/tag-sega-genesis", null, null),
        new Entry("ScummVM · juegos freeware oficiales", "ScummVM / PC clásico",
            "https://www.scummvm.org/es/games/", null, null),
        new Entry("Catálogo general de homebrew gratis", "Varias consolas",
            "https://itch.io/games/free/tag-homebrew", null, null)
    );
    private DesktopHomebrewDownloads() { }
    public static List<Entry> entries() { return ENTRIES; }
    public static Path defaultDirectory() {
        return DesktopLibrary.defaultSettingsFile().getParent().resolve("homebrew");
    }
    /** Reject arbitrary endpoints, redirects and user-controlled filename tricks. */
    public static boolean authorizedDirect(Entry entry) {
        // Only metadata audited and shipped with OKUBI may trigger a direct download.
        if (entry == null || !ENTRIES.contains(entry)
                || !entry.direct() || entry.filename() == null) return false;
        if (!entry.filename().matches("[A-Za-z0-9_-]{1,70}\\.(gb|gbc|gba|nes|sfc)"))
            return false;
        try {
            URI uri = URI.create(entry.directUrl());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "hh3.gbdev.io".equalsIgnoreCase(uri.getHost())
                    && uri.getPort() == -1
                    && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && uri.getPath().matches("/api/entry/[a-zA-Z0-9_-]+/[a-zA-Z0-9_.-]+\\.(gb|gbc|gba|nes|sfc)");
        } catch (IllegalArgumentException error) { return false; }
    }
    public static Receipt download(Entry entry, Path directory) throws IOException {
        if (!authorizedDirect(entry)) throw new IOException("No hay descarga directa autorizada para este juego.");
        Path root = directory.toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root)) throw new IOException("No se aceptan carpetas simbólicas.");
        Path file = root.resolve(entry.filename()).normalize();
        if (!file.getParent().equals(root) || Files.isSymbolicLink(file))
            throw new IOException("Ruta de descarga insegura.");
        if (Files.exists(file)) {
            if (!Files.isRegularFile(file)) throw new IOException("La ruta de destino está ocupada.");
            verifyCartridge(file, entry.filename());
            return new Receipt(file, digest(file));
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(entry.directUrl()))
                .timeout(Duration.ofSeconds(45)).header("Accept", "application/octet-stream")
                .GET().build();
        Path temp = Files.createTempFile(root, "okubi-homebrew-", ".part");
        try {
            HttpResponse<InputStream> response;
            try { response = client.send(request, HttpResponse.BodyHandlers.ofInputStream()); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Descarga interrumpida", interrupted);
            }
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IOException("El servidor respondió: " + response.statusCode());
            }
            try (InputStream in = response.body();
                 java.io.OutputStream out = Files.newOutputStream(temp)) {
                byte[] buf = new byte[8192];
                long total = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    total += n;
                    if (total > MAX_ROM) throw new IOException("La ROM supera el límite seguro.");
                    out.write(buf, 0, n);
                }
                if (total < 0x150) throw new IOException("Descarga vacía o incompleta.");
            }
            verifyCartridge(temp, entry.filename());
            String hash = digest(temp);
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ex) { Files.move(temp, file); }
            return new Receipt(file, hash);
        } finally { Files.deleteIfExists(temp); }
    }
    /** Reject a modified cached download before importing it into a game session. */
    private static void verifyCartridge(Path file, String filename) throws IOException {
        long size = Files.size(file);
        if (size < 32768 || size > MAX_ROM)
            throw new IOException("Tamaño incompatible con una ROM válida.");
        if (filename.toLowerCase(Locale.ROOT).endsWith(".gb")
                || filename.toLowerCase(Locale.ROOT).endsWith(".gbc")) {
            try (InputStream in = Files.newInputStream(file)) {
                byte[] header = in.readNBytes(0x134);
                if (header.length < 0x134) throw new IOException("Cabecera Game Boy incompleta.");
                for (int i = 0; i < NINTENDO_LOGO.length; i++) {
                    if (header[0x104 + i] != NINTENDO_LOGO[i])
                        throw new IOException("La descarga no parece una ROM de Game Boy válida.");
                }
            }
        }
    }

    private static String digest(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) >= 0) if (n > 0) md.update(b, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException ex) { throw new IOException(ex); }
    }
}
