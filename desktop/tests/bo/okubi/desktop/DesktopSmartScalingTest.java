package bo.okubi.desktop;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

/** Windows-independent math, pixel-output and save-safe preference tests. */
public final class DesktopSmartScalingTest {
    private static void check(boolean success, String message) {
        if (!success) throw new AssertionError(message);
    }

    private static BufferedImage display(int sourceWidth, int sourceHeight,
                                         int viewportWidth, int viewportHeight,
                                         DesktopVideoScaling.Mode mode) {
        BufferedImage source = new BufferedImage(sourceWidth, sourceHeight,
                BufferedImage.TYPE_INT_ARGB);
        // Colored strips let the test recognize crops, fit bars and stretching.
        for (int y = 0; y < sourceHeight; y++)
            for (int x = 0; x < sourceWidth; x++)
                source.setRGB(x, y, y < sourceHeight / 3 ? Color.RED.getRGB()
                        : y >= 2 * sourceHeight / 3 ? Color.BLUE.getRGB()
                        : Color.GREEN.getRGB());
        BufferedImage output = new BufferedImage(viewportWidth, viewportHeight,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, viewportWidth, viewportHeight);
            graphics.setClip(0, 0, viewportWidth, viewportHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            DesktopVideoScaling.Frame rect = DesktopVideoScaling.calculate(
                    sourceWidth, sourceHeight, viewportWidth, viewportHeight, mode,
                    DesktopPlaySettings.Filter.SHARP);
            graphics.drawImage(source, rect.x(), rect.y(),
                    rect.width(), rect.height(), null);
        } finally { graphics.dispose(); }
        return output;
    }

    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("okubi-smart-scaling-");
        Path prefsFile = temporary.resolve("config").resolve("cinema-scaling-v1.properties");
        Path cartridge = temporary.resolve("Pokemon-Esmeralda.srm");
        byte[] cartridgeBytes = new byte[]{10, 20, 30, 40, 50, 60};
        Files.write(cartridge, cartridgeBytes);
        DesktopVideoScaling prefs = new DesktopVideoScaling(prefsFile);
        for (DesktopLibrary.Console console : DesktopLibrary.Console.values())
            check(prefs.mode(console) == DesktopVideoScaling.Mode.AUTO,
                    "New scaling defaults to legacy-compatible AUTO");

        DesktopVideoScaling.Frame gbaFit = DesktopVideoScaling.calculate(
                240, 160, 1920, 1080, DesktopVideoScaling.Mode.FIT,
                DesktopPlaySettings.Filter.SHARP);
        check(gbaFit.x() == 150 && gbaFit.y() == 0
                && gbaFit.width() == 1620 && gbaFit.height() == 1080,
                "GBA FIT 16:9 preserves 3:2 aspect with centered letterboxes");
        check(!gbaFit.cropped() && !gbaFit.distorted(),
                "FIT never crops or distorts game pixel area");

        DesktopVideoScaling.Frame gbaInteger = DesktopVideoScaling.calculate(
                240, 160, 1920, 1080, DesktopVideoScaling.Mode.INTEGER,
                DesktopPlaySettings.Filter.SHARP);
        check(gbaInteger.width() == 1440 && gbaInteger.height() == 960
                && gbaInteger.x() == 240 && gbaInteger.y() == 60,
                "Integer scaling uses exact 6x multiple, no fractional pixels");
        DesktopVideoScaling.Frame gbaCover = DesktopVideoScaling.calculate(
                240, 160, 1920, 1080, DesktopVideoScaling.Mode.FILL,
                DesktopPlaySettings.Filter.SHARP);
        check(gbaCover.x() == 0 && gbaCover.y() == -100
                && gbaCover.width() == 1920 && gbaCover.height() == 1280
                && gbaCover.cropped() && !gbaCover.distorted(),
                "FILL covers 1080p without stretching and center-crops vertical edges");
        DesktopVideoScaling.Frame gbaStretch = DesktopVideoScaling.calculate(
                240, 160, 1920, 1080, DesktopVideoScaling.Mode.STRETCH,
                DesktopPlaySettings.Filter.SHARP);
        check(gbaStretch.x() == 0 && gbaStretch.y() == 0
                && gbaStretch.width() == 1920 && gbaStretch.height() == 1080
                && gbaStretch.distorted() && !gbaStretch.cropped(),
                "STRETCH fills viewport and correctly identifies distortion");

