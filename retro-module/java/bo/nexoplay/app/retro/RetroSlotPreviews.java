package bo.nexoplay.app.retro;

/**
 * Optional, locally generated slot thumbnails. Never changes the state bytes,
 * the legacy <SHA>.state filename or any .okubisave archive format.
 * A missing PNG must not prevent save/load.
 */
public final class RetroSlotPreviews {
    public static final int MAX_BYTES = 512 * 1024;
    public static final int MAX_IMAGE_EDGE = 512;

    private RetroSlotPreviews() { }

    public static String filename(String sha256, int slot) {
        if (!RetroSaveSlots.validHash(sha256) || !RetroSaveSlots.validSlot(slot))
            throw new IllegalArgumentException("Vista previa: ROM o ranura inválida");
        return sha256 + (slot == 1 ? ".preview.png" : ".slot" + slot + ".preview.png");
    }

    public static boolean validImageSize(long bytes) {
        return bytes >= 24 && bytes <= MAX_BYTES;
    }

    public static String details(boolean exists, long stateBytes) {
        if (!exists) return "Vacía · sin partida guardada";
        if (!RetroSaveSlots.validStateSize(stateBytes))
            return "Archivo no válido · no cargar ni sobrescribir";
        return "Guardada · " + ((stateBytes + 1023) / 1024) + " KiB";
    }

    /**
     * A corrupted or interrupted .bak recovery must never be overwritten
     * silently by the UI. This is a message only, not a repair operation.
     */
    public static boolean blockedByBackup(boolean backupExists) {
        return backupExists;
    }
}
