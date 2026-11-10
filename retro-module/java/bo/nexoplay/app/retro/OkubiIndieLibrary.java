package bo.nexoplay.app.retro;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;

/**
 * Remembers the ROM that the user explicitly selected from an author's download.
 * External browser downloads cannot be enumerated under Android scoped storage.
 * Never stores ROM bytes, claims installation, or accesses other apps' files.
 */
public final class OkubiIndieLibrary {
    private static final String PREFS = "okubi_indie_links_v1";
    private OkubiIndieLibrary() {}

    public static final class Linked {
        public final Uri uri;
        public final String system;
        Linked(Uri uri, String system) {
            this.uri = uri;
            this.system = system;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    private static String key(String creatorUrl, String title) {
        // Several different games can share the SAME author's index URL.
        // Key by title + URL, never just by the web page address.
        return "indie:" + title + "@" + creatorUrl;
    }

    public static Linked saved(Context context, String creatorUrl, String title) {
        if (creatorUrl == null || creatorUrl.isEmpty()) return null;
        String address = prefs(context).getString(key(creatorUrl, title), null);
        String system = prefs(context).getString(key(creatorUrl, title) + ":system", null);
        if (address == null || !address.startsWith("content://")
                || !("GB".equals(system) || "GBC".equals(system)
                || "GBA".equals(system))) return null;
        Uri uri = Uri.parse(address);
        // Check that the document still exists; a stale preference is not a
        // completed download. Only one byte is read, not the whole ROM.
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null || input.read() < 0) return null;
        } catch (IOException | SecurityException | IllegalArgumentException error) {
            return null;
        }
        return new Linked(uri, system);
    }

    private static boolean supports(String expectedSystems, String system) {
        if (expectedSystems == null) return false;
        for (String part : expectedSystems.split("/"))
            if (part.trim().equals(system)) return true;
        return false;
    }

    /**
     * The system and file must agree, and the SAF read grant must survive
     * reboots. Registers the same URI in the unified retro library.
     */
    public static Linked link(Context context, String creatorUrl, String title,
            String expectedSystems, Uri uri) throws IOException {
        if (creatorUrl == null || !creatorUrl.startsWith("https://")
                || uri == null || !"content".equals(uri.getScheme()))
            throw new IOException("Selecciona un archivo del creador desde Android");
        OkubiRomFormats.Detected detected =
                OkubiRomFormats.detect(context.getContentResolver(), uri);
        if (!supports(expectedSystems, detected.system))
            throw new IOException("El archivo es de " + detected.system +
                    ", pero este juego es de " + expectedSystems);
        RetroFileInspector.Result result = RetroFileInspector.inspect(
                context.getContentResolver(), uri, detected.system, false);
        if (!result.accepted) throw new IOException(result.reason);
        try {
            context.getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException error) {
            throw new IOException("El proveedor no permite conservar el acceso al archivo");
        }
        prefs(context).edit()
                .putString(key(creatorUrl, title), uri.toString())
                .putString(key(creatorUrl, title) + ":system", detected.system)
                .apply();
        // Reuse the original SAF document; never duplicate or move ROM bytes.
        OkubiPokemonRepository.add(context, uri, title, detected.system);
        return new Linked(uri, detected.system);
    }

    public static void play(Context context, String creatorUrl, String title) {
        Linked file = saved(context, creatorUrl, title);
        if (file == null) {
            android.widget.Toast.makeText(context,
                    "El archivo ya no está disponible. Vincúlalo de nuevo.",
                    android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        boolean available = "GBA".equals(file.system)
                ? SameBoyNative.isGbaAvailable() : SameBoyNative.isAvailable();
        if (!available) {
            android.widget.Toast.makeText(context,
                    "El motor de " + file.system + " no está disponible",
                    android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        Intent play = new Intent(context, OkubiGameBoyActivity.class);
        play.setData(file.uri);
        play.putExtra(OkubiGameBoyActivity.EXTRA_ROM, file.uri);
        play.putExtra(OkubiGameBoyActivity.EXTRA_NAME, title);
        play.putExtra(OkubiGameBoyActivity.EXTRA_SYSTEM, file.system);
        play.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(play);
        OkubiPokemonRepository.markPlayed(context, file.uri.toString());
    }
}
