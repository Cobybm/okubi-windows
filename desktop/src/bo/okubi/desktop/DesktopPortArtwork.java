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
    private final DesktopPortArtworkService service;
    private final DesktopPortArtworkService.Ref ref;
    private final int imageCount;
    private final boolean fitEntireImage;
    private volatile int imageIndex;
    private volatile long requestVersion;
    private javax.swing.JLabel counter;

    public DesktopPortArtwork(DesktopPortArtworkService service,
                              DesktopPortArtworkService.Ref ref,
                              int width, int height) {
        this(service, ref, width, height, 0, false);
    }

    /** Large CINEMA viewer preserves the whole screenshot and current slide. */
    DesktopPortArtwork(DesktopPortArtworkService service,
                       DesktopPortArtworkService.Ref ref,
                       int width, int height, int initialIndex,
                       boolean fitEntireImage) {
        this.service = service;
        this.ref = ref;
        this.imageCount = DesktopOfficialPortArtwork.gallery(ref).size();
        this.fitEntireImage = fitEntireImage;
        setOpaque(false);
        setPreferredSize(new Dimension(width, height));
        setMinimumSize(new Dimension(Math.min(78, width), Math.min(94, height)));
        // Small CINEMA library tiles show the primary cover only. Large PORTS
        // cards get accessible, explicitly requested, lazy gallery controls.
        if (service != null && imageCount > 1 && width >= 220) {
            setLayout(new java.awt.BorderLayout());
            javax.swing.JPanel controls = new javax.swing.JPanel(
                    new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 7, 4));
            controls.setOpaque(false);
            javax.swing.JButton previous = arrow("◀", "Captura anterior", () -> moveGallery(-1));
            javax.swing.JButton next = arrow("▶", "Captura siguiente", () -> moveGallery(1));
            counter = new javax.swing.JLabel("1 / " + imageCount);
            counter.setFont(new Font("Segoe UI", Font.BOLD, 11));
            counter.setForeground(Color.WHITE);
            counter.setOpaque(true);
            counter.setBackground(new Color(28, 16, 45));
            counter.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 7, 4, 7));
            counter.getAccessibleContext().setAccessibleName(
                    "Captura 1 de " + imageCount);
            controls.add(previous);
            controls.add(counter);
            controls.add(next);
            add(controls, java.awt.BorderLayout.SOUTH);
        }
        // No long tooltip that could hide DESCARGAS / VINCULAR .EXE.
        getAccessibleContext().setAccessibleName("Imagen de " + ref.fullName());
        getAccessibleContext().setAccessibleDescription(
                imageCount > 1 ? "Galería de capturas verificadas por proyecto"
                        : "Imagen verificada del proyecto o fondo CINEMA");
        if (service != null) requestImage(
                Math.max(0, Math.min(imageCount - 1, initialIndex)));
    }

    /** Enlarges the image on click/keyboard, without adding cramped card buttons. */
    void enableViewer(Runnable open) {
        if (open == null) return;
        setFocusable(true);
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        getAccessibleContext().setAccessibleDescription(
                "Activar para ampliar imagen del proyecto; Enter o espacio");
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent event) {
                if (javax.swing.SwingUtilities.isLeftMouseButton(event))
                    open.run();
            }
        });
        getInputMap(WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke("ENTER"),
                "open-port-gallery");
        getInputMap(WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke("SPACE"),
                "open-port-gallery");
        getActionMap().put("open-port-gallery", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                open.run();
            }
        });
    }

    private static javax.swing.JButton arrow(String symbol, String description,
                                              Runnable action) {
        javax.swing.JButton button = new javax.swing.JButton(symbol);
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setBackground(new Color(28, 16, 45));
        button.setForeground(Color.WHITE);
        button.setOpaque(true);
        button.setFocusPainted(true);
        button.setPreferredSize(new Dimension(32, 26));
        button.setBorder(javax.swing.BorderFactory.createLineBorder(
                new Color(245, 58, 155), 1));
        button.getAccessibleContext().setAccessibleName(description);
        button.addActionListener(event -> action.run());
        return button;
    }

    /** Navigate only on click; skip obsolete results from earlier async requests. */
    void moveGallery(int delta) {
        if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
            javax.swing.SwingUtilities.invokeLater(() -> moveGallery(delta));
            return;
        }
        if (service == null || imageCount < 2) return;
        requestImage(Math.floorMod(imageIndex + delta, imageCount));
    }

    private void requestImage(int index) {
        imageIndex = index;
        long ticket = ++requestVersion;
        image = null;
        state = "PORT PC";
        if (counter != null) {
            counter.setText((index + 1) + " / " + imageCount);
            counter.getAccessibleContext().setAccessibleName(
                    "Captura " + (index + 1) + " de " + imageCount);
        }
        repaint();
        service.loadGallery(ref, index, result -> {
            if (ticket != requestVersion || imageIndex != index) return;
            image = result;
            state = result == null ? "PORT PC" : "";
            repaint();
        });
    }

    /** Manual artwork changes always return to the personal/primary cover. */
    public void showImage(BufferedImage next) {
        if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
            javax.swing.SwingUtilities.invokeLater(() -> showImage(next));
            return;
        }
        ++requestVersion;
        imageIndex = 0;
        if (counter != null) {
            counter.setText("1 / " + imageCount);
            counter.getAccessibleContext().setAccessibleName(
                    "Captura 1 de " + imageCount);
        }
        image = next;
        state = next == null ? "PORT PC" : "";
        repaint();
    }

    int gallerySize() { return imageCount; }
    int selectedImageIndex() { return imageIndex; }
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
                int visibleHeight = fitEntireImage && counter != null
                        ? Math.max(12, h - 45) : h;
                double scale = fitEntireImage
                        ? Math.min((w - 18.) / image.getWidth(),
                                (visibleHeight - 16.) / image.getHeight())
                        : landscape
                            ? Math.max((w - 4.) / image.getWidth(),
                                    (h - 4.) / image.getHeight())
                            : Math.min((w - 8.) / image.getWidth(),
                                    (h - 8.) / image.getHeight());
                int rw = Math.max(1, (int) Math.ceil(image.getWidth() * scale));
                int rh = Math.max(1, (int) Math.ceil(image.getHeight() * scale));
                g.drawImage(image, (w - rw) / 2,
                        (visibleHeight - rh) / 2, rw, rh, null);
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
            // The previous GITHUB badge obscured real game artwork and
            // incorrectly labelled upstream screenshots as social cards.
            // A fallback badge remains only when no picture was available.
            if (image == null) {
                g.setFont(new Font("Segoe UI", Font.BOLD, 10));
                int labelWidth = g.getFontMetrics().stringWidth(state) + 14;
                g.setColor(new Color(20, 14, 32, 225));
                g.fillRoundRect(7, h - 25, labelWidth, 18, 9, 9);
                g.setColor(Color.WHITE);
                g.drawString(state, 14, h - 12);
            }
        } finally { g.dispose(); }
    }
}
