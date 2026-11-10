package bo.okubi.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

/** No real executables are launched; all files are synthetic in temp. */
public final class DesktopPortsLibraryTest {
    private static void yes(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static void rejects(CheckedAction action, String message) throws Exception {
        try { action.go(); throw new AssertionError("Accepted invalid file: " + message); }
        catch (IOException expected) { yes(expected.getMessage() != null, message); }
    }
    private interface CheckedAction { void go() throws Exception; }

    private static Path fakeExe(Path path, int machine) throws Exception {
        byte[] bytes = new byte[512];
        bytes[0] = 'M'; bytes[1] = 'Z';
        bytes[0x3c] = (byte) 0x80;
        bytes[0x80] = 'P'; bytes[0x81] = 'E';
        bytes[0x84] = (byte) (machine & 0xff);
        bytes[0x85] = (byte) ((machine >>> 8) & 0xff);
        Files.write(path, bytes);
        return path;
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("okubi-ports-regression-");
        Path settings = root.resolve("prefs/ports.properties");
        DesktopPortsCatalog.Port game = DesktopPortsCatalog.byId("openttd");
        DesktopPortsCatalog.Port other = DesktopPortsCatalog.byId("openrct2");
        DesktopPortsLibrary library = new DesktopPortsLibrary(settings);
        yes(library.linked(game) == null, "No auto-installed binaries");
        Path exe = fakeExe(root.resolve("openttd.exe"), 0x8664);
        byte[] original = Files.readAllBytes(exe);
        library.link(game, exe);
        yes(Files.isRegularFile(settings, LinkOption.NOFOLLOW_LINKS),
                "Preferences stored in dedicated file");
        yes(library.linked(game).equals(exe.toAbsolutePath()), "Stored executable");
        yes(library.linked(other) == null, "Other port stays unlinked");
        DesktopPortsLibrary reload = new DesktopPortsLibrary(settings);
        yes(reload.linked(game).equals(exe), "User association survives restart");
        yes(Arrays.equals(original, Files.readAllBytes(exe)),
                "Never edits third party executable");

        rejects(() -> library.link(other, root.resolve("missing.exe")), "No missing file");
        Path text = root.resolve("bad.exe");
        Files.writeString(text, "hello, this is not a PE image");
        rejects(() -> library.link(other, text), "No fake exe by extension alone");
        Path script = root.resolve("setup.bat");
        Files.writeString(script, "echo fake");
        rejects(() -> library.link(other, script), "No shell scripts");
        Path arm = fakeExe(root.resolve("arm.exe"), 0xAA64);
        rejects(() -> library.link(other, arm), "No ARM executable on Windows x64");
        Path win32 = fakeExe(root.resolve("x86.exe"), 0x14c);
        library.link(other, win32);
        yes(library.linked(other).equals(win32), "Windows x86 accepted");

        Path link = root.resolve("symbolic.exe");
        try {
            Files.createSymbolicLink(link, exe);
            rejects(() -> library.link(other, link), "Reject symlink");
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException blocked) {
            // Windows may disallow symlinks without developer privileges.
        }

        Properties badProps = new Properties();
        badProps.setProperty("port.custom-malware", root.resolve("bad.exe").toString());
        badProps.setProperty("port.openttd", exe.toString());
        try (var output = Files.newOutputStream(settings)) {
            badProps.store(output, "synthetic fixture");
        }
        DesktopPortsLibrary cleansed = new DesktopPortsLibrary(settings);
        cleansed.unlink(game);
        Properties after = new Properties();
        try (var input = Files.newInputStream(settings)) { after.load(input); }
        yes(!after.containsKey("port.custom-malware"), "Unknown IDs not persisted");
        yes(!after.containsKey("port.openttd"), "Unlink is persisted");
        yes(Arrays.equals(original, Files.readAllBytes(exe)),
                "Unlink never deletes the installed game");
        yes(!Files.exists(root.resolve("saves")), "Never accesses personal saves");
        System.out.println("DesktopPortsLibraryTest: PASS");
    }
}
