package bo.okubi.desktop;

/**
 * Focus-boundary regression: sustained Xbox A/Y/B must not be reinterpreted
 * as a new action after a modal closes or the emulator returns focus.
 */
public final class DesktopControllerFocusGateTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        DesktopControllerFocusGate gate = new DesktopControllerFocusGate();
        int xboxA = 1 << 5, xboxY = 1 << 7;
        check(!gate.isReady(), "Gate initially disarmed.");
        check(!gate.accepts(xboxA), "Held A at modal opening must not activate.");
        check(!gate.accepts(xboxY), "Held Y at modal opening must not open a second dialog.");
        check(!gate.isReady(), "An active button cannot arm a controller.");
        check(!gate.accepts(0), "Neutral frame only arms, never triggers.");
        check(gate.isReady(), "Neutral frame restores ability to navigate.");
        check(gate.accepts(xboxA), "New A press after release is accepted.");
        check(gate.accepts(xboxY), "New Y press after release is accepted.");

        gate.disarm();
        check(!gate.isReady(), "Focus loss disarms controller.");
        check(!gate.accepts(xboxY), "Holding Y across modal close is ignored.");
        check(!gate.accepts(xboxY | xboxA), "Holding two buttons is ignored.");
        check(!gate.accepts(0), "Second neutral frame arms safely.");
        check(gate.accepts(xboxA), "Fresh A press works after focus return.");

        gate.disarm();
        check(!gate.accepts(1 << 13), "Physical Back also must be released.");
        check(!gate.accepts(0), "Back release arms.");
        check(gate.accepts(1 << 4), "Physical B closes normally after release.");
        System.out.println("DesktopControllerFocusGateTest: PASS");
    }
}
