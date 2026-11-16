package bo.okubi.desktop;

/**
 * Phase 89: bounded non-blocking PCM queue for a single emulation worker.
 * SourceDataLine.available() can be too small for an entire Libretro frame;
 * retain the unwritten tail instead of discarding the game's music.
 * All values are byte lengths; source is signed LE stereo 16-bit PCM.
 */
public final class DesktopPcmQueue {
    private final byte[] buffer;
    private int start;
    private int length;
    private long droppedBytes;
    public DesktopPcmQueue(int capacityBytes) {
        if (capacityBytes < 8 || (capacityBytes & 3)!=0)
            throw new IllegalArgumentException("Stereo PCM capacity must be 4-aligned");
        buffer=new byte[capacityBytes];
    }
    public byte[] data() {return buffer;}
    public int offset() {return start;}
    public int size() {return length;}
    public long droppedBytes() {return droppedBytes;}

    public void clear() {start=0;length=0;}
    public void append(byte[] source,int count) {
        if(source==null||count<0||count>source.length||(count&3)!=0)
            throw new IllegalArgumentException("PCM stereo frame size");
        if(count==0)return;
        int skip=0;
        if(count>buffer.length) {
            skip=count-buffer.length;
            droppedBytes+=skip;
            count=buffer.length;
        }
        int overflow=length+count-buffer.length;
        if(overflow>0) {
            int remove=Math.min(length,overflow);
            start+=remove;
            length-=remove;
            droppedBytes+=remove;
        }
        if(start+length+count>buffer.length){
            System.arraycopy(buffer,start,buffer,0,length);
            start=0;
        }
        System.arraycopy(source,skip,buffer,start+length,count);
        length+=count;
    }
    public void consume(int amount) {
        if(amount<0||amount>length||(amount&3)!=0)
            throw new IllegalArgumentException("Audio device write must consume complete stereo frames");
        start+=amount;
        length-=amount;
        if(length==0)start=0;
    }
    /** Return whole PCM stereo frames only. Never block waiting for device space. */
    public int writable(int deviceAvailable) {
        return Math.max(0,Math.min(length,deviceAvailable)&~3);
    }
}
