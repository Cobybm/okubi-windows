package bo.okubi.desktop;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JPanel;

/**
 * Original procedural cover art: no internet, copyrighted box artwork,
 * screenshots, external font, ROM bytes or graphics files required.
 */
public final class RetroArtwork extends JPanel {
    private final DesktopLibrary.Console console;
    private final String title;
    private final boolean hero;

    public RetroArtwork(DesktopLibrary.Console console, String title, boolean hero) {
        this.console = console;
        this.title = title == null ? "" : title;
        this.hero = hero;
        setOpaque(false);
        setPreferredSize(hero ? new Dimension(290, 180) : new Dimension(106, 155));
        setMinimumSize(hero ? new Dimension(180, 150) : new Dimension(80, 110));
        setToolTipText(this.title);
    }

    public static Color accent(DesktopLibrary.Console console) {
        return switch (console) {
            case GB -> new Color(114, 236, 169);
            case GBC -> new Color(111, 164, 255);
            case GBA -> new Color(244, 65, 153);
            case SNES -> new Color(175, 126, 255);
        };
    }

    public static String code(DesktopLibrary.Console console) {
        return switch (console) {
            case GB -> "GB";
            case GBC -> "GBC";
            case GBA -> "GBA";
            case SNES -> "SNES";
        };
    }

    @Override protected void paintComponent(Graphics original) {
        super.paintComponent(original);
        Graphics2D g = (Graphics2D)original.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (w < 10 || h < 10) return;
            Color accent = accent(console);
            g.setClip(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 16, 16));
            g.setPaint(new GradientPaint(0, 0, new Color(37, 27, 60),
                    w, h, new Color(12, 11, 25)));
            g.fillRect(0, 0, w, h);

            // Diagonal crystalline geometry; stable per game, but no ROM scan.
            long seed = (long) title.hashCode() * 0x9E3779B97F4A7C15L;
            int blocks = hero ? 36 : 18;
            for (int i = 0; i < blocks; i++) {
                seed ^= (seed << 13);
                seed ^= (seed >>> 7);
                seed ^= (seed << 17);
                int x = (int)Math.floorMod(seed, (long)w + 30) - 15;
                int y = (int)Math.floorMod(seed >>> 9, (long)h + 30) - 15;
                int side = hero ? 7 + i % 9 : 4 + i % 6;
                g.setColor(new Color(accent.getRed(), accent.getGreen(),
                        accent.getBlue(), i % 3 == 0 ? 105 : 45));
                g.fillRect(x, y, side, side);
            }

            int circleSize = Math.max(56, Math.min(w, h) * (hero ? 8 : 7) / 10);
            int centerX = w / 2;
            int centerY = hero ? h / 2 : h * 43 / 100;
            g.setStroke(new BasicStroke(hero ? 3.4f : 2.4f));
            g.setColor(new Color(accent.getRed(), accent.getGreen(),
                    accent.getBlue(), 65));
            g.fillOval(centerX - circleSize / 2, centerY - circleSize / 2,
                    circleSize, circleSize);
            g.setColor(accent);
            g.drawOval(centerX - circleSize / 2, centerY - circleSize / 2,
                    circleSize, circleSize);

            // Original gamepad glyph: four-way D-pad and A/B dots.
            int unit = Math.max(3, circleSize / 13);
            int cx = centerX - circleSize / 6;
            g.setColor(new Color(246, 242, 251, 218));
            g.fillRoundRect(cx - unit * 2, centerY - unit / 2,
                    unit * 5, unit + 1, unit / 2, unit / 2);
            g.fillRoundRect(cx - unit / 2, centerY - unit * 2,
                    unit + 1, unit * 5, unit / 2, unit / 2);
            g.setColor(accent);
            g.fillOval(centerX + circleSize / 9, centerY - unit * 2,
                    unit * 2, unit * 2);
            g.fillOval(centerX + circleSize / 9 + unit * 2,
                    centerY + unit, unit * 2, unit * 2);

            Font glyphFont = new Font("Segoe UI", Font.BOLD,
                    Math.max(14, Math.min(hero ? 32 : 23, h / 5)));
            g.setFont(glyphFont);
            FontMetrics metrics = g.getFontMetrics();
            String code = code(console);
            int labelY = Math.min(h - 19, centerY + circleSize / 2 + (hero ? 31 : 25));
            g.setColor(Color.WHITE);
            g.drawString(code, (w - metrics.stringWidth(code)) / 2, labelY);

            g.setClip(null);
            g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 190));
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(1, 1, w - 3, h - 3, 16, 16);
        } finally {
            g.dispose();
        }
    }
}
