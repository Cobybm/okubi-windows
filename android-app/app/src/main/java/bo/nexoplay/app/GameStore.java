package bo.nexoplay.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Retains existing SharedPreferences keys, so updating from 0.2 keeps data. */
public final class GameStore {
    private static final String SELECTED = "selected_components";
    private static final String FAVORITES = "favorite_components";
    private static final String LAST = "last_game_component";
    private final SharedPreferences prefs;
    private final Context appContext;
    public GameStore(Context c) { appContext=c.getApplicationContext(); prefs = appContext.getSharedPreferences("nexo_play", Context.MODE_PRIVATE); }
    public Set<String> selected() { return new HashSet<>(prefs.getStringSet(SELECTED, Collections.emptySet())); }
    public void saveSelected(Collection<String> items) {
        Set<String> next = new HashSet<>(items);
        Set<String> removed = selected();
        removed.removeAll(next);
        Set<String> hidden = new HashSet<>(prefs.getStringSet("okubi_hidden_components", Collections.emptySet()));
        hidden.addAll(removed);
        hidden.removeAll(next);
        // Removing a game from the launcher is NOT a request to erase its
        // favorites, cover, play history, goals or collection memberships.
        // Retain existing preference keys so adding the game later restores
        // the same customization. Existing exclusion rules still apply.
        prefs.edit()
            .putStringSet(SELECTED,next)
            .putStringSet("okubi_hidden_components", hidden)
            .apply();
    }
    public Set<String> favorites() { return new HashSet<>(prefs.getStringSet(FAVORITES, Collections.emptySet())); }
    public boolean favorite(String id) { return favorites().contains(id); }
    public void toggleFavorite(String id) {
        Set<String> f = favorites();
        if (!f.remove(id)) f.add(id);
        prefs.edit().putStringSet(FAVORITES, f).apply();
    }
    public void remove(String id) {
        Set<String> s=selected(); s.remove(id); saveSelected(s);
    }
    public String nickname() { return prefs.getString("player_nickname_v05", "Jugador OKUBI"); }
    public void setNickname(String value) { prefs.edit().putString("player_nickname_v05",value).apply(); }
    public String avatar() { return prefs.getString("player_avatar_v05", "✦"); }
    public void setAvatar(String value) { prefs.edit().putString("player_avatar_v05",value).apply(); }
    public String cover(String id) { return prefs.getString("cover_v05_"+id, ""); }
    public void setCover(String id,String uri) { prefs.edit().putString("cover_v05_"+id,uri).apply(); }
    public void clearCover(String id) { prefs.edit().remove("cover_v05_"+id).apply(); }
    public String lastGame() { return prefs.getString(LAST, ""); }
    public long lastLaunched(String id) { return prefs.getLong("launch_ms_" + id, 0L); }
    public void markLaunch(String id) {
        prefs.edit().putString(LAST, id).putLong("launch_ms_" + id,System.currentTimeMillis()).apply();
    }
    /** Launching an installed game does not grant control over that game's process. */
    public boolean launch(Context c, GameApp game) {
        if (game == null) return false;
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setClassName(game.packageName,game.activityName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            c.startActivity(intent);
            markLaunch(game.id);
            return true;
        } catch (Exception e) { return false; }
    }
}
