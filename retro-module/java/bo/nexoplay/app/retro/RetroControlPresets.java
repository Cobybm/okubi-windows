package bo.nexoplay.app.retro;

/**
 * Read-only ergonomic preset values. Applying one never changes ROM files,
 * button coordinates, left-handed mode, shoulder visibility, or A/B mapping.
 */
public final class RetroControlPresets {
    private RetroControlPresets() {}

    public enum Mode {
        EQUILIBRADO("Equilibrado", 1.08f, .82f,
                "Tamaño y transparencia clásicos de OKUBI"),
        COMODO("Cómodo", 1.26f, .94f,
                "Botones más grandes y legibles para jugar con pulgares"),
        COMPACTO("Compacto", .86f, .68f,
                "Botones discretos para ver más del escenario");

        public final String label;
        public final float size;
        public final float opacity;
        public final String description;

        Mode(String label, float size, float opacity, String description) {
            this.label = label;
            this.size = size;
            this.opacity = opacity;
            this.description = description;
        }
    }

    /** Keep the same safe limits already used by persisted profiles. */
    public static float boundedSize(float size) {
        if (!Float.isFinite(size)) return Mode.EQUILIBRADO.size;
        return Math.max(.72f, Math.min(1.50f, size));
    }

    public static float boundedOpacity(float opacity) {
        if (!Float.isFinite(opacity)) return Mode.EQUILIBRADO.opacity;
        return Math.max(.25f, Math.min(1.0f, opacity));
    }

    public static Mode[] options() {
        return Mode.values();
    }
}
