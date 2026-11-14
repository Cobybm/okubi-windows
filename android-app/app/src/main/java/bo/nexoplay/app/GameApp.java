package bo.nexoplay.app;

import android.graphics.drawable.Drawable;

/** One launchable activity. IDs preserve the v0.1/v0.2 preference format. */
public final class GameApp {
    public final String id;
    public final String title;
    public final String packageName;
    public final String activityName;
    public final Drawable icon;

    GameApp(String id, String title, String packageName, String activityName, Drawable icon) {
        this.id = id;
        this.title = title;
        this.packageName = packageName;
        this.activityName = activityName;
        this.icon = icon;
    }
}
