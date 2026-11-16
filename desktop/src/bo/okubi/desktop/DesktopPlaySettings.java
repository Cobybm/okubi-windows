package bo.okubi.desktop;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Standalone, non-destructive, per-console control and video profile.
 * No ROM paths, SRAM, library keys or Android data are accessed here.
 */
public final class DesktopPlaySettings {
    public enum Filter {
        SHARP("Píxeles nítidos"), SMOOTH("Suave HD"),
        LCD("LCD retro"), CRT("CRT scanlines"), INTEGER("Pixel Perfect");
        public final String display;
        Filter(String display) { this.display = display; }
    }

    /** Controls only the ABXY labels; d-pad, shoulders and Start stay mapped. */
    public enum GamepadLayout {
        NINTENDO("Distribución Nintendo"),
        XBOX_LABELS("Letras Xbox (A=A, B=B)");
        public final String display;
        GamepadLayout(String display) { this.display = display; }
    }

    public enum Action {
        UP(0, KeyEvent.VK_UP, "Arriba"),
        DOWN(1, KeyEvent.VK_DOWN, "Abajo"),
        LEFT(2, KeyEvent.VK_LEFT, "Izquierda"),
        RIGHT(3, KeyEvent.VK_RIGHT, "Derecha"),
        A(4, KeyEvent.VK_X, "Botón A"),
        B(5, KeyEvent.VK_Z, "Botón B"),
        X(7, KeyEvent.VK_S, "Botón X"),
        Y(8, KeyEvent.VK_A, "Botón Y"),
        L(10, KeyEvent.VK_Q, "Botón L"),
        R(11, KeyEvent.VK_W, "Botón R"),
        START(12, KeyEvent.VK_ENTER, "Start"),
        SELECT(13, KeyEvent.VK_SPACE, "Select");

        public final int libretroBit;
        public final int defaultKey;
        public final String label;
        Action(int libretroBit, int defaultKey, String label) {
            this.libretroBit = libretroBit;
            this.defaultKey = defaultKey;
            this.label = label;
        }
    }

    private final Path file;
    private final EnumMap<DesktopLibrary.Console, GamepadLayout> gamepads =
            new EnumMap<>(DesktopLibrary.Console.class);
    private final EnumMap<DesktopLibrary.Console, Filter> filters =
            new EnumMap<>(DesktopLibrary.Console.class);
    private final EnumMap<DesktopLibrary.Console, EnumMap<Action, Integer>> keys =
            new EnumMap<>(DesktopLibrary.Console.class);

    public DesktopPlaySettings(Path file) throws IOException {
        this.file = file.toAbsolutePath().normalize();
        for (DesktopLibrary.Console system : DesktopLibrary.Console.values()) {
            filters.put(system, Filter.SHARP);
            gamepads.put(system, GamepadLayout.NINTENDO);
            EnumMap<Action, Integer> mapping = new EnumMap<>(Action.class);
            for (Action action : Action.values())
                mapping.put(action, action.defaultKey);
            keys.put(system, mapping);
        }
        load();
    }

    public static Path defaultFile() {
        return DesktopLibrary.defaultSettingsFile().getParent()
                .resolve("cinema-controls-v1.properties");
    }

    public synchronized Filter filter(DesktopLibrary.Console system) {
        return filters.get(system);
    }

    public synchronized void setFilter(DesktopLibrary.Console system, Filter mode)
            throws IOException {
        if (system == null || mode == null) throw new IOException("Filtro desconocido.");
        filters.put(system, mode);
        save();
    }

    public synchronized GamepadLayout gamepadLayout(DesktopLibrary.Console system) {
        return gamepads.get(system);
    }

    public synchronized void setGamepadLayout(DesktopLibrary.Console system,
                                               GamepadLayout layout)
            throws IOException {
        if (system == null || layout == null)
            throw new IOException("Perfil de mando desconocido.");
        gamepads.put(system, layout);
        save();
    }

    public synchronized int applyGamepadLayout(DesktopLibrary.Console system,
                                                int nativeMask) {
        if (gamepads.get(system) == GamepadLayout.NINTENDO)
            return nativeMask;
        // The native XInput bridge maps Xbox physical A/B to Nintendo B/A,
        // and X/Y to Nintendo Y/X. Swap to match physical button labels.
        int result = nativeMask & ~((1 << 4) | (1 << 5) | (1 << 7) | (1 << 8));
        if ((nativeMask & (1 << 4)) != 0) result |= 1 << 5;
        if ((nativeMask & (1 << 5)) != 0) result |= 1 << 4;
        if ((nativeMask & (1 << 7)) != 0) result |= 1 << 8;
        if ((nativeMask & (1 << 8)) != 0) result |= 1 << 7;
        return result;
    }

    public synchronized int key(DesktopLibrary.Console system, Action action) {
        return keys.get(system).get(action);
    }

