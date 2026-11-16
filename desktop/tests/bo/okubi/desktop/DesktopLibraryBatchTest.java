package bo.okubi.desktop;

/** Deterministic pagination safety tests, no ROMs or user settings accessed. */
public final class DesktopLibraryBatchTest {
    private static void eq(int expected, int actual, String label) {
        if (expected != actual)
            throw new AssertionError(label + ": expected " + expected + " got " + actual);
    }

    public static void main(String[] args) {
        eq(0, DesktopLibraryBatch.initialCount(0, -1), "empty library");
        eq(3, DesktopLibraryBatch.initialCount(3, 0), "small library");
        eq(48, DesktopLibraryBatch.initialCount(2000, 0), "first 48 games");
        eq(401, DesktopLibraryBatch.initialCount(2000, 400), "preserved selected game");
        eq(96, DesktopLibraryBatch.nextCount(48, 2000), "next batch");
        eq(2000, DesktopLibraryBatch.nextCount(1999, 2000), "last batch");
        eq(48, DesktopLibraryBatch.nextCount(0, 2000), "start");
        if (DesktopLibraryBatch.canLoad(2000, 2000)) throw new AssertionError("done");
        if (!DesktopLibraryBatch.canLoad(96, 2000)) throw new AssertionError("needs more");
        boolean invalidRejected = false;
        try { DesktopLibraryBatch.nextCount(10, 9); }
        catch (IllegalArgumentException expected) { invalidRejected = true; }
        if (!invalidRejected) throw new AssertionError("invalid count accepted");
        System.out.println("PASS: bounded initial, selections, batch growth, final page");
    }
}
