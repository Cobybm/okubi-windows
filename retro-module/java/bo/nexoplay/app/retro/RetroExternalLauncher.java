package bo.nexoplay.app.retro;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/**
 * Launches user-provided files in installed external emulators.
 * Confirmed integrations: PPSSPP (free, Gold, Legacy) and melonDS.
 * Does not embed emulator binaries or guarantee a game will run.
 */
public final class RetroExternalLauncher {
    private static final String PSP_ACTIVITY = "org.ppsspp.ppsspp.PpssppActivity";
    private static final String DS_ACTIVITY = "me.magnum.melonds.ui.emulator.EmulatorActivity";
    private static final String[] PSP_PACKAGES = {
            "org.ppsspp.ppsspp", "org.ppsspp.ppssppgold", "org.ppsspp.ppsspplegacy"
    };

    private RetroExternalLauncher() {}

    /** Official external emulator pages only; never installs APKs itself. */
    public static void openOfficialEmulatorPage(Activity host,String console) {
        if(host==null)return;
        String page=RetroExpansionFormats.officialEmulatorPage(console);
        if(page==null)return;
        Uri uri=Uri.parse(page);
        if(!"https".equalsIgnoreCase(uri.getScheme()))return;
        Intent browser=new Intent(Intent.ACTION_VIEW,uri);
        browser.addCategory(Intent.CATEGORY_BROWSABLE);
        try { host.startActivity(browser); }
        catch(ActivityNotFoundException | SecurityException error) {
            Toast.makeText(host,"No se pudo abrir la página del emulador",
                    Toast.LENGTH_LONG).show();
        }
    }

    private static Intent base(Activity host, Uri file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(file);
        intent.setClipData(ClipData.newUri(host.getContentResolver(), "OKUBI ROM", file));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    public static void open(Activity host, Uri file, String console) {
        if (host == null || file == null || console == null) return;
        if (!"content".equalsIgnoreCase(file.getScheme())) {
            Toast.makeText(host, "Selecciona un archivo con el explorador de Android",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if ("PSP".equals(console)) {
            for (String application : PSP_PACKAGES) {
                Intent intent = base(host, file);
                intent.setClassName(application, PSP_ACTIVITY);
                try {
                    host.startActivity(intent);
                    return;
                } catch (ActivityNotFoundException | SecurityException ignored) {
                    // Try Gold or Legacy when the free version is not installed.
                }
            }
            Toast.makeText(host, "Para jugar PSP instala PPSSPP oficial",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if ("NDS".equals(console)) {
            Intent intent = base(host, file);
            intent.setClassName("me.magnum.melonds", DS_ACTIVITY);
            try {
                host.startActivity(intent);
                return;
            } catch (ActivityNotFoundException | SecurityException error) {
                Toast.makeText(host, "Instala melonDS y añade primero tu ROM a su biblioteca",
                        Toast.LENGTH_LONG).show();
                return;
            }
        }

        Intent intent = base(host, file);
        intent.setDataAndType(file, "application/octet-stream");
        Intent chooser = Intent.createChooser(intent,
                RetroExpansionFormats.isExternalClassic(console)
                ? "Abrir "+console+" con un emulador instalado"
                : "Selecciona un emulador instalado");
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            host.startActivity(chooser);
        } catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(host,
                    "Ninguna app acepta el archivo. Ábrelo desde el explorador "
                    +"de tu emulador instalado; OKUBI no modificó la partida.",
                    Toast.LENGTH_LONG).show();
        }
    }
}
