package bo.okubi.desktop;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Curated PC port catalogue. Repository owners/URLs were checked individually
 * on GitHub in November 2026. This is a directory, NOT a file host/downloader.
 * Some engines require legitimately obtained game assets and no files ship here.
 */
public final class DesktopPortsCatalog {
    public enum Assets {
        FREE("Gratis o con datos libres"),
        ORIGINAL("Requiere archivos originales"),
        MIXED("Admite datos libres u originales");
        public final String label;
        Assets(String label) { this.label = label; }
    }

    public enum Load {
        LIGHT("Ligero, orientativo"),
        MEDIUM("Medio, orientativo"),
        VARIABLE("Variable: comprobar requisitos");
        public final String label;
        Load(String label) { this.label = label; }
    }

    public record Port(String id, String title, String genre, Assets assets,
                       Load load, String description, String owner,
                       String repository, boolean releasesAvailable,
                       String instruction) {
        public Port {
            if (!id.matches("[a-z][a-z0-9-]{2,35}"))
                throw new IllegalArgumentException("ID inválido");
            if (!owner.matches("[A-Za-z0-9-]+") ||
                    !repository.matches("[A-Za-z0-9_.-]+"))
                throw new IllegalArgumentException("GitHub dueño/repositorio inválido");
            if (title.isBlank() || description.isBlank() || instruction.isBlank())
                throw new IllegalArgumentException("Ficha incompleta");
        }

        public URI github() {
            return URI.create("https://github.com/" + owner + "/" + repository);
        }

        /** A GitHub releases page is a listing, never an untrusted binary URL. */
        public URI releases() {
            return URI.create(github() + (releasesAvailable ? "/releases" : ""));
        }

        public String requirement() { return assets.label; }

        public String searchText() {
            return (id + " " + title + " " + genre + " " + description
                    + " " + requirement()).toLowerCase(Locale.ROOT);
        }
    }

    private static Port p(String id, String title, String genre, Assets assets,
                          Load load, String description, String owner,
                          String repo, boolean releases, String instruction) {
        return new Port(id, title, genre, assets, load, description, owner,
                repo, releases, instruction);
    }

