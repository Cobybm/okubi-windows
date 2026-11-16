package bo.okubi.desktop;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.text.DateFormat;
import java.util.Date;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * GAME HUB PRO — a fully functional per-game detail sheet.
 * Presentation and user-invoked callbacks only: does not write to ROM,
 * cartridge saves or quick states. Screenshot content is supplied by a
 * background, read-only scanner.
 */
public final class DesktopGameHubPanel extends JPanel {
    private static final Color BG = new Color(17, 12, 29);
    private static final Color PANEL = new Color(31, 23, 45);
    private static final Color TEXT = new Color(244, 240, 250);
    private static final Color MUTED = new Color(180, 168, 195);
    private final DesktopLibrary.Game game;
    private final Color accent;
    private final JButton favoriteButton;
    private final JButton playButton;
    private final JButton backupButton;
    private final JButton closeButton;
    private final JPanel gallery;
    private final JLabel mediaInfo;
    private final JLabel saveInfo;
    private int screenshotCount;

    private static JLabel line(String content, int size, Color color, boolean strong) {
        JLabel label = new JLabel(content);
        label.setFont(new Font("Segoe UI", strong ? Font.BOLD : Font.PLAIN, size));
        label.setForeground(color);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static JPanel darkPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(PANEL);
        return panel;
    }