    public synchronized int bitFor(DesktopLibrary.Console system, int keyCode) {
        if (keyCode <= 0) return -1;
        for (Action action : Action.values())
            if (keys.get(system).get(action) == keyCode)
                return action.libretroBit;
        return -1;
    }

    public static boolean reservedKey(int code) {
        return code <= 0 || code == KeyEvent.VK_UNDEFINED ||
                code == KeyEvent.VK_ESCAPE || code == KeyEvent.VK_F1 ||
                code == KeyEvent.VK_F2 || code == KeyEvent.VK_F5 || code == KeyEvent.VK_F9 ||
                code == KeyEvent.VK_F11 || code == KeyEvent.VK_F12 ||
                code == KeyEvent.VK_P || code == KeyEvent.VK_M ||
                code == KeyEvent.VK_ALT || code == KeyEvent.VK_CONTROL ||
                code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_META ||
                code == KeyEvent.VK_WINDOWS || code == KeyEvent.VK_TAB;
    }

    public synchronized void remap(DesktopLibrary.Console system, Action action,
                                   int keyCode) throws IOException {
        if (system == null || action == null || reservedKey(keyCode))
            throw new IOException("Tecla reservada para Windows o para el menú de OKUBI.");
        EnumMap<Action, Integer> mapping = keys.get(system);
        for (Action other : Action.values())
            if (other != action && mapping.get(other) == keyCode)
                throw new IOException("La tecla " + KeyEvent.getKeyText(keyCode)
                        + " ya está asignada a " + other.label + ".");
        mapping.put(action, keyCode);
        save();
    }

    public synchronized void reset(DesktopLibrary.Console system) throws IOException {
        for (Action action : Action.values())
            keys.get(system).put(action, action.defaultKey);
        filters.put(system, Filter.SHARP);
        gamepads.put(system, GamepadLayout.NINTENDO);
        save();
    }

    public static int[] destination(int sourceWidth, int sourceHeight,
                                     int availableWidth, int availableHeight,
                                     Filter filter) {
        if (sourceWidth < 1 || sourceHeight < 1 ||
                availableWidth < 1 || availableHeight < 1)
            return new int[]{0, 0, 0, 0};
        double scale = Math.min((double)availableWidth / sourceWidth,
                (double)availableHeight / sourceHeight);
        if (filter == Filter.INTEGER && scale >= 1)
            scale = Math.floor(scale);
        int width = Math.max(1, Math.min(availableWidth,
                (int)Math.floor(sourceWidth * scale)));
        int height = Math.max(1, Math.min(availableHeight,
                (int)Math.floor(sourceHeight * scale)));
        return new int[]{(availableWidth - width) / 2,
                (availableHeight - height) / 2, width, height};
    }

    private void load() throws IOException {
        if (!Files.isRegularFile(file)) return;
        Properties stored = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            stored.load(input);
        }
        for (DesktopLibrary.Console system : DesktopLibrary.Console.values()) {
            String prefix = system.name() + ".";
            String layout = stored.getProperty(prefix + "gamepad");
            if (layout != null)
                try { gamepads.put(system, GamepadLayout.valueOf(layout)); }
                catch (IllegalArgumentException ignored) { }
            String filter = stored.getProperty(prefix + "filter");
            if (filter != null)
                try { filters.put(system, Filter.valueOf(filter)); }
                catch (IllegalArgumentException ignored) { }
            EnumMap<Action, Integer> loaded = new EnumMap<>(Action.class);
            Set<Integer> seen = new HashSet<>();
            for (Action action : Action.values()) {
                int key = action.defaultKey;
                String raw = stored.getProperty(prefix + action.name());
                if (raw != null) {
                    try { key = Integer.parseInt(raw); }
                    catch (NumberFormatException ignored) { }
                }
                if (reservedKey(key) || !seen.add(key))
                    key = action.defaultKey;
                loaded.put(action, key);
            }
            // If a corrupted profile still contains collisions after recovery,
            // use proven default mapping for this console only.
            if (new HashSet<>(loaded.values()).size() != Action.values().length) {
                for (Action action : Action.values())
                    loaded.put(action, action.defaultKey);
            }
            keys.put(system, loaded);
        }
    }

    private synchronized void save() throws IOException {
        Files.createDirectories(file.getParent());
        Properties output = new Properties();
        for (DesktopLibrary.Console system : DesktopLibrary.Console.values()) {
            String prefix = system.name() + ".";
            output.setProperty(prefix + "filter", filters.get(system).name());
            output.setProperty(prefix + "gamepad", gamepads.get(system).name());
            for (Action action : Action.values())
                output.setProperty(prefix + action.name(),
                        Integer.toString(keys.get(system).get(action)));
        }
        Path temporary = Files.createTempFile(file.getParent(),
                ".okubi-controls-", ".tmp");
        try {
            try (OutputStream stream = Files.newOutputStream(temporary)) {
                output.store(stream, "OKUBI CINEMA controls v1 - no user game data");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
