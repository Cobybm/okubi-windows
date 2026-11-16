package bo.okubi.desktop;

import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;

/**
 * Lets a user select a game by clicking its art, title or empty tile space.
 * Never captures embedded JButtons, so Play/Favorite keep their own callbacks.
 */
public final class DesktopLibraryMouse {
    private DesktopLibraryMouse() { }

    public static void bind(CinemaLibraryCard card, Runnable select, Runnable launch) {
        MouseAdapter listener = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) select.run();
            }

            @Override public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)
                        && event.getClickCount() == 2) launch.run();
            }

            @Override public void mouseEntered(MouseEvent event) {
                card.setHovered(true);
            }

            @Override public void mouseExited(MouseEvent event) {
                Component origin = (Component) event.getSource();
                Point point = SwingUtilities.convertPoint(origin, event.getPoint(), card);
                if (!card.contains(point)) card.setHovered(false);
            }
        };
        attach(card, listener);
    }

    private static void attach(Component component, MouseAdapter listener) {
        if (component instanceof AbstractButton) return;
        component.addMouseListener(listener);
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) attach(child, listener);
        }
    }
}
