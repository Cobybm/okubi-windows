package bo.okubi.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only format recognition for personal PlayStation backups.
 * A recognised extension does NOT mean the game boots, has a legitimate
 * license, or can run without firmware, an external emulator or a faster PC.
 * No extraction, executable launching or save migration takes place here.
 */
public final class DesktopPlaystationFormatAdvisor {
    public record Advice(String format, List<String> systems, String emulators,
                         boolean directFile, boolean ambiguous, String explanation,
                         List<String> warnings) {
        public String summary() {
            StringBuilder builder = new StringBuilder();
            builder.append("Formato: ").append(format);
            builder.append("\nPosibles consolas: ").append(
                    systems.isEmpty() ? "No identificada" : String.join(" / ", systems));
            builder.append("\nEmulador recomendado: ").append(emulators);
            builder.append("\n\n").append(explanation);
            if (ambiguous) builder.append(
                    "\n\nSelecciona la consola manualmente: el sufijo no basta para identificarla.");
            if (!directFile) builder.append(
                    "\n\nNo puede ejecutarse directamente desde la biblioteca actual de OKUBI.");
            for (String warning : warnings) builder.append("\n⚠ ").append(warning);
            return builder.toString();
        }
    }
    private DesktopPlaystationFormatAdvisor() { }

    public static Advice inspect(Path path) throws IOException {
        if (path == null || path.getFileName() == null)
            throw new IOException("Selecciona un archivo.");
        Path safe = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(safe, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(safe))
            throw new IOException("No se encontró un archivo normal y legible.");
        String name = safe.getFileName().toString().toLowerCase(Locale.ROOT);
        List<String> warnings = new ArrayList<>();
        boolean direct = true;
        boolean ambiguous = false;
        String label = "Desconocido";
        List<String> systems = List.of();
        String engine = "Sin motor identificado";
        String note = "Este archivo no pertenece a los formatos PlayStation reconocidos.";

        if (name.equals("eboot.bin")) {
            label="EBOOT.BIN";
            systems=List.of("PS3");
            engine="RPCS3 (Windows)";
            note="Normalmente se encuentra en PS3_GAME/USRDIR. "
                 +"Solo lo usamos como referencia de juego externo; comprueba los datos del disco y el firmware.";
        } else if (name.endsWith(".cue")) {
            label="CUE (descriptor de pistas)";
            systems=List.of("PS1", "PS2 CD");
            engine="DuckStation / PCSX2";
            ambiguous=true;
            note="No importes únicamente el CUE: sus archivos BIN o WAV deben permanecer junto a él.";
            inspectCue(safe,warnings);
        } else if (name.endsWith(".chd")) {
            label="CHD comprimido";
            systems=List.of("PS1","PS2","PSP");
            engine="DuckStation / PCSX2 / PPSSPP";
            ambiguous=true;
            note="Admitido por varios motores sin extraer, pero no todos los CHD tienen el mismo tipo de disco.";
        } else if (name.endsWith(".iso")) {
            label="ISO";
            systems=List.of("PS1","PS2","PSP","PS3 (según imagen y volcado)");
            engine="DuckStation / PCSX2 / PPSSPP / RPCS3";
            ambiguous=true;
            note="Un ISO puede proceder de varias consolas; la extensión no demuestra su estructura "
                    +"ni si requiere descifrado o importación previa.";
        } else if (name.endsWith(".bin") || name.endsWith(".img")) {
            label="Imagen BIN/IMG";
            systems=List.of("PS1","PS2 CD");
            engine="DuckStation / PCSX2";
            ambiguous=true;
            note="En PS1, prefiere abrir el CUE del mismo nombre si el juego tiene varias pistas.";
            if(name.endsWith(".bin") && Files.exists(safe.resolveSibling(
                    safe.getFileName().toString().replaceFirst("(?i)\\.bin$", ".cue"))))
                warnings.add("Existe un CUE vecino: importa el CUE en lugar del BIN.");
        } else if (name.endsWith(".ccd") || name.endsWith(".mds")
                || name.endsWith(".ecm")) {
            label="Imagen de disco PS1";
            systems=List.of("PS1");
            engine="DuckStation";
            note="DuckStation reconoce este descriptor/formato. Conserva las pistas asociadas.";
        } else if (name.endsWith(".m3u")) {
            label="M3U (lista de discos)";
            systems=List.of("PS1");
            engine="DuckStation";
            note="Archivo de texto con rutas de varios discos; deben seguir accesibles.";
            inspectPlaylist(safe,warnings);
        } else if (name.endsWith(".cso")) {
            label="CSO comprimido";
            systems=List.of("PS2", "PSP");
            engine="PCSX2 / PPSSPP";
            ambiguous=true;
            note="No descomprimas automáticamente; el motor correcto depende de la consola.";
        } else if (name.endsWith(".zso") || name.endsWith(".gz")
                || name.endsWith(".mdf")) {
            label="Imagen PS2";
            systems=List.of("PS2");
            engine="PCSX2";
            note="Comprueba la calidad del volcado y las pistas asociadas antes de iniciar.";
        } else if (name.endsWith(".elf")) {
            label="ELF ejecutable de juego";
            systems=List.of("PS2 (homebrew)");
            engine="PCSX2";
            note="Solo abre ELF homebrew de origen confiable; es un formato ejecutable.";
        } else if (name.endsWith(".pbp")) {
            label="PBP";
            systems=List.of("PS1","PSP");
            engine="DuckStation / PPSSPP";
            ambiguous=true;
            note="El PBP puede contener un EBOOT de PSP o un juego PS1 en formato PBP; "
                 +"elige la consola que corresponda.";
        } else if (name.endsWith(".pkg")) {
            label="PKG (paquete, no imagen de disco)";
            systems=List.of("PS3","PS Vita");
            engine="RPCS3 / Vita3K";
            direct=false;
            ambiguous=true;
            note="Debes instalarlo desde el emulador adecuado y disponer de tu licencia legítima "
                 +"si el contenido lo exige. Nunca se ejecuta como imagen de disco.";
        } else if (name.endsWith(".vpk") || name.endsWith(".vci")) {
            label="Paquete PS Vita";
            systems=List.of("PS Vita");
            engine="Vita3K";
            direct=false;
            note="Vita3K admite paquetes de juegos/dumps compatibles; deben instalarse "
                 +"desde su interfaz.";
        } else if (name.endsWith(".zip") || name.endsWith(".7z")
                || name.endsWith(".rar")) {
            label="Archivo comprimido";
            systems=List.of("Indeterminado");
            engine="Depende de su contenido";
            direct=false;
            ambiguous=true;
            note="Importar no significa ejecutar: revisa el contenido en el explorador "
                 +"o en el instalador oficial. OKUBI no extrae archivos automáticamente.";
        } else if (name.endsWith(".sbi")) {
            label="SBI (datos de subcanal)";
            systems=List.of("PS1");
            engine="DuckStation";
            direct=false;
            note="Es un archivo auxiliar para algunos juegos PAL, no un juego. "
                 +"Debe acompañar a la imagen de disco con el mismo nombre.";
        }
        if (direct && !name.equals("eboot.bin") && !systems.isEmpty()
                && !List.of("PS1", "PS2").containsAll(systems)) {
            warnings.add("La versión Windows actual solo abre directamente PS1, PS2 y EBOOT.BIN de PS3 "
                    +"con emuladores externos configurados; PSP/Vita requieren integración posterior.");
        }
        return new Advice(label,systems,engine,direct,ambiguous,note,List.copyOf(warnings));
    }

