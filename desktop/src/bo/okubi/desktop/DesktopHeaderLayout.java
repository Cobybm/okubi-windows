package bo.okubi.desktop;

import java.awt.FontMetrics;

/**
 * Calculations for a responsive, touch/gamepad-friendly Windows toolbar.
 * All six actions stay present and clickable at every supported window size.
 * Font metrics are measured from the actual Java/Swing UI scale.
 */
public final class DesktopHeaderLayout {
    public enum Mode { FULL, COMPACT, STACKED }

    private DesktopHeaderLayout() { }

    public static final int COMMAND_COUNT = 6;
    public static final int BUTTON_GAP = 8;
    public static final int OUTER_PADDING = 56;
    public static final int TITLE_GAP = 20;

    public static final String[] FULL_LABELS = {
        "⛶ MODO CONSOLA", "⚡ Juego sorpresa", "☁ CLOUD SAVE",
        "◷ ESTADÍSTICAS", "Motores", "+ Importar ROM"
    };

    public static final String[] COMPACT_LABELS = {
        "⛶ Consola", "⚡ Sorpresa", "☁ Nube",
        "◷ Horas", "Motores", "+ Importar"
    };

    /**
     * Slightly generous minimum width, matching CinemaButton's icon, border,
     * and text: painting does not have to truncate a single label.
     */
    public static int buttonWidth(String text, FontMetrics font) {
        boolean icon = CinemaIcons.forLabel(text) != CinemaIcons.Kind.NONE;
        String label = CinemaIcons.withoutEmoji(text);
        int iconSize = icon ? (17 + (label.isBlank() ? 0 : 8)) : 0;
        return font.stringWidth(label) + iconSize + 30;
    }

    public static int widest(String[] labels, FontMetrics font) {
        int width = 0;
        for (String label : labels)
            width = Math.max(width, buttonWidth(label, font));
        return width;
    }

    public static int requiredWidth(int brandWidth, int singleButtonWidth) {
        return OUTER_PADDING + Math.max(0, brandWidth) + TITLE_GAP
                + singleButtonWidth * COMMAND_COUNT
                + BUTTON_GAP * (COMMAND_COUNT - 1);
    }

    public static Mode choose(int availableWidth, int brandWidth,
                              int widestFull, int widestCompact) {
        if (availableWidth >= requiredWidth(brandWidth, widestFull))
            return Mode.FULL;
        if (availableWidth >= requiredWidth(brandWidth, widestCompact))
            return Mode.COMPACT;
        return Mode.STACKED;
    }

    public static String label(int index, Mode mode, boolean consoleMode) {
        if (index < 0 || index >= COMMAND_COUNT)
            throw new IllegalArgumentException("Toolbar action index " + index);
        if (index == 0 && consoleMode)
            return mode == Mode.COMPACT ? "◱ Ventana" : "◱ SALIR DE CONSOLA";
        return mode == Mode.COMPACT
                ? COMPACT_LABELS[index] : FULL_LABELS[index];
    }

    public static int rows(Mode mode) {
        return mode == Mode.STACKED ? 2 : 1;
    }
    public static int columns(Mode mode) {
        return mode == Mode.STACKED ? 3 : 6;
    }
}
