package bo.nexoplay.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.LruCache;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Only launchable apps: category GAME cannot be trusted for all titles. */
public final class GameCatalog {
    private final Context context;
    private final List<GameApp> apps = new ArrayList<>();
    private final Map<String, GameApp> index = new HashMap<>();
    private final LruCache<String, Drawable> icons = new LruCache<>(96);
    private static final long RELOAD_INTERVAL_MS = 15_000L;
    private long lastReloadMs = -1L;

    public GameCatalog(Context context) { this.context = context.getApplicationContext(); }

    static boolean isOkubiPackage(String pkg) {
        return "bo.nexoplay.app".equals(pkg)
                || "bo.nexoplay.app.personal".equals(pkg)
                || "bo.nexoplay.app.dpadtest".equals(pkg)
                || "bo.nexoplay.app.indietest".equals(pkg)
                || "bo.nexoplay.app.unified".equals(pkg);
    }

    @SuppressWarnings("deprecation")
    public boolean reload() {
        long now = SystemClock.elapsedRealtime();
        if (lastReloadMs >= 0 && now - lastReloadMs < RELOAD_INTERVAL_MS) return false;
        apps.clear(); index.clear();
        PackageManager pm = context.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        Set<String> seen = new HashSet<>();
        for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            // Parallel QA installations must never be mistaken for games:
            // Unified, Personal, Cruceta Test, Indie Test and the legacy app.
            if (isOkubiPackage(pkg)) continue;
            String activity = info.activityInfo.name;
            String id = pkg + "/" + activity;
            if (!seen.add(id)) continue;
            CharSequence title = info.loadLabel(pm);
            Drawable icon = icons.get(id);
            if (icon == null) {
                icon = info.loadIcon(pm);
                if (icon != null) icons.put(id, icon);
            }
            GameApp app = new GameApp(id, title == null ? pkg : title.toString(),
                    pkg, activity, icon);
            apps.add(app); index.put(id, app);
        }
        Collections.sort(apps, (a, b) -> a.title.toLowerCase(Locale.ROOT)
                .compareTo(b.title.toLowerCase(Locale.ROOT)));
        lastReloadMs = SystemClock.elapsedRealtime();
        return true;
    }

    /** Explicit user request bypasses the short resume throttle. */
    public void forceReload() {
        lastReloadMs = -1L;
        reload();
    }

    public List<GameApp> available() { return new ArrayList<>(apps); }
    public GameApp get(String id) { return index.get(id); }
    public List<GameApp> selected(GameStore store) {
        List<GameApp> out = new ArrayList<>();
        for (String id : store.selected()) {
            GameApp game = index.get(id);
            if (game != null) out.add(game);
        }
        out.sort((a,b) -> {
            if (store.favorite(a.id) != store.favorite(b.id)) return store.favorite(a.id) ? -1 : 1;
            long aDate = store.lastLaunched(a.id);
            long bDate = store.lastLaunched(b.id);
            if (aDate != bDate) return Long.compare(bDate, aDate);
            return a.title.compareToIgnoreCase(b.title);
        });
        return out;
    }
}
