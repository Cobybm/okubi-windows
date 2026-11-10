package bo.nexoplay.app;

import java.util.regex.Pattern;

/**
 * Strict allowlist for the local, user-initiated OKUBI UNIFIED backup format.
 * No ROMs, arbitrary app files, absolute paths or executable content.
 * Pure Java: validated by CI without Android.
 */
public final class OkubiMigrationRules {
    public static final int VERSION = 1;
    public static final String FORMAT = "okubi-unified";
    public static final int MAX_ENTRIES = 512;
    public static final long MAX_TOTAL = 192L * 1024 * 1024;
    public static final int MAX_PREFS = 1024 * 1024;
    public static final int MAX_MANIFEST = 512 * 1024;
    private static final Pattern SAVE = Pattern.compile(
            "saves/(gameboy|gba|snes)/[0-9a-f]{64}\\.(srm|rtc|state|slot[23]\\.state)");

    private OkubiMigrationRules() {}

    public static boolean isSave(String path) {
        return path != null && SAVE.matcher(path).matches();
    }
    public static boolean isAllowedEntry(String path) {
        return "manifest.json".equals(path)
                || "preferences.json".equals(path) || isSave(path);
    }
    public static long maximumFor(String path) {
        if ("manifest.json".equals(path)) return MAX_MANIFEST;
        if ("preferences.json".equals(path)) return MAX_PREFS;
        if (!isSave(path)) return 0;
        if (path.endsWith(".srm")) return 1024 * 1024;
        if (path.endsWith(".rtc")) return 65536;
        return 16L * 1024 * 1024;
    }
    /** Preference namespaces are fixed; ROM URI grants are never portable. */
    public static boolean allowPreference(String file,String key) {
        if(file==null || key==null || key.length()>180)return false;
        if("nexo_visual_08".equals(file))
            return "palette".equals(key) || "reduced_motion".equals(key)
                    || "compact_library".equals(key);
        if("okubi_audio_v1".equals(file))
            return "volume".equals(key) || "muted".equals(key) || "safe".equals(key);
        if("okubi_performance_v1".equals(file)) return "render".equals(key);
        // Added in phases 27-29. Only known consoles and presentation prefs
        // are migratable, never arbitrary SharedPreferences or ROM paths.
        if("okubi_visual_v1".equals(file))
            return "GB".equals(key) || "GBC".equals(key)
                    || "GBA".equals(key) || "SNES".equals(key);
        if("okubi_screen_fit_v1".equals(file))
            return "GB".equals(key) || "GBC".equals(key)
                    || "GBA".equals(key) || "SNES".equals(key);
        if("okubi_overlay_opacity_v1".equals(file))
            return "GB".equals(key) || "GBC".equals(key)
                    || "GBA".equals(key) || "SNES".equals(key);
        if("okubi_retro_slots_v1".equals(file))
            return key.matches("slot_[0-9a-f]{64}");
        if("okubi_retro_controls_v1".equals(file))
            return key.matches("(?:snes_)?(port_|land_|cinema_land_)(skin|size|alpha|left|swap|shoulder|haptics|dpadX|dpadY|[xy]_(UP|DOWN|LEFT|RIGHT|A|B|C|X|Y|Z|L|R|START|SELECT))");
        if("nexo_play".equals(file))
            return "selected_components".equals(key)
                    || "favorite_components".equals(key)
                    || "okubi_hidden_components".equals(key)
                    || "player_nickname_v05".equals(key)
                    || "player_avatar_v05".equals(key)
                    || "last_game_component".equals(key)
                    || "collections_v05".equals(key)
                    || key.matches("personal_goals_v10_[A-Za-z0-9.:_/\\-]{1,120}")
                    || key.matches("launch_ms_[A-Za-z0-9.:_/\\-]{1,120}");
        if("okubi_unified_library_v1".equals(file))
            return key.matches("(fav_|played_)retro:asset:[0-9a-f]{64}");
        if("MainActivity".equals(file)
                || "bo.nexoplay.app.MainActivity".equals(file))
            return "library_fav_only_08".equals(key)
                    || "collection_order_v06".equals(key)
                    || "library_order_v09".equals(key)
                    || "cinema_lite_v07".equals(key);
        return false;
    }
}
