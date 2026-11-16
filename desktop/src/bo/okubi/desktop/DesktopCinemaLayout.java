package bo.okubi.desktop;

import java.awt.Dimension;
import java.awt.FontMetrics;

/**
 * Lightweight desktop CINEMA layout and typography policy.
 * Pure calculations: no game state, saves, ROM access, network or I/O.
 */
public final class DesktopCinemaLayout {
    private DesktopCinemaLayout() { }

    /**
     * Protect usable library area on short displays. Responsive windows
     * show categories in the side menu whenever the tabs are hidden.
     */
    public static boolean showHero(int windowHeight) {
        return windowHeight >= 675;
    }

    public static boolean showCategoryTabs(int windowHeight) {
        return windowHeight >= 560;
    }

    /** Avoid overlapping the status message with the long desktop version. */
    public static boolean showFooterVersion(int windowWidth) {
        return windowWidth >= 1160;
    }

    /** Compensate for Windows display scaling and larger accessible fonts. */
    public static int cardRowHeight(int fontHeight) {
        int extra = Math.max(0, fontHeight - 19) * 12;
        return Math.max(180, Math.min(252, 180 + extra));
    }

    /**
     * Game Hub fits the parent and visible desktop even on laptop displays.
     * A scroll pane handles small vertical space rather than clipping actions.
     */
    public static Dimension gameHubSize(int parentWidth, int parentHeight,
                                        int screenWidth, int screenHeight) {
        int usableWidth = Math.max(360, screenWidth - 45);
        int usableHeight = Math.max(280, screenHeight - 95);
        int width = Math.min(940, Math.max(650, parentWidth - 65));
        int height = Math.min(665, Math.max(400, parentHeight - 45));
        return new Dimension(Math.min(width, usableWidth),
                Math.min(height, usableHeight));
    }

    /**
     * Display long buttons/tabs without cutting the last word mid-character
     * or breaking UTF-16 surrogate pairs (including kanji and game symbols).
     * The full label stays in Swing's model and accessibility/tooltip.
     */
    public static String fitText(String text, FontMetrics font, int availableWidth) {
        if (text == null || text.isEmpty()) return "";
        if (availableWidth <= 0) return "";
        if (font.stringWidth(text) <= availableWidth) return text;
        String dots = "…";
        if (font.stringWidth(dots) > availableWidth) return "";
        int[] points = text.codePoints().toArray();
        int low = 0, high = points.length;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            String prefix = new String(points, 0, mid);
            if (font.stringWidth(prefix + dots) <= availableWidth)
                low = mid;
            else
                high = mid - 1;
        }
        return new String(points, 0, low) + dots;
    }
}
