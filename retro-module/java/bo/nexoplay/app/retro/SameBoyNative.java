package bo.nexoplay.app.retro;

/**
 * JNI entry points for SameBoy's Libretro core. All native methods must be
 * invoked from a single dedicated worker, never concurrently on the UI thread.
 * Class loading fails gracefully when the optional NDK bridge is unavailable.
 */
public final class SameBoyNative {
    private static final boolean AVAILABLE;
    private static final boolean GBA_AVAILABLE;
    private static final boolean SNES_AVAILABLE;
    static {
        boolean ok;
        try {
            System.loadLibrary("okubi_sameboy_bridge");
            ok = true;
        } catch (UnsatisfiedLinkError error) {
            ok = false;
        }
        AVAILABLE = ok;
        boolean gba;
        try {
            System.loadLibrary("mgba_libretro_android");
            gba = ok;
        } catch (UnsatisfiedLinkError error) {
            gba = false;
        }
        GBA_AVAILABLE = ok && gba;
        boolean snes;
        try {
            System.loadLibrary("supafaust_libretro_android");
            snes = true;
        } catch (UnsatisfiedLinkError error) {
            snes = false;
        }
        SNES_AVAILABLE = ok && snes;
    }

    private SameBoyNative() {}

    public static boolean isAvailable() { return AVAILABLE; }
    public static boolean isGbaAvailable() { return GBA_AVAILABLE; }
    public static boolean isSnesAvailable() { return SNES_AVAILABLE; }
    public static native boolean nativeOpen(byte[] rom, String saveDir, String system);
    public static native int nativeStep(int buttons, int[] argb, short[] audio);
    public static native double nativeFps();
    public static native int nativeSampleRate();
    public static native byte[] nativeSaveState();
    public static native boolean nativeLoadState(byte[] contents);
    public static native byte[] nativeSaveRam();
    public static native boolean nativeLoadRam(byte[] contents);
    public static native byte[] nativeSaveRtc();
    public static native boolean nativeLoadRtc(byte[] contents);
    public static native void nativeClose();
}