    private static final List<Port> ENTRIES = List.of(
        p("openttd", "OpenTTD", "Estrategia", Assets.FREE, Load.LIGHT,
          "Gestión ferroviaria y transporte; permite paquetes gráficos y sonido libres.",
          "OpenTTD", "OpenTTD", true, "Instala la versión Windows y los recursos libres recomendados."),
        p("devilutionx", "DevilutionX", "RPG", Assets.ORIGINAL, Load.LIGHT,
          "Motor modernizado de Diablo y Hellfire. Se requieren archivos legítimos del juego.",
          "diasurgical", "DevilutionX", true, "Necesita DIABDAT.MPQ original o contenido shareware permitido."),
        p("openrct2", "OpenRCT2", "Simulación", Assets.ORIGINAL, Load.LIGHT,
          "Parques de atracciones; requiere archivos originales de RollerCoaster Tycoon 2.",
          "OpenRCT2", "OpenRCT2", true, "Configura tu carpeta legal de RollerCoaster Tycoon 2."),
        p("openloco", "OpenLoco", "Simulación", Assets.ORIGINAL, Load.LIGHT,
          "Reimplementación de Locomotion. No incorpora su material comercial.",
          "OpenLoco", "OpenLoco", true, "Selecciona los recursos de tu copia de Locomotion."),
        p("openmw", "OpenMW", "RPG", Assets.ORIGINAL, Load.VARIABLE,
          "Motor abierto para Morrowind. El repositorio GitHub es un espejo del proyecto.",
          "OpenMW", "openmw", false, "Necesitas archivos de Morrowind; consulta instrucciones del proyecto."),
        p("scummvm", "ScummVM", "Aventuras", Assets.MIXED, Load.LIGHT,
          "Motor para aventuras clásicas; admite títulos liberados y juegos propios.",
          "scummvm", "scummvm", true, "Añade los datos compatibles de cada aventura; algunos son gratuitos."),
        p("gzdoom", "GZDoom", "FPS", Assets.MIXED, Load.MEDIUM,
          "Motor para juegos Doom; admite FreeDoom u otros WAD obtenidos legalmente.",
          "ZDoom", "gzdoom", true, "Necesita un IWAD, por ejemplo FreeDoom, o tus archivos originales."),
        p("dhewm3", "dhewm3", "FPS", Assets.ORIGINAL, Load.VARIABLE,
          "Port moderno del motor de Doom 3 para sistemas actuales.",
          "dhewm", "dhewm3", true, "Necesitas los datos originales de Doom 3."),
        p("corsixth", "CorsixTH", "Simulación", Assets.ORIGINAL, Load.LIGHT,
          "Motor de Theme Hospital con interfaz moderna.",
          "CorsixTH", "CorsixTH", true, "Necesita los archivos originales de Theme Hospital."),
        p("augustus", "Augustus", "Estrategia", Assets.ORIGINAL, Load.LIGHT,
          "Motor y mejoras de Caesar III, con muchas funciones nuevas.",
          "Keriew", "augustus", true, "Necesita los datos originales de Caesar III."),
        p("julius", "Julius", "Estrategia", Assets.ORIGINAL, Load.LIGHT,
          "Reimplementación fiel de Caesar III para plataformas actuales.",
          "bvschaik", "julius", true, "Necesita los datos originales de Caesar III."),
        p("gemrb", "GemRB", "RPG", Assets.ORIGINAL, Load.MEDIUM,
          "Motor libre de Infinity Engine para RPG clásicos compatibles.",
          "gemrb", "gemrb", true, "Requiere archivos del juego original compatible."),
        p("openra", "OpenRA", "Estrategia", Assets.MIXED, Load.MEDIUM,
          "Motor de estrategia en tiempo real para títulos clásicos compatibles.",
          "OpenRA", "OpenRA", true, "Consulta el instalador oficial sobre datos descargables autorizados."),
        p("opentyrian", "OpenTyrian", "Arcade", Assets.MIXED, Load.LIGHT,
          "Port del shooter clásico Tyrian, con recursos distribuidos por separado.",
          "opentyrian", "opentyrian", true, "Descarga el paquete de datos desde las fuentes autorizadas."),
        p("vcmi", "VCMI", "Estrategia", Assets.ORIGINAL, Load.MEDIUM,
          "Motor abierto para Heroes of Might and Magic III.",
          "vcmi", "vcmi", true, "Necesita los datos originales de Heroes III."),
        p("endlesssky", "Endless Sky", "Espacial", Assets.FREE, Load.LIGHT,
          "Aventura espacial, exploración, comercio y combates; juego libre completo.",
          "endless-sky", "endless-sky", true, "Descarga una versión oficial de Windows."),
        p("warzone2100", "Warzone 2100", "Estrategia", Assets.FREE, Load.MEDIUM,
          "Juego de estrategia 3D libre y completo.",
          "Warzone2100", "warzone2100", true, "Instala el paquete Windows distribuido por el proyecto."),
        p("freeciv", "Freeciv", "Estrategia", Assets.FREE, Load.LIGHT,
          "Juego de civilizaciones y estrategia por turnos, libre.",
          "freeciv", "freeciv", true, "Selecciona el instalador Windows en las fuentes oficiales."),
        p("openjk", "OpenJK", "Acción", Assets.ORIGINAL, Load.MEDIUM,
          "Motor modernizado para Jedi Academy y Jedi Outcast.",
          "JACoders", "OpenJK", true, "Necesita archivos legales de Jedi Academy u Outcast."),
        p("yamagiquake2", "Yamagi Quake II", "FPS", Assets.ORIGINAL, Load.LIGHT,
          "Port actualizado de Quake II para sistemas modernos.",
          "yquake2", "yquake2", true, "Necesita los datos originales de Quake II."),
        p("arxlibertatis", "Arx Libertatis", "RPG", Assets.ORIGINAL, Load.MEDIUM,
          "Port y restauración técnica del clásico Arx Fatalis.",
          "arx", "ArxLibertatis", true, "Necesita datos originales de Arx Fatalis."),
        p("xash3d", "Xash3D FWGS", "FPS", Assets.ORIGINAL, Load.VARIABLE,
          "Motor alternativo para juegos GoldSrc compatibles.",
          "FWGS", "xash3d-fwgs", true, "Necesita datos propios de Half-Life u otro título compatible."),
        p("luanti", "Luanti", "Sandbox", Assets.FREE, Load.LIGHT,
          "Motor de mundos voxel libre, anteriormente Minetest.",
          "luanti-org", "luanti", true, "Descarga el motor y un juego libre compatible."),
        p("supertuxkart", "SuperTuxKart", "Carreras", Assets.FREE, Load.MEDIUM,
          "Juego de carreras 3D libre con personajes y pistas propias.",
          "supertuxkart", "stk-code", false, "Descarga la versión oficial Windows desde su web enlazada en GitHub."),
        p("daggerfallunity", "Daggerfall Unity", "RPG", Assets.MIXED, Load.VARIABLE,
          "Recreación del RPG Daggerfall; el juego original puede obtenerse gratis.",
          "Interkarma", "daggerfall-unity", true, "Necesitas los archivos completos de Daggerfall por una vía autorizada.")
    );

    private DesktopPortsCatalog() { }

    public static List<Port> all() { return ENTRIES; }

    public static List<Port> filter(String query, String genre, Assets assets) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        String requiredGenre = genre == null ? "Todos" : genre;
        ArrayList<Port> out = new ArrayList<>();
        for (Port p : ENTRIES) {
            if (!needle.isBlank() && !p.searchText().contains(needle)) continue;
            if (!"Todos".equals(requiredGenre) && !p.genre().equals(requiredGenre)) continue;
            if (assets != null && p.assets() != assets) continue;
            out.add(p);
        }
        out.sort(Comparator.comparing(Port::title, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(out);
    }

    public static List<String> genres() {
        return ENTRIES.stream().map(Port::genre).distinct().sorted().toList();
    }

    public static Port byId(String id) {
        return ENTRIES.stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
    }
}
