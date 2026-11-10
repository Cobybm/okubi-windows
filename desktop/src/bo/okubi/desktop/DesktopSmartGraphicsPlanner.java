package bo.okubi.desktop;

/**
 * Small, bounded PCSX2 graphics-only plan for a single game and PC.
 * No FPS claims, no CPU overclock, no renderer switch or BIOS/save changes.
 * The actual INI is opt-in and owned by OKUBI; never edits PCSX2's files.
 */
public final class DesktopSmartGraphicsPlanner {
    public record Plan(int scale, boolean fxaa, int anisotropy,
                       String label, String explanation) {
        public Plan {
            if (scale < 1 || scale > 2 || (anisotropy != 0
                    && anisotropy != 2 && anisotropy != 4))
                throw new IllegalArgumentException("Graphics plan outside safe limits.");
        }
        public String preview() {
            return "Resolución: " + scale + "×\n"
                    + "FXAA: " + (fxaa ? "Sí" : "No") + "\n"
                    + "Filtrado anisotrópico: " + (anisotropy == 0
                        ? "Normal / predeterminado" : anisotropy + "×")
                    + "\nMotivo: " + explanation;
        }
    }

    private DesktopSmartGraphicsPlanner() { }

    public static Plan plan(DesktopSmartPerformance.Recommendation rec,
                            DesktopSmartHardware.Snapshot pc,
                            DesktopSmartPlayHistory.Result observed) {
        if (rec == null || pc == null
                || rec.console() != DesktopSmartPerformance.Console.PS2)
            throw new IllegalArgumentException("SMART GRAPHICS is PS2-only.");
        var mode = rec.mode();
        boolean strong = rec.rating() == DesktopSmartPerformance.Rating.LIKELY
                && pc.graphicsKnown() && !pc.integratedGraphics()
                && pc.memoryGiB() >= 15 && pc.logicalCores() >= 8;
        boolean weakOrSlow = observed == DesktopSmartPlayHistory.Result.SLOW
                || observed == DesktopSmartPlayHistory.Result.FAILED
                || mode == DesktopSmartPerformance.Mode.ECO
                || rec.rating() == DesktopSmartPerformance.Rating.UNKNOWN
                || rec.rating() == DesktopSmartPerformance.Rating.NOT_RECOMMENDED;
        if (weakOrSlow && mode != DesktopSmartPerformance.Mode.QUALITY)
            return new Plan(1, false, 0, "Protección",
                    "Carga reducida: hardware limitado o juego reportado lento.");
        if (!strong) return new Plan(1, false, 0, "Conservador",
                "GPU integrada, capacidad desconocida o compatibilidad variable.");
        if (mode == DesktopSmartPerformance.Mode.QUALITY)
            return new Plan(2, true, 4, "Calidad moderada",
                    "GPU dedicada: mejora limitada a 2×, FXAA y anisotropía 4×.");
        if (mode == DesktopSmartPerformance.Mode.BALANCED)
            return new Plan(1, false, 2, "Equilibrado",
                    "Escala nativa, filtrado 2× sin efectos adicionales.");
        return new Plan(1, false, 0, "Automático conservador",
                "La calidad no aumenta sin seleccionar Calidad explícitamente.");
    }
}
