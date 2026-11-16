package bo.okubi.desktop;

/** Controller state and physical Xbox buttons, no native bridge needed. */
public final class DesktopGameHubControlsTest {
    private static void yes(boolean test, String message) {
        if (!test) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        DesktopGameHubControls nav = new DesktopGameHubControls();
        long start = 1_000_000_000L;
        yes(nav.selected() == 0, "Play is selected first.");
        nav.navigate(DesktopConsoleNavigation.RIGHT);
        yes(nav.selected() == 1, "Right selects favorite.");
        nav.navigate(DesktopConsoleNavigation.NEXT_CATEGORY);
        yes(nav.selected() == 2, "RB moves to backups.");
        nav.navigate(DesktopConsoleNavigation.DOWN);
        yes(nav.selected() == 3, "Down moves to close.");
        nav.navigate(DesktopConsoleNavigation.RIGHT);
        yes(nav.selected() == 0, "Forward wraps to play.");
        nav.navigate(DesktopConsoleNavigation.LEFT);
        yes(nav.selected() == 3, "Left wraps backwards.");
        nav.navigate(DesktopConsoleNavigation.PREVIOUS_CATEGORY);
        yes(nav.selected() == 2, "LB moves backward.");
        nav.navigate(DesktopConsoleNavigation.UP);
        yes(nav.selected() == 1, "Up moves backward.");
        nav.navigate(DesktopConsoleNavigation.LEFT | DesktopConsoleNavigation.RIGHT);
        yes(nav.selected() == 1, "Opposed directions do not move.");
        nav.select(-11);
        yes(nav.selected() == 0, "Negative indices are clamped.");
        nav.select(500);
        yes(nav.selected() == 3, "Oversized indices are clamped.");

        yes(nav.action(DesktopConsoleNavigation.ACCEPT)
                    == DesktopGameHubControls.Action.ACTIVATE, "A selects action.");
        yes(nav.action(DesktopConsoleNavigation.BACK)
                    == DesktopGameHubControls.Action.CLOSE, "B closes dialog.");
        yes(nav.action(DesktopConsoleNavigation.FAVORITE)
                    == DesktopGameHubControls.Action.FAVORITE, "X toggles favorite.");
        yes(nav.action(DesktopConsoleNavigation.CONSOLE)
                    == DesktopGameHubControls.Action.NONE,
                "Start must not change mode or launch a ROM from the dialog.");
        yes(nav.action(0) == DesktopGameHubControls.Action.NONE,
                "No input must not trigger actions.");

        nav.reset();
        yes(nav.poll(1 << 5, start) == DesktopConsoleNavigation.ACCEPT,
                "Physical Xbox A maps to select.");
        yes(nav.poll(1 << 5, start + 1) == 0,
                "Holding A never launches multiple games.");
        nav.poll(0, start + 2);
        yes(nav.poll(1 << 4, start + 3) == DesktopConsoleNavigation.BACK,
                "Physical Xbox B maps to Back.");
        nav.poll(0, start + 4);
        yes(nav.poll(1 << 8, start + 5) == DesktopConsoleNavigation.FAVORITE,
                "Physical Xbox X maps to Favorite.");
        nav.poll(0, start + 6);
        yes(nav.poll(1 << 11, start + 7) == DesktopConsoleNavigation.NEXT_CATEGORY,
                "Physical Xbox RB moves forward.");
        nav.poll(0, start + 8);
        yes(nav.poll(1 << 10, start + 9) == DesktopConsoleNavigation.PREVIOUS_CATEGORY,
                "Physical Xbox LB moves backward.");

        nav.reset();
        yes(nav.selected() == 0, "Reset always returns to Play.");
        yes(nav.poll(0, start + 10) == 0, "No controller is harmless.");
        System.out.println("DesktopGameHubControlsTest: PASS");
    }
}
