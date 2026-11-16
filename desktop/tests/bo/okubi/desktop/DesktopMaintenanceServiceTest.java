package bo.okubi.desktop;

import java.nio.file.*;
import java.io.IOException;
import java.util.Comparator;

/** Synthetic files only. No user profile or ROM is ever accessed. */
public final class DesktopMaintenanceServiceTest {
    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("okubi-phase67-");
        try {
            Path protectedFolder = temp.resolve("APPDATA").resolve("OKUBI").resolve("saves");
            Files.createDirectories(protectedFolder);
            Path save = protectedFolder.resolve("pokemon.srm");
            Files.writeString(save, "IMPORTANT USER SAVE");
            Path rom = temp.resolve("owned-game.gba");
            Files.writeString(rom, "ORIGINAL ROM");
            Path cache = temp.resolve("APPDATA").resolve("OKUBI")
                    .resolve(DesktopMaintenanceService.CACHE_FOLDER);
            DesktopMaintenanceService service = new DesktopMaintenanceService(cache);
            check(!service.inspect().cacheExists(), "missing cache must stay missing");
            Files.createDirectories(cache.resolve("GBA"));
            String keyA = "a".repeat(64);
            String keyB = "b".repeat(64);
            Files.writeString(cache.resolve("GBA").resolve(keyA + ".png"), "12345");
            Files.writeString(cache.resolve("GBA").resolve(keyB + ".png.missing"), "6789");
            Path personal = cache.resolve("GBA").resolve("personal-image.png");
            Files.writeString(personal, "KEEP");
            var report = service.inspect();
            check(report.files() == 2 && report.bytes() == 9, "preview count and bytes");
            check(Files.exists(save) && Files.exists(rom), "preview changed protected files");
            var cleaned = service.cleanConfirmed();
            check(cleaned.files() == 2, "clean count");
            check(service.inspect().files() == 0, "cache not cleaned");
            check(Files.readString(save).equals("IMPORTANT USER SAVE"), "save was modified");
            check(Files.readString(rom).equals("ORIGINAL ROM"), "ROM was modified");
            check(Files.isDirectory(cache), "cache root unexpectedly removed");
            check(Files.readString(personal).equals("KEEP"), "manual cover should remain");
            // A malicious path pointing directly at saves must be rejected.
            boolean refused = false;
            try {
                new DesktopMaintenanceService(protectedFolder).cleanConfirmed();
            } catch (IOException expected) { refused = true; }
            check(refused && Files.exists(save), "protected save directory was accepted");
            // Test directory symlink when supported on this test host.
            Path linked = cache.resolve("GBA").resolve("outside-link");
            try {
                Files.createSymbolicLink(linked, protectedFolder);
                refused = false;
                try { service.cleanConfirmed(); } catch (IOException expected) { refused = true; }
                check(refused && Files.exists(save), "symlink traversal was not refused");
                Files.delete(linked);
            } catch (UnsupportedOperationException | java.nio.file.FileSystemException
                     | SecurityException unsupported) {
                System.out.println("Symlink fixture not supported; ordinary path tests passed");
            }
            System.out.println("PASS: safe cache preview, confirmed cleanup, ROM/SRAM preservation");
        } finally {
            try (var paths = Files.walk(temp)) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(p);
            }
        }
    }
}
