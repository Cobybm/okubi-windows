package bo.okubi.desktop;

/**
 * Speed-up with live soundtrack: consume every Libretro audio frame, then
 * resample PCM in the same time domain as the game. Never silence fast-forward.
 * Pure Java and no file or settings access.
 */
public final class DesktopSpeedControl {
    private static final double[] STEPS={1.0,1.25,1.5,2.0,3.0};
    private static final String[] LABELS={"×1","×1.25","×1.5","×2","×3"};
    private DesktopSpeedControl() {}

    public static int normalize(int step) {
        return Math.max(0,Math.min(STEPS.length-1,step));
    }
    public static int next(int step) { return (normalize(step)+1)%STEPS.length; }
    public static double speed(int step) { return STEPS[normalize(step)]; }
    public static String label(int step) { return LABELS[normalize(step)]; }
    public static long frameInterval(long normalNanos,int step) {
        if(normalNanos<=0)normalNanos=16_666_667L;
        return Math.max(2_000_000L,Math.round(normalNanos/speed(step)));
    }
    public static boolean present(int step,long frame) {
        return speed(step)<2.5 || frame%3==0;
    }

    /**
     * Stereo signed 16-bit interleaved samples. Output little-endian PCM.
     * Removing samples raises pitch and speeds the game's original soundtrack
     * proportionally. Input audio remains produced by every Libretro frame.
     */
    public static final class AudioAccelerator {
        private int step;
        private double phase;
        public void setStep(int nextStep) {
            nextStep=normalize(nextStep);
            if(nextStep!=step){step=nextStep;phase=0.0;}
        }
        public int convert(short[] stereo,int inputShorts,byte[] output) {
            return convert(stereo,inputShorts,output,100);
        }
        public int convert(short[] stereo,int inputShorts,byte[] output,int volumePercent) {
            if(stereo==null||output==null)throw new IllegalArgumentException("PCM buffers");
            int volume=Math.max(0,Math.min(100,volumePercent));
            if(inputShorts<0||inputShorts>stereo.length||inputShorts%2!=0)
                throw new IllegalArgumentException("Interleaved stereo length");
            final int stereoFrames=inputShorts/2;
            double advance=speed(step);
            int pos=0;
            while(phase<stereoFrames){
                int frame=(int)phase;
                if(pos+4>output.length)throw new IllegalArgumentException("PCM capacity");
                int left=(stereo[frame*2]*volume)/100;
                int right=(stereo[frame*2+1]*volume)/100;
                output[pos++]=(byte)left;output[pos++]=(byte)(left>>>8);
                output[pos++]=(byte)right;output[pos++]=(byte)(right>>>8);
                phase+=advance;
            }
            phase-=stereoFrames;
            // Zero-length source still has no audible data.
            return pos;
        }
    }
}
