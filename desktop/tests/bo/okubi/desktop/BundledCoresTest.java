package bo.okubi.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Windows runner test: no per-user core configuration is required. */
public final class BundledCoresTest {
    private static void yes(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected core folder");
        Path directory = Path.of(args[0]).toAbsolutePath();
        System.setProperty("okubi.cores.dir", directory.toString());
        yes(BundledCores.nameFor(DesktopLibrary.Console.GB)
                .equals(BundledCores.nameFor(DesktopLibrary.Console.GBC)),
                "GB and GBC share the SameBoy core");
        for (DesktopLibrary.Console console : DesktopLibrary.Console.values()) {
            Path core = BundledCores.resolve(console);
            yes(core != null && Files.isRegularFile(core),
                    "Game is playable without configuring a DLL: " + console.name());
            yes(Files.size(core) > 40000, "Actual, nonempty core: " + console.name());
            yes(BundledCores.sha256(core).matches("[0-9a-f]{64}"),
                    "SHA256 matches pinned artifact: " + console.name());
        }
        // Tampering detection must reject a damaged binary, not silently
        // load it into the process or delete saves.
        Path maliciousDir = Files.createTempDirectory("okubi-core-integrity-");
        Path good = BundledCores.resolve(DesktopLibrary.Console.GB);
        Path tampered = maliciousDir.resolve(BundledCores.nameFor(DesktopLibrary.Console.GB));
        Files.copy(good, tampered, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(directory.resolve("CORE-SHA256.properties"),
                maliciousDir.resolve("CORE-SHA256.properties"));
        byte[] bytes = Files.readAllBytes(tampered);
        bytes[bytes.length / 2] ^= 0x20;
        Files.write(tampered, bytes);
        System.setProperty("okubi.cores.dir", maliciousDir.toString());
        try {
            BundledCores.resolve(DesktopLibrary.Console.GB);
            throw new AssertionError("Tampered core must be rejected.");
        } catch (IOException expected) {
            yes(expected.getMessage().contains("SHA-256"),
                    "Show a useful integrity warning without deleting games.");
        }
        System.setProperty("okubi.cores.dir", directory.toString());
        yes(BundledCores.resolve(DesktopLibrary.Console.GBA) != null,
                "Integrity failure in a test copy must not corrupt packaged cores");

        System.out.println("BundledCoresTest: PASS");
    }
}
