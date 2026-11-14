package bo.okubi.desktop;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.border.Border;
import javax.swing.BorderFactory;
import javax.swing.plaf.basic.BasicComboBoxUI;

/**
 * Consistent dark-mode Swing controls for the PC compatibility dialog.
 * Avoid native Windows LAF white button/combobox skins on a dark background.
 */
public final class DesktopSmartPcVisuals {
    public static final Color BG = new Color(19, 15, 29);
    public static final Color PANEL = new Color(36, 27, 49);
    public static final Color FIELD = new Color(29, 23, 43);
    public static final Color TEXT = new Color(246, 242, 250);
    public static final Color MUTED = new Color(202, 191, 213);
    public static final Color PINK = new Color(249, 105, 192);
    public static final Color BORDER = new Color(104, 76, 120);
    private static final Color BUTTON = new Color(85, 45, 105);
    private static final Color BUTTON_HOVER = new Color(117, 56, 133);
    private static final Color BUTTON_DISABLED = new Color(51, 42, 62);
    private static final Color BUTTON_DISABLED_TEXT = new Color(170, 158, 182);
    private static final Color LIKELY = new Color(125, 228, 170);
    private static final Color VARIABLE = new Color(255, 207, 123);
    private static final Color BAD = new Color(255, 158, 161);

    private DesktopSmartPcVisuals() { }

    /** Content, border and text painted here, independent of system LAF. */
    public static JButton button(String title, Runnable action) {
        JButton button = new JButton(title) {
            @Override protected void paintComponent(Graphics original) {
                Graphics2D g = (Graphics2D) original.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    boolean active = isEnabled();
                    Color color = !active ? BUTTON_DISABLED
                            : getModel().isPressed() ? PINK
                            : getModel().isRollover() ? BUTTON_HOVER : BUTTON;
                    g.setColor(color);
                    g.fill(new RoundRectangle2D.Float(1, 1, getWidth() - 2f,
                            getHeight() - 2f, 13, 13));
                    g.setStroke(new BasicStroke(isFocusOwner() ? 2f : 1f));
                    g.setColor(isFocusOwner() ? PINK : active ? BORDER
                            : BUTTON_DISABLED);
                    g.draw(new RoundRectangle2D.Float(1.5f, 1.5f,
                            getWidth() - 3f, getHeight() - 3f, 13, 13));
                    String label = getText();
                    FontMetrics metrics = g.getFontMetrics(getFont());
                    g.setColor(active ? (getModel().isPressed() ? BG : TEXT)
                            : BUTTON_DISABLED_TEXT);
                    int x = Math.max(7, (getWidth() - metrics.stringWidth(label)) / 2);
                    int y = Math.max(metrics.getAscent() + 3,
                            (getHeight() - metrics.getHeight()) / 2
                                    + metrics.getAscent());
                    g.drawString(label, x, y);
                } finally {
                    g.dispose();
                }
            }
        };
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setPreferredSize(new Dimension(148, 38));
        button.setMinimumSize(new Dimension(100, 36));
        button.setOpaque(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (action != null) button.addActionListener(e -> action.run());
        return button;
    }

    public static <E> void darkCombo(JComboBox<E> combo) {
        combo.setUI(new BasicComboBoxUI() {
            @Override protected JButton createArrowButton() {
                JButton arrow = button("▾", null);
                arrow.setPreferredSize(new Dimension(36, 35));
                arrow.setMinimumSize(new Dimension(34, 34));
                return arrow;
            }
        });
        combo.setFont(new Font("Segoe UI", Font.BOLD, 12));
        combo.setBackground(FIELD);
        combo.setForeground(TEXT);
        combo.setOpaque(true);
        combo.setFocusable(true);
        combo.setMaximumRowCount(8);
        combo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(3, 5, 3, 3)));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(
                    JList<?> list, Object value, int index,
                    boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                setBackground(selected ? BUTTON_HOVER : FIELD);
                setForeground(TEXT);
                setFont(new Font("Segoe UI", Font.BOLD, 12));
                setBorder(BorderFactory.createEmptyBorder(5, 9, 5, 7));
                return this;
            }
        });
    }

    public static Color ratingColor(DesktopSmartPerformance.Rating rating) {
        return switch (rating) {
            case LIKELY -> LIKELY;
            case VARIABLE -> VARIABLE;
            case NOT_RECOMMENDED -> BAD;
            case UNKNOWN -> MUTED;
        };
    }

    public static String ratingLabel(DesktopSmartPerformance.Console console,
                                     DesktopSmartHardware.Snapshot snapshot) {
        if (snapshot == null) return console.name() + "  ·  ANALIZANDO";
        var value = DesktopSmartPerformance.recommend(console, snapshot,
                DesktopSmartPerformance.Mode.AUTO).rating();
        String result = switch (value) {
            case LIKELY -> "PROBABLE";
            case VARIABLE -> "VARIABLE";
            case NOT_RECOMMENDED -> "NO RECOMENDADO";
            case UNKNOWN -> "SIN DATOS";
        };
        return console.name() + "  ·  " + result;
    }

    /** A dark border that cannot acquire native white title/outline colors. */
    public static Border panelBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(7, 7, 7, 7));
    }
}
