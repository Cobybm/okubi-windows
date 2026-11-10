package bo.nexoplay.app.retro;

/**
 * Pure-Java controller axis decoder: works independently of Android so the
 * deadzone, diagonal and hat-switch behavior can be regression tested.
 *
 * Mask bits 0..3 match RetroCore.Key.UP/DOWN/LEFT/RIGHT, respectively.
 * A digital hat takes priority over the analogue stick on its own axis:
 * opposing directions must never be sent to a Libretro core together.
 */
public final class RetroControllerAxes {
    public static final int UP = 1 << 0;
    public static final int DOWN = 1 << 1;
    public static final int LEFT = 1 << 2;
    public static final int RIGHT = 1 << 3;
    public static final int DIRECTIONS = UP | DOWN | LEFT | RIGHT;

    // Enter requires deliberate stick movement; exit keeps the button held
    // until the stick has returned significantly towards its neutral zone.
    public static final float ENGAGE = 0.50f;
    public static final float RELEASE = 0.32f;
    private static final float HAT_ENGAGE = 0.50f;

    private RetroControllerAxes() {}

    private static float safe(float value) {
        return Float.isNaN(value) || Float.isInfinite(value)
                ? 0f : Math.max(-1f, Math.min(1f, value));
    }

    private static int analogAxis(float value, int negative, int positive,
                                  int previousMask) {
        value = safe(value);
        if (value <= -ENGAGE ||
                ((previousMask & negative) != 0 && value <= -RELEASE))
            return negative;
        if (value >= ENGAGE ||
                ((previousMask & positive) != 0 && value >= RELEASE))
            return positive;
        return 0;
    }

    /**
     * Return the new four-direction mask. D-pad hat and stick input may be
     * combined diagonally, but a nonneutral hat axis overrides that axis.
     * The previous mask is used solely to suppress analog threshold jitter.
     */
    public static int mask(float stickX, float stickY,
                           float hatX, float hatY, int previousMask) {
        float hx = safe(hatX), hy = safe(hatY);
        int horizontal = Math.abs(hx) >= HAT_ENGAGE
                ? (hx < 0 ? LEFT : RIGHT)
                : analogAxis(stickX, LEFT, RIGHT, previousMask);
        int vertical = Math.abs(hy) >= HAT_ENGAGE
                ? (hy < 0 ? UP : DOWN)
                : analogAxis(stickY, UP, DOWN, previousMask);
        return horizontal | vertical;
    }
}
