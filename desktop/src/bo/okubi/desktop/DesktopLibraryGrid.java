package bo.okubi.desktop;

/**
 * Pure Java responsive-library geometry. Contains no save, ROM, native,
 * graphics, network or Android operations.
 */
public final class DesktopLibraryGrid {
    private DesktopLibraryGrid() { }

    public static final int MIN_CARD_WIDTH = 350;
    public static final int CARD_GAP = 14;
    public static final int MAX_COLUMNS = 3;

    /** Prefer legible game cards to squeezing three tiny cards on a laptop. */
    public static int columnsForWidth(int viewportWidth) {
        for (int count = MAX_COLUMNS; count >= 2; count--) {
            if (viewportWidth >= count * MIN_CARD_WIDTH
                    + (count - 1) * CARD_GAP) return count;
        }
        return 1;
    }

    /**
     * Keep direction within the displayed row and move vertically by the
     * actual number of columns, including a short final row.
     */
    public static int move(int index, int size, int columns, int direction) {
        if (size <= 0) return -1;
        int count = Math.max(1, Math.min(MAX_COLUMNS, columns));
        int selected = Math.max(0, Math.min(size - 1, index));
        int rowStart = (selected / count) * count;
        switch (direction) {
            case DesktopConsoleNavigation.UP:
                return selected >= count ? selected - count : selected;
            case DesktopConsoleNavigation.DOWN:
                return Math.min(size - 1, selected + count);
            case DesktopConsoleNavigation.LEFT:
                return selected > rowStart ? selected - 1 : selected;
            case DesktopConsoleNavigation.RIGHT:
                return selected + 1 < size && selected + 1 < rowStart + count
                        ? selected + 1 : selected;
            default: return selected;
        }
    }
}
