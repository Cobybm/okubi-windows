package bo.okubi.desktop;

/** Runtime-free checks of all pre-release shutdown/save safety states. */
public final class DesktopShutdownPolicyTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(DesktopShutdownPolicy.mayExit(false, false, false, false),
                "Idle application is allowed to exit.");
        for (int state = 1; state < 16; state++) {
            boolean game = (state & 1) != 0;
            boolean cloud = (state & 2) != 0;
            boolean cross = (state & 4) != 0;
            boolean guard = (state & 8) != 0;
            check(!DesktopShutdownPolicy.mayExit(game, cloud, cross, guard),
                    "Never quit with an active game or disk save task.");
            check(!DesktopShutdownPolicy.blockedReason(game, cloud, cross, guard)
                    .isBlank(), "Every blocked exit has a clear status.");
        }
        check(DesktopShutdownPolicy.blockedReason(true, true, true, true)
                    .contains("SRAM"),
                "Game saving takes precedence over background operations.");
        check(DesktopShutdownPolicy.blockedReason(false, true, false, false)
                    .contains("CLOUD"),
                "Cloud operation has a specific warning.");
        check(DesktopShutdownPolicy.blockedReason(false, false, true, false)
                    .contains("CROSS"),
                "Cross Save operation has a specific warning.");
        check(DesktopShutdownPolicy.blockedReason(false, false, false, true)
                    .contains("SMART"),
                "Smart Save Guard operation has a specific warning.");
        System.out.println("DesktopShutdownPolicyTest: PASS");
    }
}
