package bo.okubi.desktop;

/** Pure rules for incremental card creation; never modifies the game library. */
public final class DesktopLibraryBatch {
    public static final int INITIAL = 48;
    public static final int STEP = 48;

    private DesktopLibraryBatch() {}

    public static int initialCount(int total, int selection) {
        if (total < 0) throw new IllegalArgumentException("total");
        if (selection < 0 || selection >= total)
            return Math.min(total, INITIAL);
        // Preserve previously selected ROM when refreshing search or categories.
        return Math.min(total, Math.max(INITIAL, selection + 1));
    }

    public static int nextCount(int loaded, int total) {
        if (loaded < 0 || total < 0 || loaded > total)
            throw new IllegalArgumentException("invalid batch");
        return (int) Math.min((long) total, (long) loaded + STEP);
    }

    public static boolean canLoad(int loaded, int total) {
        return loaded < total;
    }
}
