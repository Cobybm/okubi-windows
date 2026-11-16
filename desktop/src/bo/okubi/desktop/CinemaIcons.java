package bo.okubi.desktop;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

/**
 * Font-independent vector iconography for Windows 10/11 without bundled fonts.
 * The old leading emojis displayed as little boxes on the user's computer.
 * These glyphs are original Java2D paths, not text or copyrighted assets.
 */
public final class CinemaIcons {
    public enum Kind {
        NONE, GAMEPAD, STAR, HISTORY, FIRE, CHART, ADD, FOLDER, SETTINGS,
        SYNC, CLOUD, BACKUP, FULLSCREEN, SHUFFLE, PLAY, MENU, CLOSE
    }

    private CinemaIcons() { }

    public static Kind forLabel(String label) {
        if (label == null || label.isEmpty()) return Kind.NONE;
        if (label.startsWith("🎮")) return Kind.GAMEPAD;
        if (label.startsWith("★") || label.startsWith("☆")) return Kind.STAR;
        if (label.startsWith("◷")) return Kind.HISTORY;
        if (label.startsWith("🔥")) return Kind.FIRE;
        if (label.startsWith("▣")) return Kind.CHART;
        if (label.startsWith("＋") || label.startsWith("+")) return Kind.ADD;
        if (label.startsWith("▤")) return Kind.FOLDER;
        if (label.startsWith("⚙")) return Kind.SETTINGS;
        if (label.startsWith("⇄")) return Kind.SYNC;
        if (label.startsWith("☁")) return Kind.CLOUD;
        if (label.startsWith("◈")) return Kind.BACKUP;
        if (label.startsWith("⛶") || label.startsWith("◱")) return Kind.FULLSCREEN;
        if (label.startsWith("⚡")) return Kind.SHUFFLE;
        if (label.startsWith("▶")) return Kind.PLAY;
        if (label.startsWith("☰")) return Kind.MENU;
        if (label.startsWith("✕")) return Kind.CLOSE;
        return Kind.NONE;
    }

    public static String withoutEmoji(String label) {
        if (label == null) return "";
        if (forLabel(label) == Kind.NONE) return label;
        int offset = label.startsWith("🎮") || label.startsWith("🔥") ? 2 : 1;
        return label.substring(offset).stripLeading();
    }

    public static String accessibleName(Kind kind, String label) {
        if (!label.isBlank()) return label;
        return switch (kind) {
            case STAR -> "Marcar o quitar favorito";
            case FULLSCREEN -> "Pantalla completa";
            case PLAY -> "Jugar";
            case MENU -> "Abrir menú";
            case CLOSE -> "Cerrar menú";
            default -> "Acción de OKUBI";
        };
    }

