package bo.okubi.desktop;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

/** 77H: deterministic audited-game-image mapping tests, no network or saved-game files. */
public final class DesktopVerifiedArtworkExpansionTest {
    private static void check(boolean good, String message) {
        if (!good) throw new AssertionError(message);
    }

    private static Path image(Path path, Color color) throws Exception {
        Files.createDirectories(path.getParent());
        BufferedImage bitmap = new BufferedImage(48, 32, BufferedImage.TYPE_INT_RGB);
        var graphics = bitmap.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        } finally { graphics.dispose(); }
        check(ImageIO.write(bitmap, "png", path.toFile()), "Synthetic PNG");
        return path;
    }

    private static void expectGallery(DesktopPortsCatalog.Port port, String officialRepo,
                                      String exactPathFragment) {
        var ref = DesktopPortArtworkService.forPort(port);
        List<URI> gallery = DesktopOfficialPortArtwork.gallery(ref);
        check(gallery.size() == 3, "Three exact screenshots for " + port.id());
        check(gallery.stream().distinct().count() == 3, "No duplicates for " + port.id());
        for (URI url : gallery) {
            check(DesktopOfficialPortArtwork.permitted(url), "Pinned resource must be allowed");
            check(url.getRawPath().startsWith("/" + officialRepo + "/"),
                    "Every screenshot comes from the correct project's repository");
            check(url.getRawPath().matches("/[^/]+/[^/]+/[0-9a-f]{40}/.+\\.(png|jpg)"),
                    "Screenshot is pinned by an immutable 40-char commit");
            check(url.getRawPath().contains(exactPathFragment),
                    "Screenshot must be sourced from project's own gameplay media directory");
            check(url.getRawQuery() == null && url.getRawFragment() == null,
                    "No URL query or fragments");
        }
    }

    public static void main(String[] args) throws Exception {
        check(DesktopPortsCatalog.all().size() == 32,
                "Port catalog count is unchanged");
        check(DesktopOfficialPortArtwork.curatedCount() == 18,
                "Exactly 18 project-owned sources, no guessed coverage");
        check(DesktopOfficialPortArtwork.galleryImageCount() == 40,
                "18 primary and 22 secondary verified images");
        var gzdoom = DesktopPortsCatalog.byId("gzdoom");
        var vcmi = DesktopPortsCatalog.byId("vcmi");
        var scummvm = DesktopPortsCatalog.byId("scummvm");
        var forceEngine = DesktopPortsCatalog.byId("theforceengine");
        var xash = DesktopPortsCatalog.byId("xash3d");
        expectGallery(vcmi, "vcmi/VCMI.eu", "/static/img/screenshots/");
        expectGallery(gzdoom, "ZDoom/gzdoom", "/src/posix/freedesktop/screenshot_");
        expectGallery(scummvm, "scummvm/scummvm", "/doc/docportal/images/Launcher/");
        expectGallery(forceEngine, "TheForceEngine/TheForceEngine.github.io", "/screenshots/DarkForces");
        // Xash3D documentation includes genuine GoldSrc gameplay with its own touch UI.
        // Generic logos and unrelated project screenshots must never be substituted.
        var xashRef = DesktopPortArtworkService.forPort(xash);
        var xashGallery = DesktopOfficialPortArtwork.gallery(xashRef);
        check(xashGallery.size() == 1, "One visually audited Xash3D in-engine screenshot");
        URI xashScreenshot = URI.create(
                "https://raw.githubusercontent.com/FWGS/xash3d-fwgs/"
                + "707596f4905689ec224e993fcaa4d4ad66aa67d6/"
                + "Documentation/images/ios-touch-ingame.jpg");
        check(xashGallery.get(0).equals(xashScreenshot),
                "Xash3D must use the exact upstream gameplay capture");
        check(DesktopOfficialPortArtwork.permitted(xashScreenshot),
                "Pinned Xash3D gameplay capture is allowlisted");
        check(!DesktopOfficialPortArtwork.permitted(URI.create(
                "https://raw.githubusercontent.com/other/game/"
                + "707596f4905689ec224e993fcaa4d4ad66aa67d6/"
                + "Documentation/images/ios-touch-ingame.jpg")),
                "Never accept images copied under a different game identity");
        check(DesktopOfficialPortArtwork.gallery(
                DesktopPortArtworkService.forPort(DesktopPortsCatalog.byId("openmw")))
                .isEmpty(), "OpenMW must not receive an unrelated image or game cover");
        check(DesktopOfficialPortArtwork.gallery(scummvm == null ? null
                : DesktopPortArtworkService.forPort(scummvm)).stream().allMatch(
                        url -> url.getPath().contains("/Launcher/")),
                "ScummVM uses its own launcher, not an arbitrary adventure-game screenshot");
        check(DesktopOfficialPortArtwork.gallery(
                DesktopPortArtworkService.forPort(DesktopPortsCatalog.byId("dosboxstaging")))
                .isEmpty(), "DOSBox should not use artwork from an unrelated DOS game");
        check(!DesktopOfficialPortArtwork.permitted(URI.create(
                "https://raw.githubusercontent.com/ZDoom/gzdoom/main/src/posix/freedesktop/screenshot_doom.png")),
                "Mutable branch and unreviewed URL are not allowed");
        check(!DesktopOfficialPortArtwork.permitted(URI.create(
                "https://opengraph.githubassets.com/1/ZDoom/gzdoom")),
                "GitHub social cards are never gameplay images");

        Path temp = Files.createTempDirectory("okubi-phase77h-synthetic");
        try (var service = new DesktopPortArtworkService(temp.resolve("artwork"))) {
            var vcmiRef = DesktopPortArtworkService.forPort(vcmi);
            var gzRef = DesktopPortArtworkService.forPort(gzdoom);
            var xashCacheRef = DesktopPortArtworkService.forPort(xash);
            var vcmiPhoto = image(service.cachePath(vcmiRef), Color.GREEN);
            var gzPhoto = image(service.cachePath(gzRef), Color.BLUE);
            var xashPhoto = image(service.cachePath(xashCacheRef), Color.CYAN);
            image(service.galleryCachePath(vcmiRef, 1), Color.YELLOW);
            image(service.galleryCachePath(gzRef, 1), Color.RED);
            check(!vcmiPhoto.equals(gzPhoto), "Primary images have separate caches");
            check(!xashPhoto.equals(gzPhoto) && !xashPhoto.equals(vcmiPhoto),
                    "Xash3D screenshot cache cannot collide with another port");
            check(!service.galleryCachePath(vcmiRef, 1)
                    .equals(service.galleryCachePath(gzRef, 1)),
                    "Extra gallery images have separate caches");
            check(service.localGallery(vcmiRef, 1).getRGB(10, 10)
                    == Color.YELLOW.getRGB(), "VCMI cannot read the GZDoom cache");
            check(service.localGallery(gzRef, 1).getRGB(10, 10)
                    == Color.RED.getRGB(), "GZDoom cannot read the VCMI cache");

            Path selected = image(temp.resolve("custom.png"), Color.MAGENTA);
            byte[] original = Files.readAllBytes(selected);
            service.chooseManual(vcmiRef, selected);
            check(service.localGallery(vcmiRef, 0).getRGB(10, 10)
                    == Color.MAGENTA.getRGB(), "Personal override takes precedence");
            check(service.localGallery(vcmiRef, 1).getRGB(10, 10)
                    == Color.YELLOW.getRGB(), "Secondary official screenshot is not erased");
            check(service.localGallery(gzRef, 0).getRGB(10, 10)
                    == Color.BLUE.getRGB(), "GZDoom cover is unchanged");
            check(Arrays.equals(original, Files.readAllBytes(selected)),
                    "Never edit original personal image");
            service.clearManual(vcmiRef);
            check(service.localGallery(vcmiRef, 0).getRGB(10, 10)
                    == Color.GREEN.getRGB(), "Clearing override returns to verified cache");
            check(Files.exists(gzPhoto), "Other port cache must survive");
            check(service.local(xashCacheRef).getRGB(10, 10) == Color.CYAN.getRGB(),
                    "Xash3D screenshot stays separate from manual VCMI image");
        }
        System.out.println("DesktopVerifiedArtworkExpansionTest: PASS");
    }
}
