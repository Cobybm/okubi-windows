package bo.okubi.desktop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

/** Phase 76: all fixtures local and synthetic; never launches programs. */
public final class DesktopUnifiedLibraryTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static Path fakeExe(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        byte[] bytes = new byte[512];
        bytes[0] = 'M'; bytes[1] = 'Z';
        bytes[0x3c] = (byte) 128;
        bytes[128] = 'P'; bytes[129] = 'E';
        bytes[132] = (byte) 0x64; bytes[133] = (byte) 0x86;
        Files.write(path, bytes);
        return path;
    }

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("okubi-phase76-");
        DesktopLibrary library = new DesktopLibrary(temp.resolve("library.properties"));
        DesktopPlayStats statistics = new DesktopPlayStats(temp.resolve("stats.properties"));
        DesktopPortsLibrary ports = new DesktopPortsLibrary(temp.resolve("ports.properties"));
        check(DesktopUnifiedLibrary.collect(library, statistics, ports).isEmpty(),
                "Empty library should not add fake games");

        Path gba = temp.resolve("Pokemon de prueba.gba");
        Path gbc = temp.resolve("Juego B.gbc");
        Files.write(gba, new byte[]{1, 2, 3});
        Files.write(gbc, new byte[]{4, 5, 6});
        library.addGame(gba);
        library.addGame(gbc);
        DesktopLibrary.Game favorite = library.games().stream()
                .filter(x -> x.rom.equals(gba.toAbsolutePath())).findFirst().orElseThrow();
        library.toggleFavorite(favorite);
        library.played(favorite);
        Path gameExe = fakeExe(temp.resolve("OpenTTD/openttd.exe"));
        DesktopPortsCatalog.Port port = DesktopPortsCatalog.byId("openttd");
        ports.link(port, gameExe);
        DesktopGitHubPortsSearch.Candidate discovered =
                new DesktopGitHubPortsSearch.Candidate(
                        "sampleowner", "sampleport", "Project from a live search",
                        "C++", 10, java.net.URI.create(
                                "https://github.com/sampleowner/sampleport"));
        Path discoveredExe = fakeExe(temp.resolve("Other/sampleport.exe"));
        ports.link(discovered, discoveredExe);

        byte[] beforeRom = Files.readAllBytes(gba);
        byte[] beforeExe = Files.readAllBytes(gameExe);
        List<DesktopUnifiedLibrary.Item> items =
                DesktopUnifiedLibrary.collect(library, statistics, ports);
        check(items.size() == 4, "Expected two ROMs, one curated and one discovered port");
        check(new HashSet<>(items.stream().map(DesktopUnifiedLibrary.Item::id).toList()).size()
                == items.size(), "Stable unique IDs");
        DesktopUnifiedLibrary.Summary summary = DesktopUnifiedLibrary.summary(items);
        check(summary.retro() == 2 && summary.ports() == 2
                && summary.favorites() == 1 && summary.recent() == 1,
                "No fabricated PC play time/favorites, accurate summary");
        check(summary.total() == 4 && summary.trackedSeconds() == 0,
                "PC game launches are not guessed as tracked playtime");
        check(DesktopUnifiedLibrary.filter(items, DesktopUnifiedLibrary.Filter.ALL, "")
                .size() == 4, "ALL should unite both types");
        check(DesktopUnifiedLibrary.filter(items, DesktopUnifiedLibrary.Filter.PC, "")
                .size() == 2, "Curated and discovered PC ports both visible");
        check(DesktopUnifiedLibrary.filter(items, DesktopUnifiedLibrary.Filter.RETRO, "")
                .size() == 2, "Retro items preserved");
        check(DesktopUnifiedLibrary.filter(items, DesktopUnifiedLibrary.Filter.FAVORITES, "")
                .size() == 1, "Existing retro favorites carried over");
        check(DesktopUnifiedLibrary.filter(items, DesktopUnifiedLibrary.Filter.RECENT, "")
                .size() == 1, "Only verifiable retro recent time used");
        check(DesktopUnifiedLibrary.filter(items, DesktopUnifiedLibrary.Filter.ALL, "OPENttD")
                .get(0).title().equals("OpenTTD"), "Search is case-insensitive");
        check(DesktopUnifiedLibrary.filter(items, DesktopUnifiedLibrary.Filter.ALL, "Nintendo")
                .isEmpty(), "Don't include phantom systems");
        check(DesktopUnifiedLibrary.filter(items, DesktopUnifiedLibrary.Filter.ALL, "GB")
                .size() == 2, "Search includes retro console");
        check(items.stream().anyMatch(x -> x.kind() == DesktopUnifiedLibrary.Kind.GITHUB_PORT
                && x.title().equals("sampleowner/sampleport")),
                "User-added GitHub port must not disappear");
        check(items.stream().allMatch(DesktopUnifiedLibrary.Item::available),
                "Available items are correctly labeled");
        check(java.util.Arrays.equals(beforeRom, Files.readAllBytes(gba))
                && java.util.Arrays.equals(beforeExe, Files.readAllBytes(gameExe)),
                "Catalog must not change game bytes");
        Files.delete(gameExe);
        check(items.stream().filter(DesktopUnifiedLibrary.Item::isPort)
                .anyMatch(x -> !x.available()), "Missing external executables should be disabled");
        check(Files.isRegularFile(gba) && Files.isRegularFile(discoveredExe),
                "No save/ROM removal in catalog");
        check(DesktopUnifiedLibraryDialog.columnsForWidth(500) == 1
                && DesktopUnifiedLibraryDialog.columnsForWidth(740) == 2
                && DesktopUnifiedLibraryDialog.columnsForWidth(1080) == 3,
                "Responsive card layout");
        System.out.println("DesktopUnifiedLibraryTest: PASS");
    }
}
