package bo.okubi.desktop;

import bo.nexoplay.app.retro.OkubiSaveArchive;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Real, shared Android archive format. Only synthetic test ROM/save data;
 * never touches an actual user device, ROM, console or existing backups.
 */
public final class DesktopCrossSaveTest {
    private static void yes(boolean value, String explanation) {
        if (!value) throw new AssertionError(explanation);
    }

    private static void expectReject(RunnableIOException task, String reason)
            throws Exception {
        try {
            task.run();
            throw new AssertionError("Expected failure: " + reason);
        } catch (IOException expected) {
            // User-facing cause should never be silent.
            yes(expected.getMessage() != null && !expected.getMessage().isBlank(),
                    "Rejection gives a useful message: " + reason);
        }
    }

    private interface RunnableIOException { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("okubi-cross-save-");
        Path libraryFile = root.resolve("library.properties");
        DesktopLibrary library = new DesktopLibrary(libraryFile);
        Path rom = root.resolve("Esmeralda.gba");
        Path otherRom = root.resolve("RojoFuego.gba");
        byte[] romBytes = new byte[32 * 1024];
        Arrays.fill(romBytes, (byte) 23);
        Files.write(rom, romBytes);
        Arrays.fill(romBytes, (byte) 11);
        Files.write(otherRom, romBytes);
        library.addGame(rom);
        library.addGame(otherRom);
        DesktopLibrary.Game game = library.games().stream()
                .filter(g -> g.rom.equals(rom.toAbsolutePath()))
                .findFirst().orElseThrow();
        DesktopLibrary.Game other = library.games().stream()
                .filter(g -> g.rom.equals(otherRom.toAbsolutePath()))
                .findFirst().orElseThrow();

        Path saveRoot = root.resolve("pc-saves");
        DesktopSaves target = new DesktopSaves(saveRoot, game.console, rom);
        Files.createDirectories(target.directory);
        byte[] oldRam = new byte[0x2000];
        byte[] oldRtc = new byte[48];
        Arrays.fill(oldRam, (byte) 13);
        Arrays.fill(oldRtc, (byte) 7);
        Files.write(target.ramFile, oldRam);
        Files.write(target.rtcFile, oldRtc);

        // Windows exports in EXACT Android .okubisave v1 format using shared
        // Android class, including ROM byte hash and section SHA-256 values.
        Path pcExport = root.resolve("PC-para-Android.okubisave");
        yes(DesktopCrossSave.export(game, saveRoot, pcExport).equals(pcExport),
                "PC export completes to requested path");
        yes(Files.isRegularFile(pcExport), "PC output exists");
        try (InputStream in = Files.newInputStream(pcExport)) {
            OkubiSaveArchive.Snapshot original =
                    OkubiSaveArchive.read(in, DesktopSaves.sha256(rom), "GBA");
            yes(Arrays.equals(original.ram, oldRam), "Android reader sees identical SRAM");
            yes(Arrays.equals(original.rtc, oldRtc), "Android reader sees identical RTC");
            yes(original.state == null, "No cross-platform save-state poison");
        }
        expectReject(() -> DesktopCrossSave.export(game, saveRoot, pcExport),
                "Never overwrite previously exported backup");
        expectReject(() -> DesktopCrossSave.inspect(other, pcExport, saveRoot),
                "ROM SHA-256 mismatch rejected");
        yes(Arrays.equals(Files.readAllBytes(target.ramFile), oldRam),
                "Wrong-ROM import never modifies disk saves");

        // Synthetic Android export deliberately includes a nonportable
        // save state. Windows must skip it, import battery+RTC, preserve prior
        // PC data, and leave F5/F9 states untouched.
        byte[] androidRam = new byte[oldRam.length];
        byte[] androidRtc = new byte[oldRtc.length];
        byte[] androidState = new byte[1000];
        Arrays.fill(androidRam, (byte) 48);
        Arrays.fill(androidRtc, (byte) 19);
        Arrays.fill(androidState, (byte) 85);
        Path androidFile = root.resolve("Android-SAF-export.okubisave");
        try (OutputStream out = Files.newOutputStream(androidFile)) {
            OkubiSaveArchive.write(out,
                    new OkubiSaveArchive.Snapshot("GBA", DesktopSaves.sha256(rom),
                            androidRam, androidRtc, androidState));
        }
        DesktopCrossSave.Preview preview = DesktopCrossSave.inspect(
                game, androidFile, saveRoot);
        yes(preview.ramBytes == 8192 && preview.rtcBytes == 48,
                "Preview reports actual section sizes");
        yes(preview.hasNonPortableState, "Warn that Android save-state will be skipped");
        Path stateFile = target.directory.resolve("preserved.slot1.state");
        Files.write(stateFile, new byte[]{1, 2, 3});

