package bo.okubi.desktop;

public final class DesktopPcmQueuePhase89Test {
    private static void check(boolean cond,String message){
        if(!cond)throw new AssertionError(message);
    }
    public static void main(String[] ignored){
        check(DesktopPcmQueue.deviceBufferBytes(8000)==4096,
                "low sample rates retain minimum aligned buffer");
        check(DesktopPcmQueue.deviceBufferBytes(44100)==5512,
                "44.1 kHz device buffer remains frame-aligned");
        check(DesktopPcmQueue.deviceBufferBytes(48000)==6000,
                "48 kHz device buffer stays original size");
        check(DesktopPcmQueue.deviceBufferBytes(53000)==6628,
                "unusual sample rates round up to stereo frame alignment");
        check(DesktopPcmQueue.deviceBufferBytes(192000)==24000,
                "high sample rates remain bounded and frame aligned");
        boolean invalidRate=false;
        try{DesktopPcmQueue.deviceBufferBytes(0);}
        catch(IllegalArgumentException expected){invalidRate=true;}
        check(invalidRate,"invalid sample rate rejected");
        DesktopPcmQueue q=new DesktopPcmQueue(16);
        byte[] original=new byte[16];
        for(int i=0;i<16;i++) original[i]=(byte)(i+1);
        q.append(original,12);
        check(q.size()==12,"first audio frames queued");
        check(q.writable(3)==0,"cannot write partial stereo sample");
        check(q.writable(6)==4,"partial device buffer accepted");
        q.consume(4);
        check(q.size()==8 && q.data()[q.offset()]==5,"retain unwritten PCM");
        boolean partialRejected=false;
        try{q.consume(2);}catch(IllegalArgumentException expected){partialRejected=true;}
        check(partialRejected,"reject half of a stereo frame");
        check(q.size()==8 && q.data()[q.offset()]==5,"invalid consume does not alter pending PCM");
        check(q.writable(-4)==0,"negative availability cannot write audio");
        q.append(original,8);
        check(q.size()==16,"pending audio + new audio");
        q.append(original,8);
        check(q.size()==16 && q.droppedBytes()==8,"bounded backlog discards oldest only");
        check(q.data()[q.offset()]==1,"latest PCM frames remain");
        check(q.writable(100)==16,"all queued bytes writable when buffer ready");
        q.consume(12);
        check(q.size()==4,"partial write retained");
        q.consume(4);
        check(q.size()==0 && q.offset()==0,"empty queue resets");
        q.clear();
        check(q.size()==0,"pause and speed change clear old sound");
        byte[] longer=new byte[24];
        for(int i=0;i<longer.length;i++)longer[i]=(byte)(i+1);
        q.append(longer,longer.length);
        check(q.size()==16 && q.data()[0]==9,"long input keeps newest 16 bytes");
        check(q.droppedBytes()==16,"overflow accounted");
        boolean invalid=false;
        try{q.append(original,7);}catch(IllegalArgumentException expected){invalid=true;}
        check(invalid,"reject non-frame-aligned audio");
        System.out.println("DesktopPcmQueuePhase89Test: OK");
    }
}
