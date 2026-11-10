package bo.okubi.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Optional public GitHub repository search. Repositories are CANDIDATES, not
 * verified Windows game ports. No accounts, tokens, installers or executables.
 * Only an explicit user action makes an outbound HTTPS request.
 */
public final class DesktopGitHubPortsSearch {
    public static final int PAGE_SIZE = 40;
    public static final int MAX_PAGE = 25; // GitHub search API exposes up to 1,000 per query.
    private static final int MAX_JSON_BYTES = 2_000_000;
    private static final String API = "https://api.github.com/search/repositories";

    public enum Scope {
        SOURCE_PORTS("Ports de videojuegos", "source port game"),
        GAME_ENGINES("Motores abiertos", "open source game engine"),
        WINDOWS("Juegos Windows", "windows game"),
        CLASSIC_FPS("FPS clásicos", "doom quake source port"),
        RPG("RPG y aventuras", "open source rpg engine"),
        STRATEGY("Estrategia", "open source strategy game"),
        RETRO("Retro y clásicos", "retro game engine"),
        EMULATORS("Emuladores", "emulator windows"),
        FREE_GAMES("Juegos libres", "free open source game");

        public final String label;
        public final String query;
        Scope(String label, String query) { this.label = label; this.query = query; }
        @Override public String toString() { return label; }
    }

    public record Candidate(String owner, String repository, String description,
                            String language, long stars, URI github) {
        public Candidate {
            if (!validPart(owner) || !validPart(repository) || stars < 0)
                throw new IllegalArgumentException("Invalid GitHub repository");
            if (!"https".equalsIgnoreCase(github.getScheme())
                    || !"github.com".equalsIgnoreCase(github.getHost())
                    || github.getRawUserInfo() != null
                    || github.getPort() != -1
                    || github.getRawQuery() != null
                    || github.getRawFragment() != null
                    || !github.getPath().equalsIgnoreCase("/" + owner + "/" + repository))
                throw new IllegalArgumentException("GitHub URL mismatch");
            description = compact(description, 260);
            language = compact(language, 50);
        }
        public String fullName() { return owner + "/" + repository; }
        public URI releases() { return github.resolve(github.getPath() + "/releases"); }
    }

    public record SearchPage(List<Candidate> candidates, int total, int page,
                             boolean incomplete) {
        public SearchPage {
            candidates = List.copyOf(candidates);
            if (page < 1 || total < 0) throw new IllegalArgumentException("Bad page");
        }
        public boolean hasNext() {
            return page < MAX_PAGE
                    && page * PAGE_SIZE < Math.min(1000, total);
        }
    }

    private DesktopGitHubPortsSearch() { }

    private static boolean validPart(String value) {
        return value != null && value.length() <= 100
                && value.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
    }

    private static String compact(String text, int limit) {
        if (text == null) return "";
        String value = text.replaceAll("[\\x00-\\x1F\\x7F]+", " ").strip();
        return value.length() > limit ? value.substring(0, limit - 1) + "…" : value;
    }

