package bo.okubi.desktop;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.Timer;

/**
 * Controller and keyboard navigation for the modal GAME HUB.
 * Never polls XInput while a running game owns the controller.
 * Stops the polling timer when dialog closes; does not alter emulator input.
 */
public final class DesktopGameHubInput {
    private final DesktopGameHubControls controls = new DesktopGameHubControls();
    private final DesktopControllerFocusGate focusGate = new DesktopControllerFocusGate();
    private final JDialog dialog;
    private final JButton[] actions;
    private final Timer timer;

    public DesktopGameHubInput(JDialog dialog, DesktopGameHubPanel panel) {
        this.dialog = dialog;
        this.actions = new JButton[]{
                panel.playAction(), panel.favoriteAction(),
                panel.backupAction(), panel.closeAction()
        };
        if (dialog == null || panel == null)
            throw new IllegalArgumentException("Game Hub dialog is required");
        timer = new Timer(85, event -> pollController());
        timer.setCoalesce(true);

        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) {
                focusGate.disarm();
                controls.select(actions[0].isEnabled() ? 0 : 1);
                focusSelected();
                timer.start();
            }
            @Override public void windowClosed(WindowEvent e) {
                timer.stop();
                controls.reset();
                focusGate.disarm();
            }
        });

        var root = dialog.getRootPane();
        register(root, "LEFT", () -> move(DesktopConsoleNavigation.LEFT));
        register(root, "RIGHT", () -> move(DesktopConsoleNavigation.RIGHT));
        register(root, "UP", () -> move(DesktopConsoleNavigation.UP));
        register(root, "DOWN", () -> move(DesktopConsoleNavigation.DOWN));
        register(root, "ENTER", this::activate);
        register(root, "ESCAPE", dialog::dispose);
        // Space remains the standard accessibility key of a focused JButton.
    }

    private static void register(javax.swing.JRootPane root,
                                 String stroke, Runnable callback) {
        root.registerKeyboardAction(event -> callback.run(),
                KeyStroke.getKeyStroke(stroke),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    public int focusedIndex() { return controls.selected(); }
    public boolean isPolling() { return timer.isRunning(); }

    public void move(int events) {
        if (!dialog.isDisplayable()) return;
        controls.navigate(events);
        focusSelected();
    }

    private void focusSelected() {
        int index = controls.selected();
        // Skip unavailable actions. No blank focus trap for a missing ROM.
        for (int tries = 0; tries < actions.length && !actions[index].isEnabled(); tries++)
            index = (index + 1) % actions.length;
        controls.select(index);
        for (int i = 0; i < actions.length; i++)
            actions[i].getModel().setRollover(i == index);
        actions[index].requestFocusInWindow();
    }

    public void activate() {
        if (!dialog.isDisplayable()) return;
        JButton button = actions[controls.selected()];
        if (button.isEnabled()) button.doClick();
    }

    /** Called by the 85ms Swing timer only while the modal owns input. */
    private void pollController() {
        if (!dialog.isActive() || EmbeddedPlayerWindow.hasActiveSession()) {
            controls.reset();
            focusGate.disarm();
            return;
        }
        int mask;
        try {
            mask = WindowsRetroNative.gamepadMask();
        } catch (UnsatisfiedLinkError | RuntimeException unavailable) {
            timer.stop();
            return;
        }
        if (!focusGate.accepts(mask)) {
            controls.reset();
            return;
        }
        int events = controls.poll(mask, System.nanoTime());
        DesktopGameHubControls.Action action = controls.action(events);
        if (action == DesktopGameHubControls.Action.CLOSE) {
            dialog.dispose();
            return;
        }
        if (action == DesktopGameHubControls.Action.FAVORITE) {
            if (actions[1].isEnabled()) actions[1].doClick();
            return;
        }
        if (action == DesktopGameHubControls.Action.ACTIVATE) {
            activate();
            return;
        }
        if (events != 0) move(events);
    }
}
