package bo.okubi.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
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
        validate(p, new HashSet<>(), 0);
    }

    private static void validate(Path p, Set<Path> ancestry, int depth)
            throws IOException {
        if (!isSupported(p))
            throw new IOException("Archivo no identificado como disco PlayStation 1.");
        Path path = p.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(path) || Files.size(path) == 0)
            throw new IOException("Disco PS1 no disponible o ilegible: "
                    + path.getFileName());
        if (!requiresCompanions(path)) return;
        if (depth > 5 || !ancestry.add(path))
            throw new IOException("Lista M3U circular o con demasiados niveles.");
        try {
            if (Files.size(path) > 256 * 1024)
                throw new IOException("Lista CUE/M3U demasiado grande.");
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            String text = Files.readString(path,
                    name.endsWith(".cue") ? StandardCharsets.ISO_8859_1
                                          : StandardCharsets.UTF_8);
            ArrayList<String> members = new ArrayList<>();
            if (name.endsWith(".cue")) {
                Matcher matches = CUE_FILE.matcher(text);
                while (matches.find()) {
                    if (members.size() >= 64)
                        throw new IOException("CUE excede 64 referencias de pistas.");
                    members.add(matches.group(1) != null
                            ? matches.group(1) : matches.group(2));
                }
            } else {
                for (String line : text.replace("\uFEFF", "").split("\\R")) {
                    String member = line.trim();
                    if (!member.isEmpty() && !member.startsWith("#")) {
                        if (members.size() >= 32)
                            throw new IOException("M3U excede 32 discos.");
                        members.add(member);
                    }
                }
            }
            if (members.isEmpty())
                throw new IOException("CUE/M3U sin pistas de disco.");
            List<String> missing = new ArrayList<>();
            for (String member : members) {
                // Reject escaped folders and traversal, never follow symlinks.
                if (member.isBlank() || member.contains("/") || member.contains("\\")
                        || member.contains(":") || member.contains("..")
                        || member.indexOf(0) >= 0)
                    throw new IOException("Ruta de pista inválida en CUE/M3U: " + member);
                Path track = path.resolveSibling(member).normalize();
                if (!Files.isRegularFile(track, LinkOption.NOFOLLOW_LINKS)
                        || !Files.isReadable(track) || Files.size(track) == 0) {
                    missing.add(member);
                    continue;
                }
                if (name.endsWith(".m3u")) {
                    if (!isSupported(track))
                        throw new IOException("M3U contiene un archivo no PS1: " + member);
                    if (requiresCompanions(track))
                        validate(track, ancestry, depth + 1);
                }
            }
            if (!missing.isEmpty())
                throw new IOException("Faltan pistas junto al archivo: "
                        + String.join(", ", missing));
        } finally {
            ancestry.remove(path);
        }
    }

}
