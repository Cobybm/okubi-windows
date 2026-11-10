package bo.okubi.desktop;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Real Swing Game Hub layout, user action callbacks and screenshot widgets. */
public final class DesktopGameHubPanelTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("okubi-hub-panel-");
        Path rom = directory.resolve("Pokemon Rojo Fuego.gba");
        Files.write(rom, new byte[]{11, 22, 33});
        DesktopLibrary library = new DesktopLibrary(directory.resolve("library.properties"));
        library.addGame(rom);
        DesktopLibrary.Game game = library.games().get(0);
        DesktopPlayStats stats = new DesktopPlayStats(directory.resolve("stats.properties"));
        DesktopPlayStats.Snapshot snapshot = stats.get(game.console, game.rom);

        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeAndWait(() -> {
            AtomicInteger played = new AtomicInteger();
            AtomicInteger favorited = new AtomicInteger();
            AtomicInteger backups = new AtomicInteger();
            AtomicInteger closed = new AtomicInteger();
            DesktopGameHubPanel panel = new DesktopGameHubPanel(game, snapshot,
                    null, played::incrementAndGet, favorited::incrementAndGet,
                    backups::incrementAndGet, closed::incrementAndGet);
            panel.setSize(860, 570);
            panel.doLayout();
            check(panel.canPlay() && panel.isFavoriteActionEnabled(),
                    "Available game offers play and favorite actions.");
            check(panel.favoriteLabel().toUpperCase().contains("AÑADIR"),
                    "First-time favorite action clearly says add.");
            check(panel.saveStatus().contains("Comprobando"),
                    "Slow ROM checksum is deferred rather than computed on EDT.");

            panel.playAction().doClick();
            panel.favoriteAction().doClick();
            panel.backupAction().doClick();
            check(played.get() == 1 && favorited.get() == 1 && backups.get() == 1,
                    "Each action invokes exactly its intended callback.");
            panel.updateFavorite(true);
            check(panel.favoriteLabel().contains("FAVORITO"),
                    "Favorite state can update without reconstructing dialog.");
            BufferedImage screenshot = new BufferedImage(180, 120,
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = screenshot.createGraphics();
            g.setColor(Color.MAGENTA);
            g.fillRect(0, 0, 180, 120);
            g.dispose();
            panel.showMedia(new DesktopGameHubMedia.Snapshot(true, true,
                    List.of(new DesktopGameHubMedia.Capture(
                            screenshot, "my-own-capture.png")), "Captura local."));
            check(panel.screenshotCount() == 1,
                    "Real screenshot appears in Game Hub gallery.");
            check(panel.saveStatus().contains("detectado"),
                    "SRAM and RTC status is visible without opening save files.");
            BufferedImage image = new BufferedImage(860, 570,
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D painter = image.createGraphics();
            panel.paint(painter);
            painter.dispose();
            check(image.getRGB(400, 60) != 0,
                    "Windows-safe dark metadata pane paints correctly.");
            check(panel.getAccessibleContext().getAccessibleName()
                    .contains("Pokemon Rojo Fuego"), "Screen reader sees game title.");
            panel.showMedia(new DesktopGameHubMedia.Snapshot(false, false,
                    List.of(), "Sin capturas."));
            check(panel.screenshotCount() == 0,
                    "Fallback never invents non-existent screenshots.");
            System.out.println("DesktopGameHubPanelTest: PASS");
        });
        check(Files.size(rom) == 3, "Game Hub cannot mutate the ROM.");
    }
}
