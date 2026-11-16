package bo.okubi.desktop;

/**
 * Lightweight, truthful visual state for the unified Windows CINEMA shelf.
 *
 * A repository social card is NEVER treated as a screenshot. Unknown projects
 * can still use the user's explicitly chosen cover, without inheriting an
 * image from any similarly named or different port.
 */
public final class DesktopCinemaArtworkPolicy {
    public enum Source {
        PERSONAL, REVIEWED_UPSTREAM, UNVERIFIED
    }

    private DesktopCinemaArtworkPolicy() {}

    public static Source source(DesktopPortArtworkService service,
                                DesktopPortArtworkService.Ref ref) {
        if (ref == null) return Source.UNVERIFIED;
        if (service != null && service.hasManualChoice(ref))
            return Source.PERSONAL;
        return DesktopOfficialPortArtwork.source(ref) != null
                ? Source.REVIEWED_UPSTREAM : Source.UNVERIFIED;
    }

    public static String label(Source source) {
        return switch (source) {
            case PERSONAL -> "IMAGEN PERSONAL";
            case REVIEWED_UPSTREAM -> "FUENTE OFICIAL REVISADA";
            case UNVERIFIED -> "SIN CAPTURA VERIFICADA";
        };
    }

    public static int galleryCount(DesktopPortArtworkService.Ref ref) {
        return ref == null ? 0 : DesktopOfficialPortArtwork.gallery(ref).size();
    }

    /** Only show the extra gallery-count hint when more than one official image exists. */
    public static String galleryHint(DesktopPortArtworkService.Ref ref) {
        int count = galleryCount(ref);
        return count > 1 ? count + " CAPTURAS" : "";
    }
}
