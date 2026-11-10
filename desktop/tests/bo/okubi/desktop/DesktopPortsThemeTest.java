package bo.okubi.desktop;

import java.awt.Color;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Runs under both Windows LAF and headless Linux; no dialogs or user data. */
public final class DesktopPortsThemeTest {
    private static void check(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason);
    }

    public static void main(String[] args) throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                // Reproduce the original screenshot scenario: the host's
                // look and feel is installed before opening the ports view.
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                JButton button = DesktopPortsTheme.button("DESCARGAS", () -> {});
                check(button.isOpaque(), "Dark action buttons need opaque background");
                check(button.isContentAreaFilled(), "Dark action buttons need filled area");
                check(button.getBackground().equals(DesktopPortsTheme.BUTTON),
                        "Windows-themed white background leaked into action button");
                check(button.getForeground().equals(DesktopPortsTheme.FOREGROUND),
                        "Button text lacks contrast");
                check(button.getBorder() != null, "Visible action button border");
                check(button.getAccessibleContext().getAccessibleName().equals("DESCARGAS"),
                        "Buttons remain accessible");
                check(button.getBackground().getRed() < 130,
                        "Button must be visibly dark, not Windows white");
                button.setSize(130, 44);
                BufferedImage pixel = new BufferedImage(130, 44,
                        BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = pixel.createGraphics();
                try { button.paint(g); } finally { g.dispose(); }
                Color painted = new Color(pixel.getRGB(6, 6), true);
                check(painted.getRed() < 170, "Offscreen action button painted light");

                JComboBox<String> combo = new JComboBox<>(
                        new String[]{"Todos", "Requiere archivos originales"});
                DesktopPortsTheme.combo(combo);
                check(combo.isOpaque(), "Combo must be opaque");
                check(combo.getBackground().equals(DesktopPortsTheme.PANEL),
                        "Native white combo background leaked");
                check(combo.getForeground().equals(DesktopPortsTheme.FOREGROUND),
                        "Native pale combo text leaked");
                JLabel idle = (JLabel) combo.getRenderer().getListCellRendererComponent(
                        new JList<>(), "Todos", -1, false, false);
                check(idle.getBackground().equals(DesktopPortsTheme.PANEL)
                        && idle.getForeground().equals(DesktopPortsTheme.FOREGROUND),
                        "Closed combo renderer must be dark with bright text");
                JLabel selected = (JLabel) combo.getRenderer().getListCellRendererComponent(
                        new JList<>(), "Todos", 0, true, false);
                check(selected.getBackground().equals(DesktopPortsTheme.LIST_SELECTED),
                        "Selected popup item must have visible highlight");
                check(selected.getForeground().equals(DesktopPortsTheme.FOREGROUND),
                        "Selected popup text should not disappear");
                combo.setSize(250, 42);
                BufferedImage comboImage = new BufferedImage(250, 42,
                        BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D cg = comboImage.createGraphics();
                try { combo.paint(cg); } finally { cg.dispose(); }
                Color comboPixel = new Color(comboImage.getRGB(10, 10), true);
                check(comboPixel.getRed() < 180, "Combobox still painted white");
                System.out.println("DesktopPortsThemeTest: PASS");
            } catch (Throwable problem) { error.set(problem); }
        });
        if (error.get() != null) throw new AssertionError(error.get());
    }
}
