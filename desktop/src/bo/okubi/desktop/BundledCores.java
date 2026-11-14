package bo.okubi.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Autoconfigures trusted, bundled Libretro Windows x64 cores.
 * The user never needs to browse for DLLs to play GB/GBC/GBA/SNES.
 * All DLLs stay under the jpackage application directory, not in APPDATA.
 */
public final class BundledCores {
    private static final Map<DesktopLibrary.Console, String> NAMES;

    static {
        EnumMap<DesktopLibrary.Console, String> names =
                new EnumMap<>(DesktopLibrary.Console.class);
        names.put(DesktopLibrary.Console.GB, "sameboy_libretro.dll");
        names.put(DesktopLibrary.Console.GBC, "sameboy_libretro.dll");
        names.put(DesktopLibrary.Console.GBA, "mgba_libretro.dll");
        names.put(DesktopLibrary.Console.SNES, "mednafen_supafaust_libretro.dll");
        NAMES = Collections.unmodifiableMap(names);
    }

    private BundledCores() { }

    public static String nameFor(DesktopLibrary.Console console) {
        return NAMES.get(console);
    }

    public static Path bundledDirectory() {
        // Controlled override is strictly for tests and portable builds.
        String test = System.getProperty("okubi.cores.dir");
        if (test != null && !test.isBlank()) return Path.of(test).toAbsolutePath();
        try {
            Path source = Path.of(BundledCores.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toAbsolutePath();
            Path location = Files.isDirectory(source) ? source : source.getParent();
            if (location != null) return location.resolve("cores");
        } catch (URISyntaxException | RuntimeException ignored) { }
        return Path.of("cores").toAbsolutePath();
    }

    /**
     * Returns null for a development build without cores; production ZIP must
     * contain all cores and the hash manifest, checked by Windows CI.
     */
    public static Path resolve(DesktopLibrary.Console console) throws IOException {
        String filename = nameFor(console);
        if (filename == null) return null;
        Path root = bundledDirectory();
        Path file = root.resolve(filename);
        if (!Files.isRegularFile(file)) return null;
        Path manifest = root.resolve("CORE-SHA256.properties");
        if (!Files.isRegularFile(manifest)) {
            throw new IOException("El núcleo integrado no tiene manifiesto SHA-256: "
                    + filename);
        }
        Properties pins = new Properties();
        try (InputStream input = Files.newInputStream(manifest)) {
            pins.load(input);
        }
        String expected = pins.getProperty(filename);
        if (expected == null || !expected.matches("[0-9a-f]{64}"))
            throw new IOException("El núcleo integrado no tiene huella válida: " + filename);
        String actual = sha256(file);
        if (!MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
            throw new IOException("El núcleo de " + console.label
                    + " falló la comprobación SHA-256. Reinstala el ZIP completo "
                    + "sin borrar tu biblioteca ni tus partidas.");
        return file;
    }

    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream stream = Files.newInputStream(file)) {
                byte[] buffer = new byte[16 * 1024];
                int size;
                while ((size = stream.read(buffer)) != -1) {
                    if (size > 0) digest.update(buffer, 0, size);
                }
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest.digest())
                hex.append(String.format(Locale.ROOT, "%02x", b & 255));
            return hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("Java no admite SHA-256.", error);
        }
    }
}
