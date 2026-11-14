package bo.nexoplay.app.retro;

/**
 * Presentation-only image modes. Never modifies ROMs, emulation memory,
 * battery RAM, RTC, save states or controller settings.
 *
 * Pure Java helpers enable JVM regression tests without Android SDK.
 */
public final class RetroVisualFilters {
    public enum Mode {
        PIXEL_PERFECT("Original · Pixel Perfect"),
        SHARP_HD("Sharp HD · Nítido"),
        LCD_RETRO("LCD Retro · Pantalla clásica"),
        SMOOTH_HD("Smooth HD · Suavizado"),
        GB_CLASSIC("Game Boy Classic · Verde");

        public final String label;
        Mode(String label) { this.label = label; }
    }

    public static final Mode DEFAULT = Mode.SHARP_HD;
    private static final int[] CLASSIC_PALETTE = {
            0xFF0F380F, 0xFF306230, 0xFF8BAC0F, 0xFF9BBC0F
    };

    private RetroVisualFilters() {}

    public static Mode parse(String stored) {
        if (stored != null) {
            try { return Mode.valueOf(stored); }
            catch (IllegalArgumentException ignored) { }
        }
        return DEFAULT;
    }

    /** Integer zoom only; zero means viewport is smaller than native pixels. */
    public static int integerScale(int availableW, int availableH, int nativeW, int nativeH) {
        if (availableW <= 0 || availableH <= 0 || nativeW <= 0 || nativeH <= 0) return 0;
        return Math.min(availableW / nativeW, availableH / nativeH);
    }

    public static boolean smooth(Mode mode) { return mode == Mode.SMOOTH_HD; }
    public static boolean classic(Mode mode) { return mode == Mode.GB_CLASSIC; }
    public static boolean lcd(Mode mode) { return mode == Mode.LCD_RETRO; }

    /**
     * Four-shade DMG-like luminance palette. Input and output must not alias,
     * because the original native framebuffer is reused for screenshot previews.
     */
    public static void classicPalette(int[] source, int[] destination) {
        if (source == null || destination == null || source == destination
                || destination.length < source.length)
            throw new IllegalArgumentException("Invalid classic palette buffers");
        for (int i = 0; i < source.length; i++) {
            int rgb = source[i];
            int r = (rgb >>> 16) & 255;
            int g = (rgb >>> 8) & 255;
            int b = rgb & 255;
            int brightness = (r * 54 + g * 183 + b * 19) >>> 8;
            destination[i] = CLASSIC_PALETTE[brightness < 64 ? 0
                    : brightness < 128 ? 1 : brightness < 192 ? 2 : 3];
        }
    }
}
