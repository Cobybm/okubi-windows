package bo.okubi.desktop;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Deterministic tests: zero HTTP calls, zero ROMs/saves, synthetic PNG only. */
public final class DesktopPortArtworkServiceTest {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static Path png(Path target, int r, int g, int b) throws Exception {
        Files.createDirectories(target.getParent());
        BufferedImage image = new BufferedImage(32, 20, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D paint = image.createGraphics();
        paint.setColor(new Color(r, g, b));
        paint.fillRect(0, 0, 32, 20);
        paint.dispose();
        if (!ImageIO.write(image, "png", target.toFile()))
            throw new AssertionError("PNG fixture");
        return target;
    }

    static void rejects(Checked action, String message) throws Exception {
        try { action.run(); throw new AssertionError("Allowed bad input: " + message); }
        catch (IOException | IllegalArgumentException expected) { }
    }

    interface Checked { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("okubi-artwork77-");
        DesktopPortArtworkService.Ref ref = new DesktopPortArtworkService.Ref(
                "arx", "ArxLibertatis");
        check(ref.preview().toString().equals(
                "https://opengraph.githubassets.com/1/arx/ArxLibertatis"),
                "Official GitHub preview only");
        DesktopPortArtworkService.Ref devilution =
                DesktopPortArtworkService.forPort(DesktopPortsCatalog.byId("devilutionx"));
        DesktopPortArtworkService.Ref augustus =
                DesktopPortArtworkService.forPort(DesktopPortsCatalog.byId("augustus"));
        DesktopPortArtworkService.Ref dhewm =
                DesktopPortArtworkService.forPort(DesktopPortsCatalog.byId("dhewm3"));
        check(DesktopOfficialPortArtwork.curatedCount() == 15,
                "Fifteen reviewed and project-matched visual sources");
        for (var pin : java.util.List.of(devilution, augustus, dhewm,
                new DesktopPortArtworkService.Ref("arx", "ArxLibertatis"),
                new DesktopPortArtworkService.Ref("OpenTTD", "OpenTTD"),
                new DesktopPortArtworkService.Ref("Warzone2100", "warzone2100"),
                new DesktopPortArtworkService.Ref("luanti-org", "luanti"),
                new DesktopPortArtworkService.Ref("OpenRA", "OpenRA"),
                new DesktopPortArtworkService.Ref("vcmi", "vcmi"),
                new DesktopPortArtworkService.Ref("ZDoom", "gzdoom"),
                new DesktopPortArtworkService.Ref("OpenRCT2", "OpenRCT2"),
                new DesktopPortArtworkService.Ref("OpenLoco", "OpenLoco"),
                new DesktopPortArtworkService.Ref("CorsixTH", "CorsixTH"),
                new DesktopPortArtworkService.Ref("endless-sky", "endless-sky"),
                new DesktopPortArtworkService.Ref("supertuxkart", "stk-code"))) {
            var url = DesktopOfficialPortArtwork.source(pin);
            check(url != null && DesktopOfficialPortArtwork.permitted(url),
                    "Pinned upstream project source must be GitHub raw");
            check(url.getPath().matches(
                    "/[^/]+/[^/]+/[0-9a-f]{40}/.+\\.(png|jpg)"),
                    "Project screenshots must use immutable 40-digit commit IDs");
        }
        check(DesktopOfficialPortArtwork.source(
                DesktopPortArtworkService.forPort(
                        DesktopPortsCatalog.byId("arxlibertatis")))
                        .getPath().contains("castle-of-arx-2x.jpg"),
                "Arx Libertatis must prefer official game screenshot to white GitHub page");
        check(DesktopOfficialPortArtwork.source(
                DesktopPortArtworkService.forPort(
                        DesktopPortsCatalog.byId("openttd")))
                        .getPath().contains("_screenshots/"),
                "OpenTTD must use its official gallery, not GitHub social card");
        check(DesktopOfficialPortArtwork.source(
                DesktopPortArtworkService.forPort(
                        DesktopPortsCatalog.byId("warzone2100")))
                        .getPath().contains("doc/images/"),
                "Warzone2100 must use upstream gameplay imagery");
        DesktopPortArtworkService.Ref unreviewed =
                new DesktopPortArtworkService.Ref("UnknownOwner", "UnknownGame");
        check(DesktopOfficialPortArtwork.source(unreviewed) == null,
                "Unreviewed projects must not get unrelated artwork");
        for (DesktopPortsCatalog.Port port : DesktopPortsCatalog.all()) {
            var portRef = DesktopPortArtworkService.forPort(port);
            var expected = DesktopOfficialPortArtwork.source(portRef);
            if (expected != null) {
                check(DesktopOfficialPortArtwork.permitted(expected),
                        "Official image must be an exact allowlisted source");
                check(expected.getPath().matches(
                        "/[^/]+/[^/]+/[0-9a-f]{40}/.+\\.(png|jpg)"),
                        "Official source must be pinned to an immutable Git SHA");
            }
        }
        check(DesktopOfficialPortArtwork.source(
                DesktopPortArtworkService.forPort(DesktopPortsCatalog.byId("openra")))
                .getPath().endsWith("/images/appdata/ingame-ra.png"),
                "OpenRA shows gameplay, not an installation graphic");
        check(DesktopOfficialPortArtwork.source(
                DesktopPortArtworkService.forPort(DesktopPortsCatalog.byId("openrct2")))
                .getPath().endsWith("/assets/img/screenshot.png"),
                "OpenRCT2 only shows its own actual screenshot");
        check(!DesktopOfficialPortArtwork.permitted(
                java.net.URI.create("https://example.com/art.png")),
                "Never accept arbitrary image domain");
        check(!DesktopOfficialPortArtwork.permitted(
                java.net.URI.create("https://raw.githubusercontent.com/evil/owner/main/a.png")),
                "Never accept unknown raw files");
        check(DesktopPortArtworkService.forPort(
                DesktopPortsCatalog.byId("arxlibertatis")).equals(ref),
                "Curated artwork identity");
        check(DesktopPortArtworkService.forLibraryId("pc:port.arxlibertatis")
                .equals(ref), "CINEMA curated port identification");
        check(DesktopPortArtworkService.forLibraryId(
                "pc:github.arx/arxlibertatis").fullName()
                .equals("arx/arxlibertatis"), "CINEMA discovered GitHub identity");
        check(DesktopPortArtworkService.forLibraryId("pc:../roms") == null,
                "Reject malformed catalog identities");
        check(DesktopPortArtworkService.forLibraryId("retro:gb") == null,
                "Never treat a retro ROM as a GitHub repository");
        rejects(() -> new DesktopPortArtworkService.Ref("https://evil", "repo"),
                "Owner path injection");
        rejects(() -> new DesktopPortArtworkService.Ref("valid", "../private"),
                "Repository traversal");
        rejects(() -> new DesktopPortArtworkService.Ref("owner", "repo/name"),
                "Repository path injection");
        check(DesktopPortArtworkService.key(ref).matches("[0-9a-f]{64}"),
                "Cache filename is a SHA-256 digest");
        check(!DesktopPortArtworkService.key(ref).contains("arx"),
                "Cache path does not disclose source repository names");

