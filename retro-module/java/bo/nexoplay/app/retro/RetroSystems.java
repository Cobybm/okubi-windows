package bo.nexoplay.app.retro;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Console definitions for OKUBI. Compatibility tiers are estimates for a
 * Galaxy A05s-class device, NOT measured benchmarks or embedded emulators.
 */
public final class RetroSystems {
    public enum Tier { RECOMMENDED, EXPERIMENTAL }
    public enum Controller { ONE_BUTTON, TWO_BUTTON, TWO_WITH_SHOULDERS, FOUR_BUTTON, SIX_BUTTON, DUAL_SCREEN, ANALOG_REQUIRED }
    public enum Performance { ECO, BALANCED, NATIVE }

    public static final class SystemSpec {
        public final String id;
        public final String title;
        public final int width;
        public final int height;
        public final Tier tier;
        public final Controller controller;
        public final Performance performance;
        public final String extensions;
        public final String coreCandidate;
        public final String caveat;

        private SystemSpec(String id, String title, int width, int height,
                Tier tier, Controller controller, Performance performance,
                String extensions, String coreCandidate, String caveat) {
            this.id = id;
            this.title = title;
            this.width = width;
            this.height = height;
            this.tier = tier;
            this.controller = controller;
            this.performance = performance;
            this.extensions = extensions;
            this.coreCandidate = coreCandidate;
            this.caveat = caveat;
        }
        public boolean matchesFile(String filename) {
            if (filename == null) return false;
            String lower = filename.toLowerCase(java.util.Locale.ROOT);
            for (String ext : extensions.split(",")) {
                if (lower.endsWith("." + ext.trim())) return true;
            }
            return false;
        }
        public String status() {
            return tier == Tier.RECOMMENDED ? "Recomendada" : "Experimental";
        }
    }

    public static final List<SystemSpec> ALL = Collections.unmodifiableList(Arrays.asList(
        new SystemSpec("GB", "Game Boy", 160, 144, Tier.RECOMMENDED,
            Controller.TWO_BUTTON, Performance.ECO, "gb", "SameBoy", "Sin núcleo incorporado"),
        new SystemSpec("GBC", "Game Boy Color", 160, 144, Tier.RECOMMENDED,
            Controller.TWO_BUTTON, Performance.ECO, "gbc,gb", "SameBoy", "Sin núcleo incorporado"),
        new SystemSpec("GBA", "Game Boy Advance", 240, 160, Tier.RECOMMENDED,
            Controller.TWO_WITH_SHOULDERS, Performance.ECO, "gba", "mGBA", "Motor integrado cuando está disponible"),
        new SystemSpec("NES", "Nintendo NES", 256, 240, Tier.RECOMMENDED,
            Controller.TWO_BUTTON, Performance.ECO, "nes,unf", "Nestopia UE", "Sin núcleo incorporado"),
        new SystemSpec("SNES", "Super Nintendo", 256, 224, Tier.RECOMMENDED,
            Controller.FOUR_BUTTON, Performance.ECO, "sfc,smc", "Beetle Supafaust (GPLv2+)", "Núcleo SNES incluido si supera la compilación"),
        new SystemSpec("SMS", "SEGA Master System", 256, 192, Tier.RECOMMENDED,
            Controller.TWO_BUTTON, Performance.ECO, "sms", "Genesis Plus GX", "Sin núcleo incorporado"),
        new SystemSpec("GG", "SEGA Game Gear", 160, 144, Tier.RECOMMENDED,
            Controller.TWO_BUTTON, Performance.ECO, "gg", "Genesis Plus GX", "Sin núcleo incorporado"),
        new SystemSpec("MD", "SEGA Mega Drive / Genesis", 320, 224, Tier.RECOMMENDED,
            Controller.SIX_BUTTON, Performance.ECO, "md,gen", "Genesis Plus GX", "Controles de 6 botones pendientes"),
        new SystemSpec("PCE", "PC Engine / TurboGrafx", 256, 224, Tier.RECOMMENDED,
            Controller.TWO_BUTTON, Performance.ECO, "pce", "Beetle PCE Fast", "Sin núcleo incorporado"),
        new SystemSpec("A2600", "Atari 2600", 160, 192, Tier.RECOMMENDED,
            Controller.ONE_BUTTON, Performance.ECO, "a26", "Stella", "Sin núcleo incorporado"),
        new SystemSpec("PS1", "PlayStation 1", 320, 240, Tier.RECOMMENDED,
            Controller.FOUR_BUTTON, Performance.BALANCED, "chd,pbp,cue", "PCSX ReARMed",
            "Algunos juegos requieren BIOS y varios archivos de disco; aún sin motor"),
        new SystemSpec("NDS", "Nintendo DS", 256, 384, Tier.RECOMMENDED,
            Controller.DUAL_SCREEN, Performance.BALANCED, "nds", "melonDS",
            "La pantalla táctil inferior y el diseño dual aún requieren implementación"),
        new SystemSpec("N64", "Nintendo 64", 320, 240, Tier.EXPERIMENTAL,
            Controller.ANALOG_REQUIRED, Performance.NATIVE, "z64,n64,v64", "Mupen64Plus-Next",
            "Necesita stick analógico, gatillo Z y pruebas por juego"),
        new SystemSpec("PSP", "PlayStation Portable", 480, 272, Tier.EXPERIMENTAL,
            Controller.ANALOG_REQUIRED, Performance.NATIVE, "iso,cso", "PPSSPP",
            "Recomendado comenzar a resolución 1x; juegos pesados podrían ir lentos"),
        new SystemSpec("DC", "SEGA Dreamcast", 640, 480, Tier.EXPERIMENTAL,
            Controller.ANALOG_REQUIRED, Performance.NATIVE, "chd,gdi,cdi", "Flycast",
            "GDIs multifichero y BIOS pueden requerir preparación adicional"),
        new SystemSpec("SAT", "SEGA Saturn", 320, 240, Tier.EXPERIMENTAL,
            Controller.SIX_BUTTON, Performance.NATIVE, "chd,cue", "Yaba Sanshiro / Beetle Saturn",
            "Compatibilidad y velocidad variables; solo biblioteca experimental")
    ));

    public static SystemSpec byId(String id) {
        for (SystemSpec item : ALL) if (item.id.equals(id)) return item;
        return null;
    }
    public static SystemSpec byPosition(int index) {
        if (index < 0 || index >= ALL.size()) return ALL.get(2);
        return ALL.get(index);
    }
    private RetroSystems() {}
}
