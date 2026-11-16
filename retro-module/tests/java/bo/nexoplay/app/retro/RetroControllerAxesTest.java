package bo.nexoplay.app.retro;

/** Java 17 controller behavior tests; independent of Android / JVM SDK stubs. */
public final class RetroControllerAxesTest {
    private static void check(int actual, int wanted, String label) {
        if (actual != wanted)
            throw new AssertionError(label + ": expected " + wanted + ", got " + actual);
    }
    private static int mask(float x, float y, float hx, float hy, int prev) {
        return RetroControllerAxes.mask(x, y, hx, hy, prev);
    }

    public static void main(String[] args) {
        int u = RetroControllerAxes.UP, d = RetroControllerAxes.DOWN;
        int l = RetroControllerAxes.LEFT, r = RetroControllerAxes.RIGHT;
        check(mask(0f, 0f, 0f, 0f, 0), 0, "neutral");
        check(mask(.6f, -.9f, 0f, 0f, 0), r | u, "diagonal");
        check(mask(-.8f, .8f, 0f, 0f, 0), l | d, "opposite diagonal");
        check(mask(.49f, 0f, 0f, 0f, 0), 0, "deadzone engages above 0.5");
        check(mask(.50f, 0f, 0f, 0f, 0), r, "threshold press");
        check(mask(.41f, 0f, 0f, 0f, r), r, "held after dropping below 0.5");
        check(mask(.31f, 0f, 0f, 0f, r), 0, "release at 0.32");
        check(mask(.7f, 0f, -1f, 0f, r), l, "hat overrides stick");
        check(mask(0f, 0f, -1f, -1f, 0), l | u, "hat diagonal");
        check(mask(-1f, -1f, 0f, 1f, 0), l | d, "hat overrides one axis");
        check(mask(-1f, -1f, 1f, 1f, 0), r | d, "hat overrides both axes");
        check(mask(Float.NaN, Float.POSITIVE_INFINITY,
                Float.NaN, Float.NEGATIVE_INFINITY, 0), 0, "malformed axes");
        check(mask(-.01f, .01f, 0f, 0f, l | d), 0, "neutral releases keys");
        check(mask(0f, 0f, 0f, 0f, r | u), 0, "full neutral");
        check(mask(-5f, 5f, 0f, 0f, 0), l | d, "clamp out-of-range");
        check(mask(.8f, .8f, -1f, 0f, 0), l | d, "mixed hat and analogue axes");
        for (int i = 0; i < 40; i++) {
            int bitmask = mask((i%2==0 ? .48f : .52f), 0f, 0f, 0f, r);
            check(bitmask, r, "hysteresis anti-jitter " + i);
        }
        System.out.println("OKUBI control axes: all 16 cases + 40 hysteresis cycles passed");
    }
}
