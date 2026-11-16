package bo.nexoplay.app.retro;

/** Deterministic non-device tests of partial AudioTrack writes and limits. */
public final class RetroPcmQueueTest {
    private static void check(boolean ok,String description) {
        if (!ok) throw new AssertionError(description);
    }
    private static short[] pair(int... stereo) {
        short[] out=new short[stereo.length];
        for(int i=0;i<stereo.length;i++)out[i]=(short)stereo[i];
        return out;
    }
    public static void main(String[] ignored) {
        RetroPcmQueue queue=new RetroPcmQueue(12);
        queue.append(pair(1,2,3,4,5,6),6);
        check(queue.size()==6,"first PCM batch");
        queue.consumed(2);
        check(queue.size()==4 && queue.data()[queue.offset()]==3,
                "partial nonblocking write retains unwritten stereo frames");
        queue.append(pair(7,8,9,10),4);
        check(queue.size()==8,"appends new PCM after pending audio");
        check(queue.data()[queue.offset()]==3,"pending audio not lost");
        queue.consumed(6);
        check(queue.size()==2 && queue.data()[queue.offset()]==9,
                "partial consumption crosses original batch boundary");
        queue.append(pair(11,12,13,14,15,16,17,18,19,20),10);
        check(queue.size()==12,"queue fits fixed 12 shorts");
        queue.append(pair(21,22,23,24),4);
        check(queue.size()==12,"queue never grows beyond bound");
        check(queue.droppedSamples()==4,"overflow drops 2 oldest stereo frames");
        check(queue.data()[queue.offset()]==13,"newest audio kept after overload");
        queue.consumed(queue.size());
        check(queue.size()==0 && queue.offset()==0,"empty queue resets offset");
        queue.append(pair(25,26),2);
        queue.clear();
        check(queue.size()==0,"speed transition drops stale pending PCM");
        check(queue.droppedSamples()==4,"clear does not hide prior drop count");
        short[] bigger=new short[20];
        for(int i=0;i<bigger.length;i++)bigger[i]=(short)(i+1);
        queue.append(bigger,bigger.length);
        check(queue.size()==12 && queue.data()[0]==9,"long input keeps newest bounded audio");
        check(queue.droppedSamples()==12,"long input dropped count");
        boolean rejected=false;
        try { queue.append(pair(1,2,3),3); }
        catch(IllegalArgumentException expected) { rejected=true; }
        check(rejected,"incomplete stereo batch rejected");
        rejected=false;
        try { queue.consumed(queue.size()+1); }
        catch(IllegalArgumentException expected) { rejected=true; }
        check(rejected,"impossible AudioTrack write length rejected");
        System.out.println("RetroPcmQueueTest: OK");
    }
}
