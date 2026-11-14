package bo.nexoplay.app.retro;

/** JVM regression tests, no Android SDK or device required. */
public final class RetroFrameTimingTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        long sixty = RetroFrameTiming.frameIntervalNanos(60.0);
        check(sixty >= 16_666_666L && sixty <= 16_666_667L, "60 FPS frame");
        long pal = RetroFrameTiming.frameIntervalNanos(50.0);
        check(pal == 20_000_000L, "PAL 50 FPS frame");
        check(RetroFrameTiming.frameIntervalNanos(Double.NaN) == 16_700_000L,
                "invalid NaN FPS fallback");
        check(RetroFrameTiming.frameIntervalNanos(Double.POSITIVE_INFINITY) == 16_700_000L,
                "infinite FPS fallback");
        check(RetroFrameTiming.frameIntervalNanos(0.0) == 16_700_000L,
                "zero FPS fallback");

        long start = 1_000_000_000L;
        check(RetroFrameTiming.nextDeadline(start, start, sixty) == start + sixty,
                "normal scheduling");
        check(RetroFrameTiming.nextDeadline(start, start + sixty * 5, sixty)
                        == start + sixty * 5,
                "large lag resets cadence without burst");
        check(RetroFrameTiming.nextDeadline(start, start + sixty, sixty)
                        == start + sixty,
                "normal jitter retains cadence");
        check(RetroFrameTiming.nextDeadline(Long.MAX_VALUE - 1, start, sixty)
                        == start, "overflow recovery");

        check(RetroFrameTiming.parkNanos(-20L) == 0L, "negative wait");
        check(RetroFrameTiming.parkNanos(1_000_000L) == 0L, "short wait");
        check(RetroFrameTiming.parkNanos(5_000_000L) == 4_250_000L,
                "nanosecond margin");
        System.out.println("OKUBI frame pacing: Java regression tests passed");
    }
}
