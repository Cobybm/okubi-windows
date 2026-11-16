package bo.nexoplay.app.retro;

/**
 * Plain Java presentation state for Android DownloadManager and OKUBI's
 * own homebrew stream. Never treats a completed system row as a valid
 * download unless the caller has verified that the file is still readable.
 */
public final class OkubiDownloadProgress {
    public static final int MISSING = -1;
    public static final int PENDING = 1;
    public static final int RUNNING = 2;
    public static final int PAUSED = 4;
    public static final int COMPLETE = 8;
    public static final int FAILED = 16;

    private OkubiDownloadProgress() {}

    public static boolean active(int status) {
        return status == PENDING || status == RUNNING || status == PAUSED;
    }

    /** -1 means the server has not provided a total; never invent a percent. */
    public static int percent(long downloaded, long total) {
        if (downloaded < 0 || total <= 0) return -1;
        if (downloaded >= total) return 100;
        // Use double instead of multiplying long values to avoid overflow.
        return (int) Math.max(0, Math.min(99, (100.0 * downloaded) / total));
    }

    public static String message(int status, long downloaded, long total,
                                 boolean verifiedFile) {
        if (status == COMPLETE)
            return verifiedFile ? "✓ DESCARGADO · ARCHIVO DISPONIBLE"
                                : "Archivo eliminado o sin permiso · reintentar";
        if (status == FAILED) return "✕ FALLÓ LA DESCARGA · reintentar";
        if (status == PAUSED) return "⌛ EN PAUSA · Android reanudará cuando pueda";
        if (status == PENDING) return "⌛ EN COLA DE DESCARGAS";
        if (status == RUNNING) {
            int p = percent(downloaded, total);
            return p >= 0 ? "↓ DESCARGANDO · " + p + "%"
                    : "↓ DESCARGANDO · tamaño desconocido";
        }
        return "AÚN NO DESCARGADO";
    }

    public static String action(int status, boolean verifiedFile,
                                boolean isPatch) {
        if (status == COMPLETE && verifiedFile)
            return isPatch ? "✓ APLICAR PARCHE" : "▶ JUGAR EN OKUBI";
        if (active(status)) return "⌛ DESCARGA EN CURSO";
        if (status == FAILED || status == COMPLETE)
            return "↻ REINTENTAR DESCARGA";
        return isPatch ? "↓ DESCARGAR PARCHE .BPS" : "↓ DESCARGAR JUEGO";
    }
}
