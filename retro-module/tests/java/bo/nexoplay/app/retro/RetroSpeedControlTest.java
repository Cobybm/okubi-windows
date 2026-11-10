package bo.nexoplay.app.retro;

public final class RetroSpeedControlTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(RetroSpeedControl.NORMAL == 1, "default x1");
        check(RetroSpeedControl.label(0).equals("×0.5"), "half-speed label");
        check(RetroSpeedControl.label(1).equals("×1"), "normal label");
        check(RetroSpeedControl.label(2).equals("×2"), "double label");
        check(RetroSpeedControl.label(3).equals("×4"), "quadruple label");
        check(RetroSpeedControl.slower(0) == 0, "minimum limit");
        check(RetroSpeedControl.faster(3) == 3, "maximum limit");
        check(RetroSpeedControl.faster(1) == 2, "speed increases");
        check(RetroSpeedControl.slower(1) == 0, "speed decreases");
        long normal = 16_666_667L;
        check(RetroSpeedControl.intervalNanos(normal,0) == normal * 2L,
                "half speed slows actual core cadence");
        check(RetroSpeedControl.intervalNanos(normal,1) == normal,
                "normal cadence identical to baseline");
        check(RetroSpeedControl.intervalNanos(normal,2) == Math.round(normal/2.0),
                "double speed");
        check(RetroSpeedControl.intervalNanos(normal,3) == Math.round(normal/4.0),
                "four times speed");
        check(RetroSpeedControl.presentFrame(1,3L), "normal draws all");
        check(RetroSpeedControl.presentFrame(2,2L), "fast draws frame 2");
        check(!RetroSpeedControl.presentFrame(2,3L), "fast skips frame 3 only visually");
        check(RetroSpeedControl.presentFrame(3,4L), "x4 draw one in four");
        check(!RetroSpeedControl.presentFrame(3,5L), "x4 suppress extra render");
        check(RetroSpeedControl.normal(1), "audio normal only x1");
        check(!RetroSpeedControl.normal(2), "audio muted on fast mode");
        System.out.println("RetroSpeedControlTest: OK");
    }
}
