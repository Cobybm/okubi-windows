package bo.okubi.desktop;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Deterministic offline screenshot regression for the new cinema hero.
 * It must render real cover colors, preserve legible foreground controls,
 * and not access ROMs, saves or the Internet.
 */
public final class CinemaHeroBackdropTest {
    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }

    private static BufferedImage snapshot(CinemaHeroBackdrop hero) {
        BufferedImage frame = new BufferedImage(760, 196,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = frame.createGraphics();
        hero.paint(graphics);
        graphics.dispose();
        return frame;
    }

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeAndWait(() -> {
            CinemaHeroBackdrop hero = new CinemaHeroBackdrop(new Color(244, 65, 153));
            hero.setSize(760, 196);
            JLabel title = new JLabel("Pokemon Esmeralda");
            title.setForeground(Color.WHITE);
            hero.add(title, java.awt.BorderLayout.CENTER);
            hero.doLayout();
            check(!hero.hasBackdrop(), "Without cover, original cinematic gradient remains.");
            BufferedImage before = snapshot(hero);
            BufferedImage cover = new BufferedImage(128, 180,
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = cover.createGraphics();
            g.setColor(new Color(32, 208, 65));
            g.fillRect(0, 0, 128, 180);
            g.setColor(new Color(12, 51, 206));
            g.fillRect(10, 10, 40, 90);
            g.dispose();

            hero.showArtworkImmediatelyForTest(cover);
            check(hero.hasBackdrop(), "Real cover enters hero background.");
            check(!hero.isTransitionRunning(), "Test frame does not leak timer.");
            BufferedImage after = snapshot(hero);
            check(before.getRGB(620, 70) != after.getRGB(620, 70),
                    "Background visibly changes to game artwork on the right.");
            Color left = new Color(after.getRGB(65, 140));
            Color right = new Color(after.getRGB(620, 70));
            check(left.getRed() < 110 && left.getGreen() < 100
                    && left.getBlue() < 130,
                    "Foreground text area stays dark and readable.");
            check(right.getGreen() > 35,
                    "Cover contributes its actual colors, not a fake empty panel.");
            check(hero.getComponentCount() == 1,
                    "All child controls stay present above image and remain clickable.");

            hero.showArtwork(null);
            check(hero.hasBackdrop(), "Null image cannot discard a valid background.");
            hero.showArtwork(cover);
            check(hero.isTransitionRunning(),
                    "Newly selected cover fades in rather than flashing.");
            hero.removeNotify();
            check(!hero.isTransitionRunning(),
                    "Animation is stopped when component is removed.");
            System.out.println("CinemaHeroBackdropTest: PASS");
        });
    }
}
