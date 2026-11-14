package bo.nexoplay.app.retro;

/** Android-independent tests for small windows, large text, tablets and foldables. */
public final class RetroAdaptiveLayoutTest {
    private static void check(boolean okay, String message) {
        if (!okay) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(RetroAdaptiveLayout.hideHeaderStatus(360, 1f),
                "Narrow multi-window hides optional status only");
        check(RetroAdaptiveLayout.compactBrand(360, 1f),
                "Narrow window preserves screen room");
        check(!RetroAdaptiveLayout.hideHeaderStatus(1000, 1f),
                "Tablet permits status");
        check(!RetroAdaptiveLayout.compactBrand(1000, 1f),
                "Tablet displays full OKUBI brand");
        check(RetroAdaptiveLayout.hideHeaderStatus(950, 1.5f),
                "Large accessibility text needs a narrower toolbar");
        check(RetroAdaptiveLayout.compactBrand(650, 1.5f),
                "Large accessibility text collapses brand sooner");
        check(!RetroAdaptiveLayout.compactBrand(650, 1f),
                "Ordinary text keeps the logo longer");
        check(RetroAdaptiveLayout.minimumTitleDp(320) == 52,
                "Small phones reserve room for the game title");
        check(RetroAdaptiveLayout.minimumTitleDp(600) == 88,
                "Normal phone keeps a readable title");
        check(RetroAdaptiveLayout.minimumTitleDp(950) == 115,
                "Tablet keeps full-width title");
        check(RetroAdaptiveLayout.hideHeaderStatus(360, Float.NaN),
                "Broken OEM font scale is harmless");
        check(RetroAdaptiveLayout.compactBrand(360, -2f),
                "Invalid OEM font scale is harmless");
        System.out.println("RetroAdaptiveLayoutTest: OK");
    }
}
