package bo.nexoplay.app.retro;

import java.util.Locale;

/**
 * Safe first-stage 8/16-bit expansion: recognition and catalog routing ONLY.
 * A recognised document is not proof an emulator core is embedded or that
 * a third-party application will accept ACTION_VIEW on the SAF URI.
 */
public final class RetroExpansionFormats {
    private RetroExpansionFormats() { }

    private static final class Format {
        final String system;
        final String[] extensions;
        final long minimum, maximum;
        Format(String system, String[] extensions, long minimum, long maximum) {
            this.system = system;
            this.extensions = extensions;
            this.minimum = minimum;
            this.maximum = maximum;
        }
    }

    private static final Format[] FORMATS = {
        new Format("SNES", new String[] {".sfc", ".smc"}, 32768L, 16L*1024*1024),
        new Format("NES", new String[] {".nes", ".unf"}, 16384L, 8L*1024*1024),
        new Format("MD", new String[] {".md", ".gen"}, 32768L, 16L*1024*1024),
        new Format("SMS", new String[] {".sms"}, 8192L, 2L*1024*1024),
        new Format("GG", new String[] {".gg"}, 8192L, 2L*1024*1024),
        new Format("PCE", new String[] {".pce"}, 8192L, 8L*1024*1024),
        new Format("A2600", new String[] {".a26"}, 2048L, 2L*1024*1024),
    };

    /** Recognise extensions with no MIME reliance (SAF often says octet-stream). */
    public static String systemFromName(String filename) {
        if (filename == null) return null;
        String value = filename.toLowerCase(Locale.ROOT);
        // Never recognise directory segments or ambiguous .bin/.iso files.
        if (value.indexOf('\0') >= 0) return null;
        for (Format format : FORMATS)
            for (String ext : format.extensions)
                if (value.endsWith(ext)) return format.system;
        return null;
    }

    public static boolean isExternalClassic(String system) {
        for (Format f : FORMATS) if (f.system.equals(system)) return true;
        return false;
    }

    /** A metadata sanity check, not a hardware/emulator compatibility claim. */
    public static boolean validKnownSize(String system, long size) {
        for (Format f : FORMATS) {
            if (f.system.equals(system))
                return size == -1 || (size >= f.minimum && size <= f.maximum);
        }
        return false;
    }

    public static String externalStatus(String system) {
        if ("SNES".equals(system))
            return "SNES · abre con emulador externo instalado, p. ej. Snes9x EX+";
        if ("NES".equals(system))
            return "NES · requiere un emulador externo, p. ej. NES.emu";
        if (isExternalClassic(system))
            return "Consola clásica · requiere un emulador externo compatible";
        return "No corresponde a esta expansión";
    }

    /** Source of install help; never downloaded or installed automatically. */
    public static String officialEmulatorPage(String system) {
        if ("SNES".equals(system))
            return "https://play.google.com/store/apps/details?id=com.explusalpha.Snes9xPlus";
        if ("NES".equals(system))
            return "https://play.google.com/store/apps/details?id=com.explusalpha.NesEmu";
        if (isExternalClassic(system))
            return "https://www.retroarch.com/platforms.php";
        return null;
    }
}
