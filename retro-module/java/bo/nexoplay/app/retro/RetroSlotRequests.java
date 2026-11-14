package bo.nexoplay.app.retro;

import java.util.ArrayDeque;
import java.io.File;

/**
 * Bounded FIFO of immutable save/load requests sent from Android UI to the
 * emulation thread. Every request pins the chosen ROM SHA and slot at click
 * time. The consumer is the ONLY thread permitted to perform native I/O.
 */
public final class RetroSlotRequests {
    public static final int MAX_PENDING = 8;

    public enum Action { SAVE, LOAD }

    /**
     * Immutable on-disk state observed BEFORE save is confirmed.
     * The worker refuses to overwrite if the file changed while a dialog or
     * FIFO queue was pending. A .bak atomic recovery file blocks ALL writes.
     */
    public static final class FileStamp {
        public final boolean exists;
        public final long length;
        public final long lastModified;

        private FileStamp(boolean present, long size, long changedAt) {
            exists = present;
            length = size;
            lastModified = changedAt;
        }

        public static FileStamp capture(File target) {
            if (target == null || target.isDirectory()
                    || (new File(target.getPath() + ".bak").exists() ||
                            new File(target.getPath() + ".new").exists())) return null;
            if (!target.exists()) return new FileStamp(false, 0L, 0L);
            if (!target.isFile() || !RetroSaveSlots.validStateSize(target.length()))
                return null; // A damaged state is not safe to overwrite.
            return new FileStamp(true, target.length(), target.lastModified());
        }

        public boolean matches(File target) {
            if (target == null || target.isDirectory()
                    || (new File(target.getPath() + ".bak").exists() ||
                            new File(target.getPath() + ".new").exists())) return false;
            if (!exists) return !target.exists();
            return target.isFile() && target.length() == length
                    && target.lastModified() == lastModified
                    && RetroSaveSlots.validStateSize(target.length());
        }
    }

    public static final class Request {
        public final Action action;
        public final String romHash;
        public final int slot;
        public final FileStamp expectedSaveTarget;

        private Request(Action action, String hash, int chosenSlot,
                        FileStamp stamp) {
            this.action = action;
            this.romHash = hash;
            this.slot = chosenSlot;
            this.expectedSaveTarget = stamp;
        }
    }

    private final ArrayDeque<Request> pending = new ArrayDeque<>(MAX_PENDING);

    /** Never replace an earlier user request silently. Reject a full queue. */
    public synchronized boolean offer(Action action, String romHash, int slot) {
        if (action == null || !RetroSaveSlots.validHash(romHash)
                || !RetroSaveSlots.validSlot(slot) || pending.size() >= MAX_PENDING) {
            return false;
        }
        return pending.offerLast(new Request(action, romHash, slot, null));
    }

    /**
     * Guarded saves are the ONLY save command allowed by the game worker.
     * Legacy generic SAVE requests remain serializable for tests but are
     * refused by the worker if they have no FileStamp.
     */
    public synchronized boolean offerGuardedSave(String romHash, int slot,
                                                  FileStamp stamp) {
        if (stamp == null || !RetroSaveSlots.validHash(romHash)
                || !RetroSaveSlots.validSlot(slot) || pending.size() >= MAX_PENDING)
            return false;
        return pending.offerLast(new Request(Action.SAVE, romHash, slot, stamp));
    }

    /** Called on emulation worker only; FIFO order is preserved. */
    public synchronized Request poll() {
        return pending.pollFirst();
    }

    /** On a restored external backup, older queued commands must not replay. */
    public synchronized void clear() {
        pending.clear();
    }

    public synchronized int size() {
        return pending.size();
    }
}
