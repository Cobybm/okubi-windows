package bo.okubi.desktop;

/** Pure PCM tests: no ROM, BIOS, audio device or user preferences. */
public final class DesktopSpeedControlPhase88Test {
    private static void check(boolean value,String msg) {
        if(!value)throw new AssertionError(msg);
    }
    public static void main(String[] args) {
        String[] labels={"×1","×1.25","×1.5","×2","×3"};
        int index=0;
        for(int i=0;i<labels.length;i++) {
            check(DesktopSpeedControl.label(index).equals(labels[i]),"speed order");
            index=DesktopSpeedControl.next(index);
        }
        check(index==0,"cycle restarts at normal x1");
        long base=16_666_667L;
        check(DesktopSpeedControl.frameInterval(base,0)==base,"normal cadence");
        check(DesktopSpeedControl.frameInterval(base,1)==Math.round(base/1.25),"1.25");
        check(DesktopSpeedControl.frameInterval(base,2)==Math.round(base/1.5),"1.5");
        check(DesktopSpeedControl.frameInterval(base,3)==Math.round(base/2.0),"2x");
        check(DesktopSpeedControl.frameInterval(base,4)==Math.round(base/3.0),"3x");
        check(DesktopSpeedControl.present(4,3),"3x video subset");
        check(!DesktopSpeedControl.present(4,4),"3x skip video, not core");

        short[] input=new short[200];
        for(int i=0;i<input.length;i++)input[i]=i%2==0?(short)12000:(short)-12000;
        byte[] pcm=new byte[400];
        DesktopSpeedControl.AudioAccelerator audio=
                new DesktopSpeedControl.AudioAccelerator();
        check(audio.convert(input,input.length,pcm)==400,"1x copies entire soundtrack");
        audio.setStep(1);
        check(audio.convert(input,input.length,pcm)==320,"1.25x outputs complete accelerated audio");
        audio.setStep(3);
        check(audio.convert(input,input.length,pcm)==200,"2x has audible PCM, never silence");
        int fullSample=((pcm[1]&255)<<8)|(pcm[0]&255);
        check((short)fullSample==12000,"2x original pitch sample present");
        audio.setStep(4);
        check(audio.convert(input,input.length,pcm)>0,"3x still sends soundtrack PCM");
        audio.setStep(0);
        check(audio.convert(input,input.length,pcm,50)==400,"gain does not affect cadence");
        int reduced=((pcm[1]&255)<<8)|(pcm[0]&255);
        check((short)reduced==6000,"independent game volume at 50%");
        check(audio.convert(input,input.length,pcm,0)==400,"mute only game PCM, not OS");
        check(pcm[0]==0 && pcm[1]==0,"game audio zero when muted");
        boolean bad=false;
        try{audio.convert(input,199,pcm);}catch(IllegalArgumentException expected){bad=true;}
        check(bad,"reject non-stereo sample lengths");
        System.out.println("DesktopSpeedControlPhase88Test: OK");
    }
}
