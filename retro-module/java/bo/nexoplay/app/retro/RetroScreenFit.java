package bo.nexoplay.app.retro;

/**
 * Native-ratio and optional edge-to-edge video viewport calculations.
 * All maths are deterministic, without Android dependencies.
 * Changes only screen pixels; never game state or input data.
 */
public final class RetroScreenFit {
    public enum Mode {
        CINEMA("CINEMA · controles a los lados"),
        EXPANDED("Ampliado · sin recortar"),
        COVER("Pantalla completa · sin perder bordes"),
        STRETCH("Estirado · llenar sin recortar");
        public final String label;
        Mode(String label) { this.label = label; }
    }

    public static final Mode DEFAULT = Mode.CINEMA;

    /** Rectangles are [left, top, right, bottom], pixel coordinates. */
    public static final class Rects {
        public final int[] source;
        public final int[] destination;
        Rects(int[] source, int[] destination) {
            this.source = source;
            this.destination = destination;
        }
    }

    private RetroScreenFit() { }

    public static Mode parse(String stored) {
        if (stored != null) {
            try { return Mode.valueOf(stored); }
            catch (IllegalArgumentException ignored) { }
        }
        return DEFAULT;
    }

    public static boolean safeFullscreen(Mode mode) {
        return mode == Mode.COVER;
    }

    /** Stored separately from CINEMA profiles; 0.2–1.0 remains tappable. */
    public static float clampOverlayOpacity(float value) {
        if (!Float.isFinite(value)) return .45f;
        return Math.max(.20f, Math.min(1.0f, value));
    }

    public static boolean fillsStage(Mode mode) {
        return mode != null && mode != Mode.CINEMA;
    }

    public static Rects layout(int surfaceW, int surfaceH,
                               int nativeW, int nativeH, Mode mode,
                               boolean integerPixels) {
        if (surfaceW <= 0 || surfaceH <= 0 || nativeW <= 0 || nativeH <= 0)
            throw new IllegalArgumentException("Invalid video dimensions");
        Mode chosen = mode == null ? DEFAULT : mode;
        int[] source = {0, 0, nativeW, nativeH};
        int[] destination = {0, 0, surfaceW, surfaceH};
        if (chosen != Mode.STRETCH) {
            // COVER now renders an aspect-correct foreground over a dim,
            // full-stage background. It must NEVER discard dialogue/text
            // like the previous center-crop implementation.
            long scaledWidth;
            long scaledHeight;
            int scale = integerPixels
                    ? RetroVisualFilters.integerScale(surfaceW, surfaceH, nativeW, nativeH)
                    : 0;
            if (scale > 0) {
                scaledWidth = (long)nativeW * scale;
                scaledHeight = (long)nativeH * scale;
            } else if ((long)surfaceW * nativeH <= (long)surfaceH * nativeW) {
                scaledWidth = surfaceW;
                scaledHeight = Math.max(1, (long)surfaceW * nativeH / nativeW);
            } else {
                scaledHeight = surfaceH;
                scaledWidth = Math.max(1, (long)surfaceH * nativeW / nativeH);
            }
            int w = (int)Math.min(surfaceW, scaledWidth);
            int h = (int)Math.min(surfaceH, scaledHeight);
            int x = (surfaceW - w) / 2;
            int y = (surfaceH - h) / 2;
            destination = new int[]{x, y, x + w, y + h};
        }
        return new Rects(source, destination);
    }
}
