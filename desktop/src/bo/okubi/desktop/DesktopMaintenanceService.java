package bo.okubi.desktop;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Only OKUBI-generated, disposable caches are eligible for maintenance.
 * Restrict scans to online-cover-art cache; never inspect save vault, ROMs or emulator installs.
 * No automatic deletion; a missing cache is not created.
 */
public final class DesktopMaintenanceService {
    public static final String CACHE_FOLDER = "cover-art-v1";
    private static final java.util.regex.Pattern GENERATED = java.util.regex.Pattern.compile("[0-9a-f]{64}\\.png(\\.missing)?");
    private final Path root;

    public DesktopMaintenanceService(Path cacheRoot) {
        this.root = cacheRoot.toAbsolutePath().normalize();
    }

    public static DesktopMaintenanceService defaultService() {
        return new DesktopMaintenanceService(OnlineCoverService.defaultDirectory());
    }

    public record Report(int files, long bytes, boolean cacheExists) {}
    private static boolean link(Path path) {
        return Files.isSymbolicLink(path) || (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                && Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther());
    }
    private void validate() throws IOException {
        // Reject symlinks at ANY ancestor, including the intended cache directory.
        Path cursor = root;
        while (cursor != null) {
            if (link(cursor)) throw new IOException("Ruta enlazada no permitida: " + cursor);
            cursor = cursor.getParent();
        }
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("La caché no es una carpeta");
        if (!CACHE_FOLDER.equals(root.getFileName().toString()))
            throw new IOException("Carpeta de caché no autorizada");
        Path parent = root.getParent();
        if (parent == null || !"OKUBI".equals(parent.getFileName().toString()))
            throw new IOException("Ruta fuera del caché de OKUBI");
    }

    public Report inspect() throws IOException {
        validate();
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return new Report(0, 0, false);
        List<Path> files = new ArrayList<>();
        final long[] bytes = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                if (attrs.isSymbolicLink() || !attrs.isDirectory())
                    throw new IOException("Carpeta enlazada o especial detectada");
                if (!dir.equals(root) && (!dir.getParent().equals(root)
                        || !isConsoleDirectory(dir.getFileName().toString())))
                    throw new IOException("Carpeta inesperada dentro de caché");
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                if (!attrs.isRegularFile()) throw new IOException("Archivo enlazado o especial detectado");
                if (eligible(file)) {
                    files.add(file);
                    bytes[0] = Math.addExact(bytes[0], attrs.size());
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return new Report(files.size(), bytes[0], true);
    }

    private boolean isConsoleDirectory(String name) {
        for (DesktopLibrary.Console console : DesktopLibrary.Console.values())
            if (console.name().equals(name)) return true;
        return false;
    }

    private boolean eligible(Path file) {
        return file.getParent() != null && file.getParent().getParent() != null
                && file.getParent().getParent().equals(root)
                && isConsoleDirectory(file.getParent().getFileName().toString())
                && GENERATED.matcher(file.getFileName().toString()).matches();
    }

    /** Call only after separate user confirmation. Re-check before every deletion. */
    public Report cleanConfirmed() throws IOException {
        validate();
        Report before = inspect();
        if (!before.cacheExists()) return before;
        // Remove only regular files and empty subfolders, never follow links.
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                if (!attrs.isDirectory() || attrs.isSymbolicLink())
                    throw new IOException("Se detectó un enlace durante la limpieza");
                if (!dir.equals(root) && (!dir.getParent().equals(root)
                        || !isConsoleDirectory(dir.getFileName().toString())))
                    throw new IOException("Carpeta inesperada dentro de caché");
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                if (!attrs.isRegularFile() || attrs.isSymbolicLink())
                    throw new IOException("Archivo inseguro durante la limpieza");
                if (eligible(file)) Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException error)
                    throws IOException {
                if (error != null) throw error;
                if (!dir.equals(root)) {
                    try (var children = Files.list(dir)) {
                        if (children.findAny().isEmpty()) Files.delete(dir);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return before;
    }
}
