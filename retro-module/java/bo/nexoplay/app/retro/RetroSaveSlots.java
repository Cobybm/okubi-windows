package bo.nexoplay.app.retro;

/**
 * Three isolated state slots for each ROM SHA-256.
 * Slot 1 intentionally retains the pre-Phase-8 <sha>.state filename so that
 * a legacy checkpoint is never renamed, overwritten during migration or lost.
 *
 * No ROMs are serialized by this helper. Actual writes remain AtomicFile and
 * run exclusively on the native emulation worker.
 */
public final class RetroSaveSlots {
    public static final int FIRST = 1;
    public static final int LAST = 3;
    public static final long MAX_STATE_BYTES = 16L * 1024 * 1024;

    private RetroSaveSlots() {}

    public static boolean validHash(String hash) {
        return hash != null && hash.matches("[0-9a-f]{64}");
    }

    public static boolean validSlot(int slot) {
        return slot >= FIRST && slot <= LAST;
    }

    public static int normalized(int slot) {
        return validSlot(slot) ? slot : FIRST;
    }

    public static String filename(String romSha256,int slot) {
        if(!validHash(romSha256) || !validSlot(slot))
            throw new IllegalArgumentException("ROM SHA-256 o ranura no válida");
        return romSha256 + (slot==FIRST ? ".state" : ".slot"+slot+".state");
    }

    public static boolean validStateSize(long bytes) {
        return bytes > 0 && bytes <= MAX_STATE_BYTES;
    }

    /** Source must exist and be bounded; destination must be another EMPTY slot. */
    public static boolean canCopy(int source, int target, long bytes,
                                  boolean destinationExists) {
        return validSlot(source) && validSlot(target) && source != target
                && validStateSize(bytes) && !destinationExists;
    }

    public static String label(int slot) {
        if(!validSlot(slot))throw new IllegalArgumentException("Ranura fuera de 1 a 3");
        return "Ranura "+slot;
    }
}
