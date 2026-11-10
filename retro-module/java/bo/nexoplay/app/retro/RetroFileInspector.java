package bo.nexoplay.app.retro;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/** Simple, non-destructive format checks before registering a personal ROM. */
public final class RetroFileInspector {
    private RetroFileInspector() {}
    public static class Result {
        public final String name;
        public final long size;
        public final boolean accepted;
        public final String reason;
        Result(String n, long s, boolean a, String r) {
            name = n; size = s; accepted = a; reason = r;
        }
    }
    public static Result inspect(ContentResolver resolver, Uri uri, String system, boolean patch) {
        String name = null;
        long size = -1;
        try (Cursor c = resolver.query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null,null,null)) {
            if (c != null && c.moveToFirst()) {
                name = c.getString(0);
                if (!c.isNull(1)) size = c.getLong(1);
            }
        } catch (Exception ignored) { }
        if (name == null || name.trim().isEmpty()) {
            return new Result("", size, false, "No se pudo leer el nombre del archivo");
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (!patch && OkubiRomFormats.isZip(name)) {
            try {
                OkubiRomFormats.Detected found = OkubiRomFormats.inspectZip(resolver, uri);
                boolean gbCompatible = "GBC".equals(system) && "GB".equals(found.system);
                // For an external-only classic, a ZIP can be catalogued only
                // after extraction. ACTION_VIEW does not guarantee that the
                // recipient emulator will understand an outer archive URI.
                if (RetroExpansionFormats.isExternalClassic(system))
                    return new Result(name,size,false,
                            "Extrae el juego "+system+" del ZIP antes de importarlo");
                if (!found.system.equals(system) && !gbCompatible)
                    return new Result(name, size, false,
                            "El ZIP contiene " + found.system + " en vez de " + system);
                return new Result(name, size, true,
                        "ZIP con " + found.innerName + " · " + OkubiRomFormats.status(system));
            } catch (IOException error) {
                return new Result(name, size, false, error.getMessage());
            }
        }
        if (!patch && OkubiRomFormats.needsExternalExtractor(name))
            return new Result(name, size, false, "Extrae primero el archivo 7z/RAR/TAR y vuelve a importarlo");
        RetroSystems.SystemSpec profile = RetroSystems.byId(system);
        boolean supported = patch
            ? lower.endsWith(".ips") || lower.endsWith(".bps") || lower.endsWith(".ups")
            : (profile != null && profile.matchesFile(lower))
                || ("3DS".equals(system) && (lower.endsWith(".3ds") || lower.endsWith(".cci")))
                || ("3DS".equals(system) && lower.endsWith(".cia"))
                || ("Switch".equals(system) && (lower.endsWith(".nsp") || lower.endsWith(".xci")))
                || ("GC".equals(system) && lower.endsWith(".gcm"))
                || ("GC/Wii".equals(system) && lower.endsWith(".rvz"))
                || ("Wii".equals(system) && (lower.endsWith(".wbfs") || lower.endsWith(".wia")))
                || ("WiiU".equals(system) && (lower.endsWith(".wud") ||
                        lower.endsWith(".wux") || lower.endsWith(".wua")))
                || ("DISC".equals(system) && lower.endsWith(".iso"))
                || ("3DS".equals(system) && lower.endsWith(".cxi"))
                || ("Switch".equals(system) && (lower.endsWith(".nsz") ||
                        lower.endsWith(".xcz")))
                || ("NDS".equals(system) && lower.endsWith(".srl"));
        if (!supported) return new Result(name, size, false, "Extensión no compatible con " + system);
        if (size == 0) return new Result(name, size, false, "El archivo está vacío");
        // Sanity limits apply only to early handheld generations.
        if (!patch && size > 0) {
            if (RetroExpansionFormats.isExternalClassic(system)
                    && !RetroExpansionFormats.validKnownSize(system,size))
                return new Result(name,size,false,
                        "Tamaño incompatible con cartucho "+system);
            if ("GB".equals(system) || "GBC".equals(system)) {
                if (size < 32768 || size > 8L*1024*1024)
                    return new Result(name,size,false,"Tamaño inusual para Game Boy");
            } else if ("GBA".equals(system) && (size < 32768 || size > 32L*1024*1024)) {
                return new Result(name,size,false,"Tamaño inusual para GBA");
            }
        }
        // Source verification: don't register unreadable/missing SAF permissions.
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null || input.read() == -1)
                return new Result(name,size,false,"El archivo no se puede leer");
        } catch (IOException | SecurityException e) {
            return new Result(name,size,false,"Sin permiso para leer el archivo");
        }
        return new Result(name,size,true,"Archivo reconocido; todavía no se ha ejecutado");
    }
}
