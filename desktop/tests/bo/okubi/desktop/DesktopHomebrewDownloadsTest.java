package bo.okubi.desktop;

import java.nio.file.Path;
import java.util.List;

/** Offline contract tests: no external network requests or user directory writes. */
public final class DesktopHomebrewDownloadsTest {
    public static void main(String[] args) {
        List<DesktopHomebrewDownloads.Entry> entries = DesktopHomebrewDownloads.entries();
        assert entries.size() >= 5;
        long direct = entries.stream().filter(DesktopHomebrewDownloads::authorizedDirect).count();
        assert direct >= 1 : "At least one explicitly permitted download";
        DesktopHomebrewDownloads.Entry approved = entries.get(0);
        assert DesktopHomebrewDownloads.authorizedDirect(approved);
        assert "2048gb.gb".equals(approved.filename());
        assert !DesktopHomebrewDownloads.authorizedDirect(
                new DesktopHomebrewDownloads.Entry("bad", "GB", "https://example.com",
                        "https://evil.example.com/api/entry/2048gb/2048.gb", "2048gb.gb"));
        assert !DesktopHomebrewDownloads.authorizedDirect(
                new DesktopHomebrewDownloads.Entry("bad", "GB", "https://example.com",
                        "https://hh3.gbdev.io/api/entry/2048gb/2048.gb?redirect=true",
                        "2048gb.gb"));
        assert !DesktopHomebrewDownloads.authorizedDirect(
                new DesktopHomebrewDownloads.Entry("bad", "GB", "https://example.com",
                        "https://hh3.gbdev.io/api/entry/2048gb/2048.gb",
                        "../escape.gb"));
        assert !DesktopHomebrewDownloads.authorizedDirect(
                new DesktopHomebrewDownloads.Entry("commercial", "PS2", "https://example.com",
                        "https://rom-site.example/rom/game.iso", "game.iso"));
        assert DesktopHomebrewDownloads.defaultDirectory().startsWith(
                DesktopLibrary.defaultSettingsFile().getParent());
        System.out.println("DesktopHomebrewDownloadsTest OK");
    }
}
