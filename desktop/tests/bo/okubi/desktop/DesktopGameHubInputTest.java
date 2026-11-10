package bo.okubi.desktop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Deterministic Swing dialog test, without a connected XInput device. */
public final class DesktopGameHubInputTest {
    private static void yes(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("okubi-gamehub-input-");
        Path rom = directory.resolve("Pokemon Rojo Fuego.gba");
        Files.write(rom, new byte[]{1, 2, 3});
        DesktopLibrary library = new DesktopLibrary(directory.resolve("library.properties"));
        library.addGame(rom);
        DesktopLibrary.Game game = library.games().get(0);
        DesktopPlayStats stats = new DesktopPlayStats(directory.resolve("stats.properties"));

        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeAndWait(() -> {
            AtomicInteger plays = new AtomicInteger();
            AtomicInteger favorites = new AtomicInteger();
            AtomicInteger backups = new AtomicInteger();
            AtomicInteger closes = new AtomicInteger();
            JDialog dialog = new JDialog();
            DesktopGameHubPanel panel = new DesktopGameHubPanel(
                    game, stats.get(game.console, game.rom), null,
                    plays::incrementAndGet, favorites::incrementAndGet,
                    backups::incrementAndGet, closes::incrementAndGet);
            dialog.setContentPane(panel);
            dialog.pack();
            DesktopGameHubInput bridge = new DesktopGameHubInput(dialog, panel);
            yes(!bridge.isPolling(), "No background poll before dialog opens.");
            yes(bridge.focusedIndex() == 0, "Initial play focus.");
            bridge.activate();
            yes(plays.get() == 1, "A activates Play exactly once.");
            bridge.move(DesktopConsoleNavigation.RIGHT);
            yes(bridge.focusedIndex() == 1, "Right focuses Favorite.");
            bridge.activate();
            yes(favorites.get() == 1, "A activates favorite exactly once.");
            bridge.move(DesktopConsoleNavigation.NEXT_CATEGORY);
            bridge.activate();
            yes(backups.get() == 1, "RB focuses Backups.");
            bridge.move(DesktopConsoleNavigation.DOWN);
            bridge.activate();
            yes(closes.get() == 1, "Down reaches Close action.");
            yes(panel.closeAction().isEnabled(),
                    "Close remains keyboard and gamepad accessible.");
            dialog.dispose();
            yes(!bridge.isPolling(), "Disposed dialog has no active timer.");
            yes(Files.exists(rom), "Controller UI never deletes game ROM.");
            System.out.println("DesktopGameHubInputTest: PASS");
        });
    }
}
