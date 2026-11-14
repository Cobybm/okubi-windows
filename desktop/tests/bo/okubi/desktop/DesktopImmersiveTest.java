package bo.okubi.desktop;

import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Standalone deterministic HUD tests. Emulation worker, SRAM, ROM and saves
 * are never opened by the immersive chrome controller.
 */
public final class DesktopImmersiveTest {
    private static void yes(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("okubi-immersive-");
        Path file = root.resolve("settings").resolve("cinema-hud-v1.properties");
        Path save = root.resolve("saved-Emerald.srm");
        byte[] ram = new byte[]{23, 45, 66, 79};
        Files.write(save, ram);

        DesktopHudSettings prefs = new DesktopHudSettings(file);
        for (DesktopLibrary.Console system : DesktopLibrary.Console.values())
            yes(prefs.mode(system) == DesktopHudSettings.Mode.AUTO,
                    "Every console starts with auto-hide, no imported guesses");

        DesktopHudSettings.View full =
                DesktopHudSettings.view(DesktopHudSettings.Mode.FULL, false, false);
        yes(full.header() && full.footer() && !full.compactHeader(),
                "FULL keeps title, commands and footer permanently present");
        DesktopHudSettings.View compact =
                DesktopHudSettings.view(DesktopHudSettings.Mode.COMPACT, false, false);
        yes(compact.header() && !compact.footer() && compact.compactHeader(),
                "COMPACT has a slimmer header but no lower bar");
        DesktopHudSettings.View autoVisible =
                DesktopHudSettings.view(DesktopHudSettings.Mode.AUTO, true, false);
        DesktopHudSettings.View autoHidden =
                DesktopHudSettings.view(DesktopHudSettings.Mode.AUTO, false, false);
        yes(autoVisible.header() && autoVisible.footer(),
                "AUTO HUD appears on user pointer activity");
        yes(!autoHidden.header() && !autoHidden.footer(),
                "AUTO HUD disappears on Swing timer expiry");
        DesktopHudSettings.View trulyHidden =
                DesktopHudSettings.view(DesktopHudSettings.Mode.HIDDEN, false, false);
        yes(!trulyHidden.header() && !trulyHidden.footer(),
                "HIDDEN leaves no chrome obscuring video");
        yes(DesktopHudSettings.view(DesktopHudSettings.Mode.HIDDEN, true, false)
                        .footer(),
                "Explicit F1 peek can temporarily reveal even HIDDEN mode");

        for (DesktopHudSettings.Mode mode : DesktopHudSettings.Mode.values()) {
            DesktopHudSettings.View menu =
                    DesktopHudSettings.view(mode, true, true);
            yes(!menu.header() && !menu.footer(),
                    "ESC pause/menu overlays never depend on HUD visibility");
        }
        yes(DesktopHudSettings.autoHide(DesktopHudSettings.Mode.AUTO)
                && DesktopHudSettings.autoHide(DesktopHudSettings.Mode.HIDDEN),
                "Only AUTO / HIDDEN use the 2.9-second hide timer");
        yes(!DesktopHudSettings.autoHide(DesktopHudSettings.Mode.FULL)
                && !DesktopHudSettings.autoHide(DesktopHudSettings.Mode.COMPACT),
                "Explicit permanently visible modes cannot unexpectedly disappear");
        yes(DesktopHudSettings.revealOnMouse(DesktopHudSettings.Mode.AUTO),
                "AUTO shows bars on mouse move");
        yes(!DesktopHudSettings.revealOnMouse(DesktopHudSettings.Mode.HIDDEN),
                "HIDDEN requires F1 or ESC, and does not flicker on mouse move");

        prefs.setMode(DesktopLibrary.Console.GBA, DesktopHudSettings.Mode.HIDDEN);
        prefs.setMode(DesktopLibrary.Console.GB, DesktopHudSettings.Mode.COMPACT);
        prefs.setMode(DesktopLibrary.Console.SNES, DesktopHudSettings.Mode.FULL);
        DesktopHudSettings restored = new DesktopHudSettings(file);
        yes(restored.mode(DesktopLibrary.Console.GBA) == DesktopHudSettings.Mode.HIDDEN,
                "GBA HIDDEN preference survives app restart");
        yes(restored.mode(DesktopLibrary.Console.GB) == DesktopHudSettings.Mode.COMPACT,
                "GB COMPACT preference survives app restart");
        yes(restored.mode(DesktopLibrary.Console.SNES) == DesktopHudSettings.Mode.FULL,
                "SNES FULL preference survives app restart");
        yes(restored.mode(DesktopLibrary.Console.GBC) == DesktopHudSettings.Mode.AUTO,
                "GBC retains independent default settings");

        // Shared keyboard profiles never assign F1 to an in-game control.
        yes(DesktopPlaySettings.reservedKey(KeyEvent.VK_F1),
                "F1 stays reserved for HUD from game controls");
        yes(DesktopPlaySettings.reservedKey(KeyEvent.VK_ESCAPE),
                "ESC stays reserved for in-game menu");

        // Corrupt/unknown user mode recovers without changing game saves.
        Properties corrupted = new Properties();
        corrupted.setProperty("GBA.hud", "UNSUPPORTED_MODE");
        corrupted.setProperty("GB.hud", "FULL");
        try (java.io.OutputStream stream = Files.newOutputStream(file)) {
            corrupted.store(stream, "future/invalid mode");
        }
        DesktopHudSettings safe = new DesktopHudSettings(file);
        yes(safe.mode(DesktopLibrary.Console.GBA) == DesktopHudSettings.Mode.AUTO,
                "Invalid setting safely returns to AUTO");
        yes(safe.mode(DesktopLibrary.Console.GB) == DesktopHudSettings.Mode.FULL,
                "Valid saved preference preserved with corrupt neighbor");
        yes(java.util.Arrays.equals(Files.readAllBytes(save), ram),
                "HUD configuration never touches SRAM or RTC files");
        yes(Files.list(file.getParent()).noneMatch(
                p -> p.toString().endsWith(".tmp")),
                "Atomic HUD preference writer leaves no temp files");

        System.out.println("DesktopImmersiveTest: PASS");
    }
}
