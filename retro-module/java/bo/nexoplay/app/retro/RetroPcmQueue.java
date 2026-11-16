package bo.nexoplay.app.retro;

/**
 * A bounded, allocation-free, single-worker PCM output queue.
 *
 * Android AudioTrack.WRITE_NON_BLOCKING may consume only part of a batch.
 * Preserve that tail instead of dropping it, keeping the soundtrack
 * continuous at 1x and fast-forward. Never retain minutes of old sound:
 * on overload discard oldest complete stereo frames within a fixed budget.
 *
 * Lives entirely on the emulation worker. No game, save or ROM access.
 */
public final class RetroPcmQueue {
    private final short[] buffer;
    private int start;
    private int count;
    private long dropped;

    public RetroPcmQueue(int capacityShorts) {
        if (capacityShorts < 4 || (capacityShorts & 1) != 0)
            throw new IllegalArgumentException("Stereo capacity must be even");
        buffer = new short[capacityShorts];
    }

    public short[] data() { return buffer; }
    public int offset() { return start; }
    public int size() { return count; }
    public long droppedSamples() { return dropped; }

    public void clear() {
        start = 0;
        count = 0;
    }

    public void append(short[] source, int sourceCount) {
        if (source == null || sourceCount < 0 || sourceCount > source.length
                || (sourceCount & 1) != 0)
            throw new IllegalArgumentException("PCM must be interleaved stereo");
        if (sourceCount == 0) return;

        int from = 0;
        if (sourceCount > buffer.length) {
            from = sourceCount - buffer.length;
            dropped += from;
            sourceCount = buffer.length;
        }

        int overflow = count + sourceCount - buffer.length;
        if (overflow > 0) {
            if ((overflow & 1) != 0) overflow++;
            int drop = Math.min(count, overflow);
            start += drop;
            count -= drop;
            dropped += drop;
        }

        if (start + count + sourceCount > buffer.length) {
            System.arraycopy(buffer, start, buffer, 0, count);
            start = 0;
        }

        System.arraycopy(source, from, buffer, start + count, sourceCount);
        count += sourceCount;
    }

    /**
     * AudioTrack must consume *complete stereo frames*. Consuming an odd
     * number of shorts would turn the next left sample into a right sample.
     * Reject anomalous device responses without corrupting our pending FIFO.
     */
    public void consumed(int writtenShorts) {
        if (writtenShorts < 0 || writtenShorts > count || (writtenShorts & 1) != 0)
            throw new IllegalArgumentException("AudioTrack must consume complete stereo frames");
        start += writtenShorts;
        count -= writtenShorts;
        if (count == 0) start = 0;
    }
}
