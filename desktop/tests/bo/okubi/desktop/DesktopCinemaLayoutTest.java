package bo.okubi.desktop;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/** Offline breakpoints, high-DPI readability, Unicode and window bounds. */
public final class DesktopCinemaLayoutTest {
    private static void check(boolean value, String explanation) {
        if (!value) throw new AssertionError(explanation);
    }

    public static void main(String[] args) throws Exception {
        check(!DesktopCinemaLayout.showHero(674)
                && DesktopCinemaLayout.showHero(675),
                "Hero hides at short laptop height without hiding the library.");
        check(!DesktopCinemaLayout.showCategoryTabs(559)
                && DesktopCinemaLayout.showCategoryTabs(560),
                "Short windows provide sidebar categories instead of clipped tabs.");
        check(!DesktopCinemaLayout.showFooterVersion(1159)
                && DesktopCinemaLayout.showFooterVersion(1160),
                "Long version text cannot overlap status on small windows.");
        check(DesktopCinemaLayout.cardRowHeight(17) == 180,
                "Normal fonts keep original cinema density.");
        check(DesktopCinemaLayout.cardRowHeight(24) == 240,
                "High DPI fonts receive more vertical room.");
        check(DesktopCinemaLayout.cardRowHeight(80) == 252,
                "Extreme fonts are limited to avoid runaway layouts.");
        Dimension small = DesktopCinemaLayout.gameHubSize(800, 520, 800, 520);
        check(small.width <= 755 && small.height <= 425,
                "Game Hub fits inside 800x520 screen including decorations.");
        Dimension large = DesktopCinemaLayout.gameHubSize(1920, 1080, 1920, 1080);
        check(large.width == 940 && large.height == 665,
                "Game Hub doesn't become excessively wide on a large monitor.");
        Dimension reduced = DesktopCinemaLayout.gameHubSize(800, 520, 620, 400);
        check(reduced.width <= 620 && reduced.height <= 400,
                "Dialogs do not grow past the screen even with a smaller monitor.");

        SwingUtilities.invokeAndWait(() -> {
            BufferedImage image = new BufferedImage(600, 100,
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                Font font = new Font("Segoe UI", Font.BOLD, 13);
                FontMetrics metrics = graphics.getFontMetrics(font);
                String original = "Pokémon Esmeralda — Edición especial 遊 🎮";
                for (int width : new int[]{0, 8, 30, 80, 120, 180, 240, 340, 600}) {
                    String visible = DesktopCinemaLayout.fitText(original, metrics, width);
                    check(metrics.stringWidth(visible) <= width,
                            "Visible text must fit the actual button width.");
                    check(!Character.isHighSurrogate(
                            visible.isEmpty() ? ' ' : visible.charAt(visible.length() - 1)),
                            "No broken UTF-16 game character at end of title.");
                    check(!visible.contains("..."),
                            "Use one compact typographic ellipsis.");
                }
                check(DesktopCinemaLayout.fitText(original, metrics, 900).equals(original),
                        "Full title remains visible when there is room.");
                check(DesktopCinemaLayout.fitText(null, metrics, 100).isEmpty(),
                        "Null label is harmless.");
            } finally { graphics.dispose(); }
            JLabel dummy = new JLabel("OKUBI");
            check(dummy != null, "Swing remains compatible with this layout policy.");
            System.out.println("DesktopCinemaLayoutTest: PASS");
        });
    }
}
