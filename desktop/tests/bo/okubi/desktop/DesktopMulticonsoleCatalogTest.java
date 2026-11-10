package bo.okubi.desktop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Safe 200-title multi-console scan and per-game independent settings. */
public final class DesktopMulticonsoleCatalogTest {
    private static void check(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason);
    }
    private static void reject(Throwing work) throws Exception {
        try { work.run(); throw new AssertionError("Unsafe action accepted."); }
        catch (java.io.IOException expected) { }
    }
    private interface Throwing { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("okubi-smart60-");
        Path originalLibrary = root.resolve("normal-OKUBI/desktop-library.properties");
        Files.createDirectories(originalLibrary.getParent());
        byte[] previous = "OLD OKUBI PLAYER SETTINGS".getBytes();
        Files.write(originalLibrary, previous);

        Path base = root.resolve("games");
        Files.createDirectories(base);
        for (int i = 0; i < 200; i++) {
            Files.write(base.resolve(String.format("PS1 Game %03d.cue", i)),
                    "FILE synthetic.bin BINARY".getBytes());
        }
        Path ps2 = base.resolve("PS2 With Spaces.iso");
        byte[] ps2Data = new byte[]{12, 34, 56, 78, 99};
        Files.write(ps2, ps2Data);
        Path ambiguous = base.resolve("PS1 Disc.iso");
        Files.write(ambiguous, new byte[]{11, 22, 33});
        Path ps3 = root.resolve("Unpacked PS3/PS3_GAME/USRDIR/EBOOT.BIN");
        Files.createDirectories(ps3.getParent());
        Files.write(ps3, new byte[]{10, 20, 30, 40});
        Path configured = root.resolve("settings/multiconsole-v1.properties");
        var catalog = new DesktopMulticonsoleCatalog(configured);
        check(catalog.games().isEmpty(), "Start with separate empty catalog.");
        check(catalog.addFolder(base) == 202, "Scan 200 PS1 and two ambiguous ISO references.");
        check(catalog.games().size() == 202, "Never duplicate library entries.");
        check(catalog.addFolder(base) == 0, "Idempotent folder scan.");
        check(catalog.addGame(ambiguous, DesktopSmartPerformance.Console.PS1),
                "An ambiguous ISO can be reassigned as PS1.");
        check(catalog.addGame(ps3, DesktopSmartPerformance.Console.PS3),
                "EBOOT.BIN can be imported directly as PS3.");
        check(catalog.games().size() == 203, "All original entries still present.");
        var selected = catalog.games().stream()
                .filter(x -> x.file().equals(ps2)).findFirst().orElseThrow();
        catalog.mode(selected, DesktopSmartPerformance.Mode.ECO);

        Path duck = root.resolve("emu/DuckStation-qt-x64-Release.exe");
        Path pcsx2 = root.resolve("emu/pcsx2-qt.exe");
        Path rpcs3 = root.resolve("emu/rpcs3.exe");
        Files.createDirectories(duck.getParent());
        Files.write(duck, new byte[]{1, 2, 3});
        Files.write(pcsx2, new byte[]{1, 2, 3});
        Files.write(rpcs3, new byte[]{1, 2, 3});
        catalog.emulator(DesktopSmartPerformance.Console.PS1, duck);
        catalog.emulator(DesktopSmartPerformance.Console.PS2, pcsx2);
        catalog.emulator(DesktopSmartPerformance.Console.PS3, rpcs3);
        reject(() -> catalog.emulator(DesktopSmartPerformance.Console.PS1, pcsx2));
        check(catalog.games().get(catalog.games().size() - 1).title()
                .equals("Unpacked PS3"), "Name PS3 game by folder, not EBOOT.BIN.");

        var saved = new DesktopMulticonsoleCatalog(configured);
        check(saved.games().size() == 203, "All catalog references survive restart.");
        check(saved.games().stream().filter(x -> x.file().equals(ps2))
                .findFirst().orElseThrow().mode() == DesktopSmartPerformance.Mode.ECO,
                "Per-game low power mode persists.");
        check(saved.emulator(DesktopSmartPerformance.Console.PS2).equals(pcsx2),
                "Official emulator path is stored independently.");

        var launch = DesktopMulticonsoleLauncher.command(
                DesktopSmartPerformance.Console.PS2,
                DesktopSmartPerformance.Mode.ECO, pcsx2, ps2);
        check(launch.get(1).equals("-batch") && launch.get(2).equals("-nofullscreen"),
                "Conservative PCSX2 per-game launch uses safe documented flags.");
        check(launch.get(3).equals("--") && launch.get(4).equals(ps2.toString()),
                "Path with spaces remains a single argument, not a command injection.");
        var duckLaunch = DesktopMulticonsoleLauncher.command(
                DesktopSmartPerformance.Console.PS1,
                DesktopSmartPerformance.Mode.QUALITY, duck, ambiguous);
        check(duckLaunch.contains("-fullscreen") && duckLaunch.get(3).equals("--"),
                "DuckStation accepts explicit user PS1 ISO and high-quality flag.");
        var ps3Launch = DesktopMulticonsoleLauncher.command(
                DesktopSmartPerformance.Console.PS3,
                DesktopSmartPerformance.Mode.ECO, rpcs3, ps3);
        check(ps3Launch.size() == 3 && ps3Launch.get(1).equals("--no-gui"),
                "RPCS3 only boots selected EBOOT.BIN, never arbitrary ISO.");

        var remove = saved.games().get(0);
        saved.remove(remove);
        check(Files.isRegularFile(remove.file()), "Removing a game only removes catalog reference.");
        check(Arrays.equals(ps2Data, Files.readAllBytes(ps2)), "ROM file bytes unchanged.");
        check(Arrays.equals(previous, Files.readAllBytes(originalLibrary)),
                "Original OKUBI retro settings remain byte-identical.");
        check(!Files.exists(root.resolve("normal-OKUBI/saves")),
                "No save directory was read, created or migrated.");
        System.out.println("DesktopMulticonsoleCatalogTest: PASS");
    }
}
