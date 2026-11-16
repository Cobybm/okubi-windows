package bo.nexoplay.app.retro;

/**
 * Phase 88: real Libretro frame cadence and accelerated game soundtrack.
 *
 * Every core frame including audio still executes. Only visual presentation
 * may be skipped, not audio generation or cartridge clock/RTC/save logic.
 * Per-session default is 1x; no existing user preference is overwritten.
 */
public final class RetroSpeedControl {
    public static final int NORMAL = 1;
    private static final double[] MULTIPLIERS =
            {0.5, 1.0, 1.25, 1.5, 2.0, 3.0, 4.0};
    private static final String[] LABELS =
            {"×0.5", "×1", "×1.25", "×1.5", "×2", "×3", "×4"};

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
    public static double multiplier(int index) { return MULTIPLIERS[normalize(index)]; }

    public static long intervalNanos(long normalFrameNanos, int index) {
        long base = normalFrameNanos > 0 ? normalFrameNanos : 16_700_000L;
        return Math.max(1L, Math.round(base / multiplier(index)));
    }

    /** AudioTrack PCM rate matches the faster game soundtrack (pitch increases). */
    public static int playbackSampleRate(int coreRate, int speedIndex) {
        int rate = coreRate > 0 ? coreRate : 48000;
        return Math.min(192000, Math.max(8000, (int)Math.round(rate * multiplier(speedIndex))));
    }

    /** Preserve every core/audio frame; skip only expensive video transfers at high speed. */
    public static boolean presentFrame(int index, long frameNumber) {
        double speed = multiplier(index);
        if (speed >= 3.0) return frameNumber % 3L == 0L;
        if (speed >= 2.0) return (frameNumber & 1L) == 0L;
        return true;
    }
}
