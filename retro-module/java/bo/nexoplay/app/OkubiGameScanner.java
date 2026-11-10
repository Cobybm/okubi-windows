package bo.nexoplay.app;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Discovers only installed, launchable Android games.
 * The existing GameCatalog already filters to activities with a launcher icon.
 * User removals are respected via okubi_hidden_components in GameStore.
 */
public final class OkubiGameScanner {
    private OkubiGameScanner() {}

    /** Returns newly added games. Never silently removes existing selections. */
    @SuppressWarnings("deprecation")
    public static int sync(Context context, GameCatalog catalog, GameStore store) {
        if (context == null || catalog == null || store == null) return 0;
        PackageManager pm = context.getPackageManager();
        SharedPreferences prefs = context.getSharedPreferences("nexo_play", Context.MODE_PRIVATE);
        Set<String> hidden = new HashSet<>(
                prefs.getStringSet("okubi_hidden_components", java.util.Collections.emptySet()));
        Set<String> selected = store.selected();
        Set<String> selectedPackages = new HashSet<>();
        Set<String> excludedPackages = new HashSet<>();
        for (GameApp entry : catalog.available()) {
            if (entry == null || entry.packageName == null) continue;
            if (selected.contains(entry.id)) selectedPackages.add(entry.packageName);
        }
        for (String entry : hidden) {
            int slash = entry.indexOf('/');
            if (slash > 0) excludedPackages.add(entry.substring(0, slash));
        }
        int added = 0;
        for (GameApp game : catalog.available()) {
            if (game == null || game.packageName == null || game.id == null
                    || selected.contains(game.id) || hidden.contains(game.id)
                    || selectedPackages.contains(game.packageName)
                    || excludedPackages.contains(game.packageName)) continue;
            boolean isGame = false;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(game.packageName, 0);
                isGame = ai.category == ApplicationInfo.CATEGORY_GAME
                        || (ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0;
            } catch (PackageManager.NameNotFoundException ignored) {
                // A launcher may disappear while Android is updating packages.
            } catch (SecurityException ignored) {
                // Work with apps Android permits us to inspect.
            }
            if (!isGame) isGame = knownGameLabel(game.title);
            if (isGame && selected.add(game.id)) {
                selectedPackages.add(game.packageName);
                added++;
            }
        }
        if (added > 0) store.saveSelected(selected);
        prefs.edit().putLong("okubi_last_game_scan", System.currentTimeMillis()).apply();
        return added;
    }

    /**
     * Conservative fallbacks for games that omit CATEGORY_GAME.
     * Avoid terms like 'football', 'steam' or 'kits' which also match utilities.
     */
    static boolean knownGameLabel(String title) {
        if (title == null) return false;
        String name = title.trim().toLowerCase(Locale.ROOT);
        return name.equals("worldbox") || name.startsWith("worldbox -")
                || name.equals("beholder") || name.startsWith("beholder:")
                || name.equals("sophia") || name.equals("minecraft")
                || name.equals("stardew valley") || name.equals("terraria")
                || name.equals("roblox") || name.equals("brawl stars")
                || name.equals("among us") || name.equals("pokémon go")
                || name.equals("pokemon go");
    }
}
