package bo.okubi.desktop;

/** Deterministic controller event and two-column library selection regression. */
public final class DesktopConsoleNavigationTest {
    private static void check(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
    }

    public static void main(String[] args) {
        for (int size = 0; size <= 21; size++) {
            check(DesktopConsoleNavigation.move(-1, size, DesktopConsoleNavigation.UP)
                    == (size == 0 ? -1 : 0), "Negative index normalized");
        }
        check(DesktopConsoleNavigation.move(0, 0, DesktopConsoleNavigation.RIGHT) == -1,
                "Empty library does not select anything");
        check(DesktopConsoleNavigation.move(0, 7, DesktopConsoleNavigation.RIGHT) == 1,
                "Right moves to second card of same row");
        check(DesktopConsoleNavigation.move(1, 7, DesktopConsoleNavigation.RIGHT) == 1,
                "Right does not spill into next row");
        check(DesktopConsoleNavigation.move(1, 7, DesktopConsoleNavigation.DOWN) == 3,
                "Down moves two cards");
        check(DesktopConsoleNavigation.move(3, 7, DesktopConsoleNavigation.UP) == 1,
                "Up moves two cards");
        check(DesktopConsoleNavigation.move(3, 7, DesktopConsoleNavigation.LEFT) == 2,
                "Left goes to previous column");
        check(DesktopConsoleNavigation.move(6, 7, DesktopConsoleNavigation.RIGHT) == 6,
                "Last odd card has no next column");
        check(DesktopConsoleNavigation.move(5, 7, DesktopConsoleNavigation.DOWN) == 6,
                "Last row clamps to actual final game");
        check(DesktopConsoleNavigation.move(22, 7, DesktopConsoleNavigation.DOWN) == 6,
                "Out-of-range index cannot overflow library");
        DesktopConsoleNavigation nav = new DesktopConsoleNavigation();
        long start = 1_000_000_000L;
        int first = nav.poll(1 << 1, start);
        check((first & DesktopConsoleNavigation.DOWN) != 0,
                "Dpad down sends immediate selection");
        check(nav.poll(1 << 1, start + 100_000_000L) == 0,
                "Holding a direction should not scroll at every poll");
        check(nav.poll(1 << 1, start + 350_000_000L)
                == DesktopConsoleNavigation.DOWN,
                "Held direction repeats after comfortable initial delay");
        check(nav.poll(1 << 1, start + 400_000_000L) == 0,
                "Held direction is throttled");
        check(nav.poll(1 << 1, start + 490_000_000L)
                == DesktopConsoleNavigation.DOWN,
                "Held direction repeats at a bounded cadence");
        nav.poll(0, start + 500_000_000L);
        check(nav.poll(1 << 1, start + 510_000_000L)
                == DesktopConsoleNavigation.DOWN,
                "Releasing and pressing restarts fresh movement");
        check(nav.poll(3, start + 520_000_000L)
                == 0, "Up and down simultaneous inputs cancel");
        check(nav.poll(12, start + 530_000_000L)
                == 0, "Left and right simultaneous inputs cancel");
        nav.reset();
        check(nav.poll(1 << 5, start) == DesktopConsoleNavigation.ACCEPT,
                "Physical Xbox A selects game");
        check(nav.poll(1 << 5, start + 1) == 0,
                "Holding Xbox A cannot open multiple games");
        nav.poll(0, start + 2);
        check(nav.poll(1 << 4, start + 3) == DesktopConsoleNavigation.BACK,
                "Physical Xbox B returns to library/window");
        nav.poll(0, start + 4);
        check(nav.poll(1 << 8, start + 5) == DesktopConsoleNavigation.FAVORITE,
                "Physical Xbox X toggles selected favorite");
        nav.poll(0, start + 6);
        check(nav.poll(1 << 12, start + 7) == DesktopConsoleNavigation.CONSOLE,
                "Xbox Start toggles the console experience");
        nav.poll(0, start + 8);
        check(nav.poll(1 << 11, start + 9) == DesktopConsoleNavigation.NEXT_CATEGORY,
                "RB cycles categories forward");
        nav.poll(0, start + 10);
        check(nav.poll(1 << 10, start + 11) == DesktopConsoleNavigation.PREVIOUS_CATEGORY,
                "LB cycles categories backward");
        nav.poll(0, start + 12);
        check(nav.poll(1 << 7, start + 13) == DesktopConsoleNavigation.DETAILS,
                "Physical Xbox Y opens the selected game details sheet");
        check(nav.poll(1 << 7, start + 14) == 0,
                "Holding Xbox Y cannot open details repeatedly");
        nav.reset();
        check(nav.poll(0, start + 100) == 0, "No connected controller is harmless");
        System.out.println("DesktopConsoleNavigationTest: PASS");
    }
}
