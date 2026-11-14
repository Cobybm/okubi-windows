package bo.okubi.desktop;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Time measured from a monotonic clock, no ROM images or physical controllers. */
public final class DesktopGameHubTest {
    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("okubi-game-hub-test-");
        Path statsFile = root.resolve("stats").resolve("gameplay-stats-v1.properties");
        Path rom = root.resolve("Mis juegos").resolve("Pokémon Rojo Fuego.gba");
        Path other = root.resolve("Mis juegos").resolve("Pokémon Esmeralda.gba");
        DesktopPlayStats stats = new DesktopPlayStats(statsFile);
        check(stats.get(DesktopLibrary.Console.GBA, rom).seconds == 0,
                "No invented hours for games already in a library");
        check(stats.totalSeconds() == 0 && stats.totalSessions() == 0,
                "New installation has zero real playtime");
        check(DesktopPlayStats.id(DesktopLibrary.Console.GBA, rom).length() == 64,
                "Game ID is a stable SHA-256 hash");
        check(!DesktopPlayStats.id(DesktopLibrary.Console.GBA, rom)
                        .equals(DesktopPlayStats.id(DesktopLibrary.Console.GB, rom)),
                "Same path under a different console has distinct stats");

        DesktopPlayStats.Session session = stats.begin(DesktopLibrary.Console.GBA, rom);
        check(stats.get(DesktopLibrary.Console.GBA, rom).sessions == 1,
                "Count real sessions only on successful emulator startup");
        session.tick(true, 10_000_000_000L);
        for (int i = 1; i <= 20; i++)
            session.tick(true, 10_000_000_000L + i * 1_000_000_000L);
        check(stats.get(DesktopLibrary.Console.GBA, rom).seconds == 15,
                "Periodic 15s checkpoint protects time after a crash");
        session.tick(false, 31_000_000_000L);
        session.tick(false, 45_000_000_000L);
        session.tick(true, 46_000_000_000L);
        session.tick(true, 47_000_000_000L);
        session.finish();
        session.finish(); // Idempotent, no double count.
        check(stats.get(DesktopLibrary.Console.GBA, rom).seconds == 21,
                "Only active frames count; pause and focus loss excluded");
        check(Files.isRegularFile(statsFile), "Stats saved to isolated local file");
        check(new DesktopPlayStats(statsFile)
                .get(DesktopLibrary.Console.GBA, rom).seconds == 21,
                "Time survives application restart");
        check(stats.get(DesktopLibrary.Console.GBA, other).sessions == 0,
                "Other game has no invented session");
        DesktopPlayStats.Session second = stats.begin(DesktopLibrary.Console.GBA, other);
        second.tick(true, 0);
        second.tick(true, 8_000_000_000L); // >2s: stalled frames not counted.
        second.tick(true, 9_000_000_000L);
        second.finish();
        check(stats.get(DesktopLibrary.Console.GBA, other).seconds == 1,
                "Unresponsive or suspended application time is excluded");
        check(stats.totalSeconds() == 22 && stats.totalSessions() == 2,
                "Real global totals sum independent game sessions");
        check(DesktopPlayStats.duration(0).equals("0 s"), "Zero remains zero");
        check(DesktopPlayStats.duration(90).equals("1 min"), "Readable minutes");
        check(DesktopPlayStats.duration(3720).equals("1 h 2 min"), "Readable hours");

        // Controller per-console preference is independent of the old keyboard layout.
        Path controls = root.resolve("cinema-controls-v1.properties");
        DesktopPlaySettings settings = new DesktopPlaySettings(controls);
        int raw = (1 << 5) | (1 << 7) | (1 << 12); // physical Xbox A, Y, Start
        check(settings.applyGamepadLayout(DesktopLibrary.Console.GBA, raw) == raw,
                "Nintendo ABXY profile preserves historical 0.6 mapping");
        settings.setGamepadLayout(DesktopLibrary.Console.GBA,
                DesktopPlaySettings.GamepadLayout.XBOX_LABELS);
        int expected = (1 << 4) | (1 << 8) | (1 << 12);
        check(settings.applyGamepadLayout(DesktopLibrary.Console.GBA, raw) == expected,
                "Xbox labels map A->A and Y->Y, preserve Start");
        check(settings.applyGamepadLayout(DesktopLibrary.Console.SNES, raw) == raw,
                "Changing GBA controller never affects SNES");
        DesktopPlaySettings restored = new DesktopPlaySettings(controls);
        check(restored.gamepadLayout(DesktopLibrary.Console.GBA)
                        == DesktopPlaySettings.GamepadLayout.XBOX_LABELS,
                "Selected controller profile survives restart");
        check(restored.key(DesktopLibrary.Console.GBA, DesktopPlaySettings.Action.B)
                == KeyEvent.VK_Z, "Controller preset never changes custom keyboard bindings");
        restored.reset(DesktopLibrary.Console.GBA);
        check(new DesktopPlaySettings(controls)
                        .gamepadLayout(DesktopLibrary.Console.GBA)
                        == DesktopPlaySettings.GamepadLayout.NINTENDO,
                "Reset restores original Xbox/Nintendo layout");

        // Fault-tolerant parsing: corrupt historical stats never become giant numbers.
        Properties corrupt = new Properties();
        corrupt.setProperty("count", "1");
        corrupt.setProperty("game.0.id",
                DesktopPlayStats.id(DesktopLibrary.Console.GBA, rom));
        corrupt.setProperty("game.0.seconds", "-100000000");
        corrupt.setProperty("game.0.sessions", "broken");
        try (java.io.OutputStream out = Files.newOutputStream(statsFile)) {
            corrupt.store(out, "corrupt statistics fixture");
        }
        DesktopPlayStats recovered = new DesktopPlayStats(statsFile);
        check(recovered.get(DesktopLibrary.Console.GBA, rom).seconds == 0,
                "Never interpret a corrupt negative duration");
        check(recovered.get(DesktopLibrary.Console.GBA, rom).sessions == 0,
                "Corrupt session count safely resets to zero");

        System.out.println("DesktopGameHubTest: PASS");
    }
}
