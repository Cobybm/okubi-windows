package bo.okubi.desktop;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Properties;

/** Pure offline package verification; all files live under a synthetic temp root. */
public final class DesktopWindowsReleaseVerifierTest {
    private static final String[] CORES = {
        "sameboy_libretro.dll", "mgba_libretro.dll",
        "mednafen_supafaust_libretro.dll"
    };
    private static final String[] LICENSES = {
        "SameBoy-MIT.txt", "mGBA-MPL-2.0.txt",
        "Supafaust-GPL-2.0-plus.txt", "THIRD-PARTY-SOURCES.txt"
    };
    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
    private static void rejects(Path app, String explanation) throws Exception {
        try {
            DesktopWindowsReleaseVerifier.inspect(app);
            throw new AssertionError("Expected rejection: " + explanation);
        } catch (IOException expected) {
            check(!expected.getMessage().isBlank(), explanation);
        }
    }
    private static byte[] pe64() {
        byte[] bytes = new byte[256];
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort(0, (short)0x5A4D);
        b.putInt(0x3c, 0x80);
        b.putInt(0x80, 0x00004550);
        b.putShort(0x84, (short)0x8664);
        return bytes;
    }
    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(file)));
    }
    private static void write(Path path, byte[] contents) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, contents);
    }
    private static void text(Path path, String contents) throws Exception {
        write(path, contents.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static void updateManifest(Path app) throws Exception {
        Properties properties = new Properties();
        for (String name : CORES) {
            properties.setProperty(name, sha256(app.resolve("cores/" + name)));
        }
        try (var output = Files.newOutputStream(
                app.resolve("cores/CORE-SHA256.properties"))) {
            properties.store(output, "Synthetic test only");
        }
    }
    private static Path fixture(Path root) throws Exception {
        Path app = root.resolve("OKUBI Desktop/app");
        Files.createDirectories(app);
        write(app.resolve("okubi-desktop.jar"), new byte[1250]);
        write(app.resolve("okubi_libretro_jni.dll"), pe64());
        text(app.resolve("RUN-QA57-ISOLATED.cmd"),
                "@echo off\nset \"APPDATA=%LOCALAPPDATA%\\OKUBI-QA57\\Roaming\"\n"
                + "REM synthetic launcher\n".repeat(8));
        for (String file : new String[]{"QA57-READ-ME-FIRST.md",
                                        "RELEASE-CANDIDATE-NOTES.md",
                                        "WINDOWS-PHASE59-GUIDE.md"}) {
            text(app.resolve(file), ("OKUBI synthetic release guide\n").repeat(5));
        }
        for (String core : CORES) write(app.resolve("cores/" + core), pe64());
        updateManifest(app);
        for (String license : LICENSES)
            text(app.resolve("cores/licenses/" + license),
                    ("Synthetic open source license for " + license + "\n").repeat(12));
        return app;
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("okubi-phase59-");
        Path app = fixture(root);
        DesktopWindowsReleaseVerifier.Report result =
                DesktopWindowsReleaseVerifier.inspect(app);
        check(result.coreCount() == 3 && result.verifiedLicenses() == 4
                && result.bridgeValid() && result.testLauncherPresent(),
                "All four safety gates pass on an intact Windows app image.");

        // The verifier must never read or change the user's account data.
        Path personal = root.resolve("normal-account/OKUBI/saves/valuable.srm");
        byte[] precious = new byte[]{22, 33, 44, 55};
        write(personal, precious);
        DesktopWindowsReleaseVerifier.inspect(app);
        check(Arrays.equals(precious, Files.readAllBytes(personal)),
                "Verifier is read-only and isolated from normal SRAM.");

        byte[] damaged = Files.readAllBytes(app.resolve("cores/mgba_libretro.dll"));
        damaged[180] ^= 16;
        Files.write(app.resolve("cores/mgba_libretro.dll"), damaged);
        rejects(app, "Modified signed core SHA is rejected.");
        write(app.resolve("cores/mgba_libretro.dll"), pe64());
        updateManifest(app);

        byte[] incorrectArch = pe64();
        ByteBuffer.wrap(incorrectArch).order(ByteOrder.LITTLE_ENDIAN)
                .putShort(0x84, (short)0x014c);
        Files.write(app.resolve("cores/mgba_libretro.dll"), incorrectArch);
        updateManifest(app);
        rejects(app, "32-bit cores are rejected even if manifest matches.");
        write(app.resolve("cores/mgba_libretro.dll"), pe64());
        updateManifest(app);

        Files.delete(app.resolve("cores/licenses/Supafaust-GPL-2.0-plus.txt"));
        rejects(app, "Missing GPL source-license notice fails closed.");
        text(app.resolve("cores/licenses/Supafaust-GPL-2.0-plus.txt"),
                ("Synthetic GPL license text\n").repeat(20));

        text(app.resolve("RUN-QA57-ISOLATED.cmd"), ("@echo off\n").repeat(20));
        rejects(app, "Unsafe QA launcher without isolation fails closed.");
        text(app.resolve("RUN-QA57-ISOLATED.cmd"),
                "set \"APPDATA=%LOCALAPPDATA%\\OKUBI-QA57\\Roaming\"\n"
                + "REM synthetic valid launcher\n".repeat(8));

        write(app.resolve("mock_libretro_core.dll"), pe64());
        rejects(app, "Mock test core must not be distributed.");
        Files.delete(app.resolve("mock_libretro_core.dll"));

        Properties empty = new Properties();
        try (var output = Files.newOutputStream(
                app.resolve("cores/CORE-SHA256.properties"))) {
            empty.store(output, "Bad manifest");
        }
        rejects(app, "Missing SHA manifest entries fail closed.");
        updateManifest(app);
        check(DesktopWindowsReleaseVerifier.inspect(app).coreCount() == 3,
                "A restored valid package passes again.");
        check(Arrays.equals(precious, Files.readAllBytes(personal)),
                "Invalid package rejection never alters existing user saves.");

        System.out.println("DesktopWindowsReleaseVerifierTest: PASS");
    }
}
