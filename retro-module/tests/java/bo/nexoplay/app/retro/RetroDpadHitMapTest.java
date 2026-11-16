package bo.nexoplay.app.retro;

/** Deterministic no-Android tests for the reported double/triple D-pad press. */
public final class RetroDpadHitMapTest {
    private static final float STRIDE = 38.0f;
    private static final float HALF = 17.0f;

    private static void equal(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + " got " + actual);
        }
    }
    private static int tap(float x,float y) {
        return RetroDpadHitMap.hit(x,y,0f,0f,STRIDE,HALF);
    }
    private static void singleFingerOnly() {
        equal(RetroDpadHitMap.UP,tap(0,-STRIDE),"up center");
        equal(RetroDpadHitMap.DOWN,tap(0,STRIDE),"down center");
        equal(RetroDpadHitMap.LEFT,tap(-STRIDE,0),"left center");
        equal(RetroDpadHitMap.RIGHT,tap(STRIDE,0),"right center");
        for (int x=-60;x<=60;x++) {
            for (int y=-60;y<=60;y++) {
                int direction=tap(x,y);
                if (Integer.bitCount(direction)>1) {
                    throw new AssertionError("A single finger generated diagonal at "+x+","+y);
                }
            }
        }
    }
    private static void centerDeadZoneAndCorners() {
        equal(RetroDpadHitMap.NONE,tap(0,0),"dead center");
        equal(RetroDpadHitMap.NONE,tap(12,12),"center diagonal dead zone");
        equal(RetroDpadHitMap.NONE,tap(21,-21),"diagonal gap");
        equal(RetroDpadHitMap.NONE,tap(-21,-21),"other diagonal gap");
        equal(RetroDpadHitMap.NONE,tap(200,200),"outside");
        equal(RetroDpadHitMap.NONE,tap(-1,18),"between center and down");
        equal(RetroDpadHitMap.UP,tap(0,-21),"up inner edge");
        equal(RetroDpadHitMap.DOWN,tap(0,21),"down inner edge");
        equal(RetroDpadHitMap.LEFT,tap(-21,0),"left inner edge");
        equal(RetroDpadHitMap.RIGHT,tap(21,0),"right inner edge");
    }
    private static void twoSeparateFingersMakeDiagonal() {
        int first=tap(0,-STRIDE);
        int second=tap(STRIDE,0);
        equal(RetroDpadHitMap.UP | RetroDpadHitMap.RIGHT,
                first | second,"two pointers create intentional up-right");
        equal(RetroDpadHitMap.LEFT | RetroDpadHitMap.DOWN,
                tap(-STRIDE,0) | tap(0,STRIDE),"down-left");
        equal(RetroDpadHitMap.UP,first | RetroDpadHitMap.NONE,
                "lifting right finger keeps up");
        equal(RetroDpadHitMap.NONE,RetroDpadHitMap.NONE,
                "all released");
    }
    private static void rejectInvalidCoordinates() {
        equal(RetroDpadHitMap.NONE,
                RetroDpadHitMap.hit(Float.NaN,0,0,0,STRIDE,HALF),"NaN input");
        equal(RetroDpadHitMap.NONE,
                RetroDpadHitMap.hit(0,0,0,0,-1,HALF),"bad stride");
        equal(RetroDpadHitMap.NONE,
                RetroDpadHitMap.hit(0,0,0,0,STRIDE,Float.POSITIVE_INFINITY),
                "infinite geometry");
    }
    private static void overlapNeverCreatesTwoDirections() {
        // If an old user profile supplied overlapping rectangles, the hit
        // helper still returns the closest single cardinal direction.
        for (int x=-50;x<=50;x++)
            for(int y=-50;y<=50;y++) {
                int m=RetroDpadHitMap.hit(x,y,0,0,18,20);
                if (Integer.bitCount(m)>1) throw new AssertionError("overlap "+x+","+y);
            }
    }
    public static void main(String[] args) {
        singleFingerOnly();
        centerDeadZoneAndCorners();
        twoSeparateFingersMakeDiagonal();
        rejectInvalidCoordinates();
        overlapNeverCreatesTwoDirections();
        System.out.println("RetroDpadHitMapTest: OK");
    }
}
