package bo.okubi.desktop;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Image-level contrast regression under Windows native Look and Feel. */
public final class DesktopAppearanceTest {
    private static void yes(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeAndWait(() -> {
            AtomicInteger clicked = new AtomicInteger(0);
            for (boolean primary : new boolean[]{false, true}) {
                CinemaButton button = new CinemaButton(
                        primary ? "+ Importar ROM" : "Configurar emuladores",
                        primary, clicked::incrementAndGet);
                button.setSize(190, 44);
                BufferedImage preview = new BufferedImage(190, 44,
                        BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = preview.createGraphics();
                button.paint(graphics);
                graphics.dispose();

                Color fill = new Color(preview.getRGB(8, 20), true);
                yes(fill.getAlpha() == 255, "Button must paint an opaque fill");
                yes(fill.getRed() < 230 && fill.getGreen() < 125,
                        "Never paint white Windows native buttons");
                yes(button.getForeground().getRed() >= 240,
                        "Button text remains visibly white");
                yes(!button.isContentAreaFilled(),
                        "Disable native look-and-feel button background");
                yes(button.isFocusTraversable(), "Button remains keyboard-accessible");
                button.doClick();
            }
            yes(clicked.get() == 2, "Both actions remain operational");
            System.out.println("DesktopAppearanceTest: PASS");
        });
    }
}