    /** Draw at a logical 20x20 resolution. Caller owns centering and scaling. */
    public static void paint(Graphics2D canvas, Kind kind, int x, int y,
                             int iconSize, Color color, boolean filled) {
        if (kind == Kind.NONE) return;
        Graphics2D g = (Graphics2D)canvas.create();
        try {
            g.translate(x, y);
            g.scale(iconSize / 20.0, iconSize / 20.0);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.setStroke(new BasicStroke(1.75f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            switch (kind) {
                case GAMEPAD -> {
                    g.drawRoundRect(1, 5, 18, 11, 5, 5);
                    line(g, 6, 8, 6, 13);
                    line(g, 3.5, 10.5, 8.5, 10.5);
                    g.fillOval(12, 8, 3, 3);
                    g.fillOval(15, 11, 3, 3);
                }
                case STAR -> {
                    Path2D star = new Path2D.Double();
                    for (int i = 0; i < 10; i++) {
                        double angle = -Math.PI / 2 + i * Math.PI / 5;
                        double radius = i % 2 == 0 ? 8.2 : 3.6;
                        double px = 10 + radius * Math.cos(angle);
                        double py = 10 + radius * Math.sin(angle);
                        if (i == 0) star.moveTo(px, py); else star.lineTo(px, py);
                    }
                    star.closePath();
                    if (filled) g.fill(star); else g.draw(star);
                }
                case HISTORY -> {
                    g.drawArc(2, 2, 16, 16, 28, 306);
                    line(g, 10, 5, 10, 10);
                    line(g, 10, 10, 14, 12);
                    line(g, 1, 4, 2, 9);
                    line(g, 1, 4, 6, 5);
                }
                case FIRE -> {
                    Path2D flame = new Path2D.Double();
                    flame.moveTo(11, 1);
                    flame.curveTo(15, 5, 12, 8, 16, 10);
                    flame.curveTo(21, 15, 16, 19, 10, 19);
                    flame.curveTo(2, 19, 0, 13, 6, 8);
                    flame.curveTo(6, 12, 9, 10, 11, 1);
                    flame.closePath();
                    g.draw(flame);
                }
                case CHART -> {
                    line(g, 3, 17, 17, 17);
                    g.fillRoundRect(4, 10, 3, 7, 1, 1);
                    g.fillRoundRect(9, 6, 3, 11, 1, 1);
                    g.fillRoundRect(14, 3, 3, 14, 1, 1);
                }
                case ADD -> {
                    line(g, 10, 3, 10, 17);
                    line(g, 3, 10, 17, 10);
                }
                case FOLDER -> {
                    Path2D folder = new Path2D.Double();
                    folder.moveTo(2, 6);
                    folder.lineTo(2, 17);
                    folder.lineTo(18, 17);
                    folder.lineTo(18, 7);
                    folder.lineTo(9, 7);
                    folder.lineTo(7, 5);
                    folder.lineTo(2, 5);
                    folder.closePath();
                    g.draw(folder);
                }
                case SETTINGS -> {
                    g.drawOval(3, 3, 14, 14);
                    g.drawOval(7, 7, 6, 6);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI * i / 4;
                        line(g, 10 + 7.5 * Math.cos(a), 10 + 7.5 * Math.sin(a),
                                10 + 9.1 * Math.cos(a), 10 + 9.1 * Math.sin(a));
                    }
                }
                case SYNC -> {
                    line(g, 2, 6, 16, 6);
                    line(g, 12.5, 2.5, 16, 6);
                    line(g, 16, 6, 12.5, 9.5);
                    line(g, 18, 14, 4, 14);
                    line(g, 7.5, 10.5, 4, 14);
                    line(g, 4, 14, 7.5, 17.5);
                }
                case CLOUD -> {
                    Path2D cloud = new Path2D.Double();
                    cloud.moveTo(5, 16);
                    cloud.curveTo(0, 16, 0, 10, 5, 10);
                    cloud.curveTo(5, 3, 14, 2, 16, 9);
                    cloud.curveTo(22, 9, 21, 16, 16, 16);
                    cloud.closePath();
                    g.draw(cloud);
                }
                case BACKUP -> {
                    g.drawRoundRect(3, 2, 14, 16, 2, 2);
                    line(g, 6, 7, 14, 7);
                    line(g, 6, 11, 14, 11);
                    line(g, 6, 15, 11, 15);
                }
                case FULLSCREEN -> {
                    line(g, 2, 7, 2, 2);
                    line(g, 2, 2, 7, 2);
                    line(g, 13, 2, 18, 2);
                    line(g, 18, 2, 18, 7);
                    line(g, 2, 13, 2, 18);
                    line(g, 2, 18, 7, 18);
                    line(g, 13, 18, 18, 18);
                    line(g, 18, 18, 18, 13);
                }
                case SHUFFLE -> {
                    line(g, 2, 5, 5, 5);
                    line(g, 5, 5, 15, 15);
                    line(g, 13, 15, 18, 15);
                    line(g, 14, 11, 18, 15);
                    line(g, 18, 15, 14, 19);
                    line(g, 2, 15, 5, 15);
                    line(g, 5, 15, 15, 5);
                    line(g, 13, 1, 18, 5);
                    line(g, 18, 5, 14, 9);
                }
                case PLAY -> {
                    Path2D play = new Path2D.Double();
                    play.moveTo(5, 3);
                    play.lineTo(17, 10);
                    play.lineTo(5, 17);
                    play.closePath();
                    g.fill(play);
                }
                case MENU -> {
                    line(g, 3, 5, 17, 5);
                    line(g, 3, 10, 17, 10);
                    line(g, 3, 15, 17, 15);
                }
                case CLOSE -> {
                    line(g, 4, 4, 16, 16);
                    line(g, 16, 4, 4, 16);
                }
                default -> { }
            }
        } finally { g.dispose(); }
    }

    private static void line(Graphics2D g, double x1, double y1,
                             double x2, double y2) {
        g.draw(new java.awt.geom.Line2D.Double(x1, y1, x2, y2));
    }
}