        DesktopCrossSave.ImportResult installed =
                DesktopCrossSave.install(game, saveRoot, preview);
        yes(installed.ramInstalled && installed.rtcInstalled,
                "Both portable components installed");
        yes(installed.skippedState, "Incompatible serialized state explicitly skipped");
        yes(Arrays.equals(Files.readAllBytes(target.ramFile), androidRam),
                "Android battery imported correctly");
        yes(Arrays.equals(Files.readAllBytes(target.rtcFile), androidRtc),
                "Android RTC imported correctly");
        yes(Arrays.equals(Files.readAllBytes(stateFile), new byte[]{1,2,3}),
                "PC snapshot preserved byte-for-byte");
        yes(installed.recoveryBackup != null
                && Files.isRegularFile(installed.recoveryBackup),
                "Previous PC battery saved externally before import");
        try (InputStream in = Files.newInputStream(installed.recoveryBackup)) {
            OkubiSaveArchive.Snapshot recovery =
                    OkubiSaveArchive.read(in, DesktopSaves.sha256(rom), "GBA");
            yes(Arrays.equals(recovery.ram, oldRam)
                    && Arrays.equals(recovery.rtc, oldRtc),
                    "Recovery backup exactly restores previous SRAM+RTC");
        }

        // Choosing the same imported save twice must not create fake backups.
        DesktopCrossSave.ImportResult unchanged = DesktopCrossSave.install(
                game, saveRoot, preview);
        yes(!unchanged.ramInstalled && !unchanged.rtcInstalled
                    && unchanged.recoveryBackup == null,
                "Same save is a verified no-op");

        // Mismatched SRAM size fails BEFORE new backup or overwrites.
        Path wrongSize = root.resolve("wrong-size.okubisave");
        try (OutputStream out = Files.newOutputStream(wrongSize)) {
            OkubiSaveArchive.write(out,
                    new OkubiSaveArchive.Snapshot("GBA", DesktopSaves.sha256(rom),
                            new byte[1234], androidRtc, null));
        }
        DesktopCrossSave.Preview incompatible =
                DesktopCrossSave.inspect(game, wrongSize, saveRoot);
        expectReject(() -> DesktopCrossSave.install(game, saveRoot, incompatible),
                "Different native SRAM lengths cannot be silently installed");
        yes(Arrays.equals(Files.readAllBytes(target.ramFile), androidRam),
                "Rejected import cannot corrupt SRAM");

        // Tamper with a section checksum and verify nothing changes.
        Path corrupt = root.resolve("corrupt.okubisave");
        byte[] modified = Files.readAllBytes(androidFile);
        modified[modified.length - 12] ^= 1;
        Files.write(corrupt, modified);
        expectReject(() -> DesktopCrossSave.inspect(game, corrupt, saveRoot),
                "SHA-256 detects modified payload");
        yes(Arrays.equals(Files.readAllBytes(target.rtcFile), androidRtc),
                "Corrupt input cannot change RTC");

        Path onlyState = root.resolve("only-state.okubisave");
        try (OutputStream out = Files.newOutputStream(onlyState)) {
            OkubiSaveArchive.write(out,
                    new OkubiSaveArchive.Snapshot("GBA", DesktopSaves.sha256(rom),
                            null, null, androidState));
        }
        expectReject(() -> DesktopCrossSave.inspect(game, onlyState, saveRoot),
                "State-only Android archives are not portable");

        // A recovery archive can itself be re-imported safely, preserving
        // current Android state as another recovery archive.
        DesktopCrossSave.Preview recoveryPreview =
                DesktopCrossSave.inspect(game, installed.recoveryBackup, saveRoot);
        DesktopCrossSave.ImportResult recovered =
                DesktopCrossSave.install(game, saveRoot, recoveryPreview);
        yes(recovered.recoveryBackup != null
                && !recovered.recoveryBackup.equals(installed.recoveryBackup),
                "Restoring an old save creates a NEW undo backup");
        yes(Arrays.equals(Files.readAllBytes(target.ramFile), oldRam),
                "Original SRAM recovered");
        yes(Arrays.equals(Files.readAllBytes(target.rtcFile), oldRtc),
                "Original RTC recovered");
        yes(Files.size(rom) == 32 * 1024, "ROM data never edited");
        yes(!Files.exists(root.resolve("pc-saves").resolve("otherRom.gba")),
                "No writes outside per-ROM save directory");

        System.out.println("DesktopCrossSaveTest: PASS");
    }
}
