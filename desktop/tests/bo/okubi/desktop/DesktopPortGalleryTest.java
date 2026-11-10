package bo.okubi.desktop;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Offline deterministic gallery QA: no HTTP, no ROMs and no user data. */
public final class DesktopPortGalleryTest {
    private static void check(boolean yes, String why) {
        if (!yes) throw new AssertionError(why);
    }

    private static Path png(Path path, Color color) throws Exception {
        Files.createDirectories(path.getParent());
        BufferedImage image = new BufferedImage(48, 30, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, 48, 30);
        } finally {
            graphics.dispose();
        }
        check(ImageIO.write(image, "png", path.toFile()), "Synthetic PNG fixture");
        return path;
    }

    private static Color paintPixel(DesktopPortArtwork gallery) throws Exception {
        BufferedImage frame = new BufferedImage(420, 155, BufferedImage.TYPE_INT_ARGB);
        SwingUtilities.invokeAndWait(() -> {
            gallery.setSize(420, 155);
            gallery.doLayout();
            java.awt.Graphics2D graphics = frame.createGraphics();
            try { gallery.paint(graphics); }
            finally { graphics.dispose(); }
        });
        return new Color(frame.getRGB(190, 45), true);
    }

    private static void waitFor(DesktopPortArtwork gallery, int index) throws Exception {
        for (int tick = 0; tick < 150; tick++) {
            final boolean[] ready = { false };
            SwingUtilities.invokeAndWait(() -> ready[0] =
                    gallery.selectedImageIndex() == index && gallery.hasImage());
            if (ready[0]) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Gallery did not load image " + index);
    }

    public static void main(String[] args) throws Exception {
        var refs = java.util.List.of(
                DesktopPortsCatalog.byId("openra"),
                DesktopPortsCatalog.byId("openloco"),
                DesktopPortsCatalog.byId("corsixth"),
                DesktopPortsCatalog.byId("endlesssky"),
                DesktopPortsCatalog.byId("supertuxkart"),
                DesktopPortsCatalog.byId("openttd"),
                DesktopPortsCatalog.byId("warzone2100"),
                DesktopPortsCatalog.byId("arxlibertatis"));
        int multi = 0;
        for (var port : refs) {
            var ref = DesktopPortArtworkService.forPort(port);
            var gallery = DesktopOfficialPortArtwork.gallery(ref);
            check(gallery.size() >= 2 && gallery.size() <= 3,
                    "Gallery photos are bounded for " + port.id());
            check(gallery.get(0).equals(DesktopOfficialPortArtwork.source(ref)),
                    "Existing primary image remains the first gallery entry");
            check(gallery.stream().distinct().count() == gallery.size(),
                    "No repeated images in gallery");
            for (var url : gallery) {
                check(DesktopOfficialPortArtwork.permitted(url), "Only exact pinned sources");
                check(url.getPath().matches("/[^/]+/[^/]+/[0-9a-f]{40}/.+\\.(png|jpg)"),
                        "Full immutable upstream commit");
                multi++;
            }
        }
        check(DesktopOfficialPortArtwork.curatedCount() == 13, "All 13 projects retained");
        check(DesktopOfficialPortArtwork.galleryImageCount() == 27,
                "14 extra images plus 13 original ones");
        check(DesktopOfficialPortArtwork.gallery(
                new DesktopPortArtworkService.Ref("random", "unverified")).isEmpty(),
                "Unknown project never gets artwork of another game");
        check(!DesktopOfficialPortArtwork.permitted(
                java.net.URI.create("https://opengraph.githubassets.com/1/OpenRA/OpenRA")),
                "Never fall back to a GitHub social-preview card");
        check(multi == 22, "Eight galleries contribute 22 approved images");

        Path folder = Files.createTempDirectory("okubi-gallery-fixture");
        var ref = DesktopPortArtworkService.forPort(DesktopPortsCatalog.byId("openra"));
        try (var service = new DesktopPortArtworkService(folder.resolve("artwork"))) {
            png(service.cachePath(ref), Color.RED);
            Path firstExtra = png(service.galleryCachePath(ref, 1), Color.GREEN);
            Path secondExtra = png(service.galleryCachePath(ref, 2), Color.BLUE);
            check(!firstExtra.equals(secondExtra), "Separate cache for each screenshot");
            check(!service.galleryCachePath(ref, 1).equals(service.cachePath(ref)),
                    "Primary cover cache remains unmodified");
            check(service.localGallery(ref, 1).getRGB(10, 10) == Color.GREEN.getRGB(),
                    "Offline gallery reads the first extra image");
            check(service.localGallery(ref, 2).getRGB(10, 10) == Color.BLUE.getRGB(),
                    "Offline gallery reads the second extra image");
            check(service.localGallery(ref, 3) == null, "Out-of-range image blocked");
            check(service.localGallery(
                    new DesktopPortArtworkService.Ref("other", "repo"), 1) == null,
                    "Other port cannot reuse this game's gallery");
            final DesktopPortArtwork[] instance = new DesktopPortArtwork[1];
            SwingUtilities.invokeAndWait(() -> instance[0] =
                    new DesktopPortArtwork(service, ref, 420, 155));
            var panel = instance[0];
            check(panel.gallerySize() == 3, "Three OpenRA images visible in navigation");
            check(panel.getToolTipText() == null, "No tooltip obscures port controls");
            waitFor(panel, 0);
            check(paintPixel(panel).getRed() > 210, "First image is primary");
            SwingUtilities.invokeAndWait(() -> panel.moveGallery(1));
            waitFor(panel, 1);
            check(paintPixel(panel).getGreen() > 210, "Next image has its own pixels");
            SwingUtilities.invokeAndWait(() -> panel.moveGallery(1));
            waitFor(panel, 2);
            check(paintPixel(panel).getBlue() > 210, "Third image is independent");
            SwingUtilities.invokeAndWait(() -> {
                panel.moveGallery(1);
                panel.moveGallery(1);
            });
            waitFor(panel, 1);
            check(paintPixel(panel).getGreen() > 210,
                    "Latest navigation request wins over stale async results");
            Path own = png(folder.resolve("my-picture.png"), Color.YELLOW);
            byte[] original = Files.readAllBytes(own);
            var chosen = service.chooseManual(ref, own);
            SwingUtilities.invokeAndWait(() -> panel.showImage(chosen));
            check(panel.selectedImageIndex() == 0, "Manual override returns to first slide");
            check(service.local(ref).getRGB(1, 1) == Color.YELLOW.getRGB(),
                    "User artwork always takes precedence");
            check(service.localGallery(ref, 1).getRGB(1, 1) == Color.GREEN.getRGB(),
                    "User artwork does not erase gallery");
            check(Arrays.equals(original, Files.readAllBytes(own)),
                    "Never edit user's original image");
            service.clearManual(ref);
            check(service.local(ref).getRGB(1, 1) == Color.RED.getRGB(),
                    "Removing custom image returns to unchanged primary cache");
            check(Files.exists(firstExtra) && Files.exists(secondExtra),
                    "Removing override cannot delete extra screenshots");
        }
        System.out.println("DesktopPortGalleryTest: PASS");
    }
}
