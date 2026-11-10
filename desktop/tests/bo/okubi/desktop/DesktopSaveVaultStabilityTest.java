package bo.okubi.desktop;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Save Vault v1 regression of unsafe layouts and tampered metadata.
 * A rejection must never modify a live SRAM/RTC byte or create ROM content.
 */
public final class DesktopSaveVaultStabilityTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("okubi-vault-stability-");
        Path liveRoot = root.resolve("live-saves");
        try {
            new DesktopSaveVault(liveRoot.resolve("vault"), liveRoot);
            throw new AssertionError("A nested vault must be forbidden.");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("separate"),
                    "Separate live saves and vault archives.");
        }
        try {
            new DesktopSaveVault(liveRoot, liveRoot.resolve("nested-live"));
            throw new AssertionError("A parent vault must be forbidden.");
        } catch (IllegalArgumentException expected) { }

        Path rom = root.resolve("Pokemon Esmeralda.gba");
        byte[] originalRom = new byte[]{0x12, 0x35, 0x53, 0x48};
        Files.write(rom, originalRom);
        DesktopLibrary library = new DesktopLibrary(root.resolve("library.properties"));
        library.addGame(rom);
        DesktopLibrary.Game game = library.games().get(0);
        DesktopSaves live = new DesktopSaves(liveRoot, game.console, game.rom);
        Files.createDirectories(live.directory);
        byte[] originalRam = new byte[]{9, 8, 7, 6, 5};
        Files.write(live.ramFile, originalRam);
        DesktopSaveVault vault = new DesktopSaveVault(root.resolve("vault"), liveRoot);
        DesktopSaveVault.Entry copy = vault.backup(game);
        check(copy.valid() && copy.ram() && !copy.rtc(),
                "A battery-only game may create a battery-only snapshot.");

        // A huge, tampered manifest is refused before property parsing or
        // write attempts. The invalid copy remains visible for diagnosis.
        Path manifest = copy.folder().resolve("manifest.properties");
        byte[] forged = new byte[70 * 1024];
        Arrays.fill(forged, (byte) 'A');
        Files.write(manifest, forged);
        DesktopSaveVault.Entry invalid = vault.history(game).get(0);
        check(!invalid.valid() && invalid.problem().contains("grande"),
                "Huge metadata is identified as invalid in history.");
        try {
            vault.restore(game, copy);
            throw new AssertionError("Corrupt vault metadata must never be restored.");
        } catch (java.io.IOException expected) {
            check(expected.getMessage().contains("grande"),
                    "Restore refuses tampered manifest before overwriting SRAM.");
        }
        check(Arrays.equals(originalRam, Files.readAllBytes(live.ramFile)),
                "Live cartridge SRAM remains untouched after corruption.");
        check(Arrays.equals(originalRom, Files.readAllBytes(rom)),
                "Source ROM remains byte-identical.");
        check(Files.isRegularFile(manifest),
                "Damaged archive is preserved as evidence, not deleted.");

        System.out.println("DesktopSaveVaultStabilityTest: PASS");
    }
}
