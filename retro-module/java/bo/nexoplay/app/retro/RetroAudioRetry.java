package bo.nexoplay.app.retro;

/**
 * Suppress repeated expensive AudioTrack initialization when no audio device
 * is available. Uses caller-supplied monotonic milliseconds, never wall time.
 */
public final class RetroAudioRetry {
    private static final long MAX_DELAY_MS = 15000L;
    private int failures;
    private long retryAfterMs;

    public boolean mayAttempt(long nowMs) { return nowMs >= retryAfterMs; }

    public void failed(long nowMs) {
        failures = Math.min(6, failures + 1);
        long delay = Math.min(MAX_DELAY_MS, 750L << (failures - 1));
        retryAfterMs = nowMs > Long.MAX_VALUE - delay
                ? Long.MAX_VALUE : nowMs + delay;
    }

    public void reset() {
        failures = 0;
        retryAfterMs = 0;
    }

    public int failures() { return failures; }
    public long retryAfterMs() { return retryAfterMs; }
}