    private static void inspectCue(Path path,List<String> warnings) throws IOException {
        if (Files.size(path) > 256 * 1024) {
            warnings.add("CUE excesivamente grande; verifica las pistas manualmente.");
            return;
        }
        Pattern cue = Pattern.compile("(?im)^\\s*FILE\\s+(?:\"([^\"]+)\"|([^\\s]+))");
        String data = Files.readString(path, StandardCharsets.ISO_8859_1);
        Matcher matcher = cue.matcher(data);
        int count=0;
        while(matcher.find() && count++<64) {
            String filename=matcher.group(1)!=null?matcher.group(1):matcher.group(2);
            if(filename.contains("\\")||filename.contains("/")||filename.contains(":")
                    || filename.indexOf('\\0')!=-1) {
                warnings.add("Pista externa/ruta no permitida en CUE: "+filename);
                continue;
            }
            Path track=path.resolveSibling(filename);
            if(!Files.isRegularFile(track,LinkOption.NOFOLLOW_LINKS))
                warnings.add("Falta pista de disco junto al CUE: "+filename);
        }
        if(count==0) warnings.add("El CUE no enumera pistas FILE.");
    }

    private static void inspectPlaylist(Path path,List<String> warnings) throws IOException {
        if(Files.size(path)>128*1024) {
            warnings.add("Lista M3U demasiado grande.");
            return;
        }
        String text=Files.readString(path,StandardCharsets.UTF_8);
        int count=0;
        for(String raw:text.split("\\R")) {
            String line=raw.trim();
            if(line.isEmpty()||line.startsWith("#"))continue;
            if(++count>32) { warnings.add("Lista M3U excede 32 discos.");break; }
            if(line.contains("/")||line.contains("\\")||line.contains(":")
                    || line.contains("..")) {
                warnings.add("Ruta externa o no segura en M3U: "+line);
                continue;
            }
            if(!Files.isRegularFile(path.resolveSibling(line),LinkOption.NOFOLLOW_LINKS))
                warnings.add("No se encuentra el disco de M3U: "+line);
        }
        if(count==0)warnings.add("Lista M3U sin discos.");
    }
}
