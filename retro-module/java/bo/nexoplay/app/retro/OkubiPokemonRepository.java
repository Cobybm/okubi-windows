package bo.nexoplay.app.retro;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Private index of documents the player has explicitly selected.
 * Stores only SAF references/labels, NEVER ROM bytes or copyrighted assets.
 * Paths and associated cartridge saves stay on the player's device.
 */
public final class OkubiPokemonRepository {
    private static final String PREF = "okubi_pokemon_repository";
    private static final String KEY = "game_files_v1";
    private static final String HIDDEN = "hidden_game_files_v2";
    private static final int MAX_ENTRIES = 250;

    public static final class Item {
        public final String uri;
        public final String title;
        public final String system;
        public boolean favorite;
        public long lastPlayed;
        public long imported;

        public Item(String uri, String title, String system, boolean favorite,
                    long lastPlayed, long imported) {
            this.uri = uri;
            this.title = title;
            this.system = system;
            this.favorite = favorite;
            this.lastPlayed = lastPlayed;
            this.imported = imported;
        }
    }

    private OkubiPokemonRepository() {}
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
    private static JSONObject encode(Item item) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("uri", item.uri);
        obj.put("title", item.title);
        obj.put("system", item.system);
        obj.put("favorite", item.favorite);
        obj.put("lastPlayed", item.lastPlayed);
        obj.put("imported", item.imported);
        return obj;
    }
    private static boolean validSystem(String system) {
        return "GB".equals(system) || "GBC".equals(system) || "GBA".equals(system)
                || RetroExpansionFormats.isExternalClassic(system)
                || "NDS".equals(system) || "N64".equals(system)
                || "GC".equals(system) || "GC/Wii".equals(system)
                || "Wii".equals(system) || "WiiU".equals(system)
                || "DISC".equals(system) || "3DS".equals(system) || "Switch".equals(system);
    }
    public static String systemFromName(String title) {
        return OkubiRomFormats.systemFromName(title);
    }
    private static List<Item> allItems(Context context) {
        List<Item> result = new ArrayList<>();
        String raw = prefs(context).getString(KEY, "[]");
        try {
            JSONArray rows = new JSONArray(raw);
            for (int i = 0; i < Math.min(rows.length(), MAX_ENTRIES); i++) {
                JSONObject obj = rows.optJSONObject(i);
                if (obj == null) continue;
                String uri = obj.optString("uri", "");
                String system = obj.optString("system", "");
                if (!uri.startsWith("content://") || !validSystem(system)) continue;
                result.add(new Item(uri, obj.optString("title", "Juego importado"), system,
                        obj.optBoolean("favorite", false), obj.optLong("lastPlayed", 0),
                        obj.optLong("imported", 0)));
            }
        } catch (JSONException ignored) {
            // Corrupt metadata must not crash the personal game library.
        }
        Collections.sort(result, Comparator.comparingLong(
                (Item entry) -> entry.lastPlayed > 0 ? entry.lastPlayed : entry.imported).reversed());
        return result;
    }

    /** Hiding an imported file preserves its title, favorite and history. */
    public static List<Item> list(Context context) {
        List<Item> visible = allItems(context);
        java.util.Set<String> hidden = prefs(context).getStringSet(HIDDEN,
                Collections.emptySet());
        visible.removeIf(item -> hidden.contains(item.uri));
        return visible;
    }

    /** Includes hidden records to prevent legacy views from reviving them. */
    public static java.util.Set<String> knownUris(Context context) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Item item : allItems(context)) ids.add(item.uri);
        return ids;
    }

    private static void setHidden(Context context, String address, boolean hide) {
        java.util.Set<String> next = new java.util.HashSet<>(
                prefs(context).getStringSet(HIDDEN, Collections.emptySet()));
        if (hide) next.add(address); else next.remove(address);
        prefs(context).edit().putStringSet(HIDDEN, next).apply();
    }

    private static void write(Context context, List<Item> values) {
        JSONArray json = new JSONArray();
        int count = 0;
        for (Item item : values) {
            if (count++ >= MAX_ENTRIES) break;
            try { json.put(encode(item)); } catch (JSONException ignored) {}
        }
        prefs(context).edit().putString(KEY, json.toString()).apply();
    }
    public static boolean add(Context context, Uri uri, String title, String system) {
        if (uri == null || !"content".equals(uri.getScheme()) || !validSystem(system))
            return false;
        List<Item> values = allItems(context);
        String address = uri.toString();
        for (Item item : values) {
            if (item.uri.equals(address)) {
                if (prefs(context).getStringSet(HIDDEN, Collections.emptySet())
                        .contains(address)) {
                    setHidden(context, address, false);
                    return true;
                }
                return false;
            }
        }
        if (values.size() >= MAX_ENTRIES) return false;
        String safeTitle = title == null ? "Juego importado" : title.trim();
        if (safeTitle.length() > 100) safeTitle = safeTitle.substring(0, 100);
        values.add(0, new Item(address, safeTitle, system, false,
                0, System.currentTimeMillis()));
        write(context, values);
        return true;
    }
    public static void remove(Context context, String address) {
        if (address == null) return;
        // Hide the entry; no ROM document or game save is touched.
        boolean known = false;
        for (Item item : allItems(context)) if (item.uri.equals(address)) {
            known = true;
            break;
        }
        if (known) setHidden(context, address, true);
    }
    public static void setFavorite(Context context, String address, boolean favorite) {
        List<Item> list = allItems(context);
        for (Item item : list) if (item.uri.equals(address)) item.favorite = favorite;
        write(context, list);
    }
    public static void markPlayed(Context context, String address) {
        List<Item> list = allItems(context);
        for (Item item : list) if (item.uri.equals(address))
            item.lastPlayed = System.currentTimeMillis();
        write(context, list);
    }
}
