package bo.okubi.desktop.controls;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Controller/keyboard guide data. Does not claim to detect hardware;
 * the app's input backend must supply the active device and actual bindings.
 */
public final class ControlGuideRegistry {
    public enum Device { KEYBOARD, GAMEPAD }
    public record Binding(String action, String keyboard, String gamepad) {
        public Binding {
            Objects.requireNonNull(action);
            Objects.requireNonNull(keyboard);
            Objects.requireNonNull(gamepad);
        }
    }

    private static final Map<String, Map<String, Binding>> DEFAULTS = createDefaults();
    private final Properties seen = new Properties();
    private final Path storage;

    public ControlGuideRegistry(Path storage) throws IOException {
        this.storage = Objects.requireNonNull(storage);
        if (Files.isRegularFile(storage)) {
            try (InputStream stream = Files.newInputStream(storage)) {
                seen.load(stream);
            }
        }
    }

    /** Always returns a fallback map for an unrecognized console. */
    public Map<String, Binding> bindingsFor(String console) {
        return DEFAULTS.getOrDefault(normalize(console), DEFAULTS.get("generic"));
    }

    public boolean shouldShow(String console, Device device) {
        Objects.requireNonNull(device);
        return !Boolean.parseBoolean(seen.getProperty(key(console, device), "false"));
    }

    /** Call only when user dismisses the first-run guide, not when game launches. */
    public void markShown(String console, Device device) throws IOException {
        Objects.requireNonNull(device);
        seen.setProperty(key(console, device), "true");
        save();
    }

    public void reset(String console, Device device) throws IOException {
        seen.remove(key(console, device));
        save();
    }

    private static String key(String console, Device device) {
        return "guide.seen." + normalize(console) + "." + device.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String normalize(String console) {
        if (console == null || console.isBlank()) return "generic";
        String v = console.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return switch (v) {
            case "gb", "gameboy", "gbc", "gameboycolor", "gba", "gameboyadvance" -> "gba";
            case "snes", "supernintendo", "superfamicom" -> "snes";
            case "nes", "famicom" -> "nes";
            case "ps1", "psx", "playstation", "ps2", "playstation2", "psp", "playstationportable" -> "playstation";
            case "genesis", "megadrive" -> "genesis";
            default -> DEFAULTS.containsKey(v) ? v : "generic";
        };
    }

    private synchronized void save() throws IOException {
        Path parent = storage.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path tmp = Files.createTempFile(parent, "okubi-control-guide-", ".tmp");
        try {
            try (OutputStream stream = Files.newOutputStream(tmp)) {
                seen.store(stream, "OKUBI first-run control guides");
            }
            try {
                Files.move(tmp, storage, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(tmp, storage, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static Map<String, Binding> layout(String[][] rows) {
        Map<String, Binding> out = new LinkedHashMap<>();
        for (String[] row : rows) out.put(row[0], new Binding(row[0], row[1], row[2]));
        return Map.copyOf(out);
    }

    private static Map<String, Map<String, Binding>> createDefaults() {
        Map<String, Map<String, Binding>> map = new LinkedHashMap<>();
        map.put("gba", layout(new String[][] {
            {"Move", "Arrow keys", "D-pad"}, {"A", "X", "South"}, {"B", "Z", "East"},
            {"L", "Q", "LB"}, {"R", "W", "RB"}, {"Start", "Enter", "Start"}, {"Select", "Space", "Back"}
        }));
        map.put("nes", layout(new String[][] {
            {"Move", "Arrow keys", "D-pad"}, {"A", "X", "South"}, {"B", "Z", "East"},
            {"Start", "Enter", "Start"}, {"Select", "Space", "Back"}
        }));
        map.put("snes", layout(new String[][] {
            {"Move", "Arrow keys", "D-pad"}, {"A", "X", "East"}, {"B", "Z", "South"},
            {"X", "S", "North"}, {"Y", "A", "West"}, {"L", "Q", "LB"}, {"R", "W", "RB"},
            {"Start", "Enter", "Start"}, {"Select", "Space", "Back"}
        }));
        map.put("playstation", layout(new String[][] {
            {"Move", "Configure in external emulator", "Left stick / D-pad"}, {"Cross", "Configure in external emulator", "South"}, {"Circle", "Configure in external emulator", "East"},
            {"Square", "Configure in external emulator", "West"}, {"Triangle", "Configure in external emulator", "North"}, {"L1", "Configure in external emulator", "LB"}, {"R1", "Configure in external emulator", "RB"},
            {"Start", "Enter", "Start"}, {"Select", "Space", "Back"}
        }));
        map.put("genesis", layout(new String[][] {
            {"Move", "W/A/S/D", "D-pad"}, {"A", "J", "West"}, {"B", "K", "South"},
            {"C", "L", "East"}, {"Start", "Enter", "Start"}
        }));
        map.put("generic", layout(new String[][] {
            {"Move", "W/A/S/D", "D-pad / left stick"}, {"Confirm", "J", "South"},
            {"Back", "K", "East"}, {"Menu", "Enter", "Start"}
        }));
        return Map.copyOf(map);
    }
}
