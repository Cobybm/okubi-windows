package bo.okubi.desktop;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Offline rendering regression: real cover, fitting, and original fallback. */
public final class OnlineCoverArtworkTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("okubi-cover-render-");
        Path rom = root.resolve("Pokemon Esmeralda.gba");
        Files.write(rom, new byte[]{0, 1, 2});
        DesktopLibrary library = new DesktopLibrary(root.resolve("library.properties"));
        library.addGame(rom);
        DesktopLibrary.Game game = library.games().get(0);

        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeAndWait(() -> {
            for (boolean hero : new boolean[]{false, true}) {
                OnlineCoverArtwork art = new OnlineCoverArtwork(game, hero, null);
                art.setSize(hero ? 185 : 106, hero ? 150 : 155);
                art.doLayout();
                check(!art.hasCover(), "Procedural fallback before cover download");
                check(art.getComponent(0).isVisible(),
                        "Original procedural graphic remains visible");
                BufferedImage simulated = new BufferedImage(120, 180,
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D picture = simulated.createGraphics();
                picture.setColor(Color.GREEN);
                picture.fillRect(0, 0, 120, 180);
                picture.dispose();
                art.showCoverForTest(simulated);
                check(art.hasCover(), "Real cover replaces fallback");
                check(!art.getComponent(0).isVisible(),
                        "Procedural art is hidden after valid cover arrives");
                BufferedImage display = new BufferedImage(
                        art.getWidth(), art.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D painter = display.createGraphics();
                art.paint(painter);
                painter.dispose();
                Color center = new Color(display.getRGB(art.getWidth() / 2,
                        art.getHeight() / 2), true);
                check(center.getGreen() > 120 && center.getRed() < 80,
                        "Original color artwork displayed in center, not procedural graphic");
                check(art.getAccessibleContext().getAccessibleName()
                        .contains("Pokemon Esmeralda"),
                        "Real image retains accessible name");
            }
            System.out.println("OnlineCoverArtworkTest: PASS");
        });
        Files.deleteIfExists(root.resolve("library.properties"));
        Files.deleteIfExists(rom);
        Files.deleteIfExists(root);
    }
}
