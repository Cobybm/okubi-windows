package bo.nexoplay.app.retro;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import java.util.Arrays;

/**
 * Orientation-scoped virtual controls. Coordinates are normalized (0..1)
 * and stored separately for portrait and landscape. No ROM data is touched.
 */
public final class RetroControlProfile {
    public enum Skin {
        OKUBI_NEON("OKUBI Neon"),
        CLASSIC_GBA("Classic GBA"),
        MINIMAL_GHOST("Minimal Ghost"),
        ARCADE_BOLD("Arcade Bold"),
        RETRO_POCKET("Retro Pocket"),
        PRO_TOUCH("Pro Touch");
        public final String label;
        Skin(String label) { this.label = label; }
    }

    private static final String PREFS = "okubi_retro_controls_v1";
    private final boolean landscape;
    private final boolean cinema;
    private final boolean snes;
    public Skin skin = Skin.OKUBI_NEON;
    public float size = 1.08f;
    public float opacity = .82f;
    public boolean leftHanded;
    public boolean swapAB;
    public boolean showShoulders = true;
    public boolean hapticEnabled = false; // Opt-in, respects Android vibration settings.
    private float dpadX;
    private float dpadY;
    private final float[] xs = new float[RetroCore.Key.values().length];
    private final float[] ys = new float[RetroCore.Key.values().length];

    private RetroControlProfile(boolean landscape, boolean cinema, boolean snes) {
        this.landscape = landscape;
        this.cinema = cinema && landscape;
        this.snes = snes;
        reset();
    }
    public static RetroControlProfile load(Context context) {
        return load(context, false);
    }
    public static RetroControlProfile loadCinema(Context context) {
        return load(context, true);
    }
    public static RetroControlProfile loadSnes(Context context) {
        return load(context, false, true);
    }
    public static RetroControlProfile loadCinemaSnes(Context context) {
        return load(context, true, true);
    }
    private static RetroControlProfile load(Context context, boolean cinema) {
        return load(context, cinema, false);
    }
    private static RetroControlProfile load(Context context, boolean cinema, boolean snes) {
        boolean land = context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        RetroControlProfile result = new RetroControlProfile(land, cinema, snes);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = result.prefix();
        try { result.skin = Skin.valueOf(prefs.getString(key + "skin", Skin.OKUBI_NEON.name())); }
        catch (IllegalArgumentException ignored) { result.skin = Skin.OKUBI_NEON; }
        result.size = clamp(prefs.getFloat(key + "size", result.size), .72f, 1.50f);
        result.opacity = clamp(prefs.getFloat(key + "alpha", result.opacity), .25f, 1f);
        result.leftHanded = prefs.getBoolean(key + "left", false);
        result.swapAB = prefs.getBoolean(key + "swap", false);
        result.showShoulders = prefs.getBoolean(key + "shoulder", true);
        result.hapticEnabled = prefs.getBoolean(key + "haptics", false);
        result.dpadX = clamp(prefs.getFloat(key + "dpadX", result.dpadX), .12f, .88f);
        result.dpadY = clamp(prefs.getFloat(key + "dpadY", result.dpadY), .18f, .82f);
        for (RetroCore.Key button : RetroCore.Key.values()) {
            int i = button.ordinal();
            result.xs[i] = optionalCoordinate(prefs.getFloat(key + "x_" + button, Float.NaN));
            result.ys[i] = optionalCoordinate(prefs.getFloat(key + "y_" + button, Float.NaN));
        }
        return result;
    }
    private static float optionalCoordinate(float value) {
        return Float.isNaN(value) ? Float.NaN : clamp(value, .08f, .92f);
    }
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    private String prefix() {
        // Existing GB/GBA keys stay byte-for-byte intact. Only SNES uses
        // new names to prevent layout edits corrupting Pokémon controls.
        return (snes ? "snes_" : "")
                + (cinema ? "cinema_land_" : landscape ? "land_" : "port_");
    }

    public float dpadX() { return leftHanded ? 1f - dpadX : dpadX; }
    public float dpadY() { return dpadY; }
    public float x(RetroCore.Key key, float fallback) {
        float override = xs[key.ordinal()];
        return Float.isNaN(override) ? fallback : override;
    }
    public float y(RetroCore.Key key, float fallback) {
        float override = ys[key.ordinal()];
        return Float.isNaN(override) ? fallback : override;
    }
    public void move(RetroCore.Key key, float nx, float ny) {
        nx = clamp(nx, .10f, .90f);
        ny = clamp(ny, .14f, .86f);
        switch (key) {
            case UP: case DOWN: case LEFT: case RIGHT:
                dpadX = leftHanded ? 1f - nx : nx;
                dpadY = ny;
                return;
            default:
                xs[key.ordinal()] = nx;
                ys[key.ordinal()] = ny;
        }
    }
    /**
     * Presets tune comfort without erasing a user's carefully dragged button
     * positions or handedness. Reset is a separate, explicit user action.
     */
    public void applyPreset(RetroControlPresets.Mode mode) {
        if (mode == null) return;
        size = RetroControlPresets.boundedSize(mode.size);
        opacity = RetroControlPresets.boundedOpacity(mode.opacity);
    }

    public void reset() {
        size = 1.08f;
        opacity = .82f;
        leftHanded = false;
        swapAB = false;
        showShoulders = true;
        hapticEnabled = false;
        skin = Skin.OKUBI_NEON;
        dpadX = cinema ? .115f : landscape ? .21f : .22f;
        dpadY = cinema ? .73f : .50f;
        Arrays.fill(xs, Float.NaN);
        Arrays.fill(ys, Float.NaN);
    }
    public void save(Context context) {
        SharedPreferences.Editor e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        String key = prefix();
        e.putString(key + "skin", skin.name())
         .putFloat(key + "size", clamp(size, .72f, 1.50f))
         .putFloat(key + "alpha", clamp(opacity, .25f, 1f))
         .putBoolean(key + "left", leftHanded)
         .putBoolean(key + "swap", swapAB)
         .putBoolean(key + "shoulder", showShoulders)
         .putBoolean(key + "haptics", hapticEnabled)
         .putFloat(key + "dpadX", dpadX)
         .putFloat(key + "dpadY", dpadY);
        for (RetroCore.Key button : RetroCore.Key.values()) {
            int i = button.ordinal();
            if (Float.isNaN(xs[i])) e.remove(key + "x_" + button);
            else e.putFloat(key + "x_" + button, xs[i]);
            if (Float.isNaN(ys[i])) e.remove(key + "y_" + button);
            else e.putFloat(key + "y_" + button, ys[i]);
        }
        e.apply();
    }
}
