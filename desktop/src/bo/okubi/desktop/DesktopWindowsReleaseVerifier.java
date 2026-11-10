package bo.okubi.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

/**
 * Phase59 non-invasive Windows-only packaged executable self-test.
 *
 * Reads only files shipped inside the jpackage app image. NEVER reads or
 * creates any user ROM, save, configuration, APPDATA, phone data or network
 * resource. Can run from the real Windows EXE with --verify-windows-release.
 */
public final class DesktopWindowsReleaseVerifier {
    private DesktopWindowsReleaseVerifier() { }

    private static final String[] CORES = {
        "sameboy_libretro.dll", "mgba_libretro.dll",
        "mednafen_supafaust_libretro.dll"
    };
    private static final String[] LICENSES = {
        "SameBoy-MIT.txt", "mGBA-MPL-2.0.txt",
        "Supafaust-GPL-2.0-plus.txt", "THIRD-PARTY-SOURCES.txt"
    };

    /** Result is guaranteed read-only; user data is never accessed. */
    public record Report(int coreCount, int verifiedLicenses, boolean bridgeValid,
                         boolean testLauncherPresent) { }

    public static Report inspect(Path appFolder) throws IOException {
        if (appFolder == null)
            throw new IOException("No se encontró la carpeta app de OKUBI.");
        Path app = appFolder.toAbsolutePath().normalize();
        checkedFile(app.resolve("okubi-desktop.jar"), 1000);
        checkedFile(app.resolve("RELEASE-CANDIDATE-NOTES.md"), 100);
        checkedFile(app.resolve("WINDOWS-PHASE59-GUIDE.md"), 100);
        checkedFile(app.resolve("QA57-READ-ME-FIRST.md"), 100);
        Path launcher = app.resolve("RUN-QA57-ISOLATED.cmd");
        checkedFile(launcher, 100);
        // The launcher must not use the normal APPDATA root for QA.
        String launcherText = Files.readString(launcher);
        if (!launcherText.contains("OKUBI-QA57")
                || !launcherText.contains("set \"APPDATA="))
            throw new IOException("El lanzador de pruebas no aísla las partidas.");

        checkedPe64(app.resolve("okubi_libretro_jni.dll"));
        Path cores = app.resolve("cores");
        Path manifest = cores.resolve("CORE-SHA256.properties");
        checkedFile(manifest, 30);
        Properties sums = new Properties();
        try (InputStream stream = Files.newInputStream(manifest)) {
            sums.load(stream);
        }
        for (String name : CORES) {
            Path core = cores.resolve(name);
            checkedPe64(core);
            String expected = sums.getProperty(name, "").trim();
            if (!expected.matches("[a-fA-F0-9]{64}"))
                throw new IOException("SHA-256 no válido en el manifiesto: " + name);
            String actual = sha256(core);
            if (!MessageDigest.isEqual(
                    HexFormat.of().parseHex(expected),
                    HexFormat.of().parseHex(actual)))
                throw new IOException("Núcleo modificado o corrupto: " + name);
        }
        for (String name : LICENSES)
            checkedFile(cores.resolve("licenses").resolve(name), 300);
        if (Files.exists(app.resolve("mock_libretro_core.dll"),
                LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Una DLL de prueba no debe incluirse en OKUBI.");
        return new Report(CORES.length, LICENSES.length, true, true);
    }

    /**
     * Main-jar location, not current working directory; the same path is
     * resolved whether invoked from Explorer or a GitHub Windows runner.
     */
    public static Path packagedAppFolder() throws IOException {
        try {
            Path code = Path.of(OkubiDesktop.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI())
                    .toAbsolutePath().normalize();
            if (!Files.isRegularFile(code, LinkOption.NOFOLLOW_LINKS)
                    || !code.getFileName().toString().equals("okubi-desktop.jar"))
                throw new IOException("La prueba requiere OKUBI Desktop empaquetado.");
            return code.getParent();
        } catch (URISyntaxException | NullPointerException error) {
            throw new IOException("No se pudo localizar el JAR del ejecutable.", error);
        }
    }

    public static int runCli(PrintStream output, PrintStream errors) {
        try {
            Report report = inspect(packagedAppFolder());
            output.println("OKUBI PHASE59 WINDOWS PACKAGE PASS: "
                    + report.coreCount() + " cores / "
                    + report.verifiedLicenses() + " licenses / x64 JNI"
                    + " / isolated QA launcher");
            return 0;
        } catch (Exception failure) {
            errors.println("OKUBI PHASE59 WINDOWS PACKAGE FAIL: "
                    + failure.getMessage());
            return 2;
        }
    }

    private static void checkedFile(Path path, long minimum) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(path)
                || Files.size(path) < minimum)
            throw new IOException("Archivo obligatorio faltante o incompleto: "
                    + path.getFileName());
    }

    /** PE32+ Windows x86_64, using bounded file seeks rather than DLL loading. */
    private static void checkedPe64(Path path) throws IOException {
        checkedFile(path, 128);
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            if (u16(file) != 0x5A4D)
                throw new IOException("DLL sin encabezado MZ: " + path.getFileName());
            file.seek(0x3c);
            long offset = Integer.toUnsignedLong(i32(file));
            if (offset < 64 || offset > file.length() - 6)
                throw new IOException("DLL con desplazamiento PE inválido.");
            file.seek(offset);
            if (i32(file) != 0x00004550 || u16(file) != 0x8664)
                throw new IOException("DLL no compatible con Windows x64: "
                        + path.getFileName());
        }
    }

    private static int u16(RandomAccessFile file) throws IOException {
        int a = file.readUnsignedByte(), b = file.readUnsignedByte();
        return a | (b << 8);
    }

    private static int i32(RandomAccessFile file) throws IOException {
        int a = u16(file), b = u16(file);
        return a | (b << 16);
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream stream = Files.newInputStream(path)) {
                byte[] block = new byte[8192];
                int n;
                while ((n = stream.read(block)) != -1) digest.update(block, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("SHA-256 no está disponible.", error);
        }
    }
}
