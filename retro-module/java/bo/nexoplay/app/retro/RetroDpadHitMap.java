package bo.nexoplay.app.retro;

/**
 * Pure Java hit-test for four disjoint D-pad arms.
 *
 * A single pointer can select ONE cardinal direction or none. Diagonals are
 * deliberately produced only by the caller combining two separate pointers.
 * This class has no Android dependency so corner and center behavior can be
 * verified on every CI build.
 */
public final class RetroDpadHitMap {
    public static final int NONE = 0;
    public static final int UP = 1;
    public static final int DOWN = 2;
    public static final int LEFT = 4;
    public static final int RIGHT = 8;

    private RetroDpadHitMap() {}

    /** @return exactly one direction bit, or NONE for the dead center / gap. */
    public static int hit(float x, float y, float centerX, float centerY,
                          float stride, float halfSize) {
        if (!Float.isFinite(x) || !Float.isFinite(y)
                || !Float.isFinite(centerX) || !Float.isFinite(centerY)
                || !Float.isFinite(stride) || !Float.isFinite(halfSize)
                || stride <= 0f || halfSize <= 0f) return NONE;
        float dx = x - centerX;
        float dy = y - centerY;
        int selected = NONE;
        float best = Float.POSITIVE_INFINITY;

        // A distance tie is resolved to one arm, never two. With the default
        // layout stride > 2 * halfSize, the four rectangles never overlap.
        if (Math.abs(dx) <= halfSize && Math.abs(dy + stride) <= halfSize) {
            selected = UP;
            best = dx * dx + (dy + stride) * (dy + stride);
        }
        if (Math.abs(dx) <= halfSize && Math.abs(dy - stride) <= halfSize) {
            float score = dx * dx + (dy - stride) * (dy - stride);
            if (score < best) { selected = DOWN; best = score; }
        }
        if (Math.abs(dy) <= halfSize && Math.abs(dx + stride) <= halfSize) {
            float score = (dx + stride) * (dx + stride) + dy * dy;
            if (score < best) { selected = LEFT; best = score; }
        }
        if (Math.abs(dy) <= halfSize && Math.abs(dx - stride) <= halfSize) {
            float score = (dx - stride) * (dx - stride) + dy * dy;
            if (score < best) selected = RIGHT;
        }
        return selected;
    }
}
