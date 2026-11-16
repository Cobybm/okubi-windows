package bo.nexoplay.app.retro;

public final class RetroSpeedControlTest {
    private static void check(boolean ok,String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] ignored) {
        check(RetroSpeedControl.NORMAL==1,"default x1 unchanged");
        String[] labels={"×0.5","×1","×1.25","×1.5","×2","×3","×4"};
        for(int i=0;i<labels.length;i++)
            check(RetroSpeedControl.label(i).equals(labels[i]),"speed label "+i);
        check(RetroSpeedControl.faster(1)==2,"1x goes to 1.25x");
        check(RetroSpeedControl.faster(6)==6,"max");
        check(RetroSpeedControl.slower(0)==0,"min");
        check(RetroSpeedControl.slower(2)==1,"back to normal");
        long frame=16_666_667L;
        check(RetroSpeedControl.intervalNanos(frame,0)==frame*2,"half speed");
        check(RetroSpeedControl.intervalNanos(frame,1)==frame,"normal frame");
        check(RetroSpeedControl.intervalNanos(frame,2)==Math.round(frame/1.25),"1.25");
        check(RetroSpeedControl.intervalNanos(frame,3)==Math.round(frame/1.5),"1.5");
        check(RetroSpeedControl.intervalNanos(frame,4)==Math.round(frame/2.0),"2");
        check(RetroSpeedControl.intervalNanos(frame,5)==Math.round(frame/3.0),"3");
        check(RetroSpeedControl.playbackSampleRate(48000,1)==48000,"normal PCM");
        check(RetroSpeedControl.playbackSampleRate(48000,2)==60000,"fast PCM 1.25");
        check(RetroSpeedControl.playbackSampleRate(48000,3)==72000,"fast PCM 1.5");
        check(RetroSpeedControl.playbackSampleRate(48000,4)==96000,"fast PCM 2");
        check(RetroSpeedControl.playbackSampleRate(48000,5)==144000,"fast PCM 3");
        check(RetroSpeedControl.playbackSampleRate(48000,6)==192000,"max PCM");
        check(RetroSpeedControl.presentFrame(1,3),"1x all visual frames");
        check(RetroSpeedControl.presentFrame(2,3),"1.25 no video drop");
        check(RetroSpeedControl.presentFrame(3,3),"1.5 no video drop");
        check(RetroSpeedControl.presentFrame(4,2),"2x present frame 2");
        check(!RetroSpeedControl.presentFrame(4,3),"2x skip only video frame 3");
        check(RetroSpeedControl.presentFrame(5,3),"3x present video 1/3");
        check(!RetroSpeedControl.presentFrame(5,4),"3x skip video");
        check(RetroSpeedControl.normal(1),"only x1 normal");
        RetroSpeedControl.AudioAccelerator audio =
                new RetroSpeedControl.AudioAccelerator();
        short[] nativeSamples=new short[200];
        short[] accelerated=new short[200];
        for(int i=0;i<200;i++)nativeSamples[i]=(short)(i%2==0?12000:-12000);
        check(audio.transform(nativeSamples,200,accelerated)==200,
                "normal native PCM intact");
        audio.setStep(2);
        check(audio.transform(nativeSamples,200,accelerated)==160,
                "1.25x leaves audible faster PCM");
        audio.setStep(4);
        check(audio.transform(nativeSamples,200,accelerated)==100,
                "2x game music sounds at 2x, not silent");
        audio.setStep(5);
        check(audio.transform(nativeSamples,200,accelerated)>0,
                "3x music still audible");
        audio.setStep(1);
        check(audio.transform(nativeSamples,200,accelerated)==200,
                "normal soundtrack restored immediately");
        check(RetroSpeedControl.maxOutputShorts(8192)==16384,
                "slow playback reserves twice the native stereo buffer");
        short[] maximumInput = new short[8192];
        for (int i=0;i<maximumInput.length;i++)
            maximumInput[i]=(short)(i%2==0?1111:-2222);
        short[] slowOutput =
                new short[RetroSpeedControl.maxOutputShorts(maximumInput.length)];
        audio.setStep(0);
        check(audio.transform(maximumInput,maximumInput.length,slowOutput)==16384,
                "x0.5 on max native audio does not overflow the PCM output buffer");
        check(slowOutput[0]==1111 && slowOutput[1]==-2222
                && slowOutput[2]==1111 && slowOutput[3]==-2222,
                "slow soundtrack duplicates complete stereo frames");
        check(slowOutput[16382]==1111 && slowOutput[16383]==-2222,
                "slow PCM does not truncate the final stereo frame");
        audio.setStep(1);
        boolean invalidCapacity=false;
        try { RetroSpeedControl.maxOutputShorts(8191); }
        catch(IllegalArgumentException expected){ invalidCapacity=true; }
        check(invalidCapacity,"reject odd stereo input capacities");
        invalidCapacity=false;
        try { RetroSpeedControl.maxOutputShorts(Integer.MAX_VALUE-1); }
        catch(IllegalArgumentException expected){ invalidCapacity=true; }
        check(invalidCapacity,"reject integer overflow on requested audio capacity");
        boolean rejected=false;
        try{audio.transform(nativeSamples,199,accelerated);}
        catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"reject truncated stereo buffer");
        System.out.println("RetroSpeedControlTest: OK");
    }
}
