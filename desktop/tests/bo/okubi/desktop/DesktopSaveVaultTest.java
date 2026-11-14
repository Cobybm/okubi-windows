package bo.okubi.desktop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Full offline lifecycle: immutable history, no overwrite and safe recovery. */
public final class DesktopSaveVaultTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("okubi-vault-test-");
        Path rom = root.resolve("Pokemon Esmeralda.gba");
        byte[] romContent = new byte[]{17, 9, 35, 48, 99};
        Files.write(rom, romContent);
        DesktopLibrary library = new DesktopLibrary(root.resolve("library.properties"));
        library.addGame(rom);
        DesktopLibrary.Game game = library.games().get(0);
        Path savesRoot = root.resolve("saves");
        Path vaultRoot = root.resolve("vault");
        DesktopSaveVault vault = new DesktopSaveVault(vaultRoot, savesRoot);
        DesktopSaves live = new DesktopSaves(savesRoot, game.console, rom);

        check(vault.history(game).isEmpty(), "No archive before the first save.");
        try {
            vault.backup(game);
            throw new AssertionError("Cannot back up nonexistent SRAM/RTC.");
        } catch (java.io.IOException expected) {
            check(expected.getMessage().contains("Todavía"),
                    "Missing save reports a clear error.");
        }
        check(!Files.exists(savesRoot),
                "A failed vault backup does not create live save files.");

        Files.createDirectories(live.directory);
        byte[] firstRam = new byte[]{1, 2, 3, 4, 5};
        byte[] firstRtc = new byte[]{10, 20, 30};
        Files.write(live.ramFile, firstRam);
        Files.write(live.rtcFile, firstRtc);
        Path previous = live.ramFile.resolveSibling(live.ramFile.getFileName() + ".previous");
        byte[] previousData = new byte[]{9, 8, 7};
        Files.write(previous, previousData);
        Path quickState = live.directory.resolve("different-core.slot1.state");
        Files.write(quickState, new byte[]{0x42, 0x43});

        DesktopSaveVault.Entry first = vault.backup(game);
        check(first.valid() && first.ram() && first.rtc(),
                "First immutable snapshot contains SRAM and RTC.");
        check(!first.folder().startsWith(savesRoot),
                "Vault lives outside live save directories.");
        check(vault.history(game).size() == 1,
                "First snapshot appears once in history.");

        byte[] secondRam = new byte[]{55, 66, 77, 88};
        byte[] secondRtc = new byte[]{99, 100};
        Files.write(live.ramFile, secondRam);
        Files.write(live.rtcFile, secondRtc);
        DesktopSaveVault.Entry second = vault.backup(game);
        check(!second.folder().equals(first.folder()),
                "New backup never overwrites previous snapshot.");
        check(vault.history(game).size() == 2,
                "Independent history contains both snapshots.");

        DesktopSaveVault.Entry recovery = vault.restore(game, first);
        check(recovery != null && "RECOVERY".equals(recovery.kind()),
                "Restoring older save protects current progress first.");
        check(Arrays.equals(firstRam, Files.readAllBytes(live.ramFile))
                && Arrays.equals(firstRtc, Files.readAllBytes(live.rtcFile)),
                "Confirmed restore activates the exact older SRAM and RTC.");
        check(Arrays.equals(previousData, Files.readAllBytes(previous)),
                "Emulator .previous recovery copy stays intact.");
        check(Arrays.equals(new byte[]{0x42, 0x43}, Files.readAllBytes(quickState)),
                "F5/F9 state stays unchanged.");
        List<DesktopSaveVault.Entry> after = vault.history(game);
        check(after.size() == 3 && after.stream()
                        .anyMatch(entry -> entry.kind().equals("RECOVERY")),
                "Protected pre-restore copy has its own entry.");

        vault.restore(game, recovery);
        check(Arrays.equals(secondRam, Files.readAllBytes(live.ramFile))
                && Arrays.equals(secondRtc, Files.readAllBytes(live.rtcFile)),
                "Pre-restore recovery can restore prior progress.");
        check(vault.history(game).size() == 4,
                "Each restore produces a new protected pre-restore version.");

        // No silent substitution of a valid archive belonging to another ROM.
        Path differentRom = root.resolve("Other Game.gba");
        Files.write(differentRom, new byte[]{5, 4, 3, 2, 1});
        library.addGame(differentRom);
        DesktopLibrary.Game other = library.games().stream()
                .filter(g -> g.rom.equals(differentRom)).findFirst().orElseThrow();
        try {
            vault.restore(other, first);
            throw new AssertionError("Cross-ROM restore must be blocked.");
        } catch (java.io.IOException expected) {
            check(expected.getMessage().contains("este juego"),
                    "Exact ROM hash boundary is enforced.");
        }

        // Corrupting an old snapshot must never modify current progress.
        Files.write(first.folder().resolve("battery.srm"), new byte[]{4, 5, 6});
        DesktopSaveVault.Entry bad = vault.history(game).stream()
                .filter(e -> e.folder().equals(first.folder())).findFirst().orElseThrow();
        check(!bad.valid() && !bad.problem().isBlank(),
                "Corrupted backup is marked invalid in the UI.");
        byte[] beforeBadRam = Files.readAllBytes(live.ramFile);
        byte[] beforeBadRtc = Files.readAllBytes(live.rtcFile);
        try {
            vault.restore(game, first);
            throw new AssertionError("Corrupt backup must not be restored.");
        } catch (java.io.IOException expected) {
            check(expected.getMessage().contains("corrupta")
                    || expected.getMessage().contains("SRAM"),
                    "Checksums prevent restoring tampered snapshots.");
        }
        check(Arrays.equals(beforeBadRam, Files.readAllBytes(live.ramFile))
                && Arrays.equals(beforeBadRtc, Files.readAllBytes(live.rtcFile)),
                "Invalid snapshot does not modify either live save.");
        check(Arrays.equals(romContent, Files.readAllBytes(rom)),
                "ROM contents remain identical after all operations.");

        System.out.println("DesktopSaveVaultTest: PASS");
    }
}
