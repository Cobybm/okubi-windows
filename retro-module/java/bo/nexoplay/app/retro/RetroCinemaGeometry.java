package bo.nexoplay.app.retro;

/**
 * Android-independent landscape geometry shared by video and touch controls.
 * A single calculation prevents the video from moving independently of the
 * hit regions after resizing, folding, rotation or display cutout changes.
 *
 * No state, files, ROM content, Android references or configuration writes.
 */
public final class RetroCinemaGeometry {
    private RetroCinemaGeometry() {}

    public static final class Layout {
        public final int stageWidth, stageHeight;
        public final int gameLeft, gameTop, gameWidth, gameHeight;
        public final int bottomRail;
        public final int leftRail, rightRail;
        public final float radius;
        public final float leftThumbX, rightThumbX;
        public final float shoulderY, dpadY, actionAY, actionBY, utilityY;
        public final float utilityHalfWidth, utilityHalfHeight;

        private Layout(int width, int height, int left, int top,
                       int frameWidth, int frameHeight, int bottom,
                       float r, float utilityWidth, float utilityHeight) {
            stageWidth = width;
            stageHeight = height;
            gameLeft = left;
            gameTop = top;
            gameWidth = frameWidth;
            gameHeight = frameHeight;
            bottomRail = bottom;
            leftRail = left;
            rightRail = width - left - frameWidth;
            radius = r;
            leftThumbX = leftRail * .46f;
            rightThumbX = width - rightRail * .46f;
            shoulderY = height * .17f;
            dpadY = Math.max(r * 2.05f, Math.min(height * .69f,
                    height - bottom - r * 1.92f));
            actionAY = Math.max(r * 1.22f, Math.min(height * .61f,
                    height - bottom - r * 1.15f));
            actionBY = Math.max(actionAY + r * .78f,
                    Math.min(height * .78f, height - bottom - r * 1.03f));
            utilityY = height - bottom * .5f;
            utilityHalfWidth = utilityWidth;
            utilityHalfHeight = utilityHeight;
        }

        public int gameRight() { return gameLeft + gameWidth; }
        public int gameBottom() { return gameTop + gameHeight; }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Fit native-ratio video into a stage with protected symmetric thumb
     * rails and a reserved lower control lane. The video is NEVER stretched
     * or cropped to imitate the mockup.
     */
    public static Layout fit(int width, int height, float aspect, float density) {
        if (width <= 0 || height <= 0 || !Float.isFinite(aspect) || aspect <= 0
                || !Float.isFinite(density) || density <= 0)
            throw new IllegalArgumentException("Dimensiones CINEMA inválidas");

        int inset = Math.max(1, Math.min(Math.round(6f * density),
                Math.round(height * .035f)));
        // CINEMA ULTRA: reclaim vertical pixels from the decorative footer
        // without removing the START/SELECT touch lane. Button geometry and
        // hitboxes use this identical bottomRail value.
        int bottomRail = clamp(Math.round(Math.max(28f * density, height * .10f)),
                Math.max(1, Math.round(height * .10f)),
                Math.max(1, Math.round(height * .23f)));
        int usableHeight = Math.max(1, height - bottomRail - inset);

        // Reserving at least 19% of the width for each thumb also protects
        // buttons from overlapping a game which has a different aspect ratio.
        int minRail = Math.max(1, Math.min(Math.round(130f * density),
                Math.round(width * .205f)));
        int maxGameWidth = Math.max(1, width - 2 * minRail);
        int gameHeight = Math.max(1, Math.min(usableHeight,
                (int)Math.floor(maxGameWidth / (double)aspect)));
        int gameWidth = Math.max(1, Math.min(maxGameWidth,
                (int)Math.floor(gameHeight * (double)aspect)));
        // Keep ratio at the viewport (the native-resolution renderer adds
        // true black margins for any fractional-pixel rounding difference).
        int left = (width - gameWidth) / 2;
        int top = inset + Math.max(0, (usableHeight - gameHeight) / 2);
        int rail = Math.min(left, width - left - gameWidth);

        float baseRadius = Math.min(Math.min(width * .050f, height * .120f),
                31f * density);
        // Space for a 4-way D-pad disc including the center gap; tiny
        // multi-window layouts gracefully use smaller buttons.
        float maxByRail = Math.max(5f, (rail * .5f - 5f * density) / 1.82f);
        float r = Math.max(4f, Math.min(baseRadius, maxByRail));
        float utilityWidth = Math.max(5f,
                Math.min(r * 1.10f, width * .045f - 3f * density));
        float utilityHeight = Math.max(4f,
                Math.min(r * .38f, bottomRail * .34f));
        return new Layout(width, height, left, top, gameWidth, gameHeight,
                bottomRail, r, utilityWidth, utilityHeight);
    }
}
