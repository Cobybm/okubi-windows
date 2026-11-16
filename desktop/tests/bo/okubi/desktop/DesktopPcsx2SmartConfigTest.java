package bo.okubi.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** End-to-end synthetic PS2 graphics override, isolation and rollback tests. */
public final class DesktopPcsx2SmartConfigTest {
    private static void check(boolean yes, String why) {
        if (!yes) throw new AssertionError(why);
    }

    private static void blocked(Throwing runnable, String why) throws Exception {
        try {
            runnable.run();
            throw new AssertionError("Expected rejection: " + why);
        } catch (IOException expected) {
            check(!expected.getMessage().isBlank(), why);
        }
    }

    @FunctionalInterface private interface Throwing { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("okubi-phase63-");
        Path regular = root.resolve("normal-PCXS2/settings/PCSX2.ini");
        byte[] settings = "[EmuCore/GS]\nupscale_multiplier = 7\n".getBytes();
        Files.createDirectories(regular.getParent());
        Files.write(regular, settings);

        Path save = root.resolve("normal-PCXS2/memcards/MemoryCard.ps2");
        byte[] memoryCard = {4, 8, 12, 16, 20};
        Files.createDirectories(save.getParent());
        Files.write(save, memoryCard);
        Path gameFile = root.resolve("owned/Final Game - PS2.iso");
        byte[] rom = {3, 6, 9, 12};
        Files.createDirectories(gameFile.getParent());
        Files.write(gameFile, rom);

        var catalog = new DesktopMulticonsoleCatalog(
                root.resolve("qa/OKUBI/multiconsole-v1.properties"));
        catalog.addGame(gameFile, DesktopSmartPerformance.Console.PS2);
        var game = catalog.games().get(0);
        var same = new DesktopSmartHardware.Snapshot("Intel i5 11400", 12,
                16L * 1024 * 1024 * 1024, "Intel UHD Graphics 730", true, 0.21);
        var upgraded = new DesktopSmartHardware.Snapshot("Intel i5 11400", 12,
                16L * 1024 * 1024 * 1024, "NVIDIA GeForce RTX 4070", true, 0.13);

        Path home = root.resolve("qa/OKUBI/smart-config-v1");
        var smart = new DesktopPcsx2SmartConfig(home);
        check(!smart.enabled(game, same), "SMART CONFIG defaults OFF, never silent.");
        blocked(() -> smart.prepareProfile(game, same, 1),
                "Cannot generate settings before consent.");
        check(!Files.exists(home.resolve("profiles")), "No config side effect before opt-in.");
        smart.setEnabled(game, same, true);
        check(smart.enabled(game, same), "Per-game opt-in is enabled.");
        check(!smart.enabled(game, upgraded), "Upgraded GPU must opt in separately.");

        int defaultScale = DesktopSmartPerformance.recommend(game.console(), same,
                DesktopSmartPerformance.Mode.AUTO).suggestedScale();
        check(defaultScale == 1, "Intel UHD integrated starts with native resolution.");
        Path profile = smart.prepareProfile(game, same, defaultScale);
        String ini = Files.readString(profile);
        check(ini.contains("[EmuCore/GS]")
                        && ini.contains("upscale_multiplier = 1"),
                "INI must use documented PCSX2 per-game graphics key.");
        check(!ini.contains("MemoryCards") && !ini.contains("Bios")
                        && !ini.contains(gameFile.toString()),
                "Only the safe graphics setting, no private paths or BIOS.");

        Path emu = root.resolve("emulators/pcsx2-qt.exe");
        Files.createDirectories(emu.getParent());
        Files.write(emu, new byte[]{1, 2, 3, 4});
        List<String> command = DesktopMulticonsoleLauncher.commandWithGameConfig(
                game.console(), DesktopSmartPerformance.Mode.ECO,
                emu, game.file(), profile);
        int idx = command.indexOf("-gamecfg");
        check(idx > 0 && command.get(idx + 1).equals(profile.toString()),
                "PCSX2 -gamecfg must receive the exact OKUBI generated INI.");
        check(command.indexOf("--") > idx + 1
                        && command.get(command.size() - 1).equals(gameFile.toString()),
                "Safe argument separation for ROM paths containing spaces.");
        check(command.contains("-nofullscreen"),
                "Conservative mode should not force fullscreen.");

