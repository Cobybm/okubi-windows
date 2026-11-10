package bo.okubi.desktop;

import java.util.Locale;

/**
 * Hardware readiness ≠ verified title compatibility. Never promises FPS,
 * firmware availability or correct game emulation.
 */
public final class DesktopSmartPerformance {
    public enum Console {
        PS1("PlayStation 1", "DuckStation", "https://github.com/stenzek/duckstation/releases/tag/latest"),
        PS2("PlayStation 2", "PCSX2", "https://pcsx2.net/downloads/"),
        PS3("PlayStation 3 (experimental)", "RPCS3", "https://rpcs3.net/download");
        public final String label, emulatorName, officialUrl;
        Console(String label, String emulatorName, String officialUrl) {
            this.label = label;
            this.emulatorName = emulatorName;
            this.officialUrl = officialUrl;
        }
        @Override public String toString() { return label; }
    }
    public enum Rating {
        LIKELY("Probable con ajustes conservadores"),
        VARIABLE("Rendimiento variable; requiere probar"),
        NOT_RECOMMENDED("No recomendado para esta PC"),
        UNKNOWN("Hardware insuficientemente identificado");
        public final String label;
        Rating(String label) { this.label = label; }
    }
    public enum Mode {
        AUTO("Automático"), ECO("Protección / bajo consumo"),
        BALANCED("Equilibrado"), QUALITY("Calidad");
        public final String label;
        Mode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    public record Recommendation(Console console, Rating rating, Mode mode,
                                 int suggestedScale, String reason) { }

    private DesktopSmartPerformance() { }

    public static Recommendation recommend(Console console,
                                           DesktopSmartHardware.Snapshot hardware,
                                           Mode selected) {
        if (console == null || hardware == null || selected == null)
            throw new IllegalArgumentException("Consola, PC y modo obligatorios.");
        double ram = hardware.memoryGiB();
        int threads = hardware.logicalCores();
        boolean unknown = ram == 0 || !hardware.graphicsKnown();
        boolean basic = hardware.integratedGraphics() || threads <= 4
                || ram < 15 || unknown;
        Rating rating;
        String reason;
        switch (console) {
            case PS1 -> {
                rating = unknown ? Rating.UNKNOWN :
                        (threads >= 2 && ram >= 4 ? Rating.LIKELY
                                : Rating.NOT_RECOMMENDED);
                reason = "PS1 suele ser menos exigente. Se necesita DuckStation "
                        + "y BIOS legítima. Compatibilidad del título sin verificar.";
            }
            case PS2 -> {
                rating = ram > 0 && (ram < 8 || threads < 2)
                        ? Rating.NOT_RECOMMENDED
                        : unknown ? Rating.UNKNOWN
                        : basic ? Rating.VARIABLE : Rating.LIKELY;
                reason = "PCSX2 exige SSE4.1 y la velocidad depende del juego. "
                        + "En GPU integrada usa resolución nativa inicialmente; "
                        + "se requiere BIOS propia. No se han medido FPS.";
            }
            case PS3 -> {
                rating = unknown ? Rating.UNKNOWN :
                        ram >= 16 && threads >= 12 && !hardware.integratedGraphics()
                        ? Rating.VARIABLE : Rating.NOT_RECOMMENDED;
                reason = "RPCS3 es experimental y muy exigente. Es necesario "
                        + "revisar compatibilidad del ID del juego, Vulkan/driver "
                        + "y firmware legítimo. No se promete ejecución.";
            }
            default -> throw new IllegalStateException();
        }
        Mode mode = selected == Mode.AUTO
                ? (basic || rating != Rating.LIKELY ? Mode.ECO : Mode.BALANCED)
                : selected;
        // Only a recommendation — external emulator settings remain under
        // the user's control, preventing unsupported config corruption.
        int scale = mode == Mode.QUALITY && rating == Rating.LIKELY ? 2 : 1;
        if (hardware.isBusy()) reason += " La CPU está muy ocupada; cierra otros programas.";
        reason += " Perfil recomendado: " + mode.label + " (" + scale
                + "× sugerido). OKUBI no modifica el registro, BIOS, "
                + "memorias de guardado ni ajustes internos del emulador.";
        return new Recommendation(console, rating, mode, scale, reason);
    }

    /** Explicit file extensions only; .iso and .chd are ambiguous. */
    public static Console suggestConsole(String filename) {
        if (filename == null) return null;
        String name = filename.toLowerCase(Locale.ROOT);
        if (name.equals("eboot.bin")) return Console.PS3;
        if (name.endsWith(".cue") || name.endsWith(".pbp") || name.endsWith(".m3u"))
            return Console.PS1;
        if (name.endsWith(".iso") || name.endsWith(".cso")) return Console.PS2;
        if (name.endsWith(".chd")) return Console.PS1;
        return null;
    }

    public static boolean acceptedFile(Console console, String filename) {
        if (console == null || filename == null) return false;
        String name = filename.toLowerCase(Locale.ROOT);
        return switch (console) {
            case PS1 -> name.endsWith(".cue") || name.endsWith(".chd")
                    || name.endsWith(".pbp") || name.endsWith(".m3u")
                    || name.endsWith(".iso");
            case PS2 -> name.endsWith(".iso") || name.endsWith(".chd")
                    || name.endsWith(".cso");
            case PS3 -> name.equals("eboot.bin");
        };
    }
}
