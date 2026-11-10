package bo.nexoplay.app.retro;

/** Immutable speed steps for the compact +/- CINEMA toolbar. */
public final class RetroSpeedControl {
    public static final int NORMAL = 1;
    private static final double[] MULTIPLIERS = {0.5, 1.0, 2.0, 4.0};
    private static final String[] LABELS = {"×0.5", "×1", "×2", "×4"};

    private RetroSpeedControl() {}

    public static int normalize(int index) {
        return Math.max(0, Math.min(MULTIPLIERS.length - 1, index));
    }

    public static int slower(int index) { return Math.max(0, normalize(index) - 1); }
    public static int faster(int index) {
        return Math.min(MULTIPLIERS.length - 1, normalize(index) + 1);
    }
    public static String label(int index) { return LABELS[normalize(index)]; }
    public static boolean normal(int index) { return normalize(index) == NORMAL; }

    /** Increase/decrease real emulation cadence, not just animation speed. */
    public static long intervalNanos(long normalFrameNanos, int index) {
        long interval = normalFrameNanos > 0 ? normalFrameNanos : 16_700_000L;
        return Math.max(1L, Math.round(interval / MULTIPLIERS[normalize(index)]));
    }

    /** Fast forward runs all core frames, but draws fewer for UI responsiveness. */
    public static boolean presentFrame(int index, long frameNumber) {
        int speed = normalize(index);
        if (speed == 2) return (frameNumber & 1L) == 0L;
        if (speed == 3) return (frameNumber & 3L) == 0L;
        return true;
    }
}
