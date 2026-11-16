package bo.okubi.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Optional PS1 firmware manager. Beetle PSX includes OpenBIOS: an external
 * BIOS is NOT required for the standard launch. If a player provides their
 * original PS1 BIOS, only canonical documented MD5-matching region files
 * are accepted. It is never downloaded or distributed by OKUBI.
 *
 * The existing JNI bridge exposes the per-console saves/ps1 folder as
 * RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY, so Beetle can read this firmware.
 */
public final class DesktopPs1Firmware {
    private DesktopPs1Firmware() { }

    private static final Map<String, String> DOCUMENTED = Map.of(
        "scph5500.bin", "8dd7d5296a650fac7319bce665a6a53c",
        "scph5501.bin", "490f666e1afb15b7362b406ed1cea246",
        "scph5502.bin", "32736f17079d0b2b7024407c39bd3050"
    );

    public enum Result { IMPORTED, ALREADY_PRESENT }

    public static Path defaultDirectory() {
        return DesktopSaves.defaultRoot().resolve("ps1");
    }

    public static boolean recognizedName(String name) {
        return name != null && DOCUMENTED.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public static Result importOwnedBios(Path source, Path systemDir) throws IOException {
        if (source == null || systemDir == null || source.getFileName() == null)
            throw new IOException("Selecciona tu archivo de BIOS.");
        String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        String expected = DOCUMENTED.get(name);
        if (expected == null)
            throw new IOException("Nombre BIOS no admitido. Usa SCPH5500.bin, SCPH5501.bin o SCPH5502.bin.");
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(source)
                || Files.size(source) != 512L * 1024L)
            throw new IOException("La BIOS original debe ser un archivo regular de 512 KiB.");
        if (!expected.equals(digest(source)))
            throw new IOException("La firma MD5 no coincide con la BIOS PS1 documentada; no se importó.");
        Path folder = systemDir.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(folder))
            throw new IOException("No se permite una carpeta BIOS enlazada.");
        Files.createDirectories(folder);
        if (Files.isSymbolicLink(folder))
            throw new IOException("No se permite una carpeta BIOS enlazada.");
        Path target = folder.resolve(name);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                    && expected.equals(digest(target)))
                return Result.ALREADY_PRESENT;
            throw new IOException("Ya existe una BIOS distinta. OKUBI nunca la sobrescribe.");
        }
        Path temporary = Files.createTempFile(folder, ".okubi-ps1-bios-", ".tmp");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            if (!expected.equals(digest(temporary)))
                throw new IOException("La copia cambió durante la importación.");
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target);
            }
            return Result.IMPORTED;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String digest(Path file) throws IOException {
        try {
            MessageDigest hash = MessageDigest.getInstance("MD5");
            try (InputStream stream = Files.newInputStream(file)) {
                byte[] block = new byte[8192];
                int size;
                while ((size = stream.read(block)) != -1)
                    hash.update(block, 0, size);
            }
            return HexFormat.of().formatHex(hash.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("El sistema no permite verificar la BIOS.", error);
        }
    }
}
