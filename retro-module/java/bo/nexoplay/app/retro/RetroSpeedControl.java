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

    /**
     * Worst-case output capacity in interleaved PCM shorts. At 0.5x playback,
     * every stereo input frame is emitted twice; all other supported speeds
     * need no more output. Reserve this buffer once before entering the
     * emulation worker to avoid crashes or per-frame allocations.
     */
    public static int maxOutputShorts(int inputShorts) {
        if (inputShorts < 0 || (inputShorts & 1) != 0
                || inputShorts > Integer.MAX_VALUE / 2)
            throw new IllegalArgumentException("Invalid stereo PCM buffer capacity");
        return inputShorts * 2;
    }

    /**
     * Fallback-free device compatibility: fixed-rate AudioTrack at native
     * sample rate, accelerated PCM by removing interleaved stereo frames.
     * Faster pitch and game soundtrack, never mute fast-forward. No allocations
     * in the render worker; preserve fractional position across core frames.
     */
    public static final class AudioAccelerator {
        private double phase;
        private int step = NORMAL;
        public void setStep(int value) {
            int updated=normalize(value);
            if(updated!=step){step=updated;phase=0.0;}
        }
        public int transform(short[] input,int length,short[] target) {
            if(input==null||target==null||length<0||length>input.length
                    ||(length&1)!=0)throw new IllegalArgumentException("PCM stereo");
            int frames=length/2, out=0;
            double stride=multiplier(step);
            while(phase<frames) {
                int at=(int)phase*2;
                if(out+2>target.length)throw new IllegalArgumentException("PCM capacity");
                target[out++]=input[at];
                target[out++]=input[at+1];
                phase+=stride;
            }
            phase-=frames;
            return out;
        }
    }

    /** Preserve every core/audio frame; skip only expensive video transfers at high speed. */
    public static boolean presentFrame(int index, long frameNumber) {
        double speed = multiplier(index);
        if (speed >= 3.0) return frameNumber % 3L == 0L;
        if (speed >= 2.0) return (frameNumber & 1L) == 0L;
        return true;
    }
}
