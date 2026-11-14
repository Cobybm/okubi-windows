package bo.okubi.desktop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Real Swing rendering, actions, accessibility and card descendant clicks. */
public final class DesktopConsoleFocusTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static BufferedImage paint(CinemaLibraryCard card) {
        BufferedImage bitmap = new BufferedImage(410, 180,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = bitmap.createGraphics();
        card.paint(graphics);
        graphics.dispose();
        return bitmap;
    }

    private static void leftClick(java.awt.Component component, int count) {
        long now = System.currentTimeMillis();
        component.dispatchEvent(new MouseEvent(component,
                MouseEvent.MOUSE_PRESSED, now, InputEvent.BUTTON1_DOWN_MASK,
                8, 8, count, false, MouseEvent.BUTTON1));
        component.dispatchEvent(new MouseEvent(component,
                MouseEvent.MOUSE_CLICKED, now + 1, 0,
                8, 8, count, false, MouseEvent.BUTTON1));
    }

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeAndWait(() -> {
            AtomicInteger selections = new AtomicInteger();
            AtomicInteger launches = new AtomicInteger();
            AtomicInteger favorites = new AtomicInteger();

            CinemaLibraryCard card = new CinemaLibraryCard(
                    "Pokémon Esmeralda", RetroArtwork.accent(DesktopLibrary.Console.GBA));
            card.setSize(410, 180);
            card.setLayout(new BorderLayout(12, 0));
            RetroArtwork art = new RetroArtwork(
                    DesktopLibrary.Console.GBA, "Pokémon Esmeralda", false);
            card.add(art, BorderLayout.WEST);
            JPanel body = new JPanel();
            body.setOpaque(false);
            JLabel title = new JLabel("Pokémon Esmeralda");
            body.add(title);
            CinemaButton favorite = new CinemaButton("☆", false,
                    favorites::incrementAndGet);
            body.add(favorite);
            card.add(body, BorderLayout.CENTER);
            card.doLayout();
            body.doLayout();

            DesktopLibraryMouse.bind(card,
                    selections::incrementAndGet, launches::incrementAndGet);
            BufferedImage idle = paint(card);
            check(!card.isSelectedGame(), "Initially not selected");
            card.setSelected(true);
            BufferedImage focused = paint(card);
            check(card.isSelectedGame(), "Selection is expressed in the tile");
            check(idle.getRGB(4, 30) != focused.getRGB(4, 30),
                    "Selected tile is visibly distinguished in screenshot pixels");
            check(card.getAccessibleContext().getAccessibleDescription()
                    .contains("seleccionado"), "Selected game has accessibility description");
            check(!card.getToolTipText().isEmpty(), "Game title has meaningful tooltip");

            leftClick(art, 1);
            check(selections.get() == 1 && launches.get() == 0,
                    "Cover click selects without auto-launch");
            leftClick(title, 1);
            check(selections.get() == 2 && launches.get() == 0,
                    "Text click selects without launching");
            leftClick(card, 2);
            check(selections.get() == 3 && launches.get() == 1,
                    "Tile double-click launches exactly one game");

            favorite.doClick();
            check(favorites.get() == 1 && selections.get() == 3
                    && launches.get() == 1,
                    "Embedded favorite button still owns only its action");

            AtomicInteger filters = new AtomicInteger();
            CinemaCategoryTab tab = new CinemaCategoryTab(
                    "FAVORITOS", "FAVORITOS", filters::incrementAndGet);
            tab.setActiveCategory(true);
            check(tab.isActiveCategory(), "Selected category has active state");
            check(tab.category().equals("FAVORITOS"), "Categories retain stable IDs");
            check(tab.getAccessibleContext().getAccessibleDescription()
                    .contains("seleccionada"), "Active tab is screen-reader accessible");
            tab.setSize(135, 38);
            BufferedImage tabImage = new BufferedImage(135, 38,
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = tabImage.createGraphics();
            tab.paint(g);
            g.dispose();
            check(tabImage.getRGB(55, 15) != 0,
                    "Tab paints a dark Windows-safe background and text");
            tab.doClick();
            check(filters.get() == 1, "Tab click invokes a real filter action");
            tab.setActiveCategory(false);
            check(!tab.isActiveCategory(), "Inactive state is reset for other tabs");
            System.out.println("DesktopConsoleFocusTest: PASS");
        });
    }
}
