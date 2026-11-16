package bo.okubi.desktop;

/** Offline expectations, conservative classifications, no live hardware guesses. */
public final class DesktopSmartPerformanceTest {
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        var basic = new DesktopSmartHardware.Snapshot(
                "AMD Ryzen 3 2200G", 4, 16L * 1024 * 1024 * 1024,
                "Radeon Vega 8 Graphics", true, 0.3);
        var ps1 = DesktopSmartPerformance.recommend(
                DesktopSmartPerformance.Console.PS1, basic,
                DesktopSmartPerformance.Mode.AUTO);
        var ps2 = DesktopSmartPerformance.recommend(
                DesktopSmartPerformance.Console.PS2, basic,
                DesktopSmartPerformance.Mode.AUTO);
        var ps3 = DesktopSmartPerformance.recommend(
                DesktopSmartPerformance.Console.PS3, basic,
                DesktopSmartPerformance.Mode.AUTO);
        check(ps1.rating() == DesktopSmartPerformance.Rating.LIKELY,
                "PS1 is likely on a supported old Windows PC.");
        check(ps2.rating() == DesktopSmartPerformance.Rating.VARIABLE,
                "PS2 with integrated Vega should not promise good FPS.");
        check(ps2.mode() == DesktopSmartPerformance.Mode.ECO
                && ps2.suggestedScale() == 1,
                "Basic PC gets a conservative 1x suggestion.");
        check(ps3.rating() == DesktopSmartPerformance.Rating.NOT_RECOMMENDED,
                "PS3 must warn strongly on an old integrated GPU.");
        check(ps3.reason().contains("experimental"),
                "PS3 explanation must expose experimental support.");

        var strong = new DesktopSmartHardware.Snapshot(
                "Modern 12 core", 16, 32L * 1024 * 1024 * 1024,
                "NVIDIA GeForce RTX 4070", true, 0.18);
        check(DesktopSmartPerformance.recommend(DesktopSmartPerformance.Console.PS2,
                strong, DesktopSmartPerformance.Mode.AUTO).rating()
                == DesktopSmartPerformance.Rating.LIKELY,
                "Better PC can get likely PS2 hardware rating.");
        check(DesktopSmartPerformance.recommend(DesktopSmartPerformance.Console.PS3,
                strong, DesktopSmartPerformance.Mode.AUTO).rating()
                == DesktopSmartPerformance.Rating.VARIABLE,
                "Even a strong PC never guarantees per-title PS3 compatibility.");
        check(DesktopSmartPerformance.recommend(DesktopSmartPerformance.Console.PS1,
                strong, DesktopSmartPerformance.Mode.QUALITY).suggestedScale() == 2,
                "Quality mode may suggest 2x for likely titles, not silently apply it.");

        var unknown = new DesktopSmartHardware.Snapshot(
                "Unknown", 4, 0, "GPU no detectada", false, -1);
        check(DesktopSmartPerformance.recommend(DesktopSmartPerformance.Console.PS2,
                unknown, DesktopSmartPerformance.Mode.AUTO).rating()
                == DesktopSmartPerformance.Rating.UNKNOWN,
                "Unknown GPU/ram cannot be scored as performant.");
        var busy = new DesktopSmartHardware.Snapshot(
                "Busy", 4, 16L * 1024 * 1024 * 1024,
                "Radeon Vega 8", true, 0.95);
        check(DesktopSmartPerformance.recommend(DesktopSmartPerformance.Console.PS2,
                busy, DesktopSmartPerformance.Mode.AUTO).reason().contains("ocupada"),
                "Warn when high CPU load is actually observed.");

        check(DesktopSmartPerformance.suggestConsole("Game.cue")
                == DesktopSmartPerformance.Console.PS1, "CUE maps to PS1.");
        check(DesktopSmartPerformance.suggestConsole("Game.iso")
                == DesktopSmartPerformance.Console.PS2, "ISO defaults to PS2.");
        check(DesktopSmartPerformance.suggestConsole("Game.chd")
                == DesktopSmartPerformance.Console.PS1, "CHD defaults to PS1.");
        check(DesktopSmartPerformance.suggestConsole("EBOOT.BIN")
                == DesktopSmartPerformance.Console.PS3, "Only EBOOT.BIN maps PS3.");
        check(!DesktopSmartPerformance.acceptedFile(DesktopSmartPerformance.Console.PS3,
                "downloaded.pkg"), "RPCS3 should not auto-boot arbitrary PKG.");
        check(DesktopSmartPerformance.acceptedFile(DesktopSmartPerformance.Console.PS1,
                "Game.iso"), "Ambiguous ISO can be explicitly classified as PS1.");
        check(!DesktopSmartPerformance.acceptedFile(DesktopSmartPerformance.Console.PS1,
                "Game.exe"), "Never accept executable as a game.");
        System.out.println("DesktopSmartPerformanceTest: PASS");
    }
}
