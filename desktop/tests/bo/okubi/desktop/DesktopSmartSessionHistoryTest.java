package bo.okubi.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Offline PC-specific process sessions, verified/claimed FPS and save safety. */
public final class DesktopSmartSessionHistoryTest {
    private static void check(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason);
    }
    @FunctionalInterface private interface Failing { void run() throws Exception; }
    private static void mustFail(Failing task, String reason) throws Exception {
        try {
            task.run();
            throw new AssertionError("Unexpected write: " + reason);
        } catch (IOException expected) {
            check(!expected.getMessage().isEmpty(), reason);
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("okubi-phase65-");
        Path rom = root.resolve("owned/Some PS2 Title.iso");
        Files.createDirectories(rom.getParent());
        byte[] bytes = {1, 2, 3, 7, 9};
        Files.write(rom, bytes);
        Path srm = root.resolve("normal-OKUBI/valuable.srm");
        Files.createDirectories(srm.getParent());
        byte[] save = {42, 33, 24, 15};
        Files.write(srm, save);
        Path ini = root.resolve("pcsx2/original-inis/PCSX2.ini");
        Files.createDirectories(ini.getParent());
        byte[] iniBytes = "[EmuCore/GS]\nupscale_multiplier = 5\n".getBytes();
        Files.write(ini, iniBytes);
        Path settings = root.resolve("qa/OKUBI/smart-session-history-v1.properties");

        var catalog = new DesktopMulticonsoleCatalog(
                root.resolve("qa/OKUBI/multiconsole-v1.properties"));
        catalog.addGame(rom, DesktopSmartPerformance.Console.PS2);
        var game = catalog.games().get(0);
        var intel = new DesktopSmartHardware.Snapshot(
                "Intel i5-11400", 12, 16L << 30,
                "Intel UHD Graphics 730", true, 0.3);
        var upgraded = new DesktopSmartHardware.Snapshot(
                "Intel i5-11400", 12, 16L << 30,
                "NVIDIA RTX 4070", true, 0.2);
        var unknown = new DesktopSmartHardware.Snapshot(
                "unknown", 12, 0, "Unknown GPU", false, -1);

        var tracker = new DesktopSmartSessionHistory(settings);
        check(tracker.stats(game, intel).sessions() == 0, "No fabricated history.");
        check(tracker.stats(game, intel).summary().contains("Sin sesiones"),
                "UI describes untested title correctly.");
        tracker.completed(game, intel, 65L, 0);
        tracker.completed(game, intel, 35L, -1);
        var observed = tracker.stats(game, intel);
        check(observed.sessions() == 2 && observed.seconds() == 100
                && observed.lastExitCode() == -1,
                "Duration, exit code and session count must be exact.");
        check(observed.summary().contains("NO medidos")
                        || observed.summary().contains("aún sin datos"),
                "Must not pretend to measure FPS from process exit code.");
        check(tracker.stats(game, upgraded).sessions() == 0,
                "Hardware changes invalidate old session evidence.");

        tracker.userFps(game, intel, 52);
        check(tracker.stats(game, intel).userReportedFps() == 52
                        && tracker.stats(game, intel).summary().contains("introducidos por ti"),
                "Only user-supplied FPS is displayed and labeled.");
        mustFail(() -> tracker.userFps(game, intel, -1), "Negative FPS forbidden.");
        mustFail(() -> tracker.userFps(game, intel, 241), "Unrealistic FPS forbidden.");
        mustFail(() -> tracker.completed(game, intel, -10, 0),
                "Negative process duration rejected.");
        mustFail(() -> tracker.completed(game, unknown, 10, 0),
                "Unknown hardware must not create misleading observations.");

        var restarted = new DesktopSmartSessionHistory(settings);
        check(restarted.stats(game, intel).sessions() == 2
                        && restarted.stats(game, intel).userReportedFps() == 52,
                "Stats persist across app restarts.");
        restarted.userFps(game, intel, 0);
        check(restarted.stats(game, intel).userReportedFps() == 0,
                "User can clear reported FPS without changing session count.");

        tracker.completed(game, intel, 100_000_000L, 1);
        check(tracker.stats(game, intel).seconds() == 100 + 86_400,
                "Duration is clamped to 24h to avoid outliers.");
        String diskText = Files.readString(settings);
        check(!diskText.contains(rom.toString()) && !diskText.contains("Intel i5"),
                "Private paths/CPU names not stored in plaintext.");
        check(Arrays.equals(Files.readAllBytes(rom), bytes)
                        && Arrays.equals(Files.readAllBytes(srm), save)
                        && Arrays.equals(Files.readAllBytes(ini), iniBytes),
                "Game ROM, original save and installed PCSX2 INI untouched.");

        Path corrupt = root.resolve("malformed.properties");
        Files.writeString(corrupt, "s.fake=\\uZZZZ\n");
        var broken = new DesktopSmartSessionHistory(corrupt);
        check(broken.stats(game, intel).sessions() == 0,
                "Corrupt file fails closed to no history.");
        check(Files.readString(corrupt).contains("ZZZZ"),
                "Corrupt file is not destroyed on read.");
        System.out.println("DesktopSmartSessionHistoryTest: PASS");
    }
}
