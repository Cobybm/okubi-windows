package bo.okubi.desktop;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.JButton;

/**
 * UI-independent, high contrast button.
 *
 * Windows Look and Feel may ignore JButton.setBackground() and render a
 * pale native button even when OKUBI has a dark theme. Rendering both the
 * background AND the foreground here keeps every action readable, while
 * JButton continues to provide keyboard activation, focus and accessibility.
 */
public final class CinemaButton extends JButton {
    private static final Color WHITE = new Color(255, 248, 253);
    private static final Color PRIMARY = new Color(184, 31, 116);
    private static final Color SECONDARY = new Color(52, 36, 72);
    private static final Color HOVER = new Color(85, 46, 105);
    private static final Color OUTLINE = new Color(226, 66, 151);
    private final boolean primary;
    private CinemaIcons.Kind glyph = CinemaIcons.Kind.NONE;
    private boolean starFilled;

    public CinemaButton(String title, boolean primary, Runnable action) {
        super();
        this.primary = primary;
        setText(title);
        setFont(new Font("Segoe UI", Font.BOLD, 12));
        setForeground(WHITE);
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setBorder(BorderFactory.createEmptyBorder(11, 15, 11, 15));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(CinemaIcons.accessibleName(glyph, getText()));
        getAccessibleContext().setAccessibleName(
                CinemaIcons.accessibleName(glyph, getText()));
        addActionListener(event -> action.run());
    }

    @Override public void setText(String value) {
        glyph = CinemaIcons.forLabel(value);
        starFilled = value != null && value.startsWith("★");
        super.setText(CinemaIcons.withoutEmoji(value));
        if (getAccessibleContext() != null)
            getAccessibleContext().setAccessibleName(
                    CinemaIcons.accessibleName(glyph, getText()));
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (w <= 2 || h <= 2) return;
            Color fill = primary ? PRIMARY : SECONDARY;
            if (getModel().isRollover()) fill = primary
                    ? new Color(204, 41, 129) : HOVER;
            if (getModel().isPressed()) fill = primary
                    ? new Color(136, 19, 85) : new Color(35, 24, 52);
            if (!isEnabled()) fill = new Color(63, 57, 72);
            g.setColor(fill);
            g.fillRoundRect(1, 1, w - 3, h - 3, 13, 13);
            boolean highlighted = isFocusOwner() || getModel().isRollover();
            g.setStroke(new BasicStroke(highlighted ? 2.0f : 1.2f));
            g.setColor(highlighted ? WHITE : OUTLINE);
            g.drawRoundRect(1, 1, w - 3, h - 3, 13, 13);

            // No super.paintComponent(): native Windows UI delegates can
            // repaint a white background or force pale theme text.
            g.setFont(getFont());
            FontMetrics metrics = g.getFontMetrics();
            String fullTitle = getText() == null ? "" : getText();
            final int iconSize = 17;
            final int iconGap = 8;
            boolean drawGlyph = glyph != CinemaIcons.Kind.NONE;
            int glyphSpace = drawGlyph ? iconSize + (fullTitle.isEmpty() ? 0 : iconGap) : 0;
            int usable = Math.max(0, w - 22 - glyphSpace);
            String title = DesktopCinemaLayout.fitText(fullTitle, metrics, usable);
            int contentWidth = (drawGlyph ? glyphSpace : 0) + metrics.stringWidth(title);
            int x = Math.max(6, (w - contentWidth) / 2);
            Color ink = isEnabled() ? WHITE : new Color(176, 164, 183);
            if (drawGlyph) {
                CinemaIcons.paint(g, glyph, x, (h - iconSize) / 2, iconSize, ink,
                        starFilled);
                x += glyphSpace;
            }
            int y = (h - metrics.getHeight()) / 2 + metrics.getAscent();
            g.setColor(ink);
            if (!title.isEmpty()) g.drawString(title, x, y);
        } finally {
            g.dispose();
        }
    }
}
