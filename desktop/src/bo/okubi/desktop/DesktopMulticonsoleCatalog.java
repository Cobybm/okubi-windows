package bo.okubi.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Independent PS1/PS2/PS3 *reference* catalog.
 * Never copies ROMs, alters BIOS/firmware, touches legacy GB/SNES libraries
 * or migrates game save files; stored in separate multiconsole-v1.properties.
 */
public final class DesktopMulticonsoleCatalog {
    public record Game(Path file, DesktopSmartPerformance.Console console,
                       DesktopSmartPerformance.Mode mode) {
        public String title() {
            if (file.getFileName().toString().equalsIgnoreCase("EBOOT.BIN")) {
                Path usrdir = file.getParent();
                Path ps3game = usrdir == null ? null : usrdir.getParent();
                Path root = ps3game == null ? null : ps3game.getParent();
                return root != null ? root.getFileName().toString() : "PS3 EBOOT";
            }
            String name = file.getFileName().toString();
            int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(0, dot) : name;
        }
        public boolean available() {
            return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    && Files.isReadable(file);
        }
    }
    private static final int MAX_GAMES = 1000;
    private final Path settings;
    private final Map<String, Game> games = new LinkedHashMap<>();
    private final EnumMap<DesktopSmartPerformance.Console, Path> executable =
            new EnumMap<>(DesktopSmartPerformance.Console.class);

    public DesktopMulticonsoleCatalog(Path settings) throws IOException {
        this.settings = settings.toAbsolutePath().normalize();
        load();
    }
    public static Path defaultFile() {
        String appData = System.getenv("APPDATA");
        Path root = appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".okubi")
                : Path.of(appData);
        return root.resolve("OKUBI").resolve("multiconsole-v1.properties");
    }
    private static Path normalize(Path path) throws IOException {
        if (path == null) throw new IOException("Falta una ruta de juego.");
        return path.toAbsolutePath().normalize();
    }
    public synchronized boolean addGame(Path path, DesktopSmartPerformance.Console console)
            throws IOException {
        Path file = normalize(path);
        if (console == null || !DesktopSmartPerformance.acceptedFile(
                console, file.getFileName().toString()))
            throw new IOException("Formato incompatible con la consola seleccionada.");
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(file))
            throw new IOException("Juego no disponible o vínculo simbólico prohibido.");
        String key = file.toString();
        Game old = games.get(key);
        if (old == null && games.size() >= MAX_GAMES)
            throw new IOException("Biblioteca alcanzó 1000 referencias.");
        if (old != null && old.console() == console) return false;
        games.put(key, new Game(file, console, old == null
                ? DesktopSmartPerformance.Mode.AUTO : old.mode()));
        save();
        return true;
    }
    public synchronized int addFolder(Path source) throws IOException {
        Path root = normalize(source);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Elige una carpeta real de juegos.");
        int count = 0;
        try (var files = Files.walk(root, 4)) {
            for (Path path : (Iterable<Path>)files::iterator) {
                if (games.size() >= MAX_GAMES) break;
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                var console = DesktopSmartPerformance.suggestConsole(
                        path.getFileName().toString());
                if (console == null || !Files.isReadable(path)) continue;
                String key = path.toAbsolutePath().normalize().toString();
                if (!games.containsKey(key)) {
                    games.put(key, new Game(path.toAbsolutePath().normalize(), console,
                            DesktopSmartPerformance.Mode.AUTO));
                    count++;
                }
            }
        } finally {
            if (count > 0) save();
        }
        return count;
    }
    public synchronized void mode(Game game, DesktopSmartPerformance.Mode mode)
            throws IOException {
        if (game == null || mode == null || !games.containsKey(game.file().toString()))
            throw new IOException("Juego no encontrado.");
        games.put(game.file().toString(), new Game(game.file(), game.console(), mode));
        save();
    }
    public synchronized void remove(Game game) throws IOException {
        if (game == null) return;
        if (games.remove(game.file().toString()) != null) save();
        // Only removes the reference, never deletes the user's game.
    }
    public synchronized List<Game> games() {
        return List.copyOf(games.values());
    }
    public synchronized Path emulator(DesktopSmartPerformance.Console console) {
        return executable.get(console);
    }
    public synchronized void emulator(DesktopSmartPerformance.Console console, Path exe)
            throws IOException {
        if (console == null) throw new IOException("Selecciona la consola.");
        Path path = normalize(exe);
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        String prefix = switch (console) {
            case PS1 -> "duckstation";
            case PS2 -> "pcsx2";
            case PS3 -> "rpcs3";
        };
        if (!name.startsWith(prefix) || !name.endsWith(".exe")
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(path))
            throw new IOException("Elige el .exe confiable oficial de "
                    + console.emulatorName + ", no otro programa.");
        executable.put(console, path);
        save();
    }
    private void load() throws IOException {
        if (!Files.isRegularFile(settings, LinkOption.NOFOLLOW_LINKS)) return;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(settings)) { props.load(in); }
        for (var console : DesktopSmartPerformance.Console.values()) {
            String emu = props.getProperty("emu." + console.name());
            if (emu == null || emu.isBlank()) continue;
            try {
                executable.put(console, Path.of(emu).toAbsolutePath().normalize());
            } catch (RuntimeException ignored) { /* Preserve old save settings. */ }
        }
        int count;
        try { count = Math.max(0, Math.min(MAX_GAMES,
                Integer.parseInt(props.getProperty("count", "0")))); }
        catch (NumberFormatException bad) { count = 0; }
        for (int i = 0; i < count; i++) {
            String value = props.getProperty("game." + i + ".path");
            if (value == null || value.isBlank()) continue;
            try {
                Path path = Path.of(value).toAbsolutePath().normalize();
                DesktopSmartPerformance.Console console =
                        DesktopSmartPerformance.Console.valueOf(
                        props.getProperty("game." + i + ".console", ""));
                if (!DesktopSmartPerformance.acceptedFile(console,
                        path.getFileName().toString())) continue;
                DesktopSmartPerformance.Mode mode;
                try {
                    mode = DesktopSmartPerformance.Mode.valueOf(
                            props.getProperty("game." + i + ".mode", "AUTO"));
                } catch (RuntimeException bad) { mode = DesktopSmartPerformance.Mode.AUTO; }
                games.putIfAbsent(path.toString(), new Game(path, console, mode));
            } catch (RuntimeException ignored) { /* Skip corrupt entry. */ }
        }
    }
    private void save() throws IOException {
        Path parent = settings.getParent();
        Files.createDirectories(parent);
        Properties props = new Properties();
        props.setProperty("format.version", "1");
        props.setProperty("count", Integer.toString(games.size()));
        for (var console : executable.entrySet())
            props.setProperty("emu." + console.getKey().name(),
                    console.getValue().toString());
        int i = 0;
        for (var game : games.values()) {
            String key = "game." + i++ + ".";
            props.setProperty(key + "path", game.file().toString());
            props.setProperty(key + "console", game.console().name());
            props.setProperty(key + "mode", game.mode().name());
        }
        Path temporary = Files.createTempFile(parent, ".okubi-multiconsole-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temporary))
                { props.store(out, "OKUBI multiconsole path references only - no ROM contents"); }
            try {
                Files.move(temporary, settings, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, settings, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
