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
 * PS1 disc files supplied by the owner. Disc images are not copied or decoded
 * by OKUBI Java: Libretro opens them by path with the complete CUE/M3U sidecars.
 * Never parse shell commands, unpack archives or classify ISO as PS1 silently.
 */
public final class DesktopPs1Disc {
    private DesktopPs1Disc() { }
    private static final Pattern CUE_FILE =
            Pattern.compile("(?im)^\\s*FILE\\s+(?:\"([^\"]+)\"|([^\\s]+))");

    public static boolean isSupported(Path p) {
        if (p == null || p.getFileName() == null) return false;
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".cue") || name.endsWith(".chd")
                || name.endsWith(".pbp") || name.endsWith(".iso")
                || name.endsWith(".bin") || name.endsWith(".img")
                || name.endsWith(".ccd") || name.endsWith(".m3u")
                || name.endsWith(".mds") || name.endsWith(".ecm");
    }

    public static boolean requiresCompanions(Path p) {
        if (!isSupported(p)) return false;
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".cue") || name.endsWith(".m3u");
    }

    public static void validateCompanions(Path p) throws IOException {
        if (!isSupported(p)) throw new IOException("No se reconoce imagen PS1.");
        Path path = p.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(path) || Files.size(path)==0)
            throw new IOException("Disco PS1 no disponible o ilegible.");
        if (!requiresCompanions(path)) return;
        if (Files.size(path) > 256 * 1024)
            throw new IOException("Lista CUE/M3U demasiado grande.");
        String name=path.getFileName().toString().toLowerCase(Locale.ROOT);
        String text=Files.readString(path,
                name.endsWith(".cue") ? StandardCharsets.ISO_8859_1
                                      : StandardCharsets.UTF_8);
        ArrayList<String> names=new ArrayList<>();
        if(name.endsWith(".cue")) {
            Matcher matches=CUE_FILE.matcher(text);
            while(matches.find() && names.size()<64) {
                String member=matches.group(1)!=null
                        ?matches.group(1):matches.group(2);
                names.add(member);
            }
        } else {
            for(String line:text.split("\\R")) {
                String member=line.trim();
                if(!member.isEmpty() && !member.startsWith("#")) {
                    if(names.size()>=32)throw new IOException("M3U excede 32 discos.");
                    names.add(member);
                }
            }
        }
        if(names.isEmpty())throw new IOException("CUE/M3U sin pistas de disco.");
        List<String> missing=new ArrayList<>();
        for(String member:names) {
            // No traversal, absolute names or hidden external locations.
            if(member.isEmpty() || member.contains("/") || member.contains("\\")
                    || member.contains(":") || member.contains("..")
                    || member.indexOf(0)>=0)
                throw new IOException("Ruta de pista inválida en CUE/M3U: "+member);
            Path track=path.resolveSibling(member);
            if(!Files.isRegularFile(track, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isReadable(track))
                missing.add(member);
        }
        if(!missing.isEmpty())throw new IOException(
                "Faltan pistas junto al archivo: "+String.join(", ",missing));
    }
}
