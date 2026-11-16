package bo.nexoplay.app.retro;

/**
 * Internal PS1 Libretro native engine, PCSX ReARMed GPLv2.
 * Its core is packaged inside OKUBI, not downloaded on game start.
 *
 * Native methods are worker-only. Dynamic core and JNI bridge ship for
 * the device's ABI; availability is checked before opening a game.
 */
public final class OkubiPs1Native {
    private static final boolean AVAILABLE;
    static {
        boolean ok=false;
        try {
            System.loadLibrary("pcsx_rearmed_libretro_android");
            System.loadLibrary("okubi_ps1_bridge");
            ok=true;
        } catch (UnsatisfiedLinkError notBundled) {
            ok=false;
        }
        AVAILABLE=ok;
    }
    private OkubiPs1Native(){}
    public static boolean available(){return AVAILABLE;}
    public static native boolean nativeOpen(String discPath,String systemDirectory,
                                             String saveDirectory);
    public static native int nativeStep(int buttons,int[] pixels,short[] audio);
    public static native double nativeFps();
    public static native int nativeSampleRate();
    public static native byte[] nativeSaveState();
    public static native boolean nativeLoadState(byte[] bytes);
    public static native byte[] nativeSaveRam();
    public static native boolean nativeLoadRam(byte[] bytes);
    public static native void nativeClose();
}
