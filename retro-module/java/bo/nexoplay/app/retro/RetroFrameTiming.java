package bo.nexoplay.app.retro;

/**
 * Stateless presentation pacing for all integrated retro cores.
 * Only schedules the worker's next wakeup: never skips core or audio frames.
 * Uses monotonic elapsedRealtimeNanos() values supplied by the caller.
 */
public final class RetroFrameTiming {
    static final long FALLBACK_FRAME_NS = 16_700_000L;
    private static final long MAX_FRAME_NS = 100_000_000L;
    private static final long PARK_MARGIN_NS = 750_000L;
    private static final long MIN_PARK_NS = 1_500_000L;

    private RetroFrameTiming() {}

    public static long frameIntervalNanos(double fps) {
        if (!Double.isFinite(fps) || fps < 10.0 || fps > 240.0) {
            return FALLBACK_FRAME_NS;
        }
        long interval = Math.round(1_000_000_000.0 / fps);
        return interval > 0 && interval <= MAX_FRAME_NS ? interval : FALLBACK_FRAME_NS;
    }

    /**
     * Correct large scheduling overruns without compressing future frames into
     * a catch-up burst. Short overruns retain the existing cadence.
     */
    public static long nextDeadline(long previousDeadline, long now, long interval) {
        if (interval <= 0 || interval > MAX_FRAME_NS) interval = FALLBACK_FRAME_NS;
        if (previousDeadline > Long.MAX_VALUE - interval) return now;
        long candidate = previousDeadline + interval;
        if (now > candidate && now - candidate > interval * 2L) return now;
        return candidate;
    }

    /**
     * Avoid a long coarse millisecond sleep. Leave a small scheduling margin,
     * without a CPU-spinning busy wait. Short remaining time is left unparked
     * just like the previous emulator loop.
     */
    public static long parkNanos(long remaining) {
        if (remaining <= MIN_PARK_NS) return 0L;
        return Math.max(0L, remaining - PARK_MARGIN_NS);
    }
}
