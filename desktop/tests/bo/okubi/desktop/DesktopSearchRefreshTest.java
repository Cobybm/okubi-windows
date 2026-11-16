package bo.okubi.desktop;

import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;

/** Deterministic smoke test for keystroke coalescing and immediate category updates. */
public final class DesktopSearchRefreshTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        AtomicInteger count = new AtomicInteger();
        DesktopSearchRefresh[] refresh = new DesktopSearchRefresh[1];
        SwingUtilities.invokeAndWait(() -> {
            refresh[0] = new DesktopSearchRefresh(160, count::incrementAndGet);
            for (int i = 0; i < 20; i++) refresh[0].request();
            check(count.get() == 0, "Typing should not synchronously rebuild UI");
        });
        Thread.sleep(420);
        SwingUtilities.invokeAndWait(() -> check(count.get() == 1,
                "Burst of 20 keystrokes should cause exactly one render"));
        SwingUtilities.invokeAndWait(() -> {
            refresh[0].request();
            refresh[0].immediate(count::incrementAndGet);
            check(count.get() == 2, "Category update should be immediate");
        });
        Thread.sleep(300);
        SwingUtilities.invokeAndWait(() -> {
            check(count.get() == 2, "Pending delayed render should have been cancelled");
            refresh[0].stop();
        });
        System.out.println("PASS: Search coalescing and immediate category change");
    }
}
