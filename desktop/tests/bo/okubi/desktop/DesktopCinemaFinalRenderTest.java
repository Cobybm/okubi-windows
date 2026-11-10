package bo.okubi.desktop;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Visual regression at a small laptop window. Swing only: no native ROM core,
 * network, user files, gamepad, battery state or installed applications.
 */
public final class DesktopCinemaFinalRenderTest {
    private static void check(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason);
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("okubi-cinema-final-");
        Path rom = root.resolve("Pokémon Esmeralda - Un título realmente largo.gba");
        byte[] original = new byte[]{20, 30, 40, 50, 60};
        Files.write(rom, original);
        DesktopLibrary library = new DesktopLibrary(root.resolve("library.properties"));
        library.addGame(rom);
        DesktopLibrary.Game game = library.games().get(0);
        DesktopPlayStats statistics = new DesktopPlayStats(root.resolve("stats.properties"));

        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeAndWait(() -> {
            DesktopGameHubPanel panel = new DesktopGameHubPanel(game,
                    statistics.get(game.console, game.rom), null,
                    () -> { }, () -> { }, () -> { }, () -> { });
            JScrollPane scroll = new JScrollPane(panel);
            scroll.setSize(735, 425);
            scroll.doLayout();
            check(panel.getPreferredSize().width <= scroll.getViewport().getExtentSize().width,
                    "The Game Hub does not need horizontal scrolling at 800px.");
            check(panel.playAction().isEnabled(), "Play action still usable.");
            check(panel.favoriteAction().isEnabled(), "Favorite action still usable.");
            check(panel.backupAction().isEnabled(), "Backup action still usable.");
            check(panel.closeAction().isEnabled(), "Close action still usable.");
            check(panel.getAccessibleContext().getAccessibleName()
                    .contains("Pokémon Esmeralda"),
                    "Full game title remains accessible despite compact layout.");

            CinemaButton small = new CinemaButton(
                    "VER FICHA DE POKÉMON 遊 🎮 Y JUGAR", true, () -> { });
            small.setSize(120, 38);
            small.getModel().setRollover(true);
            check(small.getModel().isRollover(),
                    "Controller focus highlight may be driven by the rollover model.");
            CinemaCategoryTab tab = new CinemaCategoryTab(
                    "MÁS JUGADOS", "MAS_JUGADOS", () -> { });
            tab.setSize(70, 38);
            tab.getModel().setRollover(true);
            tab.setActiveCategory(true);
            BufferedImage image = new BufferedImage(400, 110,
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try {
                g.translate(10, 8);
                small.paint(g);
                g.translate(150, 0);
                tab.paint(g);
            } finally { g.dispose(); }
            check(new Color(image.getRGB(25, 25), true).getAlpha() > 0,
                    "CINEMA Button custom paint remains visible at 120px.");
            check(new Color(image.getRGB(175, 25), true).getAlpha() > 0,
                    "Compact category tab remains rendered.");
            check(small.getAccessibleContext().getAccessibleName().length() > 10,
                    "Clipping is visual only; accessible full action remains.");
            check(tab.getAccessibleContext().getAccessibleName().contains("MÁS JUGADOS"),
                    "Category screen-reader name stays complete.");
            System.out.println("DesktopCinemaFinalRenderTest: PASS");
        });
        check(java.util.Arrays.equals(original, Files.readAllBytes(rom)),
                "Visual and layout tests cannot mutate ROM bytes.");
    }
}
