package bo.okubi.desktop;

import bo.nexoplay.app.retro.OkubiSaveArchive;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;

/** All tests use synthetic ROMs and temporary folders, never a real Drive account. */
public final class DesktopCloudSaveTest {
    private static void require(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
    }

    private interface Checked { void run() throws Exception; }
    private static void rejects(Checked task, String reason) throws Exception {
        try {
            task.run();
            throw new AssertionError("Expected rejection: " + reason);
        } catch (IOException expected) {
            require(expected.getMessage() != null && !expected.getMessage().isBlank(),
                    "Show a clear error: " + reason);
        }
    }

    private static DesktopLibrary.Game fixture(DesktopLibrary library, Path file)
            throws IOException {
        byte[] rom = new byte[32 * 1024];
        Arrays.fill(rom, (byte) 32);
        Files.write(file, rom);
        library.addGame(file);
        return library.games().get(library.games().size() - 1);
    }

    private static void writeAndroid(Path file, DesktopLibrary.Game game,
                                     byte[] ram, byte[] rtc, byte[] state)
            throws Exception {
        try (OutputStream out = Files.newOutputStream(file)) {
            OkubiSaveArchive.write(out, new OkubiSaveArchive.Snapshot(
                    game.console.name(), DesktopSaves.sha256(game.rom),
                    ram, rtc, state));
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("okubi-cloud-save-test-");
        Path local = root.resolve("pc-saves");
        Files.createDirectories(local);
        Path sync = root.resolve("Google Drive para escritorio");
        Files.createDirectories(sync);
        Path config = root.resolve("okubi-config").resolve("cloud-save-v1.properties");
        DesktopCloudSave cloud = new DesktopCloudSave(config);
        require(cloud.folder() == null, "No implicit folder access or Google auth");
        rejects(() -> cloud.selectFolder(local, local),
                "Cannot sync internal save folder");
        rejects(() -> cloud.selectFolder(root, local),
                "Cannot sync folder containing internal game saves");
        rejects(() -> cloud.selectFolder(root.resolve("does-not-exist"), local),
                "Never create an unapproved cloud directory");
        cloud.selectFolder(sync, local);
        require(cloud.folder().equals(sync), "Explicit folder chosen");
        DesktopCloudSave reloaded = new DesktopCloudSave(config);
        require(reloaded.folder().equals(sync), "Drive folder selection persists");

        DesktopLibrary library = new DesktopLibrary(root.resolve("games.properties"));
        DesktopLibrary.Game game = fixture(library, root.resolve("Esmeralda.gba"));
        DesktopSaves save = new DesktopSaves(local, game.console, game.rom);
        DesktopCloudSave.Report noData = cloud.inspect(game, local);
        require(noData.status == DesktopCloudSave.Status.EMPTY,
                "Empty PC and folder recognized accurately");
        rejects(() -> cloud.upload(game, local, true),
                "No invented SRAM to upload");

        Files.createDirectories(save.directory);
        byte[] originalRam = new byte[8192], originalRtc = new byte[48];
        Arrays.fill(originalRam, (byte) 7);
        Arrays.fill(originalRtc, (byte) 3);
        Files.write(save.ramFile, originalRam);
        Files.write(save.rtcFile, originalRtc);
        require(cloud.inspect(game, local).status == DesktopCloudSave.Status.LOCAL_ONLY,
                "PC-only progress is reported");

        DesktopCloudSave.Upload uploaded = cloud.upload(game, local, false);
        require(!uploaded.alreadyPresent && Files.isRegularFile(uploaded.file),
                "Creates first immutable version");
        require(uploaded.file.getParent().equals(sync),
                "Only writes to user-selected Drive sync folder");
        require(uploaded.file.getFileName().toString().endsWith(".okubisave"),
                "Shares real Android format");
        require(cloud.inspect(game, local).status == DesktopCloudSave.Status.IDENTICAL,
                "Identical PC and folder explicitly recognized");
        DesktopCloudSave.Upload duplicate = cloud.upload(game, local, false);
        require(duplicate.alreadyPresent && duplicate.file.equals(uploaded.file),
                "Unchanged SRAM/RTC does not create duplicate versions");

        byte[] newerRam = originalRam.clone();
        newerRam[0] = 99;
        Files.write(save.ramFile, newerRam);
        DesktopCloudSave.Report conflict = cloud.inspect(game, local);
        require(conflict.status == DesktopCloudSave.Status.CONFLICT,
                "Different PC and cloud versions are a conflict");
        rejects(() -> cloud.upload(game, local, false),
                "Conflict must require explicit confirmation");

        DesktopCloudSave.Upload second = cloud.upload(game, local, true);
        require(!second.file.equals(uploaded.file), "New version has unique name");
        require(Files.isRegularFile(uploaded.file),
                "Older revision must never be removed by upload");
        Files.setLastModifiedTime(second.file,
                FileTime.fromMillis(System.currentTimeMillis() + 5_000L));
        require(cloud.inspect(game, local).revisions.size() == 2,
                "History retains both immutable snapshots");

        // Android supplies its own arbitrary filename, not a Windows template.
        byte[] phoneRam = originalRam.clone(), phoneRtc = originalRtc.clone();
        phoneRam[2] = 44;
        byte[] nonportableState = new byte[]{1,2,3,4};
        Path fromAndroid = sync.resolve("Respaldo enviado desde mi A05s.okubisave");
        writeAndroid(fromAndroid, game, phoneRam, phoneRtc, nonportableState);
        Files.setLastModifiedTime(fromAndroid,
                FileTime.fromMillis(System.currentTimeMillis() + 10_000L));
        DesktopCloudSave.Report phoneVersions = cloud.inspect(game, local);
        require(phoneVersions.status == DesktopCloudSave.Status.CONFLICT,
                "Android uploaded game conflicts with PC before restoring");
        require(phoneVersions.revisions.size() == 3,
                "Android arbitrary file name recognized by actual ROM hash");
        DesktopCloudSave.Revision phone = phoneVersions.revisions.stream()
                .filter(v -> v.file.equals(fromAndroid)).findFirst().orElseThrow();
        require(phone.skippedNonPortableState, "Warn before skipping Android state");

        Path pcState = save.directory.resolve("original-PC.slot1.state");
        Files.write(pcState, new byte[]{9, 8, 7});
        DesktopCrossSave.ImportResult result = cloud.download(game, local, phone);
        require(result.ramInstalled, "SRAM restored from Android backup");
        require(result.recoveryBackup != null
                && Files.isRegularFile(result.recoveryBackup),
                "Old PC save retained before cloud import");
        require(Arrays.equals(Files.readAllBytes(save.ramFile), phoneRam),
                "Exact Android SRAM bytes installed");
        require(Arrays.equals(Files.readAllBytes(pcState), new byte[]{9,8,7}),
                "Windows state untouched by cloud import");
        require(Files.isRegularFile(fromAndroid)
                && Files.isRegularFile(uploaded.file)
                && Files.isRegularFile(second.file),
                "No cloud file deleted or overwritten during download");

        // Mutating a scanned version invalidates the preview before an import.
        Path damaged = sync.resolve("Android-bad.okubisave");
        writeAndroid(damaged, game, phoneRam, phoneRtc, null);
        DesktopCloudSave.Revision stale = cloud.inspect(game, local).revisions.stream()
                .filter(v -> v.file.equals(damaged)).findFirst().orElseThrow();
        byte[] changed = phoneRam.clone();
        changed[3] = 120;
        writeAndroid(damaged, game, changed, phoneRtc, null);
        rejects(() -> cloud.download(game, local, stale),
                "Changed cloud version rejected after selection");
        require(Arrays.equals(Files.readAllBytes(save.ramFile), phoneRam),
                "Rejected version cannot modify SRAM");

        Path unrelated = sync.resolve("Some other game.okubisave");
        DesktopLibrary.Game other = fixture(library, root.resolve("Otro.gba"));
        // Make sure the ROM identity differs.
        Files.write(other.rom, new byte[32 * 1024]);
        writeAndroid(unrelated, other, phoneRam, null, null);
        Path invalid = sync.resolve("Corrupt.okubisave");
        Files.write(invalid, new byte[]{1,2,3});
        DesktopCloudSave.Report scan = cloud.inspect(game, local);
        require(scan.ignoredFiles >= 2,
                "Corrupt archives and other ROMs not offered for restoration");

        // Recovery file can be selected as a local Cross Save backup,
        // not silently deleted or uploaded.
        DesktopCrossSave.Preview rollback = DesktopCrossSave.inspect(
                game, result.recoveryBackup, local);
        require(rollback.ramBytes == 8192,
                "Previous PC SRAM remains available for manual recovery");

        Files.delete(save.ramFile);
        Files.delete(save.rtcFile);
        require(cloud.inspect(game, local).status == DesktopCloudSave.Status.FOLDER_ONLY,
                "Cloud-only versions never silently restore themselves");
        require(!Files.exists(save.ramFile), "Scan must never auto-import save");

        // Strongly adversarial path check: no cloud config inside game save data.
        rejects(() -> cloud.selectFolder(save.directory, local),
                "Cannot configure a save directory as shared Drive folder");

        System.out.println("DesktopCloudSaveTest: PASS");
    }
}
