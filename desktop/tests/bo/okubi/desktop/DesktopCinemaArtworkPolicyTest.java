package bo.okubi.desktop;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Phase 77G offline QA; synthetic media only, no network or native emulator. */
public final class DesktopCinemaArtworkPolicyTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static Path image(Path path, Color fill) throws Exception {
        Files.createDirectories(path.getParent());
        BufferedImage image = new BufferedImage(128, 64, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(fill);
            graphics.fillRect(0, 0, 128, 64);
        } finally { graphics.dispose(); }
        check(ImageIO.write(image, "png", path.toFile()), "PNG test fixture");
        return path;
    }

    private static void waitFor(DesktopPortArtwork component) throws Exception {
        for (int i = 0; i < 160; i++) {
            final boolean[] ready = {false};
            SwingUtilities.invokeAndWait(() -> ready[0] = component.hasImage());
            if (ready[0]) return;
            Thread.sleep(15);
        }
        throw new AssertionError("Image did not load from synthetic cache");
    }

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("okubi-phase77g-media-");
        var openttd = DesktopPortArtworkService.forPort(
                DesktopPortsCatalog.byId("openttd"));
        var openra = DesktopPortArtworkService.forPort(
                DesktopPortsCatalog.byId("openra"));
        var unknown = new DesktopPortArtworkService.Ref("sample-owner", "sample-project");
        check(DesktopCinemaArtworkPolicy.source(null, openttd)
                        == DesktopCinemaArtworkPolicy.Source.REVIEWED_UPSTREAM,
                "Curated port has official reviewed source");
        check(DesktopCinemaArtworkPolicy.galleryCount(openra) == 3,
                "OpenRA has its own three verified captures");
        check(DesktopCinemaArtworkPolicy.galleryCount(unknown) == 0,
                "Unknown port has no borrowed official captures");
        check(DesktopCinemaArtworkPolicy.galleryHint(unknown).isEmpty(),
                "Unknown port has no fabricated gallery");
        check(DesktopCinemaArtworkPolicy.galleryHint(openra).equals("3 CAPTURAS"),
                "Show actual available gallery count");
        check(DesktopCinemaArtworkPolicy.galleryHint(null).isEmpty(),
                "Null identity has no artwork");
        check(DesktopCinemaArtworkPolicy.source(null, null)
                        == DesktopCinemaArtworkPolicy.Source.UNVERIFIED,
                "No identity never implies an official image");

        try (var artwork = new DesktopPortArtworkService(temp.resolve("cache"))) {
            check(!artwork.hasManualChoice(openttd), "Manual image not invented");
            check(DesktopCinemaArtworkPolicy.source(artwork, unknown)
                            == DesktopCinemaArtworkPolicy.Source.UNVERIFIED,
                    "Unknown GitHub port must show neutral status");
            check(DesktopCinemaArtworkPolicy.label(
                            DesktopCinemaArtworkPolicy.Source.UNVERIFIED)
                            .equals("SIN CAPTURA VERIFICADA"),
                    "Missing sources visibly distinguish from reviewed ones");
            Path local = image(temp.resolve("my-custom-art.png"), Color.YELLOW);
            byte[] original = Files.readAllBytes(local);
            artwork.chooseManual(unknown, local);
            check(artwork.hasManualChoice(unknown), "Custom image is recognized");
            check(DesktopCinemaArtworkPolicy.source(artwork, unknown)
                            == DesktopCinemaArtworkPolicy.Source.PERSONAL,
                    "Personal image has priority even for uncurated GitHub ports");
            check(DesktopCinemaArtworkPolicy.label(
                            DesktopCinemaArtworkPolicy.Source.PERSONAL)
                            .equals("IMAGEN PERSONAL"),
                    "Personal source clearly labeled");
            artwork.chooseManual(openttd, local);
            check(DesktopCinemaArtworkPolicy.source(artwork, openttd)
                            == DesktopCinemaArtworkPolicy.Source.PERSONAL,
                    "Personal image outranks the official picture");

            Path official = image(artwork.cachePath(openra), Color.GREEN);
            final DesktopPortArtwork[] small = new DesktopPortArtwork[1];
            SwingUtilities.invokeAndWait(() -> small[0] =
                    new DesktopPortArtwork(artwork, openra, 110, 147, 0, true));
            waitFor(small[0]);
            check(small[0].getComponentCount() == 0,
                    "Compact shelf does not add crowded gallery arrow controls");
            BufferedImage rendered = new BufferedImage(110, 147, BufferedImage.TYPE_INT_ARGB);
            SwingUtilities.invokeAndWait(() -> {
                small[0].setSize(110, 147);
                var g = rendered.createGraphics();
                try { small[0].paint(g); }
                finally { g.dispose(); }
            });
            check(new Color(rendered.getRGB(50, 72), true).getGreen() > 190,
                    "Center shows expected correctly matched OpenRA picture");
            check(new Color(rendered.getRGB(50, 18), true).getGreen() < 150,
                    "Wide screenshot is fully fitted, not aggressively cropped");
            check(!artwork.cachePath(openttd).equals(artwork.cachePath(openra)),
                    "Independent game caches never overlap");
            check(Files.exists(official), "Read-only shelf never removes upstream cache");
            check(Arrays.equals(original, Files.readAllBytes(local)),
                    "Original selected image stays bit-for-bit unchanged");
            artwork.clearManual(unknown);
            check(DesktopCinemaArtworkPolicy.source(artwork, unknown)
                            == DesktopCinemaArtworkPolicy.Source.UNVERIFIED,
                    "Removing personal image does not substitute another game's image");
            check(DesktopCinemaArtworkPolicy.source(artwork, openttd)
                            == DesktopCinemaArtworkPolicy.Source.PERSONAL,
                    "Removing another game's personalization cannot change this game's choice");
        }
        check(DesktopPortArtworkService.forLibraryId("retro:gb") == null,
                "Retro ROM never misclassified as PC artwork");
        check(DesktopPortArtworkService.forLibraryId("pc:github.sample-owner/sample-project")
                        .equals(unknown),
                "Unified shelf uses the exact discovered repo identity");
        System.out.println("DesktopCinemaArtworkPolicyTest: PASS");
    }
}
