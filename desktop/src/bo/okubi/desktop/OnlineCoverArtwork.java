package bo.okubi.desktop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;

/**
 * Real cover art when available, procedural RetroArtwork otherwise.
 * Network/disk IO is delegated to OnlineCoverService, never Swing painting.
 */
public final class OnlineCoverArtwork extends JPanel {
    private final RetroArtwork placeholder;
    private BufferedImage image;

    public OnlineCoverArtwork(DesktopLibrary.Game game, boolean hero,
                              OnlineCoverService service) {
        setOpaque(false);
        setLayout(new BorderLayout());
        setPreferredSize(hero ? new Dimension(185, 150)
                : new Dimension(106, 155));
        setMinimumSize(hero ? new Dimension(125, 130)
                : new Dimension(80, 110));
        String title = game.title();
        setToolTipText("Carátula de " + title
                + " · Descargada de Libretro cuando hay coincidencia");
        getAccessibleContext().setAccessibleName("Portada del juego " + title);

        placeholder = new RetroArtwork(game.console, title, hero);
        add(placeholder, BorderLayout.CENTER);
        if (service != null) service.load(game.console, game.rom, title, loaded -> {
            if (loaded == null) return;
            image = loaded;
            placeholder.setVisible(false);
            revalidate();
            repaint();
        });
    }

    /** For image regression tests without network access. */
    void showCoverForTest(BufferedImage picture) {
        if (picture == null) return;
        image = picture;
        placeholder.setVisible(false);
        revalidate();
        repaint();
    }

    public boolean hasCover() {
        return image != null;
    }

    @Override protected void paintComponent(Graphics original) {
        super.paintComponent(original);
        if (image == null) return;
        Graphics2D g = (Graphics2D) original.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int width = getWidth(), height = getHeight();
            if (width < 8 || height < 8) return;
            g.setColor(new Color(13, 12, 26));
            g.fillRoundRect(0, 0, width, height, 16, 16);
            int inset = 5;
            int availableW = Math.max(1, width - 2 * inset);
            int availableH = Math.max(1, height - 2 * inset);
            double scale = Math.min(availableW / (double) image.getWidth(),
                    availableH / (double) image.getHeight());
            int drawW = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int drawH = Math.max(1, (int) Math.round(image.getHeight() * scale));
            int dx = (width - drawW) / 2, dy = (height - drawH) / 2;
            g.setClip(new java.awt.geom.RoundRectangle2D.Double(
                    1, 1, width - 2, height - 2, 13, 13));
            // Preserve boxart proportions; never crop text/logos on the cover.
            g.drawImage(image, dx, dy, drawW, drawH, null);
            g.setClip(null);
            g.setColor(new Color(215, 83, 179, 135));
            g.drawRoundRect(1, 1, width - 3, height - 3, 14, 14);
        } finally { g.dispose(); }
    }
}
