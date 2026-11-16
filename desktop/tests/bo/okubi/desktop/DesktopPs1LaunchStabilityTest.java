package bo.okubi.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Offline PS1 disc / M3U / BIOS regression, no games or firmware provided. */
public final class DesktopPs1LaunchStabilityTest {
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("okubi-ps1-safe-test-");
        try {
            Path track = root.resolve("Disc1.bin");
            Path cue = Files.writeString(root.resolve("Disc1.cue"),
                    "FILE \"Disc1.bin\" BINARY\n  TRACK 01 MODE2/2352\n");
            Path m3u = Files.writeString(root.resolve("DiscList.m3u"), "Disc1.cue\n");
            try {
                DesktopPs1Disc.validateCompanions(m3u);
                throw new AssertionError("M3U must inspect referenced CUE tracks.");
            } catch (IOException expected) {
                check(expected.getMessage().contains("Faltan"),
                        "Error must name missing CUE/BIN tracks.");
            }
            Files.write(track, new byte[2352]);
            DesktopPs1Disc.validateCompanions(m3u);
            Path a = Files.writeString(root.resolve("a.m3u"), "b.m3u\n");
            Files.writeString(root.resolve("b.m3u"), "a.m3u\n");
            try {
                DesktopPs1Disc.validateCompanions(a);
                throw new AssertionError("Recursive M3U should not be accepted.");
            } catch (IOException expected) {
                check(expected.getMessage().contains("circular"),
                        "M3U recursion must be bounded and explicit.");
            }
            Path invalid = Files.write(root.resolve("scph5501.bin"),
                    new byte[512 * 1024]);
            Path userSystem = root.resolve("system");
            check(DesktopPs1Firmware.recognizedName("SCPH5501.BIN"),
                    "Canonical PS1 firmware recognition must be case insensitive.");
            try {
                DesktopPs1Firmware.importOwnedBios(invalid, userSystem);
                throw new AssertionError("Fabricated BIOS must not be installed.");
            } catch (IOException expected) {
                check(expected.getMessage().contains("MD5"),
                        "Firmware import must verify actual documented BIOS checksum.");
                check(!Files.exists(userSystem),
                        "Rejected firmware must not create or modify user folders.");
            }
            Path wrong = Files.write(root.resolve("not-a-bios.bin"), new byte[10]);
            try {
                DesktopPs1Firmware.importOwnedBios(wrong, userSystem);
                throw new AssertionError("Unknown BIOS filename must be rejected.");
            } catch (IOException expected) {
                check(!Files.exists(userSystem),
                        "Untrusted files must never be copied to PS1 save folder.");
            }
            check(Files.size(track) == 2352, "CUE data never modified.");
            check(Files.size(invalid) == 512 * 1024,
                    "Unverified firmware never modified.");
            System.out.println("DesktopPs1LaunchStabilityTest PASS");
        } finally {
            try (var files = Files.walk(root)) {
                files.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (IOException ignored) { }
                });
            }
        }
    }
}
