package bo.okubi.desktop;

import java.awt.Color;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Offline UI contrast and hardware-score tests; does not touch user profiles. */
public final class DesktopSmartPcVisualsTest {
    private static void check(boolean okay, String reason) {
        if (!okay) throw new AssertionError(reason);
    }
    private static int contrast(Color a, Color b) {
        int dr = Math.abs(a.getRed() - b.getRed());
        int dg = Math.abs(a.getGreen() - b.getGreen());
        int db = Math.abs(a.getBlue() - b.getBlue());
        return dr + dg + db;
    }
    private static Color pixel(BufferedImage image, int x, int y) {
        return new Color(image.getRGB(x, y), true);
    }
    private static void testComponents() {
        JButton active = DesktopSmartPcVisuals.button("Importar carpeta", () -> {});
        active.setSize(195, 42);
        JButton disabled = DesktopSmartPcVisuals.button("Página oficial", null);
        disabled.setEnabled(false);
        disabled.setSize(195, 42);
        BufferedImage enabledImage = new BufferedImage(195, 42, BufferedImage.TYPE_INT_ARGB);
        BufferedImage disabledImage = new BufferedImage(195, 42, BufferedImage.TYPE_INT_ARGB);
        active.paint(enabledImage.getGraphics());
        disabled.paint(disabledImage.getGraphics());
        Color enabledCenter = pixel(enabledImage, 90, 7);
        Color disabledCenter = pixel(disabledImage, 90, 7);
        check(enabledCenter.getRed() < 200 && enabledCenter.getGreen() < 150,
                "Windows LAF must not force a white active button.");
        check(disabledCenter.getRed() < 130 && disabledCenter.getGreen() < 130,
                "Windows LAF must not force unreadable white disabled buttons.");
        check(contrast(DesktopSmartPcVisuals.TEXT, DesktopSmartPcVisuals.FIELD) > 370,
                "Field text must have visible contrast on dark background.");
        check(contrast(DesktopSmartPcVisuals.MUTED, DesktopSmartPcVisuals.PANEL) > 275,
                "Secondary labels must remain legible.");

        JComboBox<DesktopSmartPerformance.Mode> modes =
                new JComboBox<>(DesktopSmartPerformance.Mode.values());
        DesktopSmartPcVisuals.darkCombo(modes);
        check(modes.getBackground().equals(DesktopSmartPcVisuals.FIELD),
                "Native white combobox background must be replaced.");
        check(modes.getForeground().equals(DesktopSmartPcVisuals.TEXT),
                "Combobox foreground must remain readable.");
        check(modes.getRenderer() != null && modes.getItemCount() == 4,
                "All previously saved per-game modes remain usable.");

        JPanel footer = new JPanel(new GridLayout(2, 3, 9, 7));
        for (int i = 0; i < 6; i++)
            footer.add(DesktopSmartPcVisuals.button("Botón " + i, null));
        footer.setSize(760, 86);
        footer.doLayout();
        check(footer.getComponentCount() == 6, "Six actions remain visible.");
        for (var component : footer.getComponents())
            check(component.getWidth() >= 220 && component.getHeight() >= 36,
                    "No horizontal action clipping at 760px width.");
    }
    private static void testCurrentHardware() {
        var cpu = new DesktopSmartHardware.Snapshot(
                "11th Gen Intel Core i5-11400 @ 2.60GHz", 12,
                (long)(15.8 * 1024 * 1024 * 1024),
                "Intel UHD Graphics 730", true, 0.3);
        check(DesktopSmartPcVisuals.ratingLabel(
                DesktopSmartPerformance.Console.PS1, cpu).contains("PROBABLE"),
                "PS1 should show probable on the screenshot's hardware.");
        check(DesktopSmartPcVisuals.ratingLabel(
                DesktopSmartPerformance.Console.PS2, cpu).contains("VARIABLE"),
                "PS2 integrated GPU should show variable, not guarantee FPS.");
        check(DesktopSmartPcVisuals.ratingLabel(
                DesktopSmartPerformance.Console.PS3, cpu).contains("NO RECOMENDADO"),
                "PS3 should warn on integrated UHD 730.");
        check(DesktopSmartPcVisuals.ratingLabel(
                DesktopSmartPerformance.Console.PS3, null).contains("ANALIZANDO"),
                "Before detection show analyzing instead of false compatibility.");
        for (var console : DesktopSmartPerformance.Console.values()) {
            var grade = DesktopSmartPerformance.recommend(console, cpu,
                    DesktopSmartPerformance.Mode.AUTO).rating();
            check(contrast(DesktopSmartPcVisuals.ratingColor(grade),
                    DesktopSmartPcVisuals.FIELD) > 165,
                    "Colored score labels need contrast for " + console);
        }
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            testComponents();
            testCurrentHardware();
        });
        System.out.println("DesktopSmartPcVisualsTest: PASS");
    }
}
