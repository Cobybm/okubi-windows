package bo.okubi.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Local-only library of paths to user-owned ROMs. Never imports ROM bytes,
 * deletes saves, downloads games, or changes files owned by emulators.
 */
public final class DesktopLibrary {
    public enum Console {
        GB("Game Boy"), GBC("Game Boy Color"),
        GBA("Game Boy Advance"), SNES("Super Nintendo");
        public final String label;
        Console(String label) { this.label = label; }

        public static Console fromFile(Path file) {
            if (file == null || file.getFileName() == null) return null;
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".gb")) return GB;
            if (name.endsWith(".gbc")) return GBC;
            if (name.endsWith(".gba")) return GBA;
            if (name.endsWith(".sfc") || name.endsWith(".smc")) return SNES;
            return null;
        }
    }

    public static final class Game {
        public final Path rom;
        public final Console console;
        public boolean favorite;
        public long lastPlayed;

        private Game(Path rom, Console console, boolean favorite, long lastPlayed) {
            this.rom = rom;
            this.console = console;
            this.favorite = favorite;
            this.lastPlayed = lastPlayed;
        }

        public String title() {
            String name = rom.getFileName().toString();
            int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(0, dot) : name;
        }

        public boolean isAvailable() { return Files.isRegularFile(rom); }
    }

    private final Path settingsFile;
    private final Map<String, Game> games = new LinkedHashMap<>();
    private final EnumMap<Console, Path> emulators = new EnumMap<>(Console.class);
    private final EnumMap<Console, Path> libretroCores = new EnumMap<>(Console.class);

    public DesktopLibrary(Path settingsFile) throws IOException {
        this.settingsFile = settingsFile.toAbsolutePath().normalize();
        load();
    }

    public static Path defaultSettingsFile() {
        String appData = System.getenv("APPDATA");
        Path root = appData != null && !appData.isBlank()
                ? Path.of(appData) : Path.of(System.getProperty("user.home"), ".okubi");
        return root.resolve("OKUBI").resolve("desktop-library.properties");
    }

    public synchronized boolean addGame(Path rom) throws IOException {
        if (rom == null) throw new IOException("Selecciona un archivo ROM.");
        Path safe = rom.toAbsolutePath().normalize();
        Console console = Console.fromFile(safe);
        if (console == null) throw new IOException("Formato no compatible: " + safe.getFileName());
        if (!Files.isRegularFile(safe) || !Files.isReadable(safe))
            throw new IOException("No se puede leer la ROM: " + safe);
        String key = safe.toString();
        if (games.containsKey(key)) return false;
        if (games.size() >= 2000) throw new IOException("Límite de biblioteca alcanzado.");
        games.put(key, new Game(safe, console, false, 0));
        save();
        return true;
    }

    /**
     * Import a user-selected folder as references only (max four directory
     * levels, without following symlinks). Never copies or touches game bytes.
     */
    public synchronized int addFolder(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory))
            throw new IOException("Selecciona una carpeta de juegos válida.");
        int imported = 0;
        try (java.util.stream.Stream<Path> scan = Files.walk(directory, 4)) {
            for (Path candidate : (Iterable<Path>) scan::iterator) {
                if (games.size() >= 2000) break;
                if (DesktopLibrary.Console.fromFile(candidate) == null
                        || !Files.isRegularFile(candidate)
                        || !Files.isReadable(candidate)) continue;
                Path safe = candidate.toAbsolutePath().normalize();
                if (!games.containsKey(safe.toString())) {
                    games.put(safe.toString(), new Game(safe, Console.fromFile(safe),
                            false, 0));
                    imported++;
                }
            }
        } finally {
            // Even if one nested subfolder was unreadable, any discovered
            // games remain in the library instead of being discarded.
            if (imported > 0) save();
        }
        return imported;
    }

    public synchronized List<Game> games() {
        List<Game> result = new ArrayList<>(games.values());
        result.sort(Comparator.comparing((Game g) -> !g.favorite)
                .thenComparing(Game::title, String.CASE_INSENSITIVE_ORDER));
        return Collections.unmodifiableList(result);
    }

    public synchronized void toggleFavorite(Game game) throws IOException {
        Game stored = games.get(game.rom.toString());
        if (stored == null) throw new IOException("El juego ya no está en la biblioteca.");
        stored.favorite = !stored.favorite;
        save();
    }

    public synchronized void played(Game game) throws IOException {
        Game stored = games.get(game.rom.toString());
        if (stored == null) throw new IOException("El juego ya no está en la biblioteca.");
        stored.lastPlayed = System.currentTimeMillis();
        save();
    }

    public synchronized void setEmulator(Console console, Path executable) throws IOException {
        if (console == null) throw new IOException("Selecciona una consola.");
        if (executable == null) throw new IOException("Selecciona un ejecutable.");
        Path safe = executable.toAbsolutePath().normalize();
        if (!Files.isRegularFile(safe)
                || !safe.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe"))
            throw new IOException("Selecciona un emulador de Windows (.exe) válido.");
        emulators.put(console, safe);
        save();
    }

    public synchronized Path emulator(Console console) { return emulators.get(console); }

    /** A Windows x64 Libretro DLL is not the same as an emulator EXE. */
    public synchronized void setLibretroCore(Console console, Path dll) throws IOException {
        if (console == null || dll == null) throw new IOException("Selecciona una consola y núcleo.");
        Path core = dll.toAbsolutePath().normalize();
        if (!Files.isRegularFile(core)
                || !core.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".dll"))
            throw new IOException("Selecciona una DLL Libretro Windows x64 válida.");
        libretroCores.put(console, core);
        save();
    }

    public synchronized Path libretroCore(Console console) { return libretroCores.get(console); }

    private void load() throws IOException {
        if (!Files.isRegularFile(settingsFile)) return;
        Properties values = new Properties();
        try (InputStream in = Files.newInputStream(settingsFile)) { values.load(in); }
        for (Console console : Console.values()) {
            String path = values.getProperty("emulator." + console.name());
            if (path != null && !path.isBlank()) {
                try { emulators.put(console, Path.of(path).toAbsolutePath().normalize()); }
                catch (RuntimeException ignored) { /* Corrupt old path is ignored. */ }
            }
        }
        for (Console console : Console.values()) {
            String path = values.getProperty("libretro." + console.name());
            if (path != null && !path.isBlank()) {
                try { libretroCores.put(console, Path.of(path).toAbsolutePath().normalize()); }
                catch (RuntimeException ignored) { /* No loss of older preferences. */ }
            }
        }
        int count;
        try { count = Math.max(0, Math.min(2000,
                Integer.parseInt(values.getProperty("games.count", "0")))); }
        catch (NumberFormatException ignored) { count = 0; }
        for (int i = 0; i < count; i++) {
            String prefix = "game." + i + ".";
            String path = values.getProperty(prefix + "path");
            if (path == null || path.isBlank()) continue;
            try {
                Path rom = Path.of(path).toAbsolutePath().normalize();
                Console console = Console.fromFile(rom);
                if (console == null) continue;
                long played;
                try { played = Math.max(0, Long.parseLong(values.getProperty(prefix + "played", "0"))); }
                catch (NumberFormatException ignored) { played = 0; }
                games.putIfAbsent(rom.toString(), new Game(rom, console,
                        Boolean.parseBoolean(values.getProperty(prefix + "favorite", "false")),
                        played));
            } catch (RuntimeException ignored) { /* Never crash on invalid settings. */ }
        }
    }

    private synchronized void save() throws IOException {
        Path parent = settingsFile.getParent();
        Files.createDirectories(parent);
        Properties values = new Properties();
        for (Console console : Console.values()) {
            Path emulator = emulators.get(console);
            if (emulator != null) values.setProperty("emulator." + console.name(),
                    emulator.toString());
        }
        for (Console console : Console.values()) {
            Path dll = libretroCores.get(console);
            if (dll != null) values.setProperty("libretro." + console.name(), dll.toString());
        }
        values.setProperty("games.count", Integer.toString(games.size()));
        int i = 0;
        for (Game game : games.values()) {
            String prefix = "game." + i++ + ".";
            values.setProperty(prefix + "path", game.rom.toString());
            values.setProperty(prefix + "favorite", Boolean.toString(game.favorite));
            values.setProperty(prefix + "played", Long.toString(game.lastPlayed));
        }
        Path temporary = Files.createTempFile(parent, ".okubi-library-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temporary)) {
                values.store(out, "OKUBI Desktop - local library paths only, no ROM bytes");
            }
            try {
                Files.move(temporary, settingsFile,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, settingsFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
