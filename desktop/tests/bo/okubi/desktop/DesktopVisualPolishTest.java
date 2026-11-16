package bo.okubi.desktop;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Screenshot-regression tests for missing-glyph boxes on stock Windows. */
public final class DesktopVisualPolishTest {
    private static void yes(boolean valid, String why) {
        if (!valid) throw new AssertionError(why);
    }

    private static boolean hasRenderedGlyph(BufferedImage image) {
        // The same button is drawn with and without an icon; any nonempty,
        // non-native graphic path must render pixels in the reserved icon area.
        int bright = 0;
        for (int y = 6; y < image.getHeight() - 6; y++) {
            for (int x = 7; x < 30; x++) {
                int rgb = image.getRGB(x, y);
                Color pixel = new Color(rgb, true);
                if (pixel.getRed() > 225 && pixel.getGreen() > 180
                        && pixel.getBlue() > 180 && pixel.getAlpha() > 200) bright++;
            }
        }
        return bright > 4;
    }

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeAndWait(() -> {
            AtomicInteger pressed = new AtomicInteger();
            String[] samples = {
                "🎮   Todos los juegos", "★   Favoritos", "☆",
                "◷   Jugados recientemente", "🔥   Más jugados",
                "▣   Mis estadísticas", "＋   Añadir juegos",
                "▤   Importar carpeta", "⚙   Motores incluidos",
                "⇄   Cross Save local", "☁   Cloud Save Drive",
                "◈   Partidas y respaldos", "⛶ MODO CONSOLA",
                "◱ VENTANA", "⚡ Juego sorpresa", "▶ JUGAR",
                "☰ MENÚ / ESC", "✕ VOLVER"
            };
            for (String original : samples) {
                CinemaButton button = new CinemaButton(original, false,
                        pressed::incrementAndGet);
                String display = button.getText();
                yes(!display.contains("\uFFFD") && !display.contains("\u25A1"),
                        "No replacement/square glyphs in text: " + original);
                yes(!display.contains("🎮") && !display.contains("★")
                        && !display.contains("☁") && !display.contains("⛶"),
                        "No unsupported emoji remains in painted title: " + original);
                yes(CinemaIcons.forLabel(original) != CinemaIcons.Kind.NONE,
                        "Emoji has a Java2D icon: " + original);
                yes(button.getAccessibleContext().getAccessibleName() != null,
                        "Accessible button name: " + original);
                button.setSize(208, 46);
                BufferedImage bitmap = new BufferedImage(208, 46,
                        BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = bitmap.createGraphics();
                button.paint(g);
                g.dispose();
                yes(bitmap.getRGB(90, 20) != 0,
                        "Button always paints its fill: " + original);
                button.doClick();
            }
            yes(pressed.get() == samples.length,
                    "Every icon action still triggers its actual callback");

            CinemaButton star = new CinemaButton("☆", false,
                    pressed::incrementAndGet);
            yes(star.getText().isBlank(), "Star-only button draws vector icon");
            yes(star.getAccessibleContext().getAccessibleName().contains("favorito"),
                    "Favorite-only control has an accessible description");
            star.setSize(47, 34);
            BufferedImage icon = new BufferedImage(47, 34,
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = icon.createGraphics();
            star.paint(graphics);
            graphics.dispose();
            yes(icon.getRGB(23, 15) != 0, "Favorite icon visibly drawn");

            star.setText("★");
            yes(star.getText().isEmpty(), "Dynamic icon stays font-independent");
            star.setText("◱ VENTANA");
            yes(star.getText().equals("VENTANA"),
                    "Console-mode toggle labels remain readable");

            // These are not graphics: the emulator video and existing data
            // remain independent of the theme and icon engine.
            yes(CinemaIcons.forLabel("F5 Guardar") == CinemaIcons.Kind.NONE,
                    "F5 controls don't get a decorative symbol");
            yes(CinemaIcons.withoutEmoji("Pokémon Esmeralda")
                            .equals("Pokémon Esmeralda"),
                    "Game titles keep diacritics and names");
            System.out.println("DesktopVisualPolishTest: PASS");
        });
    }
}
