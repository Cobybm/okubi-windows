package bo.okubi.desktop;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/** Offline viewer QA: never opens a real window or downloads any game image. */
public final class DesktopPortGalleryViewerTest {
    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }

    private static Path png(Path output, Color color) throws Exception {
        Files.createDirectories(output.getParent());
        BufferedImage bitmap = new BufferedImage(90, 55, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D paint = bitmap.createGraphics();
        paint.setColor(color);
        paint.fillRect(0, 0, 90, 55);
        paint.dispose();
        check(ImageIO.write(bitmap, "png", output.toFile()), "Synthetic PNG fixture");
        return output;
    }

    private static void ready(DesktopPortArtwork view, int index) throws Exception {
        for (int i = 0; i < 200; i++) {
            boolean[] loaded = {false};
            SwingUtilities.invokeAndWait(() -> loaded[0] =
                    view.hasImage() && view.selectedImageIndex() == index);
            if (loaded[0]) return;
            Thread.sleep(15);
        }
        throw new AssertionError("Timed out loading synthetic gallery image");
    }

    public static void main(String[] args) throws Exception {
        int left = DesktopConsoleNavigation.LEFT;
        int right = DesktopConsoleNavigation.RIGHT;
        int back = DesktopConsoleNavigation.BACK;
        int rb = DesktopConsoleNavigation.NEXT_CATEGORY;
        int lb = DesktopConsoleNavigation.PREVIOUS_CATEGORY;
        check(DesktopPortGalleryInput.interpret(left)
                == DesktopPortGalleryInput.Command.PREVIOUS, "Dpad left");
        check(DesktopPortGalleryInput.interpret(right)
                == DesktopPortGalleryInput.Command.NEXT, "Dpad right");
        check(DesktopPortGalleryInput.interpret(lb)
                == DesktopPortGalleryInput.Command.PREVIOUS, "LB previous");
        check(DesktopPortGalleryInput.interpret(rb)
                == DesktopPortGalleryInput.Command.NEXT, "RB next");
        check(DesktopPortGalleryInput.interpret(back | right)
                == DesktopPortGalleryInput.Command.CLOSE, "B has priority");
        check(DesktopPortGalleryInput.interpret(left | right)
                == DesktopPortGalleryInput.Command.NONE, "Conflicting directions ignored");
        check(DesktopPortGalleryInput.interpret(0)
                == DesktopPortGalleryInput.Command.NONE, "Neutral input inert");

        Path temp = Files.createTempDirectory("okubi-viewer77f");
        var ref = DesktopPortArtworkService.forPort(DesktopPortsCatalog.byId("openra"));
        try (var service = new DesktopPortArtworkService(temp.resolve("artwork"))) {
            png(service.cachePath(ref), Color.RED);
            png(service.galleryCachePath(ref, 1), Color.GREEN);
            png(service.galleryCachePath(ref, 2), Color.BLUE);
            DesktopPortArtwork[] view = new DesktopPortArtwork[1];
            SwingUtilities.invokeAndWait(() -> view[0] =
                    new DesktopPortArtwork(service, ref, 640, 420, 1, true));
            ready(view[0], 1);
            check(view[0].gallerySize() == 3, "Full viewer inherits source gallery");
            check(view[0].getToolTipText() == null, "No obstructive tooltip");
            check(view[0].selectedImageIndex() == 1,
                    "Viewer starts from selected small-card image");
            AtomicInteger opens = new AtomicInteger();
            SwingUtilities.invokeAndWait(() -> {
                view[0].enableViewer(opens::incrementAndGet);
                check(view[0].isFocusable(), "Keyboard accessible gallery trigger");
                check(view[0].getCursor().getType() == Cursor.HAND_CURSOR,
                        "Image uses clickable cursor");
                Object enter = view[0].getInputMap(JComponent.WHEN_FOCUSED)
                        .get(KeyStroke.getKeyStroke("ENTER"));
                Object space = view[0].getInputMap(JComponent.WHEN_FOCUSED)
                        .get(KeyStroke.getKeyStroke("SPACE"));
                check(enter != null && space != null && enter.equals(space),
                        "Enter and Space both open image");
                view[0].getActionMap().get(enter).actionPerformed(
                        new java.awt.event.ActionEvent(view[0], 0, "open"));
                var click = new MouseEvent(view[0], MouseEvent.MOUSE_CLICKED,
                        12345, 0, 12, 12, 1, false, MouseEvent.BUTTON1);
                for (var listener : view[0].getMouseListeners())
                    listener.mouseClicked(click);
                view[0].setSize(640, 420);
                view[0].doLayout();
            });
            check(opens.get() == 2, "Mouse click and keyboard action open exactly once");
            BufferedImage frame = new BufferedImage(640, 420, BufferedImage.TYPE_INT_ARGB);
            SwingUtilities.invokeAndWait(() -> {
                var g = frame.createGraphics();
                try { view[0].paint(g); }
                finally { g.dispose(); }
            });
            check(new Color(frame.getRGB(320, 160), true).getGreen() > 200,
                    "Large image is rendered in center of viewer");
            // Full fit keeps margin background beside a wide small screenshot,
            // instead of cropping its contents to fill the entire modal.
            check(new Color(frame.getRGB(10, 140), true).getGreen() < 190,
                    "Viewer preserves whole image rather than center crop");
            var userImage = png(temp.resolve("my-image.png"), Color.YELLOW);
            byte[] original = Files.readAllBytes(userImage);
            var manual = service.chooseManual(ref, userImage);
            SwingUtilities.invokeAndWait(() -> view[0].showImage(manual));
            check(view[0].selectedImageIndex() == 0,
                    "Manual replacement restores first gallery position");
            check(java.util.Arrays.equals(original, Files.readAllBytes(userImage)),
                    "Original custom image was not changed");
            check(Files.isRegularFile(service.galleryCachePath(ref, 1)),
                    "Secondary image survives manual override");
        }
        System.out.println("DesktopPortGalleryViewerTest: PASS");
    }
}
