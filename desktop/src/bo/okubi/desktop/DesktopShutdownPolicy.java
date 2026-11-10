package bo.okubi.desktop;

/**
 * Shared release-candidate lifecycle gate. Never stop the JVM while any
 * emulator or disk worker may be saving or restoring battery/RTC data.
 * Pure decision logic; does not read, write, delete or migrate user files.
 */
public final class DesktopShutdownPolicy {
    private DesktopShutdownPolicy() { }

    public static boolean mayExit(boolean gameActive, boolean cloudBusy,
                                  boolean crossSaveBusy, boolean smartGuardBusy) {
        return !gameActive && !cloudBusy && !crossSaveBusy && !smartGuardBusy;
    }

    public static String blockedReason(boolean gameActive, boolean cloudBusy,
                                       boolean crossSaveBusy, boolean smartGuardBusy) {
        if (gameActive) return "Cierra la partida para completar SRAM, RTC y estados.";
        if (cloudBusy) return "Termina primero la sincronización CLOUD SAVE.";
        if (crossSaveBusy) return "Termina primero la importación o exportación CROSS SAVE.";
        if (smartGuardBusy) return "Espera a que SMART SAVE GUARD termine el respaldo.";
        return "";
    }
}
