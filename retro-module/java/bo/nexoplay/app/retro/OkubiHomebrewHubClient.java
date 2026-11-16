package bo.nexoplay.app.retro;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Browse only author-published open-source ROMs through Homebrew Hub.
 * No arbitrary downloader URLs, no commercial/hack-ROM titles, no auto-run.
 */
public final class OkubiHomebrewHubClient {
    public static final class FreeGame {
        public final String title, system, url, filename, license, slug;
        FreeGame(String title, String system, String url,
                 String filename, String license, String slug) {
            this.title=title;
            this.system=system;
            this.url=url;
            this.filename=filename;
            this.license=license;
            this.slug=slug;
        }
    }
    private OkubiHomebrewHubClient() { }

    public static List<FreeGame> search(String platform, String text) throws IOException {
        if (!"GB".equals(platform) && !"GBC".equals(platform) && !"GBA".equals(platform))
            throw new IOException("Consola no disponible para descarga directa.");
        String term = text == null ? "" : text.trim();
        if (term.length() > 40) throw new IOException("Búsqueda demasiado larga.");
        String requestUrl = "https://hh3.gbdev.io/api/search?platform=" + platform
                + "&typetag=game&results=10&q="
                + URLEncoder.encode(term, "UTF-8");
        HttpURLConnection conn = (HttpURLConnection)new URL(requestUrl).openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(9000);
        conn.setReadTimeout(9000);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "OKUBI-Homebrew/1.0");
        try {
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK)
                throw new IOException("Homebrew Hub respondió " + conn.getResponseCode());
            byte[] data;
            try (InputStream input = conn.getInputStream();
                 ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] block = new byte[8192];
                int n;
                while ((n=input.read(block)) != -1) {
                    if (buffer.size() + n > 512 * 1024)
                        throw new IOException("El catálogo excede el límite permitido.");
                    buffer.write(block, 0, n);
                }
                data = buffer.toByteArray();
            }
            return parse(new String(data, StandardCharsets.UTF_8), platform);
        } finally {
            conn.disconnect();
        }
    }

    /** Pure parser, testable with synthetic JSON without network or permissions. */
    public static List<FreeGame> parse(String payload, String platform) throws IOException {
        if (!"GB".equals(platform) && !"GBC".equals(platform) && !"GBA".equals(platform))
            throw new IOException("Plataforma no compatible.");
        if (payload == null || payload.length() > 512 * 1024)
            throw new IOException("Catálogo inválido.");
        ArrayList<FreeGame> out = new ArrayList<>();
        try {
            JSONArray games = new JSONObject(payload).getJSONArray("entries");
            for (int i=0; i<games.length() && out.size()<10; i++) {
                JSONObject entry = games.optJSONObject(i);
                if (entry == null || !"game".equalsIgnoreCase(entry.optString("typetag")))
                    continue;
                if (!platform.equalsIgnoreCase(entry.optString("platform"))) continue;
                String slug = entry.optString("slug","");
                String title = entry.optString("title","");
                String license = entry.optString("license","");
                if (!slug.matches("[a-zA-Z0-9_-]{2,80}") || title.trim().isEmpty()
                        || title.length() > 110 || !openLicense(license)) continue;
                JSONArray files = entry.optJSONArray("files");
                if (files == null) continue;
                String ext = "." + platform.toLowerCase(Locale.ROOT);
                for (int f=0; f<files.length(); f++) {
                    JSONObject file = files.optJSONObject(f);
                    if (file == null || !file.optBoolean("playable",false)) continue;
                    String filename = file.optString("filename","");
                    if (!filename.matches("[A-Za-z0-9_.-]{1,80}")
                            || !filename.toLowerCase(Locale.ROOT).endsWith(ext)) continue;
                    String endpoint = "https://hh3.gbdev.io/api/entry/" + slug + "/" + filename;
                    out.add(new FreeGame(title, platform, endpoint,
                            slug + "-" + filename, license, slug));
                    break;
                }
            }
        } catch (JSONException error) {
            throw new IOException("Respuesta de catálogo no reconocida.",error);
        }
        return out;
    }
    private static boolean openLicense(String license) {
        String s=license.trim().toUpperCase(Locale.ROOT)
                .replace(' ','-');
        return s.equals("MIT") || s.equals("ZLIB") || s.equals("ISC")
                || s.equals("CC0") || s.startsWith("CC0-")
                || s.equals("UNLICENSE") || s.startsWith("BSD-")
                || s.startsWith("APACHE-2") || s.startsWith("GPL-")
                || s.startsWith("LGPL-") || s.startsWith("MPL-");
    }
}