        byte[] validated = Files.readAllBytes(profile);
        Path again = smart.prepareProfile(game, same, 1);
        check(profile.equals(again) && Arrays.equals(validated, Files.readAllBytes(again)),
                "Replaying same session never overwrites approved settings.");
        blocked(() -> smart.prepareProfile(game, same, 0), "Cannot choose 0x.");
        blocked(() -> smart.prepareProfile(game, same, 4), "Cannot choose expensive 4x.");

        Files.writeString(profile, ini.replace("upscale_multiplier = 1",
                                                "upscale_multiplier = 8"));
        blocked(() -> smart.prepareProfile(game, same, 1),
                "Tampered managed profile must fail closed without overwriting.");
        check(Files.readString(profile).contains("= 8"),
                "Never overwrite external modifications, even inside managed profile.");
        Files.write(profile, validated);

        smart.setEnabled(game, same, false);
        check(!smart.enabled(game, same), "Rollback disables profile without an emulator call.");
        blocked(() -> smart.prepareProfile(game, same, 1),
                "Opt-out forbids future automatic graphic changes.");
        List<String> normal = DesktopMulticonsoleLauncher.command(
                game.console(), DesktopSmartPerformance.Mode.ECO, emu, game.file());
        check(!normal.contains("-gamecfg"),
                "Next launch after opt-out uses original external settings.");
        check(Arrays.equals(settings, Files.readAllBytes(regular)),
                "PCSX2 global settings remain byte-identical.");
        check(Arrays.equals(memoryCard, Files.readAllBytes(save)),
                "PS2 memory card survives opt-in and opt-out byte-identical.");
        check(Arrays.equals(rom, Files.readAllBytes(gameFile)),
                "Game image never touched.");

        var reloaded = new DesktopPcsx2SmartConfig(home);
        check(!reloaded.enabled(game, same), "Opt-out remains after restart.");
        reloaded.setEnabled(game, same, true);
        check(new DesktopPcsx2SmartConfig(home).enabled(game, same),
                "Consent persists for this game and this PC only.");
        check(!new DesktopPcsx2SmartConfig(home).enabled(game, upgraded),
                "Consent never carries to a different GPU.");

        var ps1Game = new DesktopMulticonsoleCatalog.Game(
                gameFile, DesktopSmartPerformance.Console.PS1,
                DesktopSmartPerformance.Mode.AUTO);
        blocked(() -> smart.setEnabled(ps1Game, same, true),
                "DuckStation cannot receive unverified PCSX2 config.");
        blocked(() -> DesktopMulticonsoleLauncher.commandWithGameConfig(
                        DesktopSmartPerformance.Console.PS1,
                        DesktopSmartPerformance.Mode.ECO, emu, gameFile, profile),
                "Only PS2 adapter accepts -gamecfg.");

        // Stronger PC can use a separate opt-in for 2x without changing 1x.
        reloaded.setEnabled(game, upgraded, true);
        Path quality = reloaded.prepareProfile(game, upgraded, 2);
        check(!quality.equals(profile) && Files.readString(quality)
                        .contains("upscale_multiplier = 2"),
                "Quality profile is isolated by hardware and bounded to 2x.");
        check(Files.readString(profile).contains("upscale_multiplier = 1"),
                "Original Intel native 1x profile remains unchanged.");

        String approvalContents = Files.readString(home.resolve(
                "smart-config-pcsx2-v1.properties"));
        check(!approvalContents.contains(gameFile.toString())
                        && !approvalContents.contains("Intel i5"),
                "Consent file uses hashed identifiers, not private paths/CPU names.");
        System.out.println("DesktopPcsx2SmartConfigTest: PASS");
    }
}