    public DesktopGameHubPanel(DesktopLibrary.Game game,
                               DesktopPlayStats.Snapshot stats,
                               OnlineCoverService covers,
                               Runnable play, Runnable favorite,
                               Runnable backups, Runnable close) {
        if (game == null || stats == null)
            throw new IllegalArgumentException("Game Hub requires a valid library game");
        this.game = game;
        this.accent = RetroArtwork.accent(game.console);
        setBackground(BG);
        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        JPanel content = darkPanel();
        content.setLayout(new BorderLayout(18, 0));
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(76, 54, 104), 1, true),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)));

        OnlineCoverArtwork cover = new OnlineCoverArtwork(game, true, covers);
        // Keep real boxart visible without forcing horizontal scrollbars
        // in the smallest supported 800px Windows desktop window.
        cover.setPreferredSize(new Dimension(165, 255));
        cover.setMinimumSize(new Dimension(145, 215));
        content.add(cover, BorderLayout.WEST);

        JPanel info = darkPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.add(line("OKUBI  /  GAME HUB PRO", 12, accent, true));
        info.add(Box.createVerticalStrut(11));
        JLabel name = line(game.title(), 23, TEXT, true);
        name.setToolTipText(game.title());
        info.add(name);
        info.add(Box.createVerticalStrut(7));
        info.add(line(game.console.label.toUpperCase()
                + (game.isAvailable() ? "  ·  LISTO PARA JUGAR" : "  ·  ROM NO ENCONTRADA"),
                11, game.isAvailable() ? new Color(137, 225, 181)
                        : new Color(255, 163, 136), true));
        info.add(Box.createVerticalStrut(15));

        JPanel metrics = darkPanel();
        metrics.setOpaque(false);
        metrics.setLayout(new GridLayout(1, 3, 8, 0));
        metrics.add(metric("TIEMPO REAL", DesktopPlayStats.duration(stats.seconds)));
        metrics.add(metric("SESIONES", Long.toString(stats.sessions)));
        String last = stats.lastSessionEpochMillis > 0
                ? DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(new Date(stats.lastSessionEpochMillis))
                : "Sin sesiones";
        metrics.add(metric("ÚLTIMA SESIÓN", last));
        metrics.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));
        metrics.setAlignmentX(LEFT_ALIGNMENT);
        info.add(metrics);
        info.add(Box.createVerticalStrut(14));

        saveInfo = line("GUARDADOS · Comprobando archivos en segundo plano...",
                12, MUTED, false);
        info.add(saveInfo);
        info.add(Box.createVerticalStrut(7));
        JLabel note = line("Las horas son reales; no incluyen emuladores externos.",
                11, MUTED, false);
        info.add(note);
        info.add(Box.createVerticalGlue());
        content.add(info, BorderLayout.CENTER);
        content.setPreferredSize(new Dimension(640, 295));
        add(content, BorderLayout.NORTH);

        JPanel screenshots = darkPanel();
        screenshots.setLayout(new BorderLayout(0, 10));
        screenshots.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(76, 54, 104), 1, true),
                BorderFactory.createEmptyBorder(11, 15, 12, 15)));
        screenshots.add(line("TUS CAPTURAS · GUARDADAS POR OKUBI", 12, accent, true),
                BorderLayout.NORTH);
        gallery = darkPanel();
        gallery.setOpaque(false);
        gallery.setLayout(new GridLayout(1, 3, 10, 0));
        mediaInfo = line("Buscando capturas locales, sin modificar archivos...",
                12, MUTED, false);
        gallery.add(mediaInfo);
        screenshots.add(gallery, BorderLayout.CENTER);
        screenshots.setPreferredSize(new Dimension(640, 148));
        add(screenshots, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(1, 4, 10, 0));
        actions.setOpaque(false);
        actions.setPreferredSize(new Dimension(640, 43));
        playButton = new CinemaButton("▶ JUGAR", true, play);
        playButton.setEnabled(game.isAvailable());
        playButton.setToolTipText("Iniciar " + game.title());
        favoriteButton = new CinemaButton("★ FAVORITO", false, favorite);
        updateFavorite(game.favorite);
        backupButton = new CinemaButton("⇄ RESPALDOS", false, backups);
        backupButton.setToolTipText("Abrir las herramientas de respaldo existentes");
        closeButton = new CinemaButton("✕ CERRAR", false, close);
        actions.add(playButton);
        actions.add(favoriteButton);
        actions.add(backupButton);
        actions.add(closeButton);
        JPanel footer = new JPanel(new BorderLayout(0, 7));
        footer.setOpaque(false);
        JLabel controls = line(
                "MANDO: A ELEGIR  ·  B VOLVER  ·  X FAVORITO  ·  LB/RB CAMBIAR ACCIÓN",
                11, MUTED, true);
        controls.setHorizontalAlignment(SwingConstants.CENTER);
        footer.add(controls, BorderLayout.NORTH);
        footer.add(actions, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        getAccessibleContext().setAccessibleName("Ficha de " + game.title());
    }

    private JPanel metric(String title, String value) {
        JPanel cell = new JPanel();
        cell.setBackground(new Color(45, 33, 62));
        cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
        cell.setBorder(BorderFactory.createEmptyBorder(11, 10, 9, 8));
        JLabel heading = line(title, 10, MUTED, true);
        cell.add(heading);
        cell.add(Box.createVerticalStrut(6));
        JLabel amount = line(value, 15, TEXT, true);
        amount.setToolTipText(value);
        cell.add(amount);
        return cell;
    }

    public void updateFavorite(boolean favorite) {
        favoriteButton.setText(favorite ? "★ FAVORITO" : "☆ AÑADIR FAVORITO");
        favoriteButton.setToolTipText(favorite ? "Quitar de favoritos"
                : "Añadir a favoritos");
    }

    public void showMedia(DesktopGameHubMedia.Snapshot media) {
        if (!javax.swing.SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("Game Hub UI updates must run on Swing EDT");
        if (media == null) return;
        saveInfo.setText("GUARDADOS · SRAM: " + (media.hasBattery() ? "detectado" : "sin archivo")
                + "   ·   RTC: " + (media.hasRtc() ? "detectado" : "sin archivo"));
        gallery.removeAll();
        screenshotCount = media.captures().size();
        if (media.captures().isEmpty()) {
            mediaInfo.setText(media.note());
            gallery.add(mediaInfo);
        } else {
            for (DesktopGameHubMedia.Capture capture : media.captures())
                gallery.add(new ScreenshotPreview(capture.image(),
                        capture.filename()));
            for (int i = media.captures().size(); i < 3; i++) {
                JLabel vacant = line("SIN CAPTURA", 11, MUTED, false);
                vacant.setHorizontalAlignment(SwingConstants.CENTER);
                gallery.add(vacant);
            }
        }
        gallery.revalidate();
        gallery.repaint();
    }

    public int screenshotCount() { return screenshotCount; }
    public boolean canPlay() { return playButton.isEnabled(); }
    public boolean isFavoriteActionEnabled() { return favoriteButton.isEnabled(); }
    public String saveStatus() { return saveInfo.getText(); }
    public String favoriteLabel() { return favoriteButton.getText(); }
    public JButton playAction() { return playButton; }
    public JButton favoriteAction() { return favoriteButton; }
    public JButton backupAction() { return backupButton; }
    public JButton closeAction() { return closeButton; }

    private static final class ScreenshotPreview extends JPanel {
        private final BufferedImage image;
        ScreenshotPreview(BufferedImage image, String filename) {
            this.image = image;
            setOpaque(false);
            setToolTipText(filename);
            getAccessibleContext().setAccessibleName("Captura local: " + filename);
        }
        @Override protected void paintComponent(Graphics original) {
            super.paintComponent(original);
            Graphics2D g = (Graphics2D) original.create();
            try {
                int w = getWidth(), h = getHeight();
                if (w < 5 || h < 5 || image == null) return;
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                double scale = Math.min((w - 8) / (double) image.getWidth(),
                        (h - 8) / (double) image.getHeight());
                int dw = Math.max(1, (int)(image.getWidth() * scale));
                int dh = Math.max(1, (int)(image.getHeight() * scale));
                int dx = (w - dw) / 2, dy = (h - dh) / 2;
                g.drawImage(image, dx, dy, dw, dh, null);
                g.setColor(new Color(157, 91, 202));
                g.setStroke(new BasicStroke(1.1f));
                g.drawRoundRect(dx, dy, dw, dh, 9, 9);
            } finally { g.dispose(); }
        }
    }
}
