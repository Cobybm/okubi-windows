package bo.okubi.desktop;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, conservative name matching for Libretro's public thumbnails.
 * NEVER guesses between similar titles: only exact normalized names/known
 * translations are accepted, not nearest-string or substring guesses.
 */
public final class LibretroCoverCatalog {
    private static final Pattern LINK =
            Pattern.compile("href\\s*=\\s*[\"]([^\"]+\\.png)[\"]",
                    Pattern.CASE_INSENSITIVE);
    private static final String ROOT = "https://thumbnails.libretro.com/";

    private LibretroCoverCatalog() { }

    public static String playlist(DesktopLibrary.Console console) {
        return switch (console) {
            case GB -> "Nintendo - Game Boy";
            case GBC -> "Nintendo - Game Boy Color";
            case GBA -> "Nintendo - Game Boy Advance";
            case SNES -> "Nintendo - Super Nintendo Entertainment System";
        };
    }

    public static String directoryUrl(DesktopLibrary.Console console) {
        return ROOT + encode(playlist(console)) + "/Named_Boxarts/";
    }

    public static String coverUrl(DesktopLibrary.Console console, String filename) {
        if (!isSafeFilename(filename))
            throw new IllegalArgumentException("Invalid Libretro cover name");
        return directoryUrl(console) + encode(filename);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static boolean isSafeFilename(String value) {
        return value != null && value.length() <= 255
                && value.toLowerCase(Locale.ROOT).endsWith(".png")
                && !value.contains("/") && !value.contains("\\")
                && !value.contains("..") && !value.contains("\u0000");
    }

    public static List<String> filenames(String indexHtml) {
        if (indexHtml == null) return List.of();
        Set<String> result = new LinkedHashSet<>();
        Matcher links = LINK.matcher(indexHtml);
        while (links.find()) {
            try {
                // URLDecoder interprets '+' as space. Preserve literal plus
                // signs in source directory listings.
                String encoded = links.group(1).replace("+", "%2B");
                String decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
                if (isSafeFilename(decoded)) result.add(decoded);
            } catch (IllegalArgumentException ignored) {
                // Untrusted or malformed href: skip instead of fetching it.
            }
        }
        return List.copyOf(result);
    }

    private static String normalize(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        return ascii.replaceAll("[^a-z0-9]+", " ").trim()
                .replaceAll("\\s+", " ");
    }

    public static String gameStem(String name) {
        if (name == null) return "";
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String stem = name.substring(slash + 1);
        int dot = stem.lastIndexOf('.');
        if (dot > 0 && (stem.length() - dot) <= 6)
            stem = stem.substring(0, dot);
        // DAT suffixes are metadata, not part of the game identity.
        return stem.replaceAll("\\s*\\([^)]*\\)", " ")
                .replaceAll("\\s*\\[[^]]*\\]", " ").trim();
    }

    public static Set<String> aliases(String gameTitle) {
        String raw = normalize(gameStem(gameTitle));
        Set<String> aliases = new LinkedHashSet<>();
        aliases.add(raw);
        // Common Spanish names on personal GBA ROM dumps; the source lists
        // these as "Pokemon - Edicion ..." rather than the user's short title.
        if (raw.startsWith("pokemon ")) {
            String suffix = raw.substring("pokemon ".length());
            if (suffix.equals("esmeralda")) {
                aliases.add("pokemon edicion esmeralda");
                aliases.add("pokemon emerald version");
            } else if (suffix.equals("rojo fuego")) {
                aliases.add("pokemon edicion rojo fuego");
                aliases.add("pokemon firered version");
            } else if (suffix.equals("verde hoja")) {
                aliases.add("pokemon edicion verde hoja");
                aliases.add("pokemon leafgreen version");
            } else if (suffix.equals("rubi")) {
                aliases.add("pokemon edicion rubi");
                aliases.add("pokemon ruby version");
            } else if (suffix.equals("zafiro")) {
                aliases.add("pokemon edicion zafiro");
                aliases.add("pokemon sapphire version");
            }
            if (suffix.equals("emerald")) aliases.add("pokemon emerald version");
            if (suffix.equals("firered")) aliases.add("pokemon firered version");
            if (suffix.equals("leafgreen")) aliases.add("pokemon leafgreen version");
        }
        return aliases;
    }

    public static String bestMatch(String html, String gameTitle) {
        return bestMatch(filenames(html), gameTitle);
    }

    public static String bestMatch(List<String> filenames, String gameTitle) {
        Set<String> targets = aliases(gameTitle);
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        for (String name : filenames) {
            if (!isSafeFilename(name)) continue;
            String base = name.substring(0, name.length() - 4);
            String normalized = normalize(gameStem(base));
            if (!targets.contains(normalized)) continue;
            int score = 100;
            // Prefer Spanish cover for a Spanish ROM title, without
            // accepting a different game's image.
            if (targets.contains("pokemon edicion esmeralda")
                    || targets.contains("pokemon edicion rojo fuego")
                    || targets.contains("pokemon edicion verde hoja")
                    || targets.contains("pokemon edicion rubi")
                    || targets.contains("pokemon edicion zafiro")) {
                if (name.contains("(Spain)")) score += 10;
                if (normalized.contains("edicion")) score += 5;
            } else if (name.contains("(USA, Europe)")) score += 5;
            else if (name.contains("(USA)")) score += 4;
            else if (name.contains("(Europe)")) score += 3;
            if (name.contains("(Alternate)") || name.contains("(Proto)")
                    || name.contains("(Beta)") || name.contains("(Rev "))
                score -= 15;
            if (score > bestScore || (score == bestScore
                    && (best == null || name.compareTo(best) < 0))) {
                best = name;
                bestScore = score;
            }
        }
        return best;
    }
}