        DesktopVideoScaling.Frame gbFit = DesktopVideoScaling.calculate(
                160, 144, 1366, 768, DesktopVideoScaling.Mode.FIT,
                DesktopPlaySettings.Filter.SHARP);
        check(gbFit.height() == 768 && gbFit.width() <= 1366 && gbFit.x() > 0,
                "Classic Game Boy 10:9 output fits a modern laptop screen");
        DesktopVideoScaling.Frame snes = DesktopVideoScaling.calculate(
                256, 224, 1024, 768, DesktopVideoScaling.Mode.INTEGER,
                DesktopPlaySettings.Filter.SHARP);
        check(snes.width() == 768 && snes.height() == 672,
                "SNES integer scaling remains 3x on a 1024x768 monitor");

        DesktopVideoScaling.Frame small = DesktopVideoScaling.calculate(
                256, 224, 180, 96, DesktopVideoScaling.Mode.INTEGER,
                DesktopPlaySettings.Filter.SHARP);
        check(small.width() > 0 && small.height() > 0
                && small.width() <= 180 && small.height() <= 96,
                "Small windows gracefully downscale integer mode instead of clipping");
        DesktopVideoScaling.Frame sameRatio = DesktopVideoScaling.calculate(
                240, 160, 720, 480, DesktopVideoScaling.Mode.STRETCH,
                DesktopPlaySettings.Filter.SHARP);
        check(!sameRatio.distorted(), "STRETCH with matching ratio has no distortion");
        check(!DesktopVideoScaling.calculate(0, 160, 1920, 1080,
                DesktopVideoScaling.Mode.FILL,
                DesktopPlaySettings.Filter.SHARP).drawable(),
                "Malformed native frame does not render any pixels");
        check(!DesktopVideoScaling.calculate(240, 160, 0, 1080,
                DesktopVideoScaling.Mode.FIT,
                DesktopPlaySettings.Filter.SHARP).drawable(),
                "Zero-size viewport is safely ignored");

        DesktopVideoScaling.Frame oldPixelPerfect = DesktopVideoScaling.calculate(
                240, 160, 1079, 699, DesktopVideoScaling.Mode.AUTO,
                DesktopPlaySettings.Filter.INTEGER);
        check(oldPixelPerfect.width() == 960 && oldPixelPerfect.height() == 640,
                "Old Pixel Perfect filter keeps integer scaling in AUTO mode");
        DesktopVideoScaling.Frame oldSharp = DesktopVideoScaling.calculate(
                240, 160, 1079, 699, DesktopVideoScaling.Mode.AUTO,
                DesktopPlaySettings.Filter.SHARP);
        check(oldSharp.width() > oldPixelPerfect.width(),
                "Old SHARP filter keeps fractional FIT in AUTO mode");
        DesktopVideoScaling.Frame explicitFit = DesktopVideoScaling.calculate(
                240, 160, 1079, 699, DesktopVideoScaling.Mode.FIT,
                DesktopPlaySettings.Filter.INTEGER);
        check(explicitFit.width() > oldPixelPerfect.width(),
                "Explicit FIT overrides legacy Pixel Perfect sizing when requested");

        BufferedImage fit = display(4, 3, 16, 9, DesktopVideoScaling.Mode.FIT);
        BufferedImage fill = display(4, 3, 16, 9, DesktopVideoScaling.Mode.FILL);
        BufferedImage stretched = display(4, 3, 16, 9, DesktopVideoScaling.Mode.STRETCH);
        check(fit.getRGB(0, 4) == Color.BLACK.getRGB(),
                "FIT black letterbox borders, no edge stretching");
        check(fill.getRGB(0, 4) != Color.BLACK.getRGB()
                && fill.getRGB(15, 4) != Color.BLACK.getRGB(),
                "FILL covers every screen column with actual video");
        check(stretched.getRGB(0, 4) != Color.BLACK.getRGB(),
                "STRETCH fills visible viewport with video");

