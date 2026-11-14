package bo.okubi.desktop;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import javax.swing.Timer;

/**
 * Scoped controller support for the port gallery. Uses existing Windows XInput
 * bridge and the focus-release safety gate; never owns inputs during gameplay.
 */
public final class DesktopPortGalleryInput {
    public enum Command { NONE, PREVIOUS, NEXT, CLOSE }

    private final JDialog dialog;
    private final DesktopPortArtwork preview;
    private final DesktopConsoleNavigation navigation = new DesktopConsoleNavigation();
    private final DesktopControllerFocusGate focusGate = new DesktopControllerFocusGate();
    private final Timer timer;

    public DesktopPortGalleryInput(JDialog dialog, DesktopPortArtwork preview) {
        if (dialog == null || preview == null)
            throw new IllegalArgumentException("Gallery window and artwork required");
        this.dialog = dialog;
        this.preview = preview;
        this.timer = new Timer(90, event -> pollController());
        timer.setCoalesce(true);

        var root = dialog.getRootPane();
        bind(root, "LEFT", () -> preview.moveGallery(-1));
        bind(root, "RIGHT", () -> preview.moveGallery(1));
        bind(root, "ESCAPE", dialog::dispose);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent event) {
                navigation.reset();
                focusGate.disarm();
                timer.start();
            }
            @Override public void windowDeactivated(WindowEvent event) {
                navigation.reset();
                focusGate.disarm();
            }
            @Override public void windowActivated(WindowEvent event) {
                navigation.reset();
                focusGate.disarm();
            }
            @Override public void windowClosed(WindowEvent event) {
                timer.stop();
                navigation.reset();
                focusGate.disarm();
            }
        });
    }

    private static void bind(javax.swing.JRootPane root, String stroke, Runnable action) {
        root.registerKeyboardAction(event -> action.run(), KeyStroke.getKeyStroke(stroke),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /** Pure event mapping enables tests without hardware, JNI or Swing windows. */
    public static Command interpret(int events) {
        if ((events & DesktopConsoleNavigation.BACK) != 0) return Command.CLOSE;
        boolean previous = (events & (DesktopConsoleNavigation.LEFT
                | DesktopConsoleNavigation.UP
                | DesktopConsoleNavigation.PREVIOUS_CATEGORY)) != 0;
        boolean next = (events & (DesktopConsoleNavigation.RIGHT
                | DesktopConsoleNavigation.DOWN
                | DesktopConsoleNavigation.NEXT_CATEGORY)) != 0;
        if (previous == next) return Command.NONE;
        return previous ? Command.PREVIOUS : Command.NEXT;
    }

    public boolean isPolling() { return timer.isRunning(); }

    private void pollController() {
        if (!dialog.isActive() || EmbeddedPlayerWindow.hasActiveSession()) {
            navigation.reset();
            focusGate.disarm();
            return;
        }
        int mask;
        try {
            mask = WindowsRetroNative.gamepadMask();
        } catch (UnsatisfiedLinkError | RuntimeException nativeUnavailable) {
            timer.stop();
            return;
        }
        if (!focusGate.accepts(mask)) {
            navigation.reset();
            return;
        }
        switch (interpret(navigation.poll(mask, System.nanoTime()))) {
            case CLOSE -> dialog.dispose();
            case PREVIOUS -> preview.moveGallery(-1);
            case NEXT -> preview.moveGallery(1);
            default -> { }
        }
    }
}
