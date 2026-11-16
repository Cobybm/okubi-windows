package bo.okubi.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileSystemException;
import java.util.List;

/** Uses only disposable MZ/PE fixtures: no real EXE, games, saves or installs. */
public final class DesktopInstalledPortsScannerTest {
    private static void ok(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }

    private static Path fakeExe(Path path, int machine) throws IOException {
        Files.createDirectories(path.getParent());
        byte[] bytes = new byte[512];
        bytes[0] = 'M';
        bytes[1] = 'Z';
        bytes[0x3c] = (byte) 128;
        bytes[128] = 'P';
        bytes[129] = 'E';
        bytes[132] = (byte) (machine & 0xff);
        bytes[133] = (byte) (machine >> 8);
        Files.write(path, bytes);
        return path;
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("okubi-phase75-");
        ok(DesktopInstalledPortsScanner.knownExecutableNames() >= 20,
                "Must recognize known PC port executable names");
        Path openttd = fakeExe(root.resolve("OpenTTD/openttd.exe"), 0x8664);
        Path gzdoom = fakeExe(root.resolve("Games/FPS/gzdoom.exe"), 0x14c);
        fakeExe(root.resolve("Games/FPS/unknown-mystery.exe"), 0x8664);
        fakeExe(root.resolve("saves/openttd.exe"), 0x8664);
        fakeExe(root.resolve("roms/gzdoom.exe"), 0x8664);
        fakeExe(root.resolve(".git/objects/devilutionx.exe"), 0x8664);
        fakeExe(root.resolve("deep/a/b/c/d/luanti.exe"), 0x8664);
        Files.writeString(root.resolve("openrct2.exe"), "not a Windows PE");
        fakeExe(root.resolve("arm/opentyrian.exe"), 0xAA64);
        Path duplicate = fakeExe(root.resolve("Others/OpenTTD.exe"), 0x8664);

        DesktopInstalledPortsScanner.ScanResult result = DesktopInstalledPortsScanner.scan(root);
        List<String> ids = result.matches().stream().map(m -> m.port().id()).toList();
        ok(ids.size() == 2, "Only safe, unique, matching PE ports should be found: " + ids);
        ok(ids.contains("openttd") && ids.contains("gzdoom"), "Both known ports found");
        ok(result.matches().stream().allMatch(m -> m.exe().equals(openttd)
                    || m.exe().equals(gzdoom) || m.exe().equals(duplicate)),
                "No private, hidden or fabricated files");
        ok(!result.truncated(), "Small fixture must not hit a scan limit");
        ok(result.filesExamined() > 0 && result.filesExamined() <= 25,
                "Bounded number of checked files");
        ok(Files.isRegularFile(openttd) && Files.isRegularFile(gzdoom),
                "No files removed");
        ok(!Files.exists(root.resolve("desktop-ports-v1.properties")),
                "Scanner never stores or creates links");

        try {
            Path sym = root.resolve("link");
            Files.createSymbolicLink(sym, root.resolve("Games"));
            DesktopInstalledPortsScanner.ScanResult linked = DesktopInstalledPortsScanner.scan(root);
            ok(linked.matches().size() == 2, "Do not traverse directory symlinks");
            boolean rejected = false;
            try { DesktopInstalledPortsScanner.scan(sym); }
            catch (IOException expected) { rejected = true; }
            ok(rejected, "User-selected symlink root must be rejected");
        } catch (UnsupportedOperationException | FileSystemException restricted) {
            // GitHub Windows images may require elevated privileges for symlinks.
        }

        boolean nullRejected = false;
        try { DesktopInstalledPortsScanner.scan(null); }
        catch (IOException expected) { nullRejected = true; }
        ok(nullRejected, "Reject null selection");

        Path empty = Files.createTempDirectory("okubi-phase75-empty-");
        DesktopInstalledPortsScanner.ScanResult noResults = DesktopInstalledPortsScanner.scan(empty);
        ok(noResults.matches().isEmpty() && noResults.filesExamined() == 0,
                "Empty folder produces no false matches");

        System.out.println("DesktopInstalledPortsScannerTest: PASS");
    }
}
