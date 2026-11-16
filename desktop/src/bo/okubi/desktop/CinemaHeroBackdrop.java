package bo.okubi.desktop;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;

/**
 * Cinematic, responsive hero panel. Uses a softly enlarged copy of the
 * selected game's downloaded cover as a decorative backdrop. The actual
 * cover remains sharp in OnlineCoverArtwork on the right.
 *
 * Rendering only: never touches ROMs, SRAM/RTC, settings or network.
 * A short Swing timer fades in artwork and stops when the panel is removed.
 */
public final class CinemaHeroBackdrop extends JPanel {
    private static final Color DARK = new Color(15, 11, 29);
    private static final Color INK = new Color(29, 18, 43);
    private static final int FRAME_MS = 25;
    private static final int FRAMES = 12;
    private final Color accent;
    private BufferedImage softArtwork;
    private float reveal;
    private Timer transition;

    public CinemaHeroBackdrop(Color accent) {
        super(new BorderLayout(20, 0));
        this.accent = accent == null ? new Color(245, 58, 155) : accent;
        setOpaque(false);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(15, 24, 15, 18));
    }

    public boolean hasBackdrop() {
        return softArtwork != null;
    }

    public boolean isTransitionRunning() {
        return transition != null && transition.isRunning();
    }

    /**
     * Called on Swing's EDT from OnlineCoverService. Downsampling first
     * gives a smooth, blurred atmospheric background without expensive
     * full-size convolution filters during every repaint.
     */
    public void showArtwork(BufferedImage cover) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> showArtwork(cover));
            return;
        }
        if (cover == null || cover.getWidth() <= 0 || cover.getHeight() <= 0)
            return;
        if (transition != null) transition.stop();

        // Downsample to 42 x 58 once; Swing paints this blurred preview,
        // not a high-resolution copy of the downloaded artwork.
        BufferedImage small = new BufferedImage(42, 58,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(cover, 0, 0, 42, 58, null);
        } finally {
            g.dispose();
        }
        softArtwork = small;
        reveal = 0f;
        transition = new Timer(FRAME_MS, event -> {
            reveal = Math.min(1f, reveal + 1f / FRAMES);
            repaint();
            if (reveal >= 1f) ((Timer) event.getSource()).stop();
        });
        transition.setCoalesce(true);
        transition.start();
        repaint();
    }

    /** Deterministic test-only frame without timing or network. */
    void showArtworkImmediatelyForTest(BufferedImage cover) {
        showArtwork(cover);
        if (transition != null) transition.stop();
        reveal = 1f;
        repaint();
    }

    @Override public void removeNotify() {
        if (transition != null) transition.stop();
        super.removeNotify();
    }

    @Override protected void paintComponent(Graphics original) {
        super.paintComponent(original);
        Graphics2D g = (Graphics2D) original.create();
        try {
            int width = getWidth(), height = getHeight();
            if (width < 8 || height < 8) return;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setClip(new RoundRectangle2D.Float(0, 0, width, height, 20, 20));

            g.setPaint(new GradientPaint(0, 0, new Color(45, 23, 65),
                    width, height, DARK));
            g.fillRect(0, 0, width, height);

            if (softArtwork != null && reveal > 0f) {
                int artWidth = Math.max(160, (int) (width * 0.66));
                int left = width - artWidth;
                g.setComposite(AlphaComposite.SrcOver.derive(0.52f * reveal));
                // Cropping the background is intentional. The full actual
                // boxart remains visible on the right in OnlineCoverArtwork.
                g.drawImage(softArtwork, left, -height / 5, artWidth,
                        (int) (height * 1.4), null);
                g.setComposite(AlphaComposite.SrcOver);
            }

            // Strong dark gradient keeps the left title and buttons readable.
            g.setPaint(new GradientPaint(0, 0, new Color(20, 14, 34, 240),
                    width * 0.75f, 0, new Color(20, 14, 34, 80)));
            g.fillRect(0, 0, width, height);

            g.setColor(new Color(accent.getRed(), accent.getGreen(),
                    accent.getBlue(), 45));
            for (int i = 0; i < 4; i++)
                g.drawOval(width - 240 + i * 25, -90 + i * 18, 280, 280);

            g.setColor(new Color(accent.getRed(), accent.getGreen(),
                    accent.getBlue(), 210));
            g.fillRoundRect(0, 0, 5, height, 5, 5);
            g.setColor(new Color(accent.getRed(), accent.getGreen(),
                    accent.getBlue(), 156));
            g.drawRoundRect(1, 1, width - 3, height - 3, 20, 20);
        } finally {
            g.dispose();
        }
    }
}
