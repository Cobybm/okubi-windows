package bo.okubi.desktop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Offline feedback tests with synthetic files, no user ROMs or save accesses. */
public final class DesktopSmartPlayHistoryTest {
    private static void check(boolean valid, String reason) {
        if (!valid) throw new AssertionError(reason);
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("okubi-smartplay62-");
        Path owned = root.resolve("owned/test game.iso");
        Files.createDirectories(owned.getParent());
        byte[] gameBytes = {1, 2, 3, 4};
        Files.write(owned, gameBytes);
        Path protectedSave = root.resolve("normal-OKUBI/important.srm");
        Files.createDirectories(protectedSave.getParent());
        byte[] originalSave = {12, 24, 36, 48, 60};
        Files.write(protectedSave, originalSave);
        Path historyFile = root.resolve("qa-profile/OKUBI/smart-play-history-v1.properties");
        var catalog = new DesktopMulticonsoleCatalog(
                root.resolve("qa-profile/OKUBI/multiconsole-v1.properties"));
        catalog.addGame(owned, DesktopSmartPerformance.Console.PS2);
        var game = catalog.games().get(0);
        var intel = new DesktopSmartHardware.Snapshot(
                "Intel Core i5-11400", 12, 16L * 1024 * 1024 * 1024,
                "Intel UHD Graphics 730", true, .5);
        var upgraded = new DesktopSmartHardware.Snapshot(
                "Intel Core i5-11400", 12, 16L * 1024 * 1024 * 1024,
                "NVIDIA GeForce RTX 4070", true, .2);
        var unknown = new DesktopSmartHardware.Snapshot(
                "unknown", 12, 0, "GPU no detectada", false, -1);
        var history = new DesktopSmartPlayHistory(historyFile);
        check(history.observation(game, intel).result()
                        == DesktopSmartPlayHistory.Result.UNTESTED,
                "A console hardware estimate cannot masquerade as a tested game.");
        check(history.effectiveMode(game, intel) == DesktopSmartPerformance.Mode.AUTO,
                "Auto is initially governed by the conservative hardware rule.");
        history.confirm(game, intel, DesktopSmartPlayHistory.Result.SLOW);
        check(history.observation(game, intel).result()
                        == DesktopSmartPlayHistory.Result.SLOW,
                "Direct user feedback is stored and displayed.");
        check(history.effectiveMode(game, intel) == DesktopSmartPerformance.Mode.ECO,
                "A slow game should launch in conservative mode next time.");
        check(history.observation(game, upgraded).result()
                        == DesktopSmartPlayHistory.Result.UNTESTED,
                "Hardware change must invalidate title test evidence.");
        check(history.observation(game, unknown).result()
                        == DesktopSmartPlayHistory.Result.UNTESTED,
                "Unknown hardware must not inherit PC test evidence.");
        try {
            history.confirm(game, unknown, DesktopSmartPlayHistory.Result.GOOD);
            throw new AssertionError("Unidentified hardware feedback must be rejected.");
        } catch (java.io.IOException expected) { }
        var persisted = new DesktopSmartPlayHistory(historyFile);
        check(persisted.observation(game, intel).result()
                        == DesktopSmartPlayHistory.Result.SLOW,
                "History must persist across restarts.");
        catalog.mode(game, DesktopSmartPerformance.Mode.QUALITY);
        game = catalog.games().get(0);
        check(persisted.effectiveMode(game, intel) == DesktopSmartPerformance.Mode.QUALITY,
                "Manual mode always overrides the automated low-power suggestion.");
        catalog.mode(game, DesktopSmartPerformance.Mode.AUTO);
        game = catalog.games().get(0);
        persisted.confirm(game, intel, DesktopSmartPlayHistory.Result.GOOD);
        check(persisted.effectiveMode(game, intel) == DesktopSmartPerformance.Mode.AUTO,
                "Good feedback must not force excessive rendering quality.");
        persisted.confirm(game, intel, DesktopSmartPlayHistory.Result.FAILED);
        check(persisted.effectiveMode(game, intel) == DesktopSmartPerformance.Mode.ECO,
                "Failed start must suggest safe retry, not claim successful emulation.");

        check(Arrays.equals(gameBytes, Files.readAllBytes(owned)),
                "Saving a rating never rewrites the game image.");
        check(Arrays.equals(originalSave, Files.readAllBytes(protectedSave)),
                "User saves remain byte-for-byte intact.");
        String settings = Files.readString(historyFile);
        check(!settings.contains(owned.toString()), "History does not leak game paths.");
        check(!settings.contains("Intel Core"), "History does not expose full CPU identity.");

        Path malformed = root.resolve("corrupt.properties");
        Files.writeString(malformed, "result.bad=\\uZXXX\n");
        var corrupt = new DesktopSmartPlayHistory(malformed);
        check(corrupt.observation(game, intel).result()
                        == DesktopSmartPlayHistory.Result.UNTESTED,
                "Malformed history must be safely ignored.");
        check(Files.readString(malformed).contains("ZXXX"),
                "Corrupted file is not destroyed on read.");
        System.out.println("DesktopSmartPlayHistoryTest: PASS");
    }
}
