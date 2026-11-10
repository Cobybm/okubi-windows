package bo.nexoplay.app.retro;

/** Java-only test of opt-in MANDO PRO sizing, safe bounds and labels. */
public final class RetroControlPresetsTest {
    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
    private static void close(float a, float b, String reason) {
        check(Math.abs(a - b) <= .001f, reason);
    }
    public static void main(String[] args) {
        RetroControlPresets.Mode[] options = RetroControlPresets.options();
        check(options.length == 3, "exactly 3 options");
        check(options[0] == RetroControlPresets.Mode.EQUILIBRADO, "stable default");
        check(options[1] == RetroControlPresets.Mode.COMODO, "comfortable second");
        check(options[2] == RetroControlPresets.Mode.COMPACTO, "compact third");
        close(options[0].size, 1.08f, "legacy standard size preserved");
        close(options[0].opacity, .82f, "legacy standard opacity preserved");
        check(options[1].size > options[0].size, "comfortable is larger");
        check(options[2].size < options[0].size, "compact is smaller");
        check(options[1].opacity > options[0].opacity, "comfortable is clearer");
        check(options[2].opacity < options[0].opacity, "compact is subtler");
        for (RetroControlPresets.Mode option : options) {
            check(option.label != null && !option.label.isBlank(), "label");
            check(option.description != null && !option.description.isBlank(), "description");
            close(RetroControlPresets.boundedSize(option.size), option.size,
                    "preset size in safe range");
            close(RetroControlPresets.boundedOpacity(option.opacity), option.opacity,
                    "preset alpha in safe range");
        }
        close(RetroControlPresets.boundedSize(10f), 1.50f, "clamp large");
        close(RetroControlPresets.boundedSize(-1f), .72f, "clamp small");
        close(RetroControlPresets.boundedOpacity(3f), 1f, "clamp opaque");
        close(RetroControlPresets.boundedOpacity(-2f), .25f, "clamp transparent");
        close(RetroControlPresets.boundedSize(Float.NaN), 1.08f, "NaN size");
        close(RetroControlPresets.boundedOpacity(Float.POSITIVE_INFINITY),
                .82f, "infinite opacity");
        System.out.println("RetroControlPresetsTest: OK");
    }
}
