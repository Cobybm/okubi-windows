package bo.okubi.desktop;

import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * Regression for Windows screenshot showing a 360px-wide audio JOptionPane
 * with clipped labels and a truncated warning. This runs headlessly on CI.
 */
public final class DesktopMediaPanelLayoutTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicInteger volume = new AtomicInteger(100);
            AtomicInteger speedIndex = new AtomicInteger(0);
            AtomicInteger[] calls = new AtomicInteger[6];
            for (int i = 0; i < calls.length; i++) calls[i] = new AtomicInteger();
            DesktopMediaPanel panel = new DesktopMediaPanel(volume, speedIndex,
                    () -> calls[0].incrementAndGet(),
                    () -> calls[1].incrementAndGet(),
                    () -> calls[2].incrementAndGet(),
                    () -> calls[3].incrementAndGet(),
                    () -> calls[4].incrementAndGet(),
                    () -> {
                        calls[5].incrementAndGet();
                        speedIndex.updateAndGet(DesktopSpeedControl::next);
                    });
            panel.setSize(panel.getPreferredSize());
            panel.doLayout();
            JPanel grid = panel.controlsForTest();
            grid.doLayout();
            check(panel.getPreferredSize().width >= 620,
                    "Media popup must not shrink to screenshot's clipped 360px width");
            check(panel.getPreferredSize().height >= 330,
                    "Media popup must reserve space for sliders and notices");
            check(grid.getComponentCount() == 6, "Six independent audio actions required");
            check(grid.getWidth() >= 570, "Two-column grid must use full dialog width");
            int i = 0;
            for (Component c : grid.getComponents()) {
                check(c instanceof JButton, "Each media control must be a keyboard-friendly button");
                JButton button = (JButton) c;
                Rectangle rect = button.getBounds();
                check(rect.x >= 0 && rect.y >= 0
                                && rect.x + rect.width <= grid.getWidth()
                                && rect.y + rect.height <= grid.getHeight(),
                        "Media action must remain inside grid: " + button.getText());
                FontMetrics metrics = button.getFontMetrics(button.getFont());
                check(rect.width >= metrics.stringWidth(button.getText()) + 42,
                        "Media action label must not be elided: " + button.getText());
                button.doClick();
                check(calls[i].get() == 1, "Media button callback #" + i + " broken");
                i++;
            }
            check(volume.get() == 100, "Opening media panel must not change game volume");
            panel.sliderForTest().setValue(37);
            check(volume.get() == 37, "Volume changes must affect only injected game gain");
            JButton speedButton = (JButton) grid.getComponent(5);
            check(speedButton.getText().contains(DesktopSpeedControl.label(speedIndex.get())),
                    "Speed label must update after user clicks F6 button");

            boolean wrapped = false;
            for (Component c : ((JPanel) panel.getComponent(2)).getComponents()) {
                if (c instanceof JTextArea text) {
                    wrapped = text.getLineWrap() && text.getWrapStyleWord();
                }
            }
            check(wrapped, "Windows-wide mute warning must wrap instead of being clipped");
            System.out.println("DesktopMediaPanelLayoutTest: PASS");
        });
    }
}
