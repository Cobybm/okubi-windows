package bo.okubi.desktop;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JButton;

/** Compact, keyboard-accessible library category tab for console navigation. */
public final class CinemaCategoryTab extends JButton {
    private static final Color TEXT = new Color(244, 237, 251);
    private final String category;
    private boolean active;

    public CinemaCategoryTab(String title, String category, Runnable action) {
        super(title);
        this.category = category;
        setFont(new Font("Segoe UI", Font.BOLD, 12));
        setForeground(TEXT);
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("Biblioteca: " + title + " · LB/RB cambia de categoría");
        getAccessibleContext().setAccessibleName("Categoría: " + title);
        addActionListener(event -> action.run());
    }

    public String category() { return category; }
    public boolean isActiveCategory() { return active; }

    public void setActiveCategory(boolean value) {
        active = value;
        getAccessibleContext().setAccessibleDescription(
                value ? "Categoría seleccionada" : "Cambiar a esta categoría");
        repaint();
    }

    @Override protected void paintComponent(Graphics original) {
        Graphics2D g = (Graphics2D) original.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (w < 2 || h < 2) return;
            Color fill = active ? new Color(98, 35, 87)
                    : getModel().isRollover() ? new Color(58, 37, 76)
                    : new Color(33, 27, 49);
            if (getModel().isPressed()) fill = new Color(78, 28, 79);
            g.setColor(fill);
            g.fillRoundRect(1, 1, w - 3, h - 3, 12, 12);
            boolean highlighted = isFocusOwner() || getModel().isRollover();
            g.setColor(highlighted ? new Color(255, 237, 248)
                    : active ? new Color(255, 86, 181)
                    : new Color(78, 60, 99));
            g.setStroke(new BasicStroke(highlighted ? 2f : 1f));
            g.drawRoundRect(1, 1, w - 3, h - 3, 12, 12);
            if (active) {
                g.setColor(new Color(255, 103, 191));
                g.fillRoundRect(10, h - 5, Math.max(0, w - 20), 3, 2, 2);
            }
            g.setFont(getFont());
            FontMetrics metrics = g.getFontMetrics();
            String name = DesktopCinemaLayout.fitText(
                    getText(), metrics, Math.max(0, w - 18));
            int x = Math.max(6, (w - metrics.stringWidth(name)) / 2);
            int y = (h - metrics.getHeight()) / 2 + metrics.getAscent();
            g.setColor(TEXT);
            g.drawString(name, x, y);
        } finally { g.dispose(); }
    }
}
