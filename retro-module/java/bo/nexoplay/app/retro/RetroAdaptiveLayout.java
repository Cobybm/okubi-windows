package bo.nexoplay.app.retro;

/**
 * Small-screen / large-font layout decisions for phones, tablets, foldables
 * and resizable Android windows. No Android dependencies, settings or I/O.
 *
 * Screen width is the current available width in dp, NOT the device's
 * physical screen width; this keeps behavior predictable in split-screen.
 */
public final class RetroAdaptiveLayout {
    private RetroAdaptiveLayout() { }

    private static float safeFontScale(float scale) {
        return !Float.isFinite(scale) || scale <= 0 ? 1f : scale;
    }

    /** Status always remains visible through the menu even when header hides it. */
    public static boolean hideHeaderStatus(int widthDp, float fontScale) {
        float scale = safeFontScale(fontScale);
        return widthDp < Math.round(810f * Math.min(1.6f, scale));
    }

    /** Compact branding leaves space for an always-visible ☰ menu button. */
    public static boolean compactBrand(int widthDp, float fontScale) {
        float scale = safeFontScale(fontScale);
        return widthDp < Math.round(510f * Math.min(1.6f, scale));
    }

    /** Keep a minimum title width; action tray can scroll horizontally. */
    public static int minimumTitleDp(int widthDp) {
        return widthDp >= 700 ? 115 : widthDp >= 480 ? 88 : 52;
    }
}
