package bo.nexoplay.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import bo.nexoplay.app.retro.OkubiPersonalLibrary;
import bo.nexoplay.app.retro.OkubiPokemonRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Read-through, non-destructive catalog for Android launchers and personal retro files.
 *
 * There is NO new database migration: the legacy Android keys and the imported
 * Pokémon library remain authoritative. Android IDs are preserved unchanged.
 * Retro IDs depend only on the original SAF URI or the allowlisted asset, never
 * on a mutable title. This adapter does not copy games or saved states.
 */
public final class OkubiUnifiedLibrary {
    private static final String PREF = "okubi_unified_library_v1";
    private static final String SPOTLIGHT_KEY = "home_spotlight_id_v20";
    private final Context context;
    private final GameCatalog catalog;
    private final GameStore store;
    private final SharedPreferences prefs;

    public enum Kind { ANDROID, ROM, PERSONAL_ASSET }

    public static final class Entry {
        public final String id;
        public final String title;
        public final String system;
        public final Kind kind;
        public final GameApp androidGame;
        public final String uri;
        public final String asset;
        public final boolean favorite;
        public final long lastPlayed;

        private Entry(String id, String title, String system, Kind kind,
                      GameApp androidGame, String uri, String asset,
                      boolean favorite, long lastPlayed) {
            this.id = id;
            this.title = title;
            this.system = system;
            this.kind = kind;
            this.androidGame = androidGame;
            this.uri = uri;
            this.asset = asset;
            this.favorite = favorite;
            this.lastPlayed = lastPlayed;
        }
    }

    public OkubiUnifiedLibrary(Context context, GameCatalog catalog, GameStore store) {
        this.context = context.getApplicationContext();
        this.catalog = catalog;
        this.store = store;
        this.prefs = this.context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(64);
            for (byte b : hash) {
                builder.append(Character.forDigit((b >>> 4) & 15, 16));
                builder.append(Character.forDigit(b & 15, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 no disponible", error);
        }
    }

    public static String idForRomUri(String uri) {
        if (uri == null || !uri.startsWith("content://"))
            throw new IllegalArgumentException("Referencia SAF inválida");
        return "retro:uri:" + digest(uri);
    }

    public static String idForAsset(String asset) {
        if (!OkubiPersonalLibrary.isAllowed(asset))
            throw new IllegalArgumentException("Cartucho integrado no autorizado");
        return "retro:asset:" + digest(asset);
    }

    public List<Entry> all() {
        List<Entry> entries = new ArrayList<>();
        Set<String> ids = new HashSet<>();

        for (GameApp game : catalog.selected(store)) {
            if (game != null && ids.add(game.id)) {
                entries.add(new Entry(game.id, game.title, "ANDROID", Kind.ANDROID,
                        game, null, null, store.favorite(game.id), store.lastLaunched(game.id)));
            }
        }
        for (OkubiPokemonRepository.Item game : OkubiPokemonRepository.list(context)) {
            if (game.uri == null || !game.uri.startsWith("content://")) continue;
            String id = idForRomUri(game.uri);
            if (ids.add(id)) {
                entries.add(new Entry(id, game.title, game.system, Kind.ROM,
                        null, game.uri, null, game.favorite, game.lastPlayed));
            }
        }
        for (OkubiPersonalLibrary.Game game : OkubiPersonalLibrary.available(context)) {
            String id = idForAsset(game.asset);
            if (ids.add(id)) {
                entries.add(new Entry(id, game.title, game.system, Kind.PERSONAL_ASSET,
                        null, null, game.asset, prefs.getBoolean("fav_" + id, false),
                        prefs.getLong("played_" + id, 0L)));
            }
        }
        return entries;
    }

    /** Resolve the owner-picked hero or the most recently opened *available*
     *  game, regardless of Android vs retro. Never persists fallback choices. */
    public Entry spotlight(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) return null;
        List<OkubiLibrarySpotlight.Candidate> ranked =
                new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            if (entry != null) ranked.add(new OkubiLibrarySpotlight.Candidate(
                    entry.id, entry.title, entry.lastPlayed, entry.favorite));
        }
        String id = OkubiLibrarySpotlight.select(
                prefs.getString(SPOTLIGHT_KEY, null), ranked);
        if (id == null) return null;
        for (Entry entry : entries) {
            if (entry != null && id.equals(entry.id)) return entry;
        }
        return null;
    }

    /** A pin is a UI preference; it never modifies the game itself. */
    public boolean isSpotlightPinned(Entry entry) {
        return entry != null && entry.id.equals(
                prefs.getString(SPOTLIGHT_KEY, ""));
    }

    public void setSpotlight(Entry entry) {
        // Only currently visible library entries may be pinned.
        if (entry == null || find(entry.id) == null) return;
        prefs.edit().putString(SPOTLIGHT_KEY, entry.id).apply();
    }

    public void clearSpotlight() {
        prefs.edit().remove(SPOTLIGHT_KEY).apply();
    }

    public boolean hasSpotlightPin() {
        return !prefs.getString(SPOTLIGHT_KEY, "").isEmpty();
    }

    public Entry find(String id) {
        if (id == null) return null;
        for (Entry entry : all()) if (entry.id.equals(id)) return entry;
        return null;
    }

    public void setFavorite(Entry entry, boolean favorite) {
        if (entry == null) return;
        if (entry.kind == Kind.ANDROID) {
            if (store.favorite(entry.id) != favorite) store.toggleFavorite(entry.id);
        } else if (entry.kind == Kind.ROM) {
            OkubiPokemonRepository.setFavorite(context, entry.uri, favorite);
        } else {
            prefs.edit().putBoolean("fav_" + entry.id, favorite).apply();
        }
    }

    /** Calling this denotes an intentional launch, not verified time played. */
    public void markOpened(Entry entry) {
        if (entry == null) return;
        if (entry.kind == Kind.ROM) OkubiPokemonRepository.markPlayed(context, entry.uri);
        if (entry.kind == Kind.PERSONAL_ASSET)
            prefs.edit().putLong("played_" + entry.id, System.currentTimeMillis()).apply();
    }

    public String retroCover(Entry entry) {
        if (entry == null || entry.kind == Kind.ANDROID) return "";
        return prefs.getString("cover_" + entry.id, "");
    }

    public void setRetroCover(Entry entry, Uri image) {
        if (entry == null || entry.kind == Kind.ANDROID) return;
        if (image == null) {
            prefs.edit().remove("cover_" + entry.id).apply();
        } else if ("content".equals(image.getScheme())) {
            prefs.edit().putString("cover_" + entry.id, image.toString()).apply();
        }
    }
}