        try (DesktopPortArtworkService service = new DesktopPortArtworkService(folder.resolve("cache"))) {
            Path source = png(folder.resolve("source-image.png"), 220, 40, 110);
            byte[] preserved = Files.readAllBytes(source);
            Path remote = png(service.cachePath(ref), 25, 90, 190);
            check(service.local(ref) != null, "Available cache loads without network");
            check(service.local(ref).getRGB(1, 1) == new Color(25, 90, 190).getRGB(),
                    "Offline cache image is valid");
            BufferedImage manual = service.chooseManual(ref, source);
            check(manual.getRGB(1, 1) == new Color(220, 40, 110).getRGB(),
                    "User-selected override has expected pixels");
            check(service.local(ref).getRGB(1, 1) == manual.getRGB(1, 1),
                    "Manual override takes precedence over GitHub cache");
            check(Arrays.equals(Files.readAllBytes(source), preserved),
                    "Original local image remains unchanged");

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<BufferedImage> received = new AtomicReference<>();
            AtomicBoolean correctThread = new AtomicBoolean(false);
            service.load(ref, image -> {
                correctThread.set(SwingUtilities.isEventDispatchThread());
                received.set(image);
                latch.countDown();
            });
            check(latch.await(6, TimeUnit.SECONDS), "Async cache result delivered");
            check(correctThread.get(), "Updates must run on Swing EDT");
            check(received.get().getRGB(1, 1) == manual.getRGB(1, 1),
                    "No network needed for manual override");

            CountDownLatch refreshed = new CountDownLatch(1);
            service.refresh(ref, image -> {
                received.set(image);
                refreshed.countDown();
            });
            check(refreshed.await(6, TimeUnit.SECONDS), "Manual refresh is async");
            check(received.get().getRGB(1, 1) == manual.getRGB(1, 1),
                    "Refresh must never override chosen manual image");

            service.clearManual(ref);
            check(service.local(ref).getRGB(1, 1)
                    == new Color(25, 90, 190).getRGB(), "Remove manual returns to offline cache");
            check(Files.exists(remote), "Clear override must not delete original cache");

            Path invalid = folder.resolve("invalid.png");
            Files.writeString(invalid, "NOT AN IMAGE");
            rejects(() -> service.chooseManual(ref, invalid), "Corrupted image rejected");
            Path oversized = folder.resolve("large.png");
            Files.write(oversized, new byte[DesktopPortArtworkService.MAX_BYTES + 1]);
            rejects(() -> service.chooseManual(ref, oversized), "Oversized file rejected");
            try {
                Path link = folder.resolve("image-link.png");
                Files.createSymbolicLink(link, source);
                rejects(() -> service.chooseManual(ref, link), "Symlink image rejected");
            } catch (UnsupportedOperationException | java.nio.file.FileSystemException denied) {
                // Windows CI can prohibit symlink creation without developer mode.
            }

            check(!service.cachePath(unreviewed).equals(service.cachePath(devilution)),
                    "Curated upstream screenshot must use a distinct cache namespace");
            check(service.local(unreviewed) == null,
                    "Unreviewed project has no cached gameplay and must use CINEMA fallback");
            check(service.cachePath(devilution).toString()
                    .contains("official-screenshots-v2"),
                    "Fresh curated screenshot must replace stale GitHub webpage thumbnail");
            check(!DesktopOfficialPortArtwork.permitted(unreviewed.preview()),
                    "Unreviewed GitHub social cards must never be downloaded");
            for (DesktopPortsCatalog.Port port : DesktopPortsCatalog.all()) {
                var approved = DesktopOfficialPortArtwork.source(
                        DesktopPortArtworkService.forPort(port));
                if (approved == null) check(!DesktopOfficialPortArtwork.permitted(
                        DesktopPortArtworkService.forPort(port).preview()),
                        "Unreviewed port never receives a generic GitHub page screenshot");
            }
            DesktopPortArtwork panel = new DesktopPortArtwork(service, ref, 118, 150);
            check(panel.getToolTipText() == null,
                    "No long tooltip may cover Download/Link buttons");
            panel.setSize(118, 150);
            BufferedImage frame = new BufferedImage(118, 150, BufferedImage.TYPE_INT_ARGB);
            SwingUtilities.invokeAndWait(() -> {
                java.awt.Graphics2D g = frame.createGraphics();
                try { panel.paint(g); } finally { g.dispose(); }
            });
            check(frame.getRGB(59, 75) != 0, "Image component has a visible fallback");
        }
        check(Files.isRegularFile(folder.resolve("source-image.png")),
                "No original image removed");
        System.out.println("DesktopPortArtworkServiceTest: PASS");
    }
}
