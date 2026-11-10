package bo.nexoplay.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Appearance only: no changes to the performance of other applications. */
public final class AppearancePrefs {
    private static final String FILE="nexo_visual_08";
    public static final String ROSE="rose", GOLD="gold", BLUE="blue", JADE="jade";
    private AppearancePrefs() {}
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
    public static String palette(Context context){
        String stored=prefs(context).getString("palette",ROSE);
        return BLUE.equals(stored)||JADE.equals(stored)?stored:ROSE;
    }
    public static void setPalette(Context context,String palette){
        String safe=BLUE.equals(palette)||JADE.equals(palette)?palette:ROSE;
        prefs(context).edit().putString("palette",safe).apply();
        apply(context);
    }
    public static void apply(Context context){
        switch(palette(context)){
            case BLUE: CinemaUi.GOLD=0xffaed5e9;break;
            case JADE: CinemaUi.GOLD=0xffb9d9c4;break;
            default: CinemaUi.GOLD=0xffff3e70;
        }
    }
    public static boolean reducedMotion(Context context){return prefs(context).getBoolean("reduced_motion",false);}
    public static void setReducedMotion(Context context,boolean enabled){
        prefs(context).edit().putBoolean("reduced_motion",enabled).apply();
    }
    public static boolean compactLibrary(Context context){return prefs(context).getBoolean("compact_library",false);}
    public static void setCompactLibrary(Context context,boolean compact){
        prefs(context).edit().putBoolean("compact_library",compact).apply();
    }
}
