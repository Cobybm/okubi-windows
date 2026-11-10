package bo.nexoplay.app.retro;

/** Actual Java regression tests for shared cinema geometry and thumb clearance. */
public final class RetroCinemaGeometryTest {
    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }

    private static float clamp(float n, float lo, float hi) {
        return Math.max(lo, Math.min(hi, n));
    }

    private static void verify(int width, int height, float aspect, float density) {
        RetroCinemaGeometry.Layout g =
                RetroCinemaGeometry.fit(width, height, aspect, density);
        check(g.stageWidth == width && g.stageHeight == height, "stage dimensions");
        check(g.gameLeft >= 0 && g.gameTop >= 0, "positive origin");
        check(g.gameWidth > 0 && g.gameHeight > 0, "positive viewport");
        check(g.gameRight() <= width && g.gameBottom() <= height - g.bottomRail,
                "game must not cover bottom utility lane");
        check(g.leftRail == g.rightRail || Math.abs(g.leftRail - g.rightRail) <= 1,
                "symmetric thumb lanes");
        check(g.leftRail > 0 && g.rightRail > 0, "thumbs have own space");
        check(Math.abs(g.gameWidth / (float)g.gameHeight - aspect)
                <= 2f / g.gameHeight, "native ratio must not be stretched");
        check(g.gameLeft + g.gameWidth / 2f == width / 2f
                || Math.abs(g.gameLeft + g.gameWidth / 2f - width / 2f) <= .5f,
                "game centered");
        check(g.utilityY >= g.gameBottom(), "utility lane below game");
        check(g.utilityY < height, "utility center on screen");
        check(g.utilityHalfWidth > 0 && g.utilityHalfHeight > 0, "utility targets");
        check(g.leftThumbX < g.gameLeft && g.rightThumbX > g.gameRight(),
                "default thumb anchor outside video");

        float r = Math.min(g.radius * 1.08f,
                (Math.min(g.leftRail, g.rightRail) * .5f - 5f * density) / 1.82f);
        check(r > 0, "controller must have positive radius");
        float dpadReach = r * (1.22f + .57f) + 4f * density;
        // Our ordinary landscape test fixtures always have adequate space
        // for the complete D-pad disc within the protected left thumb rail.
        check(2f * dpadReach <= g.leftRail + .01f, "D-pad must fit left rail");
        float dpadX = clamp(.115f * width, dpadReach, g.gameLeft - dpadReach);
        check(dpadX - dpadReach >= -.02f && dpadX + dpadReach <= g.gameLeft + .02f,
                "D-pad cannot cover game pixels");

        float actionR = r * .84f;
        float hitR = actionR + Math.min(7f * density, actionR * .30f);
        float rightMin = g.gameRight() + hitR;
        float rightMax = width - hitR;
        check(rightMin <= rightMax, "action buttons fit in right rail");
        float aX = clamp(g.rightThumbX + r * .58f, rightMin, rightMax);
        float bX = clamp(g.rightThumbX - r * .66f, rightMin, rightMax);
        check(aX - hitR >= g.gameRight() && bX - hitR >= g.gameRight(),
                "A/B hitboxes cannot steal touches from game");
        check(aX + hitR <= width && bX + hitR <= width,
                "A/B hitboxes cannot leave physical screen");

        // Mirror transform must work for left-handed users too.
        float leftMin = hitR, leftMax = g.gameLeft - hitR;
        check(leftMin <= leftMax, "left-handed A/B fit in left rail");
        float leftA = clamp(g.leftThumbX - r * .58f, leftMin, leftMax);
        check(leftA + hitR <= g.gameLeft, "left-handed A cannot hit game");
        float utilitySeparation = width * (.555f - .445f);
        check(utilitySeparation >= 2f * g.utilityHalfWidth,
                "START/SELECT silhouettes never overlap");
        check(g.utilityY - g.utilityHalfHeight >= g.gameBottom(),
                "START/SELECT visible caps never overlap game viewport");
    }

    /**
     * Phase 23: a slimmer footer yields a taller *native-ratio* viewport.
     * Verify the larger video does not steal touches from START/SELECT.
     */
    private static void ultraLandscape() {
        for (float aspect : new float[]{240f / 160f, 256f / 224f, 160f / 144f}) {
            RetroCinemaGeometry.Layout g =
                    RetroCinemaGeometry.fit(1600, 610, aspect, 2.7f);
            check(g.bottomRail <= 81, "CINEMA ULTRA footer too large");
            check(g.bottomRail >= Math.round(610f * .10f), "footer missing");
            check(g.gameBottom() <= 610 - g.bottomRail,
                    "video overlaps utility lane");
            check(g.utilityY - g.utilityHalfHeight >= g.gameBottom(),
                    "utility controls overlap video");
            check(g.gameLeft > 0 && g.gameRight() < g.stageWidth,
                    "thumb controls must remain outside video");
        }
        RetroCinemaGeometry.Layout gba =
                RetroCinemaGeometry.fit(1600, 610, 240f / 160f, 2.7f);
        // Old footer was ~92 pixels; its maximum playable height was 502 px.
        // A taller, center-preserving viewport is now available without cropping.
        check(gba.gameHeight > 502, "GBA video did not gain height");
    }

    public static void main(String[] args) {
        ultraLandscape();
        // A05s landscape and other realistic smartphone/tablet viewports.
        verify(1600, 610, 240f / 160f, 2.7f);
        verify(1600, 610, 160f / 144f, 2.7f);
        verify(1280, 560, 240f / 160f, 2.25f);
        verify(2340, 1080, 240f / 160f, 3.5f);
        verify(960, 500, 160f / 144f, 1.8f);
        verify(1080, 440, 240f / 160f, 2f);
        verify(800, 480, 240f / 160f, 1.35f);
        for (float bad : new float[] {Float.NaN, 0f, -1f, Float.POSITIVE_INFINITY}) {
            try {
                RetroCinemaGeometry.fit(1600, 610, bad, 2.7f);
                throw new AssertionError("invalid aspect accepted");
            } catch (IllegalArgumentException expected) { }
        }
        try {
            RetroCinemaGeometry.fit(1600, 610, 1.5f, 0f);
            throw new AssertionError("invalid density accepted");
        } catch (IllegalArgumentException expected) { }
        try {
            RetroCinemaGeometry.fit(0, 610, 1.5f, 2.7f);
            throw new AssertionError("invalid viewport accepted");
        } catch (IllegalArgumentException expected) { }
        System.out.println("RetroCinemaGeometryTest: OK");
    }
}
