package bo.okubi.desktop;

import bo.nexoplay.app.retro.OkubiSaveArchive;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Offline, exact Android archive and Windows compatibility; no user saves. */
public final class DesktopUniversalSavePreflightTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static void reject(Checked work) throws Exception {
        try {
            work.run();
            throw new AssertionError("An unsafe archive should be refused.");
        } catch (IOException expected) {
            check(expected.getMessage() != null && !expected.getMessage().isBlank(),
                    "Reject with a useful reason.");
        }
    }
    private interface Checked { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("okubi-universal-");
        Path rom = root.resolve("Pokemon Esmeralda.gba");
        byte[] romBytes = new byte[4096];
        Arrays.fill(romBytes, (byte) 19);
        Files.write(rom, romBytes);
        Path otherRom = root.resolve("Pokemon Rojo Fuego.gba");
        byte[] other = new byte[4096];
        Arrays.fill(other, (byte) 29);
        Files.write(otherRom, other);
        DesktopLibrary library = new DesktopLibrary(root.resolve("library.properties"));
        library.addGame(rom);
        library.addGame(otherRom);
        DesktopLibrary.Game game = library.games().stream()
                .filter(x -> x.rom.equals(rom)).findFirst().orElseThrow();
        DesktopLibrary.Game wrong = library.games().stream()
                .filter(x -> x.rom.equals(otherRom)).findFirst().orElseThrow();

        Path savesRoot = root.resolve("saves");
        DesktopSaves local = new DesktopSaves(savesRoot, game.console, game.rom);
        Files.createDirectories(local.directory);
        byte[] exportedRam = new byte[0x2000];
        byte[] exportedRtc = new byte[48];
        Arrays.fill(exportedRam, (byte) 40);
        Arrays.fill(exportedRtc, (byte) 55);
        Files.write(local.ramFile, exportedRam);
        Files.write(local.rtcFile, exportedRtc);
        Path archive = root.resolve("android-share.okubisave");
        DesktopCrossSave.export(game, savesRoot, archive);
        byte[] originalArchive = Files.readAllBytes(archive);

        DesktopUniversalSavePreflight.Result identical =
                DesktopUniversalSavePreflight.inspect(game, archive, savesRoot);
        check(identical.status() == DesktopUniversalSavePreflight.Status.IDENTICAL,
                "Matching SRAM and RTC should never be imported twice.");
        check(!identical.canInstall(), "Identical content means no import.");
        check(identical.existingRam() && identical.existingRtc(),
                "Existing save files are detected.");

        // Same file sizes, different content => safe to offer import.
        byte[] newerRam = new byte[exportedRam.length];
        Arrays.fill(newerRam, (byte) 66);
        Files.write(local.ramFile, newerRam);
        DesktopUniversalSavePreflight.Result ready =
                DesktopUniversalSavePreflight.inspect(game, archive, savesRoot);
        check(ready.status() == DesktopUniversalSavePreflight.Status.READY
                && ready.canInstall(), "Changed content is portable if sizes match.");
        check(ready.message().contains("recuperación"),
                "Existing progress requires a recovery backup.");

        // Incompatible SRAM size => blocked before confirmation, no writes.
        byte[] unusualRam = new byte[64];
        Files.write(local.ramFile, unusualRam);
        DesktopUniversalSavePreflight.Result blocked =
                DesktopUniversalSavePreflight.inspect(game, archive, savesRoot);
        check(blocked.status() == DesktopUniversalSavePreflight.Status.SIZE_MISMATCH
                && !blocked.canInstall(), "Incompatible SRAM length must be rejected.");
        check(Arrays.equals(unusualRam, Files.readAllBytes(local.ramFile)),
                "No overwrite during rejected preflight.");

        Files.delete(local.ramFile);
        Files.delete(local.rtcFile);
        DesktopUniversalSavePreflight.Result first =
                DesktopUniversalSavePreflight.inspect(game, archive, savesRoot);
        check(first.status() == DesktopUniversalSavePreflight.Status.READY
                && first.canInstall() && !first.existingRam(),
                "First save on Windows can import the portable parts.");
        check(!Files.exists(local.ramFile),
                "Inspection never creates SRAM/RTC.");

        reject(() -> DesktopUniversalSavePreflight.inspect(wrong, archive, savesRoot));
        DesktopCrossSave.Preview verified = DesktopCrossSave.inspect(
                game, archive, savesRoot);
        reject(() -> DesktopUniversalSavePreflight.evaluate(wrong, verified, savesRoot));

        byte[] state = new byte[120];
        Arrays.fill(state, (byte) 12);
        Path withState = root.resolve("android-with-state.okubisave");
        try (var out = Files.newOutputStream(withState)) {
            OkubiSaveArchive.write(out, new OkubiSaveArchive.Snapshot(
                    "GBA", DesktopSaves.sha256(rom), exportedRam, exportedRtc, state));
        }
        DesktopUniversalSavePreflight.Result nonPortable =
                DesktopUniversalSavePreflight.inspect(game, withState, savesRoot);
        check(nonPortable.willSkipQuickState()
                && nonPortable.message().contains("omitirá"),
                "Never claim Android state is compatible with Windows.");

        check(Arrays.equals(originalArchive, Files.readAllBytes(archive)),
                "Archive unchanged by preflight.");
        check(Arrays.equals(romBytes, Files.readAllBytes(rom)),
                "ROM data unchanged by preflight.");
        check(!Files.exists(local.ramFile) && !Files.exists(local.rtcFile),
                "Preflight is always read-only.");
        System.out.println("DesktopUniversalSavePreflightTest: PASS");
    }
}
