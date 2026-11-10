package bo.nexoplay.app.retro;

/** Pure JVM regression tests for the five presentation-only visual modes. */
public final class RetroVisualFiltersTest {
    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        RetroVisualFilters.Mode[] modes = RetroVisualFilters.Mode.values();
        check(modes.length == 5, "Five image modes are selectable");
        check(RetroVisualFilters.parse(null) == RetroVisualFilters.DEFAULT,
                "Missing preference uses safe Sharp HD");
        check(RetroVisualFilters.parse("CORRUPTED") == RetroVisualFilters.DEFAULT,
                "Unknown preference must not crash a game");
        for (RetroVisualFilters.Mode mode : modes)
            check(RetroVisualFilters.parse(mode.name()) == mode,
                    "Mode round trips through preferences");
        check(RetroVisualFilters.DEFAULT == RetroVisualFilters.Mode.SHARP_HD,
                "Sharp HD is default");
        check(RetroVisualFilters.smooth(RetroVisualFilters.Mode.SMOOTH_HD),
                "Only Smooth HD uses bilinear filter");
        check(!RetroVisualFilters.smooth(RetroVisualFilters.Mode.SHARP_HD),
                "Sharp HD preserves pixel edges");
        check(RetroVisualFilters.lcd(RetroVisualFilters.Mode.LCD_RETRO),
                "LCD scanlines enabled");
        check(RetroVisualFilters.classic(RetroVisualFilters.Mode.GB_CLASSIC),
                "Classic palette enabled");
        check(RetroVisualFilters.integerScale(1600, 610, 240, 160) == 3,
                "GBA landscape fits three exact pixels");
        check(RetroVisualFilters.integerScale(1600, 610, 160, 144) == 4,
                "GB landscape fits four exact pixels");
        check(RetroVisualFilters.integerScale(160, 100, 240, 160) == 0,
                "Small surfaces fall back without cropping");
        check(RetroVisualFilters.integerScale(-1, 600, 240, 160) == 0,
                "Invalid surface cannot overflow");
        check(RetroVisualFilters.integerScale(1600, 610, 0, 160) == 0,
                "Invalid native resolution cannot divide by zero");

        int[] original = {0xFF000000, 0xFFFFFFFF, 0xFF777777, 0xFF999999};
        int[] sourceCopy = original.clone();
        int[] recolored = new int[4];
        RetroVisualFilters.classicPalette(original, recolored);
        check(recolored[0] == 0xFF0F380F, "Darkest Game Boy green");
        check(recolored[1] == 0xFF9BBC0F, "Lightest Game Boy green");
        check(recolored[2] == 0xFF306230, "Medium-dark green");
        check(recolored[3] == 0xFF8BAC0F, "Medium-light green");
        for (int i = 0; i < original.length; i++)
            check(original[i] == sourceCopy[i], "Original pixels must stay unchanged");
        try {
            RetroVisualFilters.classicPalette(original, original);
            throw new AssertionError("Source/destination alias must be rejected");
        } catch (IllegalArgumentException expected) { }
        System.out.println("RetroVisualFiltersTest: OK");
    }
}
