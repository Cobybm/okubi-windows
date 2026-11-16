package bo.nexoplay.app.retro;

/** JVM tests for viewport fit, aspect-preserving crop and stretch. */
public final class RetroScreenFitTest {
    private static void yes(boolean ok, String description) {
        if (!ok) throw new AssertionError(description);
    }
    private static int w(int[] rect) { return rect[2] - rect[0]; }
    private static int h(int[] rect) { return rect[3] - rect[1]; }

    public static void main(String[] args) {
        yes(RetroScreenFit.Mode.values().length == 4, "Four screen choices");
        yes(RetroScreenFit.parse("INVALID") == RetroScreenFit.DEFAULT,
                "Unknown mode must retain safe default");
        yes(RetroScreenFit.parse(null) == RetroScreenFit.Mode.CINEMA, "Safe default");
        for (RetroScreenFit.Mode mode : RetroScreenFit.Mode.values())
            yes(RetroScreenFit.parse(mode.name()) == mode, "Preference round trip");

        // A05s-like wide landscape stage: GBA source 240x160.
        int surfaceW = 1380, surfaceH = 620;
        RetroScreenFit.Rects fit = RetroScreenFit.layout(surfaceW, surfaceH,
                240, 160, RetroScreenFit.Mode.EXPANDED, false);
        yes(w(fit.source) == 240 && h(fit.source) == 160, "Fit must not crop");
        yes(w(fit.destination) == 930 && h(fit.destination) == 620,
                "Fit uses all available height while retaining 3:2");
        RetroScreenFit.Rects integer = RetroScreenFit.layout(surfaceW, surfaceH,
                240, 160, RetroScreenFit.Mode.EXPANDED, true);
        yes(w(integer.destination) == 720 && h(integer.destination) == 480,
                "Optional pixel perfect remains integer-scaled");

        RetroScreenFit.Rects cover = RetroScreenFit.layout(surfaceW, surfaceH,
                240, 160, RetroScreenFit.Mode.COVER, false);
        yes(w(cover.source) == 240 && h(cover.source) == 160,
                "Fullscreen must preserve complete game pixels and dialogue");
        yes(w(cover.destination) == 930 && h(cover.destination) == 620,
                "Fullscreen game content fits proportionally over the backdrop");
        yes(RetroScreenFit.safeFullscreen(RetroScreenFit.Mode.COVER),
                "Fullscreen activates safe backdrop composition");
        yes(RetroScreenFit.clampOverlayOpacity(-1f) == .20f,
                "Overlay opacity has a readable lower bound");
        yes(RetroScreenFit.clampOverlayOpacity(2f) == 1f,
                "Overlay opacity cannot exceed 100 percent");
        yes(RetroScreenFit.clampOverlayOpacity(Float.NaN) == .45f,
                "Invalid opacity uses a safe fallback");

        RetroScreenFit.Rects stretch = RetroScreenFit.layout(surfaceW, surfaceH,
                240, 160, RetroScreenFit.Mode.STRETCH, false);
        yes(w(stretch.destination) == surfaceW && h(stretch.destination) == surfaceH,
                "Stretch fills stage");
        yes(w(stretch.source) == 240 && h(stretch.source) == 160,
                "Stretch never discards source pixels");

        RetroScreenFit.Rects portrait = RetroScreenFit.layout(480, 800,
                160, 144, RetroScreenFit.Mode.COVER, false);
        yes(w(portrait.source) == 160 && h(portrait.source) == 144,
                "Portrait fullscreen never cuts sides of dialogue");
        RetroScreenFit.Rects sameAspect = RetroScreenFit.layout(960, 640,
                240, 160, RetroScreenFit.Mode.COVER, false);
        yes(w(sameAspect.source) == 240 && h(sameAspect.source) == 160,
                "Matching ratio crops nothing");

        try {
            RetroScreenFit.layout(0, 600, 240, 160,
                    RetroScreenFit.Mode.EXPANDED, false);
            throw new AssertionError("Invalid surface should be rejected");
        } catch (IllegalArgumentException expected) { }
        System.out.println("RetroScreenFitTest: OK");
    }
}
