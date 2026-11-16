package bo.nexoplay.app;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import java.util.Locale;

/**
 * PS1 frontend adapter for the *user-installed* official DuckStation app.
 * DuckStation is not embedded or modified (its distribution license does not
 * allow OKUBI to redistribute a changed fork).
 *
 * DuckStation EmulationActivity accepts an explicit bootPath string. It may
 * accept a content:// URI with a granted read permission on Android versions
 * where the external provider and DuckStation support scoped storage.
 *
 * IMPORTANT: multi-track CUE/M3U companions are NOT auto-granted by SAF;
 * we warn instead of promising those files will start.
 */
public final class OkubiPs1ExternalLauncher {
    public static final String PACKAGE = "com.github.stenzek.duckstation";
    public static final String ACTIVITY =
            "com.github.stenzek.duckstation.EmulationActivity";
    public static final String OFFICIAL_DOWNLOAD =
            "https://github.com/stenzek/duckstation#android";
    private OkubiPs1ExternalLauncher() { }

    public static boolean supported(String filename) {
        if (filename==null || filename.trim().isEmpty() || filename.length()>240
                || filename.contains("/") || filename.contains("\\")
                || filename.indexOf(0)>=0) return false;
        String name=filename.toLowerCase(Locale.ROOT);
        return name.endsWith(".chd") || name.endsWith(".cue")
                || name.endsWith(".ccd") || name.endsWith(".mds")
                || name.endsWith(".m3u") || name.endsWith(".img")
                || name.endsWith(".iso") || name.endsWith(".bin")
                || name.endsWith(".pbp") || name.endsWith(".ecm");
    }

    public static boolean needsOtherFiles(String filename) {
        if(!supported(filename)) return false;
        String name=filename.toLowerCase(Locale.ROOT);
        return name.endsWith(".cue") || name.endsWith(".ccd")
                || name.endsWith(".mds") || name.endsWith(".m3u")
                || name.endsWith(".bin") || name.endsWith(".img");
    }

    public static boolean consoleAmbiguous(String filename) {
        if(!supported(filename)) return false;
        String name=filename.toLowerCase(Locale.ROOT);
        return name.endsWith(".iso") || name.endsWith(".pbp")
                || name.endsWith(".chd") || name.endsWith(".bin");
    }

    public static Intent createIntent(Uri selected) {
        if(selected==null || !"content".equalsIgnoreCase(selected.getScheme()))
            throw new IllegalArgumentException("Solo documentos autorizados por Android.");
        Intent intent=new Intent(Intent.ACTION_MAIN);
        intent.setClassName(PACKAGE,ACTIVITY);
        intent.putExtra("bootPath",selected.toString());
        intent.putExtra("resumeState",false);
        intent.setData(selected);
        intent.setClipData(ClipData.newRawUri("PS1 elegido por usuario",selected));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }
}
