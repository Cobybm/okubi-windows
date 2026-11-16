package bo.nexoplay.app.retro;

/**
 * Pure Java frame PRESENTATION policy; the emulation core always steps every
 * frame. No audio, game clock or cartridge save is ever reduced or skipped.
 * AUTO falls back to half-rate presentation only after sustained lateness.
 */
public final class RetroRenderPolicy {
    public enum Mode { AUTOMATIC, QUALITY, BATTERY }

    private static final int WINDOW = 120;
    private static final int ENABLE_MISSES = 24;
    private static final int RECOVER_MISSES = 4;
    private static final int RECOVER_WINDOWS = 3;

    private Mode mode = Mode.AUTOMATIC;
    private boolean autoReduced;
    private int windowFrames;
    private int lateFrames;
    private int recoveryWindows;
    private long presentationIndex;

    public static Mode parseMode(String name) {
        if (name == null) return Mode.AUTOMATIC;
        try { return Mode.valueOf(name); }
        catch (IllegalArgumentException error) { return Mode.AUTOMATIC; }
    }

    public void setMode(Mode requested) {
        Mode next = requested == null ? Mode.AUTOMATIC : requested;
        if (mode == next) return;
        mode = next;
        reset();
    }

    public Mode getMode() { return mode; }

    public boolean isReduced() {
        return mode == Mode.BATTERY || (mode == Mode.AUTOMATIC && autoReduced);
    }

    /**
     * Called for every emulated frame, including when the Surface is absent.
     * A display-only skip prevents needless native->Java pixel copies.
     */
    public boolean shouldPresent(boolean surfaceReady) {
        long index = presentationIndex++;
        return surfaceReady && (!isReduced() || (index & 1L) == 0L);
    }

    /**
     * Observe wall-clock lateness after audio and rendering for one complete
     * emulation frame; isolated system jitter never triggers eco rendering.
     */
    public void observeFrame(long delayNanos, long frameNanos) {
        if (mode != Mode.AUTOMATIC || frameNanos <= 0) return;
        if (delayNanos > frameNanos / 2L) lateFrames++;
        if (++windowFrames < WINDOW) return;
        if (!autoReduced) {
            if (lateFrames >= ENABLE_MISSES) {
                autoReduced = true;
                recoveryWindows = 0;
            }
        } else if (lateFrames <= RECOVER_MISSES) {
            if (++recoveryWindows >= RECOVER_WINDOWS) {
                autoReduced = false;
                recoveryWindows = 0;
            }
        } else {
            recoveryWindows = 0;
        }
        windowFrames = 0;
        lateFrames = 0;
    }

    /** Resets history after pause, device rotation or external interruption. */
    public void reset() {
        autoReduced = false;
        windowFrames = 0;
        lateFrames = 0;
        recoveryWindows = 0;
        presentationIndex = 0;
    }
}
