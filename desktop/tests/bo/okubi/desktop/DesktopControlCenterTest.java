package bo.okubi.desktop;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

/** Independent JVM regression checks: no display, real game, or core needed. */
public final class DesktopControlCenterTest {
    private static void check(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("okubi-cinema-controls-");
        Path settings = directory.resolve("cinema-controls-v1.properties");
        DesktopPlaySettings initial = new DesktopPlaySettings(settings);
        for (DesktopLibrary.Console system : DesktopLibrary.Console.values()) {
            check(initial.filter(system) == DesktopPlaySettings.Filter.SHARP,
                    "New console defaults to SHARP");
            for (DesktopPlaySettings.Action action : DesktopPlaySettings.Action.values()) {
                check(initial.key(system, action) == action.defaultKey,
                        "Default keyboard map for " + system + ": " + action);
                check(initial.bitFor(system, action.defaultKey) == action.libretroBit,
                        "Libretro input bits remain identical to old 0.4 controls");
            }
        }
        DesktopPlaySettings.Action action = DesktopPlaySettings.Action.B;
        initial.remap(DesktopLibrary.Console.GBA, action, KeyEvent.VK_C);
        initial.setFilter(DesktopLibrary.Console.GBA, DesktopPlaySettings.Filter.CRT);
        initial.setFilter(DesktopLibrary.Console.GB, DesktopPlaySettings.Filter.LCD);
        initial.setFilter(DesktopLibrary.Console.SNES, DesktopPlaySettings.Filter.INTEGER);

        DesktopPlaySettings recovered = new DesktopPlaySettings(settings);
        check(recovered.key(DesktopLibrary.Console.GBA, action) == KeyEvent.VK_C,
                "GBA rebind persists after process restart");
        check(recovered.bitFor(DesktopLibrary.Console.GBA, KeyEvent.VK_C) == 5,
                "GBA B input uses remapped key");
        check(recovered.bitFor(DesktopLibrary.Console.GBA, KeyEvent.VK_Z) == -1,
                "Old GBA key no longer triggers B after rebind");
        check(recovered.bitFor(DesktopLibrary.Console.GB, KeyEvent.VK_Z) == 5,
                "GB remains unchanged when changing GBA");
        check(recovered.filter(DesktopLibrary.Console.GBA) == DesktopPlaySettings.Filter.CRT,
                "CRT filter persists for GBA");
        check(recovered.filter(DesktopLibrary.Console.GB) == DesktopPlaySettings.Filter.LCD,
                "LCD filter is independent of CRT");
        check(recovered.filter(DesktopLibrary.Console.SNES) == DesktopPlaySettings.Filter.INTEGER,
                "SNES integer mode persists");
        check(recovered.filter(DesktopLibrary.Console.GBC) == DesktopPlaySettings.Filter.SHARP,
                "GBC unaffected by other consoles");

        for (int reserved : new int[]{KeyEvent.VK_ESCAPE, KeyEvent.VK_F5,
                KeyEvent.VK_F9, KeyEvent.VK_F11, KeyEvent.VK_F12, KeyEvent.VK_P,
                KeyEvent.VK_M, KeyEvent.VK_TAB, KeyEvent.VK_CONTROL}) {
            try {
                recovered.remap(DesktopLibrary.Console.GBA, action, reserved);
                throw new AssertionError("Reserved shortcut accepted: " + reserved);
            } catch (IOException expected) { }
        }
        try {
            recovered.remap(DesktopLibrary.Console.GBA, action, KeyEvent.VK_X);
            throw new AssertionError("Duplicate A mapping accepted");
        } catch (IOException expected) { }
        check(recovered.key(DesktopLibrary.Console.GBA, action) == KeyEvent.VK_C,
                "Rejected remaps do not alter valid mapping");

        int[] nearest = DesktopPlaySettings.destination(240, 160, 1079, 699,
                DesktopPlaySettings.Filter.INTEGER);
        check(nearest[2] == 960 && nearest[3] == 640,
                "Pixel Perfect uses exact integer 4x size");
        check(nearest[0] == (1079 - 960) / 2 && nearest[1] == (699 - 640) / 2,
                "Pixel Perfect centers the frame");
        int[] scaled = DesktopPlaySettings.destination(240, 160, 1079, 699,
                DesktopPlaySettings.Filter.SMOOTH);
        check(scaled[2] > 960 && scaled[3] > 640,
                "Fractional modes maximize viewport while preserving aspect");
        int[] small = DesktopPlaySettings.destination(256, 224, 200, 90,
                DesktopPlaySettings.Filter.INTEGER);
        check(small[2] <= 200 && small[3] <= 90
                && small[2] > 0 && small[3] > 0,
                "Tiny split screen shrinks image instead of clipping");
        int[] invalid = DesktopPlaySettings.destination(0, 144, 1280, 720,
                DesktopPlaySettings.Filter.CRT);
        check(Arrays.equals(invalid, new int[]{0,0,0,0}),
                "Invalid dimensions never produce a drawing target");
        check(DesktopPlaySettings.Filter.values().length == 5,
                "All five visual modes remain selectable");

        recovered.reset(DesktopLibrary.Console.GBA);
        DesktopPlaySettings reset = new DesktopPlaySettings(settings);
        check(reset.key(DesktopLibrary.Console.GBA, action) == KeyEvent.VK_Z,
                "Restore defaults resets controls on one console");
        check(reset.filter(DesktopLibrary.Console.GBA) == DesktopPlaySettings.Filter.SHARP,
                "Restore defaults resets the affected filter only");
        check(reset.filter(DesktopLibrary.Console.SNES) == DesktopPlaySettings.Filter.INTEGER,
                "Restore on GBA never changes SNES");

        // A corrupted profile should fail safely back to defaults.
        Properties damaged = new Properties();
        damaged.setProperty("GB.A", "invalid");
        damaged.setProperty("GB.B", Integer.toString(KeyEvent.VK_X));
        damaged.setProperty("GB.filter", "INVALID_FILTER");
        try (java.io.OutputStream out = Files.newOutputStream(settings)) {
            damaged.store(out, "corrupt preferences fixture");
        }
        DesktopPlaySettings safe = new DesktopPlaySettings(settings);
        check(safe.key(DesktopLibrary.Console.GB, DesktopPlaySettings.Action.A)
                == KeyEvent.VK_X, "Valid fallback A key preserved");
        check(safe.key(DesktopLibrary.Console.GB, DesktopPlaySettings.Action.B)
                == KeyEvent.VK_Z, "Duplicate B key recovered to default");
        check(safe.filter(DesktopLibrary.Console.GB) == DesktopPlaySettings.Filter.SHARP,
                "Invalid filter recovered");
        check(Files.list(directory).noneMatch(p -> p.toString().endsWith(".tmp")),
                "No temporary files left by atomic save");

        System.out.println("DesktopControlCenterTest: PASS");
    }
}
