package bo.okubi.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Executed on a real Windows x64 runner with a freshly compiled dummy DLL. */
public final class EmbeddedCoreTest {
    private static void check(boolean condition, String explanation) {
        if (!condition) throw new AssertionError(explanation);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1)
            throw new IllegalArgumentException("Usage: EmbeddedCoreTest <mock-dll>");
        Path root = Files.createTempDirectory("okubi-embedded-test-");
        Path dll = Path.of(args[0]).toAbsolutePath();
        Path rom = root.resolve("Prueba de usuario.gb");
        Files.write(rom, new byte[32 * 1024]);

        DesktopLibrary library = new DesktopLibrary(root.resolve("library.properties"));
        library.addGame(rom);
        library.setLibretroCore(DesktopLibrary.Console.GB, dll);
        check(new DesktopLibrary(root.resolve("library.properties"))
                .libretroCore(DesktopLibrary.Console.GB).equals(dll),
                "Core DLL selection must survive app restart");

        DesktopSaves saves = new DesktopSaves(root.resolve("saves"),
                DesktopLibrary.Console.GB, rom);
        check(saves.ramFile.getFileName().toString().endsWith(".srm"), "Battery extension");
        check(saves.rtcFile.getFileName().toString().endsWith(".rtc"), "RTC extension");
        check(DesktopSaves.sha256(rom).length() == 64, "ROM keyed by hash");

        WindowsRetroNative.open(dll, rom, DesktopLibrary.Console.GB, saves.directory);
        try {
            check(WindowsRetroNative.version().contains("LIBRETRO"), "JNI bridge loaded");
            check(WindowsRetroNative.fps() >= 59.9 && WindowsRetroNative.fps() <= 60.1,
                    "Real core FPS");
            check(WindowsRetroNative.sampleRate() == 44100, "Real core sample rate");
            int[] frame = new int[160 * 144];
            short[] audio = new short[8192];
            int samples = WindowsRetroNative.step(0, frame, audio);
            check(samples == 1470, "Stereo audio frames returned through JNI");
            check(audio[0] == 120 && audio[1] == -120, "Stereo channels preserved");
            check(frame[0] == 0xff0000ff, "RGB frame published");
            check(WindowsRetroNative.saveRam()[0] == 42, "Initial SRAM exposed");

            samples = WindowsRetroNative.step(1 << 4, frame, audio);
            check(samples == 1470, "Audio still advances with pressed button");
            check(frame[0] == 0xffff0000, "Libretro input changes rendered frame");
            check(WindowsRetroNative.saveRam()[0] == 43,
                    "Libretro input affects native SRAM");

            byte[] state = WindowsRetroNative.saveState();
            check(state != null && state.length == 64, "Snapshot serializes");
            byte[] replacement = new byte[16];
            Arrays.fill(replacement, (byte) 7);
            check(WindowsRetroNative.loadRam(replacement), "SRAM import");
            check(WindowsRetroNative.saveRam()[0] == 7, "Imported SRAM visible");
            check(WindowsRetroNative.loadState(state), "Snapshot restores");
            check(WindowsRetroNative.saveRam()[0] == 43, "Snapshot restored SRAM");

            saves.store();
            check(Files.size(saves.ramFile) == 16, "SRAM saved to isolated directory");
            check(Files.size(saves.rtcFile) == 4, "RTC saved to isolated directory");

            WindowsRetroNative.step(1 << 4, frame, audio);
            saves.store();
            Path previous = saves.ramFile.resolveSibling(
                    saves.ramFile.getFileName() + ".previous");
            check(Files.isRegularFile(previous), "Previous SRAM retained");
            check(Files.readAllBytes(previous)[0] == 43, "Previous SRAM bytes intact");

            byte[] rtc = new byte[]{1, 2, 3, 4};
            check(WindowsRetroNative.loadRtc(rtc), "RTC import");
            check(Arrays.equals(WindowsRetroNative.saveRtc(), rtc), "RTC contents preserved");
            saves.store();
            check(Files.isRegularFile(saves.rtcFile.resolveSibling(
                    saves.rtcFile.getFileName() + ".previous")), "RTC previous is retained");
        } finally {
            WindowsRetroNative.close();
        }

        // Test reopening and restoring the battery from the on-disk copy.
        WindowsRetroNative.open(dll, rom, DesktopLibrary.Console.GB, saves.directory);
        try {
            saves.restore();
            check(WindowsRetroNative.saveRam()[0] == 44, "Battery restorable after restart");
            check(Arrays.equals(WindowsRetroNative.saveRtc(),
                    new byte[]{1,2,3,4}), "RTC restorable after restart");
        } finally {
            WindowsRetroNative.close();
        }

        Path testSave = root.resolve("atomic.srm");
        DesktopSaves.storeData(testSave, new byte[]{1, 2, 3}, 1024);
        DesktopSaves.storeData(testSave, new byte[]{4, 5, 6}, 1024);
        check(Arrays.equals(Files.readAllBytes(testSave), new byte[]{4, 5, 6}),
                "New battery write valid");
        check(Arrays.equals(Files.readAllBytes(
                testSave.resolveSibling("atomic.srm.previous")),
                new byte[]{1, 2, 3}), "Old battery kept intact");
        try {
            DesktopSaves.storeData(testSave, new byte[1100], 1024);
            throw new AssertionError("Oversized SRAM must be rejected");
        } catch (IOException expected) { }

        check(Files.size(rom) == 32768, "ROM was never modified");
        check(EmbeddedPlayerWindow.bitForKey(java.awt.event.KeyEvent.VK_X) == 4,
                "X triggers Nintendo A");
        check(EmbeddedPlayerWindow.bitForKey(java.awt.event.KeyEvent.VK_Z) == 5,
                "Z triggers Nintendo B");
        check(EmbeddedPlayerWindow.bitForKey(java.awt.event.KeyEvent.VK_ENTER) == 12,
                "Enter starts game");
        System.out.println("OKUBI Embedded Libretro core test: PASS");
    }
}
