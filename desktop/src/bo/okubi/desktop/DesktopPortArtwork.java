package bo.okubi.desktop;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;

/** Asynchronous PC artwork with original CINEMA fallback; never paints network I/O. */
public final class DesktopPortArtwork extends JPanel {
    private static final Color ACCENT = new Color(245, 58, 155);
    private static final Color BG = new Color(19, 14, 34);
    private BufferedImage image;
    private String state = "PORT PC";

    public DesktopPortArtwork(DesktopPortArtworkService service,
                              DesktopPortArtworkService.Ref ref,
                              int width, int height) {
        setOpaque(false);
        setPreferredSize(new Dimension(width, height));
        setMinimumSize(new Dimension(Math.min(78, width), Math.min(94, height)));
        // A long Swing tooltip covered DESCARGAS and VINCULAR .EXE in the
        // user's 77A screenshot; metadata belongs in the normal card text.
        getAccessibleContext().setAccessibleName("Imagen de " + ref.fullName());
        getAccessibleContext().setAccessibleDescription(
                "Vista previa del proyecto; podría mostrar una tarjeta GitHub");
        if (service != null) service.load(ref, this::showImage);
    }

    public void showImage(BufferedImage next) {
        if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
            javax.swing.SwingUtilities.invokeLater(() -> showImage(next));
            return;
        }
        image = next;
        state = next == null ? "PORT PC" : "GITHUB";
        repaint();
    }

    public boolean hasImage() { return image != null; }

    @Override protected void paintComponent(Graphics source) {
        super.paintComponent(source);
        Graphics2D g = (Graphics2D) source.create();
        try {
            int w = getWidth(), h = getHeight();
            if (w < 10 || h < 10) return;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setClip(new java.awt.geom.RoundRectangle2D.Double(
                    0, 0, w - 1, h - 1, 14, 14));
            g.setPaint(new GradientPaint(0, 0, new Color(65, 35, 85),
                    w, h, BG));
            g.fillRect(0, 0, w, h);
            if (image != null) {
                // Banner aspect ratio: fill landscape screenshots; preserve
                // portrait game logos to avoid cutting their main subject.
                boolean landscape = image.getWidth() * 10L >= image.getHeight() * 13L;
                double scale = landscape
                        ? Math.max((w - 4.) / image.getWidth(),
                                (h - 4.) / image.getHeight())
                        : Math.min((w - 8.) / image.getWidth(),
                                (h - 8.) / image.getHeight());
                int rw = Math.max(1, (int) Math.ceil(image.getWidth() * scale));
                int rh = Math.max(1, (int) Math.ceil(image.getHeight() * scale));
                g.drawImage(image, (w - rw) / 2, (h - rh) / 2, rw, rh, null);
            } else {
                g.setFont(new Font("Segoe UI", Font.BOLD, Math.max(17, w / 5)));
                g.setColor(new Color(158, 127, 206));
                String symbol = "PC";
                int x = Math.max(4, (w - g.getFontMetrics().stringWidth(symbol)) / 2);
                g.drawString(symbol, x, h / 2 + 8);
                g.setColor(new Color(245, 58, 155, 80));
                g.drawOval(w / 2 - 34, h / 2 - 35, 68, 68);
            }
            g.setClip(null);
            g.setColor(ACCENT);
            g.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);
            g.setFont(new Font("Segoe UI", Font.BOLD, 10));
            String label = state;
            int labelWidth = g.getFontMetrics().stringWidth(label) + 14;
            g.setColor(new Color(20, 14, 32, 225));
            g.fillRoundRect(7, h - 25, labelWidth, 18, 9, 9);
            g.setColor(Color.WHITE);
            g.drawString(label, 14, h - 12);
        } finally { g.dispose(); }
    }
}
