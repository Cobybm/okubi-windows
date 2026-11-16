package bo.nexoplay.app.retro;

/**
 * Removes impossible opposite directions from combined multitouch input.
 * Preserves A/B/X/Y, shoulder buttons and every unrelated key bit.
 * Pure Java: independent of Android and safe to regression-test on JVM.
 */
public final class RetroInputMask {
    private RetroInputMask() {}

    public static int resolveOpposites(int mask, int up, int down, int left, int right) {
        if ((mask & up) != 0 && (mask & down) != 0) {
            mask &= ~(up | down);
        }
        if ((mask & left) != 0 && (mask & right) != 0) {
            mask &= ~(left | right);
        }
        return mask;
    }
}
