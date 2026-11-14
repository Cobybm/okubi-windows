package bo.okubi.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Entire automatic-prelaunch guard tested without Windows or an emulator.
 * Covers first save, dedup, changes, daily limit, opt-out persistence and
 * preservation of SRAM, RTC, ROM and existing manual history.
 */
public final class DesktopSmartSaveGuardTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("okubi-smart-save-");
        Path rom = root.resolve("Pokemon Esmeralda.gba");
        byte[] romBytes = new byte[]{44, 10, 91, 80, 12, 20};
        Files.write(rom, romBytes);
        DesktopLibrary library = new DesktopLibrary(root.resolve("library.properties"));
        library.addGame(rom);
        DesktopLibrary.Game game = library.games().get(0);
        Path vaultDir = root.resolve("save-vault");
        Path saveDir = root.resolve("saves");
        Path settings = root.resolve("smart.properties");
        DesktopSaveVault vault = new DesktopSaveVault(vaultDir, saveDir);
        DesktopSmartSaveGuard guard = new DesktopSmartSaveGuard(
                settings, vaultDir, saveDir);
        check(guard.enabled(), "First install enables protection by default.");

        check(guard.beforePlay(game) == DesktopSaveVault.AutomaticResult.NO_SAVE,
                "A new game has no battery progress to copy.");
        check(vault.history(game).isEmpty(),
                "No empty archives must be created before a game has saved.");

        DesktopSaves save = new DesktopSaves(saveDir, game.console, rom);
        Files.createDirectories(save.directory);
        byte[] firstRam = new byte[]{1, 2, 3, 4};
        byte[] rtc = new byte[]{10, 20, 30};
        Files.write(save.ramFile, firstRam);
        Files.write(save.rtcFile, rtc);
        byte[] previous = new byte[]{77, 88};
        Path previousFile = save.ramFile.resolveSibling(save.ramFile.getFileName() + ".previous");
        Files.write(previousFile, previous);

        check(guard.beforePlay(game) == DesktopSaveVault.AutomaticResult.CREATED,
                "First run with real progress creates a verified snapshot.");
        List<DesktopSaveVault.Entry> first = vault.history(game);
        check(first.size() == 1 && "AUTO".equals(first.get(0).kind()),
                "Automatic backup is labeled AUTO in vault history.");
        check(first.get(0).title().contains("AUTOMÁTICA"),
                "User sees an understandable automatic backup label.");
        Path preservedFirst = first.get(0).folder();
        check(guard.beforePlay(game) == DesktopSaveVault.AutomaticResult.UNCHANGED,
                "Launching the same game without a save change makes no duplicate.");
        check(vault.history(game).size() == 1, "No duplicates in history.");

        byte[] nextRam = new byte[]{4, 5, 6, 7};
        Files.write(save.ramFile, nextRam);
        check(guard.beforePlay(game) == DesktopSaveVault.AutomaticResult.CREATED,
                "Changed SRAM generates another independently verified version.");
        check(vault.history(game).size() == 2, "Both versions remain present.");
        check(Files.isDirectory(preservedFirst), "First snapshot was not replaced.");
        check(Arrays.equals(firstRam,
                        Files.readAllBytes(preservedFirst.resolve("battery.srm"))),
                "Original snapshot retains exact prior SRAM.");

        // A manually saved identical version also prevents another auto copy.
        byte[] manual = new byte[]{50, 60, 70, 80};
        Files.write(save.ramFile, manual);
        DesktopSaveVault.Entry manualBackup = vault.backup(game);
        check("MANUAL".equals(manualBackup.kind()), "Manual backup remains manual.");
        check(guard.beforePlay(game) == DesktopSaveVault.AutomaticResult.UNCHANGED,
                "Existing manual snapshot suppresses auto duplicate.");
        check(vault.history(game).size() == 3, "No duplicate of a manual snapshot.");

        guard.setEnabled(false);
        check(!guard.enabled(), "User can disable prelaunch automatic backup.");
        DesktopSmartSaveGuard restarted = new DesktopSmartSaveGuard(settings, vaultDir, saveDir);
        check(!restarted.enabled(), "User opt-out survives a full app restart.");
        Files.write(save.ramFile, new byte[]{99, 100});
        check(restarted.beforePlay(game) == DesktopSaveVault.AutomaticResult.DISABLED,
                "Disabled guard leaves changes untouched and never copies.");
        check(vault.history(game).size() == 3, "No archive created while disabled.");
        restarted.setEnabled(true);
        check(new DesktopSmartSaveGuard(settings, vaultDir, saveDir).enabled(),
                "Re-enabling survives another restart.");

        // Initially 2 AUTO copies; create exactly 6 more, then block new
        // automatic archives for the day instead of silently deleting history.
        for (int index = 0; index < 6; index++) {
            Files.write(save.ramFile, new byte[]{(byte) (110 + index)});
            check(restarted.beforePlay(game) == DesktopSaveVault.AutomaticResult.CREATED,
                    "Each changed version up to the daily cap is captured.");
        }
        int beforeLimit = vault.history(game).size();
        Files.write(save.ramFile, new byte[]{125});
        check(restarted.beforePlay(game) == DesktopSaveVault.AutomaticResult.DAILY_LIMIT,
                "Ninth unique version requires user's consent to play unprotected.");
        check(vault.history(game).size() == beforeLimit,
                "Daily limit does not silently remove or overwrite old snapshots.");
        check(vault.history(game).stream()
                .filter(entry -> entry.kind().equals("AUTO")).count() == 8,
                "Eight automatic snapshots stay independently visible.");

        check(Arrays.equals(new byte[]{125}, Files.readAllBytes(save.ramFile)),
                "Automatic backup NEVER writes to the current SRAM.");
        check(Arrays.equals(rtc, Files.readAllBytes(save.rtcFile)),
                "Automatic backup NEVER changes RTC.");
        check(Arrays.equals(previous, Files.readAllBytes(previousFile)),
                "Automatic backup NEVER touches .previous recovery.");
        check(Arrays.equals(romBytes, Files.readAllBytes(rom)),
                "Automatic backup NEVER changes the ROM.");
        check(Files.isDirectory(manualBackup.folder()),
                "Manual backup remains intact even with daily automation cap.");

        // Corrupted settings cannot prevent startup or force user opt-in.
        Files.writeString(settings, "automatic.prelaunch.enabled=invalid-value\\n");
        DesktopSmartSaveGuard broken = new DesktopSmartSaveGuard(settings, vaultDir, saveDir);
        check(!broken.enabled(), "Corrupted preferences default OFF without crashing.");
        check(broken.beforePlay(game) == DesktopSaveVault.AutomaticResult.DISABLED,
                "Corrupt prefs cannot create unexpected snapshots.");
        broken.setEnabled(true);
        check(new DesktopSmartSaveGuard(settings, vaultDir, saveDir).enabled(),
                "User can repair settings by deliberately enabling the guard.");
        System.out.println("DesktopSmartSaveGuardTest: PASS");
    }
}
