package bo.okubi.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;

/**
 * Isolated battery/RTC storage for the embedded Windows cores.
 * Separate from external-emulator data, Android app data and ROM files.
 */
public final class DesktopSaves {
    private static final int MAX_RAM = 1024 * 1024;
    private static final int MAX_RTC = 64 * 1024;

    public final Path ramFile;
    public final Path rtcFile;
    public final Path directory;

    public DesktopSaves(Path root, DesktopLibrary.Console console, Path rom)
            throws IOException {
        if (root == null || console == null || rom == null)
            throw new IOException("Faltan datos para identificar una partida.");
        String digest = sha256(rom);
        directory = root.toAbsolutePath().normalize()
                .resolve(console.name().toLowerCase(Locale.ROOT));
        ramFile = directory.resolve(digest + ".srm");
        rtcFile = directory.resolve(digest + ".rtc");
    }

    public static Path defaultRoot() {
        String appData = System.getenv("APPDATA");
        Path root = appData != null && !appData.isBlank()
                ? Path.of(appData) : Path.of(System.getProperty("user.home"), ".okubi");
        return root.resolve("OKUBI").resolve("saves");
    }

    public static String sha256(Path rom) throws IOException {
        if (!Files.isRegularFile(rom) || !Files.isReadable(rom))
            throw new IOException("No se puede leer la ROM para identificar su partida.");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream file = Files.newInputStream(rom)) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = file.read(buffer)) != -1)
                    if (count > 0) digest.update(buffer, 0, count);
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest.digest())
                hex.append(String.format(Locale.ROOT, "%02x", b & 255));
            return hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("SHA-256 no está disponible.", error);
        }
    }

    public void restore() throws IOException {
        if (Files.isRegularFile(ramFile)) {
            byte[] data = Files.readAllBytes(ramFile);
            if (data.length == 0 || data.length > MAX_RAM
                    || !WindowsRetroNative.loadRam(data))
                throw new IOException("Guardado SRAM anterior incompatible. "
                        + "No se modificó: " + ramFile);
        }
        if (Files.isRegularFile(rtcFile)) {
            byte[] data = Files.readAllBytes(rtcFile);
            if (data.length == 0 || data.length > MAX_RTC
                    || !WindowsRetroNative.loadRtc(data))
                throw new IOException("Reloj RTC anterior incompatible. "
                        + "No se modificó: " + rtcFile);
        }
    }

    public void store() throws IOException {
        storeData(ramFile, WindowsRetroNative.saveRam(), MAX_RAM);
        storeData(rtcFile, WindowsRetroNative.saveRtc(), MAX_RTC);
    }

    /** Save states are pinned to the exact core DLL to avoid incompatible loads. */
    public Path stateFile(Path coreDll) throws IOException {
        String coreDigest = sha256(coreDll);
        String romDigest = ramFile.getFileName().toString().replace(".srm", "");
        return directory.resolve(romDigest + "." + coreDigest.substring(0, 16)
                + ".slot1.state");
    }

    public void saveState(Path coreDll, byte[] state) throws IOException {
        if (state == null || state.length == 0)
            throw new IOException("Este núcleo no permite guardar estados.");
        storeData(stateFile(coreDll), state, 32 * 1024 * 1024);
    }

    public byte[] readState(Path coreDll) throws IOException {
        Path source = stateFile(coreDll);
        if (!Files.isRegularFile(source))
            throw new IOException("No existe un estado rápido para esta ROM y núcleo.");
        long length = Files.size(source);
        if (length <= 0 || length > 32L * 1024 * 1024)
            throw new IOException("El estado tiene un tamaño no permitido. No se cargó.");
        return Files.readAllBytes(source);
    }

    /** Screenshots are separate from battery saves and never overwrite them. */
    public Path screenshot(java.awt.image.BufferedImage frame) throws IOException {
        if (frame == null || frame.getWidth() <= 0 || frame.getHeight() <= 0)
            throw new IOException("No hay imagen para capturar todavía.");
        Path folder = directory.resolve("screenshots");
        Files.createDirectories(folder);
        String prefix = ramFile.getFileName().toString().substring(0, 12) + "-";
        Path target = Files.createTempFile(folder, prefix, ".png");
        try {
            if (!javax.imageio.ImageIO.write(frame, "png", target.toFile()))
                throw new IOException("No existe codificador PNG en Java.");
            return target;
        } catch (IOException problem) {
            Files.deleteIfExists(target);
            throw problem;
        }
    }

    /** Also testable without a loaded emulator core. */
    static void storeData(Path target, byte[] data, int maximum) throws IOException {
        if (data == null || data.length == 0) return;
        if (maximum <= 0 || data.length > maximum)
            throw new IOException("Tamaño de partida no permitido.");
        Path directory = target.toAbsolutePath().getParent();
        if (directory == null) throw new IOException("Carpeta de guardado inválida.");
        Files.createDirectories(directory);
        if (Files.exists(target)) {
            byte[] existing = Files.readAllBytes(target);
            if (Arrays.equals(existing, data)) return;
            if (existing.length > maximum)
                throw new IOException("Guardado anterior inesperado, no se sobrescribe: " + target);
            // Keep the previous battery as a second recoverable file.
            Files.copy(target, target.resolveSibling(target.getFileName() + ".previous"),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        Path temp = Files.createTempFile(directory, ".okubi-save-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temp,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer payload = ByteBuffer.wrap(data);
                while (payload.hasRemaining()) channel.write(payload);
                channel.force(true);
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
