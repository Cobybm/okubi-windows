package bo.okubi.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** No emulator, game images or installed Windows software required. */
public final class DesktopLibraryTest {
    private static void check(boolean actual, String explanation) {
        if (!actual) throw new AssertionError(explanation);
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("okubi-desktop-test-");
        Path gba = root.resolve("Copia original Pokémon Esmeralda.gba");
        Path gb = root.resolve("Game Boy.gb");
        Path gbc = root.resolve("Game Boy Color.GBC");
        Path snes = root.resolve("SNES.sfc");
        Path snesSmc = root.resolve("SNES 2.smc");
        for (Path path : Arrays.asList(gba, gb, gbc, snes, snesSmc)) {
            Files.write(path, new byte[]{1, 2, 3, 4});
        }
        Path properties = root.resolve("preferences").resolve("library.properties");
        DesktopLibrary lib = new DesktopLibrary(properties);
        check(lib.games().isEmpty(), "New library is empty");
        check(lib.addGame(gba), "GBA import");
        check(lib.addGame(gb), "GB import");
        check(lib.addGame(gbc), "GBC import");
        check(lib.addGame(snes), "SNES .sfc import");
        check(lib.addGame(snesSmc), "SNES .smc import");
        check(!lib.addGame(gba), "Duplicate ROM is not re-added");
        check(lib.games().size() == 5, "All supported consoles");
        check(DesktopLibrary.Console.fromFile(gba) == DesktopLibrary.Console.GBA, "GBA recognition");
        check(DesktopLibrary.Console.fromFile(gbc) == DesktopLibrary.Console.GBC, "Case insensitive GBC");
        check(DesktopLibrary.Console.fromFile(snesSmc) == DesktopLibrary.Console.SNES, "SNES recognition");
        check(DesktopLibrary.Console.fromFile(root.resolve("bad.exe")) == null,
                "Never import executables as ROMs");

        DesktopLibrary.Game first = lib.games().stream().filter(g -> g.rom.equals(gba))
                .findFirst().orElseThrow();
        check(first.title().equals("Copia original Pokémon Esmeralda"), "Accents and spaces");
        lib.toggleFavorite(first);
        lib.played(first);
        DesktopLibrary reloaded = new DesktopLibrary(properties);
        DesktopLibrary.Game restored = reloaded.games().stream()
                .filter(g -> g.rom.equals(gba)).findFirst().orElseThrow();
        check(restored.favorite, "Favorite survives restart");
        check(restored.lastPlayed > 0, "Last-played survives restart");

        Path exe = root.resolve("Emuladores").resolve("SameBoy - SDL.exe");
        Files.createDirectories(exe.getParent());
        Files.write(exe, new byte[]{1, 2});
        reloaded.setEmulator(DesktopLibrary.Console.GBC, exe);
        check(new DesktopLibrary(properties).emulator(DesktopLibrary.Console.GBC).equals(exe),
                "Chosen emulator executable survives restart");

        Path fakeExe = root.resolve("virus.bat");
        Files.write(fakeExe, new byte[]{1});
        try {
            reloaded.setEmulator(DesktopLibrary.Console.GBA, fakeExe);
            throw new AssertionError("Batch files are never accepted as emulator executable");
        } catch (IOException expected) { }

        Path folder = root.resolve("Mis juegos").resolve("Pokémon");
        Files.createDirectories(folder);
        Path folderRom = folder.resolve("Rojo Fuego.gba");
        Files.write(folderRom, new byte[]{7, 8, 9});
        Files.write(folder.resolve("ignorar.exe"), new byte[]{7});
        int addedFromFolder = reloaded.addFolder(root.resolve("Mis juegos"));
        check(addedFromFolder == 1, "Recursive bulk import finds only supported ROMs");
        check(reloaded.addFolder(root.resolve("Mis juegos")) == 0,
                "Scanning twice never duplicates ROMs");
        check(new DesktopLibrary(properties).games().size() == 6,
                "Folder import persists locally without copying ROM bytes");

        byte[] before = Files.readAllBytes(gba);
        Files.delete(gb); // Missing paths remain in the library; never wipe saves or user data.
        check(lib.games().stream().filter(g -> g.rom.equals(gb))
                        .anyMatch(g -> !g.isAvailable()), "Missing ROM is displayed, not deleted");
        check(Arrays.equals(before, Files.readAllBytes(gba)), "ROM bytes were not modified");

        Path gameWithSpecialCharacters = root.resolve("my game & other;flags.gba");
        Files.write(gameWithSpecialCharacters, new byte[]{5});
        List<String> argsList = EmulatorLauncher.command(exe, gameWithSpecialCharacters);
        check(argsList.size() == 2, "No shells, injection or extra arguments");
        check(argsList.get(1).equals(gameWithSpecialCharacters.toAbsolutePath().toString()),
                "Spaces and shell metacharacters are preserved as literal path");
        check(argsList.get(0).equals(exe.toAbsolutePath().toString()),
                "Absolute emulator path is used");
        try {
            EmulatorLauncher.command(exe, gb);
            throw new AssertionError("Missing ROM must refuse launch");
        } catch (IOException expected) { }
        try {
            EmulatorLauncher.command(fakeExe, gba);
            throw new AssertionError("Non-executable extension must refuse launch");
        } catch (IOException expected) { }
        System.out.println("OKUBI Desktop library and launcher tests: PASS");
    }
}
