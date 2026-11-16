package bo.nexoplay.app.retro;

/** JVM regression tests for simultaneous touch directions. */
public final class RetroInputMaskTest {
    private static final int UP=1, DOWN=2, LEFT=4, RIGHT=8;
    private static final int A=16, B=32, X=64, Y=128, START=256;
    private static void check(int actual, int expected, String message) {
        if (actual != expected) throw new AssertionError(message +
                ": expected "+expected+", got "+actual);
    }
    private static int resolve(int mask) {
        return RetroInputMask.resolveOpposites(mask,UP,DOWN,LEFT,RIGHT);
    }
    public static void main(String[] args) {
        check(resolve(UP | DOWN),0,"neutral up/down");
        check(resolve(LEFT | RIGHT),0,"neutral left/right");
        check(resolve(UP | DOWN | LEFT | RIGHT | A),A,"all contradictory");
        check(resolve(UP | RIGHT | B),UP | RIGHT | B,"valid diagonal and B");
        check(resolve(UP | DOWN | LEFT | X | Y),LEFT | X | Y,
                "keep horizontal and SNES actions");
        check(resolve(LEFT | RIGHT | A | B | START),A | B | START,
                "preserve actions and utility keys");
        check(resolve(0),0,"release");
        check(resolve(A | B),A | B,"simultaneous action buttons");
        System.out.println("OKUBI MANDO ULTRA multitouch regression: OK");
    }
}