        prefs.setMode(DesktopLibrary.Console.GBA, DesktopVideoScaling.Mode.FILL);
        prefs.setMode(DesktopLibrary.Console.GB, DesktopVideoScaling.Mode.INTEGER);
        prefs.setMode(DesktopLibrary.Console.SNES, DesktopVideoScaling.Mode.STRETCH);
        DesktopVideoScaling persisted = new DesktopVideoScaling(prefsFile);
        check(persisted.mode(DesktopLibrary.Console.GBA) == DesktopVideoScaling.Mode.FILL,
                "GBA scaling persists across application restarts");
        check(persisted.mode(DesktopLibrary.Console.GB)
                == DesktopVideoScaling.Mode.INTEGER,
                "GB mode independent of GBA");
        check(persisted.mode(DesktopLibrary.Console.SNES)
                == DesktopVideoScaling.Mode.STRETCH,
                "SNES can independently stretch image");
        check(persisted.mode(DesktopLibrary.Console.GBC)
                == DesktopVideoScaling.Mode.AUTO,
                "GBC retains legacy-compatible default");

        check(DesktopVideoScaling.next(DesktopVideoScaling.Mode.AUTO)
                    == DesktopVideoScaling.Mode.INTEGER,
                "F2 from AUTO selects full-integer pixel scale");
        check(DesktopVideoScaling.next(DesktopVideoScaling.Mode.INTEGER)
                    == DesktopVideoScaling.Mode.FILL,
                "F2 cycles to border-filling cover");
        check(DesktopVideoScaling.next(DesktopVideoScaling.Mode.FILL)
                    == DesktopVideoScaling.Mode.STRETCH,
                "F2 cycles to optional stretching");
        check(DesktopVideoScaling.next(DesktopVideoScaling.Mode.STRETCH)
                    == DesktopVideoScaling.Mode.FIT,
                "F2 returns to the safe undistorted FIT");
        check(DesktopPlaySettings.reservedKey(KeyEvent.VK_F2),
                "F2 cannot be swallowed by custom gameplay key mapping");
        check(DesktopVideoScaling.warning(DesktopVideoScaling.Mode.FILL)
                    .toLowerCase(java.util.Locale.ROOT).contains("recorta"),
                "FILL warns about cropped edges");
        check(DesktopVideoScaling.warning(DesktopVideoScaling.Mode.STRETCH)
                    .toLowerCase(java.util.Locale.ROOT).contains("proporciones"),
                "STRETCH warns about aspect-ratio distortion");

        Properties damaged = new Properties();
        damaged.setProperty("GBA.scale", "UNKNOWN_FUTURE_VALUE");
        damaged.setProperty("SNES.scale", "FILL");
        try (java.io.OutputStream out = Files.newOutputStream(prefsFile)) {
            damaged.store(out, "invalid scaling fixture");
        }
        DesktopVideoScaling recovered = new DesktopVideoScaling(prefsFile);
        check(recovered.mode(DesktopLibrary.Console.GBA) == DesktopVideoScaling.Mode.AUTO,
                "Corrupt scaling falls back without touching saves");
        check(recovered.mode(DesktopLibrary.Console.SNES) == DesktopVideoScaling.Mode.FILL,
                "Uncorrupted mode is still honored");
        check(Arrays.equals(Files.readAllBytes(cartridge), cartridgeBytes),
                "Changing all video sizes never modifies cartridge SRAM bytes");
        try (java.util.stream.Stream<Path> files = Files.list(prefsFile.getParent())) {
            check(files.noneMatch(path -> path.toString().endsWith(".tmp")),
                    "Preferences write atomically and leave no tmp files");
        }
        System.out.println("DesktopSmartScalingTest: PASS");
    }
}
