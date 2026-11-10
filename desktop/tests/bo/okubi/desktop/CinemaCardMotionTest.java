package bo.okubi.desktop;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Short-lived hover animation never changes selected ROM or game state. */
public final class CinemaCardMotionTest {
    private static void yes(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeAndWait(() -> {
            CinemaLibraryCard card = new CinemaLibraryCard(
                    "Pokemon Esmeralda", new Color(244, 65, 153));
            card.setSize(400, 180);
            yes(!card.isSelectedGame(), "Card initially unselected.");
            yes(!card.isHoveredGame(), "No hover on initial render.");
            card.setHovered(true);
            yes(card.isHoveredGame(), "Hover activates without starting a game.");
            yes(card.isHoverTransitionRunning(), "Hover glows with a short timer.");
            card.setSelected(true);
            yes(card.isSelectedGame(), "Hover must not steal selected game.");
            BufferedImage image = new BufferedImage(400, 180, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            card.paint(graphics);
            graphics.dispose();
            yes(image.getRGB(4, 30) != 0, "Selection border renders on Windows.");
            card.setHovered(false);
            yes(!card.isHoveredGame(), "Mouse exit turns hover state off.");
            card.removeNotify();
            yes(!card.isHoverTransitionRunning(),
                    "A discarded card cannot leave a Swing timer running.");
            yes(card.getAccessibleContext().getAccessibleDescription()
                    .contains("seleccionado"), "Screen reader still knows selected state.");
            System.out.println("CinemaCardMotionTest: PASS");
        });
    }
}
