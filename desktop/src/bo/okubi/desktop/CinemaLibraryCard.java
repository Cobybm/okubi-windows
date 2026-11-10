package bo.okubi.desktop;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Console-style game tile with a highly visible selection state.
 * Purely visual: no ROM access, native input, saves or background worker.
 */
public final class CinemaLibraryCard extends JPanel {
    private final Color accent;
    private final String gameTitle;
    private boolean selected;
    private boolean hovered;
    private float hoverGlow;
    private Timer hoverAnimation;

    public CinemaLibraryCard(String gameTitle, Color accent) {
        this.gameTitle = gameTitle == null ? "" : gameTitle;
        this.accent = accent == null ? new Color(245, 58, 155) : accent;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(13, 13, 13, 13));
        getAccessibleContext().setAccessibleName(this.gameTitle);
        getAccessibleContext().setAccessibleDescription(
                "Tarjeta de juego. Usa las flechas o cruceta para seleccionar y A o Enter para jugar.");
        setToolTipText(this.gameTitle + " · Doble clic para jugar");
        setSelected(false);
    }

    public boolean isSelectedGame() { return selected; }
    public boolean isHoveredGame() { return hovered; }
    public boolean isHoverTransitionRunning() {
        return hoverAnimation != null && hoverAnimation.isRunning();
    }

    public void setSelected(boolean value) {
        selected = value;
        Color outline = selected ? accent : new Color(87, 68, 108);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(outline, selected ? 3 : 1, true),
                BorderFactory.createEmptyBorder(
                        selected ? 10 : 12, selected ? 10 : 12,
                        selected ? 10 : 12, selected ? 10 : 12)));
        getAccessibleContext().setAccessibleDescription(selected
                ? "Juego seleccionado: " + gameTitle + ". A o Enter para jugar; X o F para favorito."
                : "Juego disponible: " + gameTitle + ". Flechas para seleccionar.");
        repaint();
    }

    /** Gentle six-frame focus glow without changing selection or game input. */
    public void setHovered(boolean value) {
        if (hovered == value) return;
        hovered = value;
        if (hoverAnimation != null) hoverAnimation.stop();
        hoverAnimation = new Timer(28, event -> {
            hoverGlow = Math.max(0f, Math.min(1f,
                    hoverGlow + (hovered ? 0.20f : -0.20f)));
            repaint();
            if ((hovered && hoverGlow >= 1f)
                    || (!hovered && hoverGlow <= 0f))
                ((Timer) event.getSource()).stop();
        });
        hoverAnimation.setCoalesce(true);
        hoverAnimation.start();
        repaint();
    }

    @Override public void removeNotify() {
        if (hoverAnimation != null) hoverAnimation.stop();
        super.removeNotify();
    }

    @Override protected void paintComponent(Graphics original) {
        super.paintComponent(original);
        Graphics2D g = (Graphics2D) original.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (w < 8 || h < 8) return;
            Color left = selected ? new Color(57, 35, 76)
                    : new Color(29 + Math.round(14 * hoverGlow),
                            23 + Math.round(9 * hoverGlow),
                            41 + Math.round(17 * hoverGlow));
            Color right = selected ? new Color(35, 26, 54)
                    : new Color(20, 17, 34);
            g.setPaint(new GradientPaint(0, 0, left, w, h, right));
            g.fillRoundRect(1, 1, w - 3, h - 3, 18, 18);
            if (selected) {
                g.setColor(new Color(accent.getRed(), accent.getGreen(),
                        accent.getBlue(), 55));
                g.fillRoundRect(4, 4, Math.max(2, w / 3), h - 8, 16, 16);
                g.setColor(accent);
                g.fillRoundRect(4, 10, 4, Math.max(1, h - 20), 4, 4);
                g.setStroke(new BasicStroke(2.0f));
                g.drawRoundRect(3, 3, w - 7, h - 7, 16, 16);
            } else if (hoverGlow > 0f) {
                g.setColor(new Color(accent.getRed(), accent.getGreen(),
                        accent.getBlue(), Math.round(125 * hoverGlow)));
                g.setStroke(new BasicStroke(1.4f));
                g.drawRoundRect(3, 3, w - 7, h - 7, 16, 16);
            }
        } finally {
            g.dispose();
        }
    }
}
