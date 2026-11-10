package bo.nexoplay.app.retro;

/** Real JVM checks for optional thumbnails and immutable legacy names. */
public final class RetroSlotPreviewsTest {
    private static void check(boolean ok,String reason) {
        if(!ok)throw new AssertionError(reason);
    }
    private static void rejects(String hash,int slot) {
        try {
            RetroSlotPreviews.filename(hash,slot);
            throw new AssertionError("preview path accepted invalid ROM or slot");
        } catch(IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        String hash="ab".repeat(32);
        check((hash+".state").equals(RetroSaveSlots.filename(hash,1)),
                "legacy slot is not renamed");
        check((hash+".preview.png").equals(RetroSlotPreviews.filename(hash,1)),
                "slot 1 sidecar");
        check((hash+".slot2.preview.png").equals(RetroSlotPreviews.filename(hash,2)),
                "slot 2 sidecar");
        check((hash+".slot3.preview.png").equals(RetroSlotPreviews.filename(hash,3)),
                "slot 3 sidecar");
        check(!(hash+".state").equals(RetroSlotPreviews.filename(hash,1)),
                "sidecar never overlaps checkpoint");
        rejects(null,1);
        rejects(hash.toUpperCase(),1);
        rejects("../"+hash,1);
        rejects(hash,0);
        rejects(hash,4);
        check(!RetroSlotPreviews.validImageSize(0),"reject empty");
        check(!RetroSlotPreviews.validImageSize(23),"reject incomplete PNG");
        check(RetroSlotPreviews.validImageSize(24),"minimum image size");
        check(RetroSlotPreviews.validImageSize(RetroSlotPreviews.MAX_BYTES),"maximum");
        check(!RetroSlotPreviews.validImageSize(RetroSlotPreviews.MAX_BYTES+1L),
                "reject oversized image");
        check(RetroSlotPreviews.details(false,0).contains("Vacía"),
                "legacy empty slot label");
        check(RetroSlotPreviews.details(true,4096).contains("4 KiB"),
                "size in KiB");
        check(RetroSlotPreviews.details(true,0).contains("no válido"),
                "zero-size state cannot load");
        check(!RetroSlotPreviews.blockedByBackup(false),"ordinary target");
        check(RetroSlotPreviews.blockedByBackup(true),"recovery needs protection");
        System.out.println("RetroSlotPreviewsTest: OK");
    }
}
