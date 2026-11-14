package bo.okubi.desktop;

/**
 * Prevents stale controller presses from crossing window/modal boundaries.
 *
 * A newly focused UI must observe one completely neutral XInput frame
 * before it can accept action events. This prevents holding Xbox A/Y/B
 * while closing a dialog, changing window or returning from a game from
 * automatically launching the next unrelated action.
 *
 * Pure state: no native, Swing, filesystem, emulator or saves.
 */
public final class DesktopControllerFocusGate {
    private boolean waitingForRelease = true;

    /** Re-arm the safety gate on every focus loss or active emulator session. */
    public void disarm() {
        waitingForRelease = true;
    }

    /**
     * @return true only after seeing a neutral frame since last disarm.
     *         The neutral frame itself is not an action.
     */
    public boolean accepts(int nativeMask) {
        if (waitingForRelease) {
            if ((nativeMask & ((1 << 14) - 1)) == 0)
                waitingForRelease = false;
            return false;
        }
        return true;
    }

    public boolean isReady() {
        return !waitingForRelease;
    }
}
