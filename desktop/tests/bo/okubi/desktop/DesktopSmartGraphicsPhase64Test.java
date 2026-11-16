package bo.okubi.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Phase64 synthetic quality/rollback and real -gamecfg CLI contract tests. */
public final class DesktopSmartGraphicsPhase64Test {
    private static void check(boolean actual, String message) {
        if (!actual) throw new AssertionError(message);
    }

    private static void rejects(Failing action, String reason) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected fail-closed: " + reason);
        } catch (IOException expected) {
            check(!expected.getMessage().isBlank(), reason);
        }
    }

    @FunctionalInterface private interface Failing { void run() throws Exception; }

    private static void planner() {
        var integrated = new DesktopSmartHardware.Snapshot(
                "Intel Core i5-11400", 12, 16L << 30,
                "Intel UHD Graphics 730", true, 0.2);
        var dedicated = new DesktopSmartHardware.Snapshot(
                "Intel Core i5-11400", 12, 32L << 30,
                "NVIDIA RTX 4070", true, 0.3);
        var weak = new DesktopSmartHardware.Snapshot(
                "unknown", 4, 0, "unknown", false, -1);

        var vega = DesktopSmartPerformance.recommend(
                DesktopSmartPerformance.Console.PS2, integrated,
                DesktopSmartPerformance.Mode.AUTO);
        var eco = DesktopSmartGraphicsPlanner.plan(vega, integrated,
                DesktopSmartPlayHistory.Result.SLOW);
        check(eco.scale() == 1 && !eco.fxaa() && eco.anisotropy() == 0,
                "Intel UHD + slow title must never force effects.");
        var integratedQuality = DesktopSmartGraphicsPlanner.plan(
                DesktopSmartPerformance.recommend(DesktopSmartPerformance.Console.PS2,
                        integrated, DesktopSmartPerformance.Mode.QUALITY),
                integrated, DesktopSmartPlayHistory.Result.GOOD);
        check(integratedQuality.scale() == 1 && integratedQuality.anisotropy() == 0
                        && !integratedQuality.fxaa(),
                "Manual Quality cannot force demanding PS2 graphics on integrated UHD.");
        var strong = DesktopSmartGraphicsPlanner.plan(
                DesktopSmartPerformance.recommend(DesktopSmartPerformance.Console.PS2,
                        dedicated, DesktopSmartPerformance.Mode.QUALITY),
                dedicated, DesktopSmartPlayHistory.Result.GOOD);
        check(strong.scale() == 2 && strong.fxaa() && strong.anisotropy() == 4,
                "Dedicated GPU and explicit Quality can opt into bounded 2x/FXAA/4x.");
        var balanced = DesktopSmartGraphicsPlanner.plan(
                DesktopSmartPerformance.recommend(DesktopSmartPerformance.Console.PS2,
                        dedicated, DesktopSmartPerformance.Mode.BALANCED),
                dedicated, DesktopSmartPlayHistory.Result.UNTESTED);
        check(balanced.scale() == 1 && !balanced.fxaa() && balanced.anisotropy() == 2,
                "Balanced starts native with no costly antialiasing.");
        check(DesktopSmartGraphicsPlanner.plan(
                DesktopSmartPerformance.recommend(DesktopSmartPerformance.Console.PS2,
                        weak, DesktopSmartPerformance.Mode.AUTO),
                weak, DesktopSmartPlayHistory.Result.UNTESTED).scale() == 1,
                "Unknown hardware must not opt into scale 2x.");
        check(strong.preview().contains("FXAA")
                        && strong.preview().contains("2×"),
                "Preview must show exact graphics before user launches.");
    }

    public static void main(String[] args) throws Exception {
        planner();
        check(DesktopPcsx2CliProbe.supportsGameConfig(
                "PCSX2 QT\nUsage: pcsx2-qt.exe [parameters] [--] [boot filename]\n"
                        + "-batch: Enables batch mode\n"
                        + "-gamecfg <file>: Overrides the game settings"),
                "Documented PCSX2 syntax should pass.");
        check(!DesktopPcsx2CliProbe.supportsGameConfig(
                "Old PCSX2 -batch [--] [game]"), "Old version must reject.");
        check(!DesktopPcsx2CliProbe.supportsGameConfig(
                "DuckStation -batch [--] [game]"), "Wrong emulator must reject.");

        Path root = Files.createTempDirectory("okubi-phase64-");
        Path rom = root.resolve("owned/PS2 Game.iso");
        Files.createDirectories(rom.getParent());
        byte[] romContents = {7, 3, 8, 1};
        Files.write(rom, romContents);
        Path gameSave = root.resolve("regular-pcsx2/memcards/valuable.ps2");
        Files.createDirectories(gameSave.getParent());
        byte[] saveContents = {5, 10, 15, 20, 25};
        Files.write(gameSave, saveContents);
        Path global = root.resolve("regular-pcsx2/inis/PCSX2.ini");
        Files.createDirectories(global.getParent());
        byte[] settings = "upscale_multiplier = 7\n".getBytes();
        Files.write(global, settings);
        Path oldOkubi = root.resolve("regular-OKUBI/desktop-library.properties");
        Files.createDirectories(oldOkubi.getParent());
        byte[] oldPreferences = {33, 44, 55};
        Files.write(oldOkubi, oldPreferences);

        var catalog = new DesktopMulticonsoleCatalog(
                root.resolve("qa/OKUBI/multiconsole-v1.properties"));
        catalog.addGame(rom, DesktopSmartPerformance.Console.PS2);
        var game = catalog.games().get(0);
        var intel = new DesktopSmartHardware.Snapshot(
                "Intel Core i5-11400", 12, 16L << 30,
                "Intel UHD Graphics 730", true, 0.2);
        var dedicated = new DesktopSmartHardware.Snapshot(
                "Intel Core i5-11400", 12, 32L << 30,
                "NVIDIA RTX 4070", true, 0.12);
        var manager = new DesktopPcsx2SmartConfig(
                root.resolve("qa/OKUBI/smart-config-v1"));
        var slow = DesktopSmartGraphicsPlanner.plan(
                DesktopSmartPerformance.recommend(game.console(), intel,
                        DesktopSmartPerformance.Mode.AUTO),
                intel, DesktopSmartPlayHistory.Result.SLOW);
        rejects(() -> manager.prepareGraphicsProfile(game, intel, slow),
                "Default is OFF; no new graphics files without consent.");
        manager.setEnabled(game, intel, true);
        Path lowProfile = manager.prepareGraphicsProfile(game, intel, slow);
        String lowContent = Files.readString(lowProfile);
        check(lowProfile.toString().contains("-graphics-v2-")
                        && lowContent.contains("[EmuCore/GS]")
                        && lowContent.contains("upscale_multiplier = 1")
                        && lowContent.contains("fxaa = false")
                        && lowContent.contains("MaxAnisotropy = 0"),
                "Low-power profile creates exact three-key v2 INI.");
        Path previous = manager.prepareProfile(game, intel, 1);
        check(!previous.equals(lowProfile)
                        && Files.readString(previous).contains("upscale_multiplier = 1"),
                "Legacy Phase63 INI remains intact and separate.");
        Path pcsx2 = root.resolve("pcsx2/pcsx2-qt.exe");
        Files.createDirectories(pcsx2.getParent());
        Files.write(pcsx2, new byte[]{1, 2, 3});
        List<String> command = DesktopMulticonsoleLauncher.commandWithGameConfig(
                game.console(), DesktopSmartPerformance.Mode.ECO,
                pcsx2, rom, lowProfile);
        check(command.contains("-gamecfg") && command.contains("--")
                        && command.indexOf("-gamecfg") < command.indexOf("--"),
                "Safe launcher accepts v2 INI while protecting title arguments.");
        check(command.get(command.size()-1).equals(rom.toString()),
                "Game paths with spaces remain a single CLI argument.");

        manager.setEnabled(game, dedicated, true);
        var strong = DesktopSmartGraphicsPlanner.plan(
                DesktopSmartPerformance.recommend(game.console(), dedicated,
                        DesktopSmartPerformance.Mode.QUALITY),
                dedicated, DesktopSmartPlayHistory.Result.GOOD);
        Path highProfile = manager.prepareGraphicsProfile(game, dedicated, strong);
        check(!highProfile.equals(lowProfile)
                        && Files.readString(highProfile).contains("upscale_multiplier = 2")
                        && Files.readString(highProfile).contains("fxaa = true")
                        && Files.readString(highProfile).contains("MaxAnisotropy = 4"),
                "Quality plan is separate by hardware and capped to 2x/4x.");

        Files.writeString(highProfile, Files.readString(highProfile)
                .replace("MaxAnisotropy = 4", "MaxAnisotropy = 16"));
        rejects(() -> manager.prepareGraphicsProfile(game, dedicated, strong),
                "Tampered high graphics plan is never silently overwritten.");
        check(Files.readString(highProfile).contains("MaxAnisotropy = 16"),
                "Tampered profile stays unchanged as evidence.");

        manager.setEnabled(game, intel, false);
        check(!manager.enabled(game, intel),
                "Opt-out leaves original PCSX2 graphics in control.");
        rejects(() -> manager.prepareGraphicsProfile(game, intel, slow),
                "Rollback blocks generated settings on next launch.");
        check(!DesktopMulticonsoleLauncher.command(game.console(),
                        DesktopSmartPerformance.Mode.ECO, pcsx2, rom).contains("-gamecfg"),
                "Normal command after opt-out never passes temporary settings.");

        check(Arrays.equals(romContents, Files.readAllBytes(rom))
                        && Arrays.equals(saveContents, Files.readAllBytes(gameSave))
                        && Arrays.equals(settings, Files.readAllBytes(global))
                        && Arrays.equals(oldPreferences, Files.readAllBytes(oldOkubi)),
                "ROM, memory card, global PCSX2 INI and retro library unchanged.");
        System.out.println("DesktopSmartGraphicsPhase64Test: PASS");
    }
}
