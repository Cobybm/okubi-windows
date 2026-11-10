package bo.okubi.desktop;

/**
 * Pure-Java two-column library selection and edge-triggered XInput events.
 * No JNI calls, native state, controller drivers, ROM files or Swing references.
 */
public final class DesktopConsoleNavigation {
    public static final int UP = 1;
    public static final int DOWN = 1 << 1;
    public static final int LEFT = 1 << 2;
    public static final int RIGHT = 1 << 3;
    public static final int ACCEPT = 1 << 4;
    public static final int BACK = 1 << 5;
    public static final int FAVORITE = 1 << 6;
    public static final int CONSOLE = 1 << 7;
    public static final int NEXT_CATEGORY = 1 << 8;
    public static final int PREVIOUS_CATEGORY = 1 << 9;
    public static final int DETAILS = 1 << 10; // Physical Xbox Y

    private static final long INITIAL_REPEAT_NANOS = 350_000_000L;
    private static final long REPEAT_NANOS = 125_000_000L;
    private static final int DIRECTION_MASK = 15;
    private int lastMask;
    private long nextRepeatNanos;

    /** Index stays in the same row for Left/Right, and moves one row for Up/Down. */
    public static int move(int index, int size, int direction) {
        if (size <= 0) return -1;
        int selected = Math.max(0, Math.min(index, size - 1));
        switch (direction) {
            case UP: return selected >= 2 ? selected - 2 : selected;
            case DOWN: return Math.min(size - 1, selected + 2);
            case LEFT: return selected % 2 == 1 ? selected - 1 : selected;
            case RIGHT: return selected % 2 == 0 && selected + 1 < size
                    ? selected + 1 : selected;
            default: return selected;
        }
    }

    public void reset() {
        lastMask = 0;
        nextRepeatNanos = 0;
    }

    /**
     * Windows bridge masks: XInput A is Libretro B (bit5), XInput B
     * is Libretro A (bit4); Xbox X = Libretro Y (bit8).
     * Controller Start toggles Console Mode, shoulders cycle categories.
     */
    public int poll(int nativeMask, long nowNanos) {
        int raw = nativeMask & ((1 << 14) - 1);
        int pressed = raw & ~lastMask;
        int events = 0;
        if ((pressed & (1 << 5)) != 0) events |= ACCEPT; // Physical Xbox A
        if ((pressed & (1 << 4)) != 0) events |= BACK; // Physical Xbox B
        if ((pressed & (1 << 8)) != 0) events |= FAVORITE; // Physical Xbox X
        if ((pressed & (1 << 7)) != 0) events |= DETAILS; // Physical Xbox Y
        if ((pressed & (1 << 12)) != 0) events |= CONSOLE; // Start
        if ((pressed & (1 << 11)) != 0) events |= NEXT_CATEGORY; // RB
        if ((pressed & (1 << 10)) != 0) events |= PREVIOUS_CATEGORY; // LB

        int direction = raw & DIRECTION_MASK;
        if ((direction & 3) == 3) direction &= ~3;
        if ((direction & 12) == 12) direction &= ~12;
        int previousDirection = lastMask & DIRECTION_MASK;
        if (direction != 0) {
            if (direction != previousDirection) {
                events |= direction;
                nextRepeatNanos = nowNanos + INITIAL_REPEAT_NANOS;
            } else if (nowNanos >= nextRepeatNanos) {
                events |= direction;
                nextRepeatNanos = nowNanos + REPEAT_NANOS;
            }
        } else {
            nextRepeatNanos = 0;
        }
        lastMask = raw;
        return events;
    }
}
