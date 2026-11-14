package bo.okubi.desktop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * QA57 synthetic sandbox + deterministic library load/stress smoke.
 * Never reads the user's real %APPDATA%, APK, ROMs or game preferences.
 */
public final class DesktopQa57IsolationTest {
    private static void check(boolean valid, String why) {
        if (!valid) throw new AssertionError(why);
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("okubi-qa57-isolation-");
        Path cartridges = root.resolve("synthetic-cartridges");
        Files.createDirectories(cartridges);
        Path originalAppData = root.resolve("normal-account");
        Path qaAppData = root.resolve("isolated-qa57");
        DesktopLibrary production = new DesktopLibrary(
                originalAppData.resolve("OKUBI/desktop-library.properties"));
        DesktopLibrary qa = new DesktopLibrary(
                qaAppData.resolve("OKUBI/desktop-library.properties"));
        check(production.games().isEmpty() && qa.games().isEmpty(),
                "Each fresh profile begins empty.");

        Path rom = cartridges.resolve("Synthetic Test Game.gba");
        byte[] romBytes = new byte[]{11, 21, 31, 41, 51};
        Files.write(rom, romBytes);
        production.addGame(rom);
        qa.addGame(rom);
        check(production.games().size() == 1 && qa.games().size() == 1,
                "ROM references can be imported into two independent libraries.");
        DesktopLibrary.Game owned = production.games().get(0);
        DesktopLibrary.Game sandbox = qa.games().get(0);
        qa.toggleFavorite(sandbox);
        check(qa.games().get(0).favorite
                && !production.games().get(0).favorite,
                "A QA favorite cannot change the regular library.");

        DesktopSaves live = new DesktopSaves(
                originalAppData.resolve("OKUBI/saves"), owned.console, owned.rom);
        DesktopSaves testSave = new DesktopSaves(
                qaAppData.resolve("OKUBI/saves"), sandbox.console, sandbox.rom);
        byte[] priorSave = new byte[]{10, 20, 30, 40};
        byte[] qaSave = new byte[]{60, 70, 80, 90};
        Files.createDirectories(live.directory);
        Files.createDirectories(testSave.directory);
        Files.write(live.ramFile, priorSave);
        Files.write(testSave.ramFile, qaSave);

        DesktopSaveVault vault = new DesktopSaveVault(
                qaAppData.resolve("OKUBI/save-vault-v1"),
                qaAppData.resolve("OKUBI/saves"));
        DesktopSaveVault.Entry backup = vault.backup(sandbox);
        check(backup.valid() && backup.folder().startsWith(qaAppData),
                "QA archive belongs to isolated profile.");
        Files.write(testSave.ramFile, new byte[]{1, 1, 1, 1});
        vault.restore(sandbox, backup);
        check(Arrays.equals(qaSave, Files.readAllBytes(testSave.ramFile)),
                "QA restore affects only the isolated game save.");
        check(Arrays.equals(priorSave, Files.readAllBytes(live.ramFile)),
                "Regular SRAM survives a QA restore byte for byte.");
        check(Arrays.equals(romBytes, Files.readAllBytes(rom)),
                "Neither profile writes to its source ROM.");

        // Stress a normal-size library without a fixed CPU timing threshold.
        // Verify bounded scan and deterministic repeated navigation order.
        Path batch = cartridges.resolve("bulk");
        Files.createDirectories(batch);
        int count = 150;
        for (int i = 0; i < count; i++) {
            Path game = batch.resolve(String.format("QA Cartridge %03d.gb", i));
            Files.write(game, new byte[]{(byte)i, (byte)(i >> 1)});
        }
        long started = System.nanoTime();
        int imported = qa.addFolder(batch);
        List<DesktopLibrary.Game> first = qa.games();
        List<DesktopLibrary.Game> again = qa.games();
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        check(imported == count && first.size() == count + 1,
                "Library processes 150 synthetic cartridges in one bounded scan.");
        check(first.size() == again.size()
                && first.get(0).rom.equals(again.get(0).rom)
                && first.get(first.size() - 1).rom.equals(again.get(again.size() - 1).rom),
                "Repeated navigation returns deterministic stable game order.");
        check(production.games().size() == 1,
                "QA library stress cannot pollute the installed library.");
        System.out.println("DesktopQa57IsolationTest: PASS; 150 synthetic references scanned in "
                + elapsedMs + "ms (informational, not a hardware speed promise)");
    }
}
