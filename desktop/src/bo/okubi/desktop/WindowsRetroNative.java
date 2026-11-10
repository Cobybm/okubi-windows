package bo.okubi.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One Libretro core at a time; only the game worker invokes JNI.
 * The user chooses the Windows 64-bit core DLL. No games or third-party
 * emulators are included in the OKUBI executable.
 */
public final class WindowsRetroNative {
    private WindowsRetroNative() { }

    public static final int GB_PIXELS = 160 * 144;
    public static final int GBA_PIXELS = 240 * 160;
    public static final int SNES_PIXELS = 256 * 224;
    public static final int MAX_AUDIO_SAMPLES = 8192;

    private static final class DllLoader {
        static final boolean loaded = loadLibrary();

        static boolean loadLibrary() {
            List<Path> locations = new ArrayList<>();
            String launcher = System.getProperty("jpackage.app-path");
            if (launcher != null && !launcher.isBlank()) {
                Path exe = Path.of(launcher).toAbsolutePath().getParent();
                if (exe != null)
                    locations.add(exe.resolve("app").resolve("okubi_libretro_jni.dll"));
            }
            // jpackage places the application JAR and bundled JNI DLL
            // together in its app directory; no working-directory assumption.
            try {
                java.net.URI jar = WindowsRetroNative.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI();
                Path appDirectory = Path.of(jar).toAbsolutePath().getParent();
                if (appDirectory != null)
                    locations.add(appDirectory.resolve("okubi_libretro_jni.dll"));
            } catch (java.net.URISyntaxException | RuntimeException ignored) { }
            locations.add(Path.of(System.getProperty("user.dir", "."))
                    .resolve("build/desktop/native/okubi_libretro_jni.dll").toAbsolutePath());
            String override = System.getProperty("okubi.native.bridge");
            if (override != null && !override.isBlank())
                locations.add(0, Path.of(override).toAbsolutePath());
            for (Path dll : locations) {
                if (Files.isRegularFile(dll)) {
                    System.load(dll.toString());
                    return true;
                }
            }
            throw new UnsatisfiedLinkError(
                    "Falta el puente de OKUBI Desktop. Extrae TODO el ZIP "
                    + "y conserva la subcarpeta app y runtime.");
        }
    }

    private static void ensureBridge() {
        if (!DllLoader.loaded) throw new UnsatisfiedLinkError("Puente no disponible.");
    }

    public static String version() {
        ensureBridge();
        return "OKUBI-DESKTOP-LIBRETRO-1";
    }

    public static void open(Path coreDll, Path romPath,
                            DesktopLibrary.Console console, Path saveDirectory)
            throws IOException {
        if (console == null || coreDll == null || romPath == null || saveDirectory == null)
            throw new IOException("Selecciona una ROM y un núcleo Libretro.");
        Path core = coreDll.toAbsolutePath().normalize();
        Path rom = romPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(core)
                || !core.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".dll"))
            throw new IOException("El núcleo Libretro x64 (.dll) no existe.");
        if (!Files.isRegularFile(rom) || !Files.isReadable(rom)
                || DesktopLibrary.Console.fromFile(rom) != console)
            throw new IOException("El juego no está disponible o su consola no coincide.");
        long length = Files.size(rom);
        int maxMB = console == DesktopLibrary.Console.GBA ? 32
                : console == DesktopLibrary.Console.SNES ? 16 : 8;
        if (length < 32 * 1024 || length > (long)maxMB * 1024 * 1024)
            throw new IOException("La ROM tiene un tamaño no compatible (" + length + " bytes).");
        Files.createDirectories(saveDirectory);
        byte[] content = Files.readAllBytes(rom);
        ensureBridge();
        if (!nativeOpen(content, saveDirectory.toAbsolutePath().toString(),
                console.name(), core.toString(), rom.toString()))
            throw new IOException("El núcleo Libretro no pudo cargar este juego. "
                    + "Comprueba que la DLL sea de Windows x64 y compatible con "
                    + console.label + ".");
    }

    public static int step(int mask, int[] frame, short[] audio) {
        if (frame == null || audio == null) throw new IllegalArgumentException("Buffers");
        return nativeStep(mask, frame, audio);
    }

    public static double fps() { return nativeFps(); }
    public static int sampleRate() { return nativeSampleRate(); }
    public static byte[] saveRam() { return nativeSaveRam(); }
    public static boolean loadRam(byte[] data) { return nativeLoadRam(data); }
    public static byte[] saveRtc() { return nativeSaveRtc(); }
    public static boolean loadRtc(byte[] data) { return nativeLoadRtc(data); }
    public static byte[] saveState() { return nativeSaveState(); }
    public static boolean loadState(byte[] data) { return nativeLoadState(data); }
    public static void close() { nativeClose(); }
    /** Returns zero if no XInput controller is connected. */
    public static int gamepadMask() { ensureBridge(); return nativeGamepadMask(); }

    private static native boolean nativeOpen(byte[] rom, String saveDirectory,
            String console, String coreDllPath, String sourceRomPath);
    private static native int nativeStep(int mask, int[] frame, short[] audio);
    private static native double nativeFps();
    private static native int nativeSampleRate();
    private static native byte[] nativeSaveRam();
    private static native boolean nativeLoadRam(byte[] bytes);
    private static native byte[] nativeSaveRtc();
    private static native boolean nativeLoadRtc(byte[] bytes);
    private static native byte[] nativeSaveState();
    private static native boolean nativeLoadState(byte[] bytes);
    private static native void nativeClose();
    private static native int nativeGamepadMask();
}
