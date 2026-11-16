package bo.okubi.desktop;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Opt-in, bounded scanner for existing PC ports in ONE user-selected folder.
 * No full-drive scan, cloud calls, installs, launch, saves, game data or writes.
 */
public final class DesktopInstalledPortsScanner {
    public static final int MAX_FILES = 4_000;
    public static final int MAX_RESULTS = 60;
    public static final int MAX_DEPTH = 4;

    public record Match(DesktopPortsCatalog.Port port, Path exe) { }
    public record ScanResult(List<Match> matches, int filesExamined, boolean truncated) {
        public ScanResult { matches = List.copyOf(matches); }
    }

    private static final Set<String> IGNORED = Set.of(
            ".git", "saves", "savegames", "savestates", "roms", "bios", "firmware",
            "userdata", "node_modules", "system volume information", "$recycle.bin"
    );

    private static final Map<String, String> KNOWN_EXES = Map.ofEntries(
            Map.entry("openttd.exe", "openttd"),
            Map.entry("devilutionx.exe", "devilutionx"),
            Map.entry("openrct2.exe", "openrct2"),
            Map.entry("openloco.exe", "openloco"),
            Map.entry("openmw.exe", "openmw"),
            Map.entry("scummvm.exe", "scummvm"),
            Map.entry("gzdoom.exe", "gzdoom"),
            Map.entry("dhewm3.exe", "dhewm3"),
            Map.entry("corsix-th.exe", "corsixth"),
            Map.entry("augustus.exe", "augustus"),
            Map.entry("julius.exe", "julius"),
            Map.entry("gemrb.exe", "gemrb"),
            Map.entry("opentyrian.exe", "opentyrian"),
            Map.entry("vcmi.exe", "vcmi"),
            Map.entry("endless-sky.exe", "endlesssky"),
            Map.entry("warzone2100.exe", "warzone2100"),
            Map.entry("freeciv.exe", "freeciv"),
            Map.entry("yquake2.exe", "yamagiquake2"),
            Map.entry("luanti.exe", "luanti"),
            Map.entry("supertuxkart.exe", "supertuxkart"),
            Map.entry("daggerfallunity.exe", "daggerfallunity"),
            Map.entry("openxcom.exe", "openxcom"),
            Map.entry("theforceengine.exe", "theforceengine"),
            Map.entry("ioquake3.exe", "ioquake3"),
            Map.entry("openapoc.exe", "openapoc"),
            Map.entry("xash3d.exe", "xash3d")
    );

    private DesktopInstalledPortsScanner() { }

    public static ScanResult scan(Path selected) throws IOException {
        if (selected == null) throw new IOException("Selecciona una carpeta.");
        Path root = selected.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root))
            throw new IOException("La carpeta seleccionada no es válida o es un enlace.");
        List<Match> matches = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        int[] examined = {0};
        boolean[] truncated = {false};

        Files.walkFileTree(root, Set.of(), MAX_DEPTH, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(root) && (attrs.isSymbolicLink()
                        || IGNORED.contains(dir.getFileName().toString()
                                .toLowerCase(Locale.ROOT)))) return FileVisitResult.SKIP_SUBTREE;
                return examined[0] >= MAX_FILES || matches.size() >= MAX_RESULTS
                        ? stop() : FileVisitResult.CONTINUE;
            }

            private FileVisitResult stop() {
                truncated[0] = true;
                return FileVisitResult.TERMINATE;
            }

            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (examined[0] >= MAX_FILES || matches.size() >= MAX_RESULTS)
                    return stop();
                examined[0]++;
                if (!attrs.isRegularFile() || attrs.isSymbolicLink()) return FileVisitResult.CONTINUE;
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                String id = KNOWN_EXES.get(name);
                if (id == null || !unique.add(id)) return FileVisitResult.CONTINUE;
                DesktopPortsCatalog.Port port = DesktopPortsCatalog.byId(id);
                if (port == null) return FileVisitResult.CONTINUE;
                try {
                    DesktopPortsLibrary.checkedExecutable(file);
                    matches.add(new Match(port, file.toAbsolutePath().normalize()));
                } catch (IOException rejected) {
                    // Name alone is never proof of a valid Windows executable.
                    unique.remove(id);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult visitFileFailed(Path file, IOException error) {
                // Unreadable directories are simply skipped; never request admin privileges.
                return FileVisitResult.CONTINUE;
            }
        });
        return new ScanResult(matches, examined[0], truncated[0]);
    }

    public static int knownExecutableNames() { return KNOWN_EXES.size(); }
}
