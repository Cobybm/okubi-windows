package bo.nexoplay.app;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import java.util.Locale;

/** Explicit PPSSPP free/Gold handoff; no private ROM copies or broad storage access. */
public final class OkubiPspExternalLauncher {
    private OkubiPspExternalLauncher() { }
    public static final String FREE = "org.ppsspp.ppsspp";
    public static final String GOLD = "org.ppsspp.ppssppgold";
    public static final String ACTIVITY = "org.ppsspp.ppsspp.PpssppActivity";
    public static final String OFFICIAL_URL = "https://www.ppsspp.org/downloads/";

    public static boolean supported(String filename) {
        if(filename==null || filename.indexOf(0)>=0 || filename.contains("/")
                || filename.contains("\\") || filename.length()>240)return false;
        String name=filename.toLowerCase(Locale.ROOT);
        return name.endsWith(".iso") || name.endsWith(".cso")
                || name.endsWith(".chd") || name.equals("eboot.pbp");
    }

    public static Intent intent(Uri document, String targetPackage) {
        if(document==null || !"content".equals(document.getScheme()))
            throw new IllegalArgumentException("PPSSPP necesita un documento Android autorizado.");
        if(!FREE.equals(targetPackage) && !GOLD.equals(targetPackage))
            throw new IllegalArgumentException("Solo se permiten componentes oficiales de PPSSPP.");
        Intent launch=new Intent(Intent.ACTION_VIEW);
        launch.setClassName(targetPackage, ACTIVITY);
        launch.setData(document);
        launch.setClipData(ClipData.newRawUri("Juego PSP seleccionado",document));
        launch.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // Official PPSSPP frontend option, not a modification of user settings.
        launch.putExtra("org.ppsspp.ppsspp.Args","--pause-menu-exit");
        return launch;
    }
}
