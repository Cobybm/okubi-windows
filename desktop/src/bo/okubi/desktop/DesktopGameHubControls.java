package bo.okubi.desktop;

/**
 * Game Hub console-navigation state, independent of Swing, native drivers,
 * ROM files and save data. Only handles controller events while the modal
 * GAME HUB window explicitly owns input.
 */
public final class DesktopGameHubControls {
    public enum Action { NONE, ACTIVATE, FAVORITE, CLOSE }
    public static final int ACTION_COUNT = 4;
    private final DesktopConsoleNavigation gamepad = new DesktopConsoleNavigation();
    private int index;

    public int selected() { return index; }

    public void reset() {
        gamepad.reset();
        index = 0;
    }

    /** Translate a real XInput mask with the established edge/repeat rules. */
    public int poll(int nativeMask, long nowNanos) {
        return gamepad.poll(nativeMask, nowNanos);
    }

    /** Navigate the four buttons JUGAR / FAVORITO / RESPALDOS / CERRAR. */
    public int navigate(int events) {
        boolean forward = (events & (DesktopConsoleNavigation.RIGHT
                | DesktopConsoleNavigation.DOWN
                | DesktopConsoleNavigation.NEXT_CATEGORY)) != 0;
        boolean backward = (events & (DesktopConsoleNavigation.LEFT
                | DesktopConsoleNavigation.UP
                | DesktopConsoleNavigation.PREVIOUS_CATEGORY)) != 0;
        if (forward != backward)
            index = Math.floorMod(index + (forward ? 1 : -1), ACTION_COUNT);
        return index;
    }

    public Action action(int events) {
        if ((events & DesktopConsoleNavigation.BACK) != 0)
            return Action.CLOSE;
        if ((events & DesktopConsoleNavigation.FAVORITE) != 0)
            return Action.FAVORITE;
        if ((events & DesktopConsoleNavigation.ACCEPT) != 0)
            return Action.ACTIVATE;
        return Action.NONE;
    }

    /** Preserve focus when a button is disabled, but never activate it. */
    public void select(int index) {
        this.index = Math.max(0, Math.min(ACTION_COUNT - 1, index));
    }
}
