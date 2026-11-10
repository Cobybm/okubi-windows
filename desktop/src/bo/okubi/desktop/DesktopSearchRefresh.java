package bo.okubi.desktop;

import javax.swing.Timer;
import javax.swing.SwingUtilities;

/** Coalesces fast keystrokes into one library reflow; updates stay on Swing EDT. */
public final class DesktopSearchRefresh {
    private final Timer timer;

    public DesktopSearchRefresh(int delayMillis, Runnable refresh) {
        if (delayMillis < 1 || refresh == null) throw new IllegalArgumentException();
        timer = new Timer(delayMillis, e -> refresh.run());
        timer.setRepeats(false);
        timer.setCoalesce(true);
    }

    public void request() {
        if (!SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("Search refresh must run on Swing EDT");
        timer.restart();
    }

    /** For category changes and game imports, render immediately once. */
    public void immediate(Runnable refresh) {
        if (!SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("Immediate refresh must run on Swing EDT");
        timer.stop();
        refresh.run();
    }

    public void stop() { timer.stop(); }
}
