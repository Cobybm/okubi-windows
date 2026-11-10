package bo.okubi.desktop;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;

/**
 * Offline read-only scan: only screenshots belonging to the exact ROM,
 * preserve original ROM and save bytes, ignore invalid/symlinked images.
 */
public final class DesktopGameHubMediaTest {
    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("okubi-gamehub-");
        Path rom = root.resolve("Pokemon Esmeralda.gba");
        byte[] originalRom = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        Files.write(rom, originalRom);
        DesktopLibrary library = new DesktopLibrary(root.resolve("library.properties"));
        library.addGame(rom);
        DesktopLibrary.Game game = library.games().get(0);
        Path savesRoot = root.resolve("saves");

        DesktopGameHubMedia.Snapshot none = DesktopGameHubMedia.inspect(game, savesRoot);
        check(!none.hasBattery() && !none.hasRtc()
                && none.captures().isEmpty(), "Unplayed game remains truly empty.");
        check(!Files.exists(savesRoot),
                "Read-only scan cannot create a save directory.");

        DesktopSaves saves = new DesktopSaves(savesRoot, game.console, game.rom);
        Files.createDirectories(saves.directory);
        byte[] originalSave = new byte[]{71, 72, 73, 74};
        Files.write(saves.ramFile, originalSave);
        Files.write(saves.rtcFile, new byte[]{31, 32});
        Path screenshots = saves.directory.resolve("screenshots");
        Files.createDirectories(screenshots);
        String prefix = saves.ramFile.getFileName().toString().substring(0, 12) + "-";

        BufferedImage screenshot = new BufferedImage(180, 120,
                BufferedImage.TYPE_INT_RGB);
        var painter = screenshot.createGraphics();
        painter.setColor(Color.BLUE);
        painter.fillRect(0, 0, 180, 120);
        painter.dispose();

        for (int i = 1; i <= 4; i++) {
            Path file = screenshots.resolve(prefix + "capture-" + i + ".png");
            ImageIO.write(screenshot, "png", file.toFile());
            Files.setLastModifiedTime(file,
                    java.nio.file.attribute.FileTime.fromMillis(1_700_000_000_000L + 1000 * i));
        }
        // Do not accidentally display another ROM's screenshot or a corrupt png.
        ImageIO.write(screenshot, "png", screenshots.resolve("not-our-rom.png").toFile());
        Files.write(screenshots.resolve(prefix + "corrupt.png"), new byte[]{0, 1, 2});
        long stamp = Files.getLastModifiedTime(saves.ramFile).toMillis();

        DesktopGameHubMedia.Snapshot found = DesktopGameHubMedia.inspect(game, savesRoot);
        check(found.hasBattery() && found.hasRtc(),
                "SRAM and RTC detected strictly by presence.");
        check(found.captures().size() == 3, "Only three valid captures are displayed.");
        check(found.captures().get(0).filename().contains("capture-4"),
                "Newest screenshot first.");
        check(found.captures().stream().allMatch(c -> c.filename().startsWith(prefix)),
                "No screenshot from another game leaks into the gallery.");
        check(found.captures().get(0).image().getWidth() == 180,
                "Original screenshot pixels are loaded, not fake covers.");
        check(Arrays.equals(originalSave, Files.readAllBytes(saves.ramFile))
                && Arrays.equals(originalRom, Files.readAllBytes(rom)),
                "Neither saves nor ROM bytes are modified.");
        check(Files.getLastModifiedTime(saves.ramFile).toMillis() == stamp,
                "Save timestamps are not changed by Game Hub.");

        Path missing = root.resolve("not-installed.gba");
        check(DesktopGameHubMedia.inspect(null, savesRoot).captures().isEmpty(),
                "Missing game handled without accessing storage.");
        check(!Files.exists(missing), "Scanning cannot create a ROM.");
        System.out.println("DesktopGameHubMediaTest: PASS");
    }
}
