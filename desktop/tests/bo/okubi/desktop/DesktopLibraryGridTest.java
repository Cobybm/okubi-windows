package bo.okubi.desktop;

/** Grid layout and controller navigation at real laptop/desktop widths. */
public final class DesktopLibraryGridTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static int move(int index, int size, int columns, int direction) {
        return DesktopLibraryGrid.move(index, size, columns, direction);
    }

    public static void main(String[] args) {
        int min = DesktopLibraryGrid.MIN_CARD_WIDTH;
        int gap = DesktopLibraryGrid.CARD_GAP;
        check(DesktopLibraryGrid.columnsForWidth(0) == 1, "Unset width: one column");
        check(DesktopLibraryGrid.columnsForWidth(520) == 1,
                "800px laptop minus sidebar uses one spacious column");
        check(DesktopLibraryGrid.columnsForWidth(2 * min + gap - 1) == 1,
                "One pixel short of two columns does not squeeze cards");
        check(DesktopLibraryGrid.columnsForWidth(2 * min + gap) == 2,
                "Exact two-column breakpoint");
        check(DesktopLibraryGrid.columnsForWidth(850) == 2,
                "Default desktop library width uses two columns");
        check(DesktopLibraryGrid.columnsForWidth(3 * min + 2 * gap - 1) == 2,
                "One pixel short of three columns stays at two");
        check(DesktopLibraryGrid.columnsForWidth(3 * min + 2 * gap) == 3,
                "Exact three-column breakpoint");
        check(DesktopLibraryGrid.columnsForWidth(1920) == 3,
                "Ultrawide library caps at three legible columns");

        check(move(0, 0, 2, DesktopConsoleNavigation.DOWN) == -1,
                "Empty library cannot select anything");
        check(move(1, 5, 1, DesktopConsoleNavigation.DOWN) == 2,
                "One-column Down advances exactly one card");
        check(move(1, 5, 1, DesktopConsoleNavigation.RIGHT) == 1,
                "One-column Right must not skip to another row");
        check(move(1, 5, 1, DesktopConsoleNavigation.LEFT) == 1,
                "One-column Left must not skip to another row");

        check(move(1, 7, 2, DesktopConsoleNavigation.DOWN) == 3,
                "Two-column Down matches original Xbox controls");
        check(move(5, 7, 2, DesktopConsoleNavigation.DOWN) == 6,
                "Short last two-column row clamps safely");
        check(move(2, 8, 3, DesktopConsoleNavigation.DOWN) == 5,
                "Three-column Down advances one visual row");
        check(move(5, 8, 3, DesktopConsoleNavigation.DOWN) == 7,
                "Three-column final row clamps to last game");
        check(move(2, 8, 3, DesktopConsoleNavigation.RIGHT) == 2,
                "Three-column Right cannot spill into the next row");
        check(move(3, 8, 3, DesktopConsoleNavigation.LEFT) == 3,
                "Three-column Left cannot spill into the prior row");
        check(move(4, 8, 3, DesktopConsoleNavigation.UP) == 1,
                "Up preserves visual column");
        check(move(1, 8, 3, DesktopConsoleNavigation.UP) == 1,
                "Up on first row does not unexpectedly jump");

        for (int columns = 1; columns <= 3; columns++) {
            for (int size = 1; size <= 41; size++) {
                for (int index = -1; index <= size + 1; index++) {
                    for (int direction : new int[]{
                            DesktopConsoleNavigation.UP,
                            DesktopConsoleNavigation.DOWN,
                            DesktopConsoleNavigation.LEFT,
                            DesktopConsoleNavigation.RIGHT}) {
                        int next = move(index, size, columns, direction);
                        check(next >= 0 && next < size,
                                "Selection always stays inside actual game list");
                        if (direction == DesktopConsoleNavigation.LEFT
                                || direction == DesktopConsoleNavigation.RIGHT) {
                            int selected = Math.max(0, Math.min(size - 1, index));
                            check(next / columns == selected / columns,
                                    "Horizontal input cannot cross rows");
                        }
                    }
                }
            }
        }
        System.out.println("DesktopLibraryGridTest: PASS");
    }
}
