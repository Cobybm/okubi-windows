package bo.okubi.desktop;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Deterministic, real Swing-font/header layout regression.
 * The actual min app width is 800px. Test narrow 800/960, ordinary
 * 1140 and 1440, wide 1920, and large text to simulate Windows scaling.
 */
public final class DesktopHeaderResponsiveTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void checkMode(
            DesktopHeaderLayout.Mode mode, int width, int brandWidth,
            int full, int compact) {
        if (mode == DesktopHeaderLayout.Mode.FULL)
            check(width >= DesktopHeaderLayout.requiredWidth(brandWidth, full),
                    "FULL cannot truncate a label");
        if (mode == DesktopHeaderLayout.Mode.COMPACT)
            check(width >= DesktopHeaderLayout.requiredWidth(brandWidth, compact),
                    "COMPACT cannot truncate a label");
        if (mode == DesktopHeaderLayout.Mode.STACKED)
            check((width - 36 - 16) / 3 >= full,
                    "Stacked 3-column rows fit the complete label on 800px+");
        check(DesktopHeaderLayout.rows(mode)
                    * DesktopHeaderLayout.columns(mode) == 6,
                "All six toolbar actions are always displayed");
    }

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeAndWait(() -> {
            JLabel label = new JLabel("OKUBI");
            Font font = new Font("Segoe UI", Font.BOLD, 12);
            FontMetrics fm = label.getFontMetrics(font);
            int full = DesktopHeaderLayout.widest(
                    DesktopHeaderLayout.FULL_LABELS, fm);
            int compact = DesktopHeaderLayout.widest(
                    DesktopHeaderLayout.COMPACT_LABELS, fm);
            int consoleExit = DesktopHeaderLayout.buttonWidth(
                    "◱ SALIR DE CONSOLA", fm);
            full = Math.max(full, consoleExit);
            check(compact < full, "Compact labels take less width than full names");
            int brand = 255;
            int[] widths = {800, 900, 960, 980, 1024, 1140, 1280, 1440, 1920};
            DesktopHeaderLayout.Mode previous = DesktopHeaderLayout.Mode.STACKED;
            for (int width : widths) {
                DesktopHeaderLayout.Mode mode = DesktopHeaderLayout.choose(
                        width, brand, full, compact);
                checkMode(mode, width, brand, full, compact);
                if (mode == DesktopHeaderLayout.Mode.STACKED)
                    check(width < DesktopHeaderLayout.requiredWidth(brand, compact),
                            "Stack only when compact row won't fit");
                if (mode == DesktopHeaderLayout.Mode.COMPACT)
                    check(width < DesktopHeaderLayout.requiredWidth(brand, full),
                            "Prefer full over compact when possible");
                previous = mode;
            }
            check(DesktopHeaderLayout.choose(800, brand, full, compact)
                    == DesktopHeaderLayout.Mode.STACKED,
                    "800px laptop width must use two comfortable rows");
            check(DesktopHeaderLayout.choose(1920, brand, full, compact)
                    == DesktopHeaderLayout.Mode.FULL,
                    "1920px screen shows all complete button labels");

            int fullBoundary = DesktopHeaderLayout.requiredWidth(brand, full);
            int compactBoundary = DesktopHeaderLayout.requiredWidth(brand, compact);
            check(DesktopHeaderLayout.choose(fullBoundary, brand, full, compact)
                    == DesktopHeaderLayout.Mode.FULL,
                    "Toolbar uses full labels at the measured break point");
            check(DesktopHeaderLayout.choose(fullBoundary - 1, brand, full, compact)
                    != DesktopHeaderLayout.Mode.FULL,
                    "One pixel less safely falls back instead of truncating");
            check(DesktopHeaderLayout.choose(compactBoundary, brand, full, compact)
                    == DesktopHeaderLayout.Mode.COMPACT,
                    "Compact mode works exactly at measured break point");
            check(DesktopHeaderLayout.choose(compactBoundary - 1, brand, full, compact)
                    == DesktopHeaderLayout.Mode.STACKED,
                    "Compact doesn't silently clip when window shrinks");

            // Simulate larger accessibility text without hardcoded pixels.
            FontMetrics large = label.getFontMetrics(
                    new Font("Segoe UI", Font.BOLD, 19));
            int largeFull = DesktopHeaderLayout.widest(
                    DesktopHeaderLayout.FULL_LABELS, large);
            int largeCompact = DesktopHeaderLayout.widest(
                    DesktopHeaderLayout.COMPACT_LABELS, large);
            check(largeFull > full && largeCompact > compact,
                    "Break points use measured UI font not fixed DPI guesses");
            DesktopHeaderLayout.Mode highDpi =
                    DesktopHeaderLayout.choose(1140, brand, largeFull, largeCompact);
            check(highDpi != DesktopHeaderLayout.Mode.FULL,
                    "Large font forces safer layout");

            // Test real Swing GridLayout cells; all controls remain clickable,
            // fully labelled in accessibility and able to switch to console mode.
            AtomicInteger[] actions = new AtomicInteger[6];
            CinemaButton[] buttons = new CinemaButton[6];
            for (int i = 0; i < 6; i++) {
                actions[i] = new AtomicInteger();
                final int index = i;
                buttons[i] = new CinemaButton(
                        DesktopHeaderLayout.FULL_LABELS[i], i % 2 == 0,
                        () -> actions[index].incrementAndGet());
            }

            for (int width : new int[]{800, 1024, 1140, 1920}) {
                DesktopHeaderLayout.Mode mode = DesktopHeaderLayout.choose(
                        width, brand, full, compact);
                JPanel grid = new JPanel(new GridLayout(
                        DesktopHeaderLayout.rows(mode),
                        DesktopHeaderLayout.columns(mode), 8, 8));
                grid.setSize(new Dimension(
                        mode == DesktopHeaderLayout.Mode.STACKED
                                ? width - 36 : width - (56 + brand + 20),
                        mode == DesktopHeaderLayout.Mode.STACKED ? 88 : 42));
                for (int i = 0; i < buttons.length; i++) {
                    String text = DesktopHeaderLayout.label(i, mode, false);
                    buttons[i].setText(text);
                    String complete = DesktopHeaderLayout.label(
                            i, DesktopHeaderLayout.Mode.FULL, false);
                    buttons[i].setToolTipText(complete);
                    buttons[i].getAccessibleContext().setAccessibleName(
                            CinemaIcons.withoutEmoji(complete));
                    grid.add(buttons[i]);
                }
                grid.doLayout();
                for (int i = 0; i < buttons.length; i++) {
                    CinemaButton b = buttons[i];
                    check(b.getWidth() >= DesktopHeaderLayout.buttonWidth(
                            DesktopHeaderLayout.label(i, mode, false), fm),
                            "Unclipped button at width " + width + ": " + i);
                    check(b.getHeight() >= 34, "Button still comfortably touchable");
                    check(b.getToolTipText().equals(
                            DesktopHeaderLayout.FULL_LABELS[i]),
                            "Compact label never loses its tooltip");
                    check(b.getAccessibleContext().getAccessibleName() != null,
                            "Toolbar action stays accessible");
                    // Draw with Windows-native look-and-feel and vector icons.
                    BufferedImage img = new BufferedImage(
                            b.getWidth(), b.getHeight(), BufferedImage.TYPE_INT_RGB);
                    java.awt.Graphics2D g = img.createGraphics();
                    b.paint(g);
                    g.dispose();
                    b.doClick();
                }
                grid.removeAll();
            }
            for (int i = 0; i < actions.length; i++)
                check(actions[i].get() == 4,
                        "All toolbar actions still run after four resizes: " + i);

            check(DesktopHeaderLayout.label(0,
                    DesktopHeaderLayout.Mode.COMPACT, true).contains("Ventana"),
                    "Console mode button shortens to Ventana in compact mode");
            check(DesktopHeaderLayout.label(0,
                    DesktopHeaderLayout.Mode.FULL, true).contains("SALIR"),
                    "Console mode exit has a fully descriptive label");
            check(DesktopHeaderLayout.label(5,
                    DesktopHeaderLayout.Mode.STACKED, false).contains("ROM"),
                    "Import ROM accessible in stacked bottom row");
            check(DesktopHeaderLayout.label(2,
                    DesktopHeaderLayout.Mode.STACKED, false).contains("CLOUD"),
                    "Cloud Save remains visible at narrow widths");
            System.out.println("DesktopHeaderResponsiveTest: PASS");
        });
    }
}
