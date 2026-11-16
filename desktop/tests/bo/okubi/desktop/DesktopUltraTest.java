package bo.okubi.desktop;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Tests UI artwork and non-destructive quick states without requiring real ROMs. */
public final class DesktopUltraTest {
    private static void yes(boolean correct, String why) {
        if (!correct) throw new AssertionError(why);
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("okubi-ultra-test-");
        Path game = root.resolve("Mi copia Esmeralda.gba");
        Files.write(game, new byte[32 * 1024]);
        Path coreA = root.resolve("core-a.dll");
        Path coreB = root.resolve("core-b.dll");
        Files.write(coreA, new byte[]{1, 2, 3});
        Files.write(coreB, new byte[]{9, 8, 7});
        DesktopSaves saves = new DesktopSaves(root.resolve("saves"),
                DesktopLibrary.Console.GBA, game);

        byte[] original = new byte[256];
        byte[] updated = new byte[256];
        Arrays.fill(original, (byte)41);
        Arrays.fill(updated, (byte)55);
        saves.saveState(coreA, original);
        yes(Arrays.equals(saves.readState(coreA), original),
                "Quick save must be exactly readable");
        saves.saveState(coreA, updated);
        yes(Arrays.equals(saves.readState(coreA), updated),
                "F5 replaces slot only after backing up previous bytes");
        Path stateFile = saves.stateFile(coreA);
        yes(Arrays.equals(Files.readAllBytes(stateFile.resolveSibling(
                        stateFile.getFileName() + ".previous")), original),
                "Previous F5 slot retained");
        try {
            saves.readState(coreB);
            throw new AssertionError("Different Libretro core must not load old snapshot");
        } catch (java.io.IOException expected) { }
        try {
            saves.saveState(coreA, new byte[0]);
            throw new AssertionError("Empty state must be rejected");
        } catch (java.io.IOException expected) { }
        yes(Arrays.equals(Files.readAllBytes(stateFile), updated),
                "Invalid state must not overwrite previous valid state");

        BufferedImage screenshot = new BufferedImage(240, 160,
                BufferedImage.TYPE_INT_ARGB);
        screenshot.setRGB(0, 0, 0xfff040aa);
        Path file = saves.screenshot(screenshot);
        yes(Files.isRegularFile(file) && file.toString().endsWith(".png"),
                "F12 PNG screenshot created");
        BufferedImage loaded = javax.imageio.ImageIO.read(file.toFile());
        yes(loaded.getRGB(0, 0) == 0xfff040aa, "PNG colors preserved");
        yes(!Files.exists(saves.ramFile),
                "Screenshots and snapshots never overwrite live cartridge SRAM");

        for (DesktopLibrary.Console console : DesktopLibrary.Console.values()) {
            RetroArtwork poster = new RetroArtwork(console, "Juego de prueba", false);
            poster.setSize(140, 170);
            BufferedImage out = new BufferedImage(140, 170,
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = out.createGraphics();
            poster.paint(graphics);
            graphics.dispose();
            Color center = new Color(out.getRGB(70, 85), true);
            yes(center.getAlpha() == 255, "Neon covers must draw solid pixels");
            yes(RetroArtwork.code(console).length() >= 2,
                    "Console artwork label must be present");

            RetroArtwork hero = new RetroArtwork(console, "Destacado", true);
            hero.setSize(240, 170);
            BufferedImage image = new BufferedImage(240, 170,
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D heroGraphics = image.createGraphics();
            hero.paint(heroGraphics);
            heroGraphics.dispose();
            yes(image.getRGB(120, 85) != 0, "Hero artwork must be rendered");
        }

        int pad = WindowsRetroNative.gamepadMask();
        yes((pad & ~((1 << 14) - 1)) == 0,
                "XInput maps only supported buttons, or returns zero");
        yes(Files.size(game) == 32768, "ROM remains untouched");
        System.out.println("DesktopUltraTest: PASS");
    }
}
