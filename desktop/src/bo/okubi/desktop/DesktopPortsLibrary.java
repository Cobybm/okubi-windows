package bo.okubi.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Links existing, user-selected PC installations, never downloads/extracts
 * arbitrary GitHub binaries or modifies game files/saves.
 */
public final class DesktopPortsLibrary {
    private static final String PREFIX = "port.";
    private final Path propertiesFile;
    private final Properties links = new Properties();

    public DesktopPortsLibrary(Path file) throws IOException {
        this.propertiesFile = file.toAbsolutePath().normalize();
        if (Files.isRegularFile(propertiesFile, LinkOption.NOFOLLOW_LINKS)) {
            try (InputStream input = Files.newInputStream(propertiesFile)) {
                links.load(input);
            }
        }
        // Untrusted old preferences cannot introduce arbitrary project IDs.
        for (String key : new java.util.HashSet<>(links.stringPropertyNames())) {
            boolean curated = key.startsWith(PREFIX)
                    && DesktopPortsCatalog.byId(key.substring(PREFIX.length())) != null;
            boolean discovered = key.length() <= 225
                    && key.matches("github\\.[a-z0-9][a-z0-9-]*/[a-z0-9][a-z0-9._-]*");
            if (!curated && !discovered) links.remove(key);
        }
    }

    public static Path defaultFile() {
        String appData = System.getenv("APPDATA");
        Path root = appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".okubi")
                : Path.of(appData, "OKUBI");
        return root.resolve("desktop-ports-v1.properties");
    }

    private static byte[] header(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            return input.readNBytes(4096);
        }
    }

    /** Validate the actual executable bytes, not only the extension. */
    public static Path checkedExecutable(Path executable) throws IOException {
        if (executable == null) throw new IOException("Selecciona un ejecutable.");
        Path absolute = executable.toAbsolutePath().normalize();
        if (!absolute.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                .endsWith(".exe")) throw new IOException("Solo se admiten ejecutables .exe de Windows.");
        if (!Files.isRegularFile(absolute, LinkOption.NOFOLLOW_LINKS) ||
                Files.isSymbolicLink(absolute)) throw new IOException("El archivo .exe no existe o es un enlace.");
        if (Files.size(absolute) < 128 || Files.size(absolute) > 2_000_000_000L)
            throw new IOException("Tamaño de ejecutable no válido.");
        byte[] bytes = header(absolute);
        if (bytes.length < 128 || bytes[0] != 'M' || bytes[1] != 'Z')
            throw new IOException("El archivo no tiene cabecera ejecutable Windows.");
        int offset = ByteBuffer.wrap(bytes, 0x3c, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (offset < 64 || offset + 6 > bytes.length ||
                bytes[offset] != 'P' || bytes[offset + 1] != 'E' ||
                bytes[offset + 2] != 0 || bytes[offset + 3] != 0)
            throw new IOException("No se encontró firma PE de Windows.");
        int machine = ByteBuffer.wrap(bytes, offset + 4, 2)
                .order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xffff;
        if (machine != 0x8664 && machine != 0x14c)
            throw new IOException("El ejecutable no es para Windows x64/x86.");
        return absolute;
    }

    public synchronized Path linked(DesktopPortsCatalog.Port port) {
        String raw = links.getProperty(PREFIX + port.id());
        if (raw == null || raw.isBlank()) return null;
        try {
            Path file = Path.of(raw).toAbsolutePath().normalize();
            return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ? file : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public synchronized void link(DesktopPortsCatalog.Port port, Path executable)
            throws IOException {
        Path approved = checkedExecutable(executable);
        String key = PREFIX + port.id();
        String old = links.getProperty(key);
        links.setProperty(key, approved.toString());
        try { save(); }
        catch (IOException error) {
            if (old == null) links.remove(key);
            else links.setProperty(key, old);
            throw error;
        }
    }

    public synchronized void unlink(DesktopPortsCatalog.Port port) throws IOException {
        String key = PREFIX + port.id();
        String old = links.getProperty(key);
        links.remove(key);
        try { save(); }
        catch (IOException error) {
            if (old != null) links.setProperty(key, old);
            throw error;
        }
    }

    /** User-review-only repository shortcuts; never trust online repository as a runnable binary. */
    private static String key(DesktopGitHubPortsSearch.Candidate candidate) {
        return "github." + candidate.fullName().toLowerCase(java.util.Locale.ROOT);
    }

    public synchronized Path linked(DesktopGitHubPortsSearch.Candidate candidate) {
        String raw = links.getProperty(key(candidate));
        if (raw == null || raw.isBlank()) return null;
        try {
            Path file = Path.of(raw).toAbsolutePath().normalize();
            return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ? file : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public synchronized void link(DesktopGitHubPortsSearch.Candidate candidate,
                                  Path executable) throws IOException {
        Path approved = checkedExecutable(executable);
        String name = key(candidate);
        String previous = links.getProperty(name);
        links.setProperty(name, approved.toString());
        try { save(); } catch (IOException error) {
            if (previous == null) links.remove(name);
            else links.setProperty(name, previous);
            throw error;
        }
    }

    public synchronized void unlink(DesktopGitHubPortsSearch.Candidate candidate)
            throws IOException {
        String name = key(candidate);
        String previous = links.getProperty(name);
        links.remove(name);
        try { save(); } catch (IOException error) {
            if (previous != null) links.setProperty(name, previous);
            throw error;
        }
    }

    public Process launch(DesktopGitHubPortsSearch.Candidate candidate) throws IOException {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
                .contains("windows")) throw new IOException("Solo se ejecutan ports en Windows.");
        Path file = linked(candidate);
        if (file == null) throw new IOException("Selecciona primero un ejecutable instalado.");
        Path exe = checkedExecutable(file);
        return new ProcessBuilder(exe.toString()).directory(exe.getParent().toFile()).start();
    }

    private void save() throws IOException {
        Path parent = propertiesFile.getParent();
        if (parent == null) throw new IOException("Ruta de configuración inválida.");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "okubi-ports-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temporary)) {
                links.store(out, "OKUBI PC ports: user-selected executable paths only");
            }
            try {
                Files.move(temporary, propertiesFile,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, propertiesFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public Process launch(DesktopPortsCatalog.Port port) throws IOException {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
                .contains("windows")) throw new IOException("Solo se ejecutan ports en Windows.");
        Path exe = linked(port);
        if (exe == null) throw new IOException("Primero vincula el ejecutable instalado.");
        exe = checkedExecutable(exe);
        // Deliberately no cmd.exe, shell interpretation, script or auto-download.
        // This method is called only from an explicit Play confirmation in the UI.
        return new ProcessBuilder(exe.toString())
                .directory(exe.getParent().toFile()).start();
    }
}
