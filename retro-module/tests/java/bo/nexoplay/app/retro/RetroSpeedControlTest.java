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
        System.out.println("RetroSpeedControlTest: OK");
    }
}