    public static URI endpoint(Scope scope, String customQuery, int page) {
        if (scope == null || page < 1 || page > MAX_PAGE)
            throw new IllegalArgumentException("Unsupported GitHub search page");
        String query = customQuery == null ? "" : customQuery.strip();
        if (query.length() > 80 || query.indexOf('\n') >= 0 || query.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Maximum search length is 80 characters");
        // GitHub search language is intentionally exposed: quoted phrases and
        // topic: qualifiers may be used. The API URL itself is ALWAYS fixed.
        String terms = query.isBlank() ? scope.query : query;
        String q = terms + " archived:false fork:false";
        return URI.create(API + "?q="
                + URLEncoder.encode(q, StandardCharsets.UTF_8)
                + "&sort=stars&order=desc&per_page=" + PAGE_SIZE + "&page=" + page);
    }

    public static SearchPage fetch(Scope scope, String query, int page)
            throws IOException, InterruptedException {
        URI uri = endpoint(scope, query, page);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(7))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(14))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "OKUBI-Windows-Ports-Explorer")
                .GET().build();
        HttpResponse<InputStream> response =
                client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream stream = response.body()) {
            if (response.statusCode() == 403 || response.statusCode() == 429)
                throw new IOException("GitHub limitó las consultas. Prueba más tarde o usa los 25 ports revisados.");
            if (response.statusCode() == 422)
                throw new IOException("GitHub no aceptó esa búsqueda. Cambia los términos.");
            if (response.statusCode() != 200)
                throw new IOException("GitHub no respondió correctamente (HTTP "
                        + response.statusCode() + ").");
            byte[] body = stream.readNBytes(MAX_JSON_BYTES + 1);
            if (body.length > MAX_JSON_BYTES)
                throw new IOException("La respuesta de GitHub supera el tamaño permitido.");
            return parse(body, page);
        }
    }

    /** Untrusted remote JSON. Never parse executable code, HTML or redirects. */
    public static SearchPage parse(byte[] bytes, int page) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_JSON_BYTES)
            throw new IOException("Respuesta GitHub inválida");
        Object decoded = new Json(new String(bytes, StandardCharsets.UTF_8)).parse();
        if (!(decoded instanceof Map<?, ?> root))
            throw new IOException("JSON de GitHub sin objeto principal");
        int total = (int) Math.min(1_000_000, Math.max(0, number(root.get("total_count"))));
        boolean incomplete = Boolean.TRUE.equals(root.get("incomplete_results"));
        if (!(root.get("items") instanceof List<?> items))
            throw new IOException("GitHub no devolvió lista de repositorios");
        ArrayList<Candidate> out = new ArrayList<>();
        for (Object value : items) {
            if (!(value instanceof Map<?, ?> obj)) continue;
            if (Boolean.TRUE.equals(obj.get("archived"))
                    || Boolean.TRUE.equals(obj.get("fork"))
                    || Boolean.TRUE.equals(obj.get("disabled"))
                    || Boolean.TRUE.equals(obj.get("private"))) continue;
            if (!(obj.get("owner") instanceof Map<?, ?> user)) continue;
            String owner = string(user.get("login"));
            String repo = string(obj.get("name"));
            String html = string(obj.get("html_url"));
            if (!validPart(owner) || !validPart(repo)) continue;
            try {
                URI github = URI.create(html);
                Candidate c = new Candidate(owner, repo,
                        string(obj.get("description")),
                        string(obj.get("language")), number(obj.get("stargazers_count")),
                        github);
                if (out.stream().noneMatch(x -> x.fullName().equalsIgnoreCase(c.fullName())))
                    out.add(c);
            } catch (IllegalArgumentException ignored) {
                // A forged GitHub URL or malformed repository must never render.
            }
        }
        if (out.size() > PAGE_SIZE) out.subList(PAGE_SIZE, out.size()).clear();
        return new SearchPage(out, total, page, incomplete);
    }

    private static String string(Object o) { return o instanceof String s ? s : ""; }
    private static long number(Object o) {
        return o instanceof Number n ? Math.max(0, n.longValue()) : 0;
    }

    /** Small JSON value decoder with limits: only public repository metadata. */
    private static final class Json {
        private final String source;
        private int at;
        private int depth;
        Json(String source) { this.source = source; }
        Object parse() throws IOException {
            Object v = value();
            space();
            if (at != source.length()) throw fail();
            return v;
        }
        private IOException fail() { return new IOException("JSON GitHub malformado"); }
        private void space() {
            while (at < source.length()
                    && " \n\r\t".indexOf(source.charAt(at)) >= 0) at++;
        }
        private char peek() throws IOException {
            space();
            if (at >= source.length()) throw fail();
            return source.charAt(at);
        }
        private void expect(char x) throws IOException {
            if (peek() != x) throw fail();
            at++;
        }
        private Object value() throws IOException {
            if (++depth > 32) throw fail();
            try {
                char c = peek();
                if (c == '"') return quoted();
                if (c == '{') {
                    at++;
                    Map<String, Object> map = new LinkedHashMap<>();
                    if (peek() == '}') { at++; return map; }
                    while (true) {
                        String key = quoted();
                        expect(':');
                        map.put(key, value());
                        char end = peek();
                        if (end == '}') { at++; return map; }
                        expect(',');
                    }
                }
                if (c == '[') {
                    at++;
                    List<Object> list = new ArrayList<>();
                    if (peek() == ']') { at++; return list; }
                    while (true) {
                        if (list.size() > 500) throw fail();
                        list.add(value());
                        char end = peek();
                        if (end == ']') { at++; return list; }
                        expect(',');
                    }
                }
                for (String literal : List.of("true", "false", "null")) {
                    if (source.startsWith(literal, at)) {
                        at += literal.length();
                        return switch (literal) {
                            case "true" -> Boolean.TRUE;
                            case "false" -> Boolean.FALSE;
                            default -> null;
                        };
                    }
                }
                int start = at;
                while (at < source.length()
                        && "-+0123456789.eE".indexOf(source.charAt(at)) >= 0) at++;
                if (start == at) throw fail();
                try { return Double.parseDouble(source.substring(start, at)); }
                catch (NumberFormatException bad) { throw fail(); }
            } finally { depth--; }
        }
        private String quoted() throws IOException {
            expect('"');
            StringBuilder s = new StringBuilder();
            while (at < source.length()) {
                char c = source.charAt(at++);
                if (c == '"') return s.toString();
                if (c < 0x20) throw fail();
                if (c == '\\') {
                    if (at >= source.length()) throw fail();
                    c = source.charAt(at++);
                    if (c == 'u') {
                        if (at + 4 > source.length()) throw fail();
                        try {
                            c = (char) Integer.parseInt(source.substring(at, at + 4), 16);
                        } catch (NumberFormatException error) { throw fail(); }
                        at += 4;
                    } else {
                        c = switch (c) {
                            case '"' -> '"';
                            case '\\' -> '\\';
                            case '/' -> '/';
                            case 'b' -> '\b';
                            case 'f' -> '\f';
                            case 'n' -> '\n';
                            case 'r' -> '\r';
                            case 't' -> '\t';
                            default -> throw fail();
                        };
                    }
                }
                if (s.length() >= 24_000) throw fail();
                s.append(c);
            }
            throw fail();
        }
    }
}
