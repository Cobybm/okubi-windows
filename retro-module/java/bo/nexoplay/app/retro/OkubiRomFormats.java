package bo.nexoplay.app.retro;

import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads player-owned cartridges through the Android document provider.
 * ZIPs are inspected in memory, NEVER unpacked to an arbitrary filesystem
 * path, so malicious ZIP entry names cannot escape private storage.
 */
public final class OkubiRomFormats {
    private static final int MAX_ENTRIES = 64;
    private static final int MAX_IGNORED_ENTRY = 1024 * 1024;
    public static final int MAX_GAME_BOY = 8 * 1024 * 1024;
    private OkubiRomFormats() {}

    public static final class Detected {
        public final String system;
        public final String innerName;
        public final String detail;
        Detected(String system, String innerName, String detail) {
            this.system = system; this.innerName = innerName; this.detail = detail;
        }
    }
    public static String documentName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0))
                return cursor.getString(0);
        } catch (RuntimeException ignored) { }
        return "";
    }
    public static Detected detect(ContentResolver resolver, Uri uri) throws IOException {
        String name = documentName(resolver, uri);
        if (isZip(name)) return inspectZip(resolver, uri);
        if (needsExternalExtractor(name))
            throw new IOException("7z, RAR y TAR requieren extracción previa en Android");
        String system = systemFromName(name);
        if (system == null) throw new IOException("Formato no reconocido: " + name);
        return new Detected(system, safeName(name), status(system));
    }
    public static boolean isZip(String filename) {
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".zip");
    }
    public static boolean needsExternalExtractor(String filename) {
        if (filename == null) return false;
        String value = filename.toLowerCase(Locale.ROOT);
        return value.endsWith(".7z") || value.endsWith(".rar") || value.endsWith(".tar")
                || value.endsWith(".gz");
    }
    /**
     * Recognition is not a promise of emulation. Nintendo 3DS/Switch and
     * GameCube dumps are metadata-only on the current A05s build.
     */
    public static String systemFromName(String filename) {
        if (filename == null) return null;
        String s = filename.toLowerCase(Locale.ROOT);
        if (s.endsWith(".gbc")) return "GBC";
        if (s.endsWith(".gb")) return "GB";
        if (s.endsWith(".gba")) return "GBA";
        String classic = RetroExpansionFormats.systemFromName(s);
        if (classic != null) return classic;
        if (s.endsWith(".nds") || s.endsWith(".srl")) return "NDS";
        if (s.endsWith(".z64") || s.endsWith(".n64") || s.endsWith(".v64")) return "N64";
        if (s.endsWith(".rvz")) return "GC/Wii";
        if (s.endsWith(".gcm")) return "GC";
        if (s.endsWith(".wbfs") || s.endsWith(".wia")) return "Wii";
        if (s.endsWith(".wud") || s.endsWith(".wux") || s.endsWith(".wua")) return "WiiU";
        if (s.endsWith(".iso")) return "DISC";
        if (s.endsWith(".3ds") || s.endsWith(".cci") || s.endsWith(".cia")
                || s.endsWith(".cxi")) return "3DS";
        if (s.endsWith(".nsp") || s.endsWith(".xci")
                || s.endsWith(".nsz") || s.endsWith(".xcz")) return "Switch";
        return null;
    }
    public static String status(String system) {
        if ("GB".equals(system) || "GBC".equals(system)) return "Motor SameBoy dentro de OKUBI";
        if ("GBA".equals(system)) return "mGBA dentro de OKUBI cuando el núcleo esté instalado";
        if ("SNES".equals(system)) return "Beetle Supafaust dentro de OKUBI si está disponible";
        if (RetroExpansionFormats.isExternalClassic(system))
            return RetroExpansionFormats.externalStatus(system);
        if ("NDS".equals(system) || "N64".equals(system))
            return "Necesita emulador externo compatible";
        if ("GC".equals(system) || "GC/Wii".equals(system)
                || "Wii".equals(system) || "WiiU".equals(system)
                || "DISC".equals(system) || "3DS".equals(system)
                || "Switch".equals(system))
            return "Solo biblioteca · requiere otro sistema/emulador, no disponible dentro de OKUBI";
        return "Formato sin emulador configurado";
    }
    private static boolean recognized(String system) {
        return system != null;
    }

    /** Reads only ZIP entry names and skips at most 1 MiB of non-ROM content. */
    public static Detected inspectZip(ContentResolver resolver, Uri uri) throws IOException {
        if (resolver == null || uri == null) throw new IOException("Archivo no disponible");
        try (InputStream raw = resolver.openInputStream(uri)) {
            if (raw == null) throw new IOException("No se pudo abrir el ZIP");
            try (ZipInputStream zipped = new ZipInputStream(raw)) {
                ZipEntry entry;
                int entries = 0;
                while ((entry = zipped.getNextEntry()) != null) {
                    if (++entries > MAX_ENTRIES) throw new IOException("ZIP con demasiados archivos");
                    if (entry.isDirectory()) continue;
                    String system = systemFromName(entry.getName());
                    if (recognized(system)) {
                        long size = entry.getSize();
                        if (size == 0) throw new IOException("ROM vacía dentro del ZIP");
                        if (("GB".equals(system) || "GBC".equals(system))
                                && size > MAX_GAME_BOY)
                            throw new IOException("Cartucho Game Boy demasiado grande");
                        return new Detected(system, safeName(entry.getName()), "ROM encontrada en ZIP");
                    }
                    byte[] discard = new byte[4096];
                    int size = 0, count;
                    while ((count = zipped.read(discard)) != -1) {
                        size += count;
                        if (size > MAX_IGNORED_ENTRY)
                            throw new IOException("ZIP con archivos adicionales demasiado grandes");
                    }
                    zipped.closeEntry();
                }
            }
        }
        throw new IOException("El ZIP no contiene ROMs reconocibles");
    }

    /**
     * GBA games may reach 32 MiB. Stream a player-selected raw GBA or ZIP
     * entirely in memory, with bounded decompression and no public temp files.
     */
    /** Bounded ROM load from optional APK assets supplied by the player. */
    public static byte[] readEmbeddedGba(InputStream source) throws IOException {
        if (source == null) throw new IOException("No se pudo abrir la copia incorporada");
        return readBounded(source, 32 * 1024 * 1024, 32768);
    }

    public static byte[] readGameAdvance(ContentResolver resolver, Uri uri, String outerName)
            throws IOException {
        final int maxGba = 32 * 1024 * 1024;
        if (!isZip(outerName)) {
            try (InputStream input = resolver.openInputStream(uri)) {
                if (input == null) throw new IOException("No se puede leer el cartucho GBA");
                return readBounded(input, maxGba, 32768);
            }
        }
        try (InputStream raw = resolver.openInputStream(uri)) {
            if (raw == null) throw new IOException("No se puede abrir el ZIP");
            try (ZipInputStream zipped = new ZipInputStream(raw)) {
                ZipEntry entry;
                int count = 0;
                while ((entry = zipped.getNextEntry()) != null) {
                    if (++count > MAX_ENTRIES)
                        throw new IOException("ZIP con demasiados archivos");
                    if (entry.isDirectory()) continue;
                    if ("GBA".equals(systemFromName(entry.getName()))) {
                        if (entry.getSize() > maxGba)
                            throw new IOException("ROM GBA demasiado grande");
                        return readBounded(zipped, maxGba, 32768);
                    }
                    byte[] discard = new byte[4096];
                    int total = 0, read;
                    while ((read = zipped.read(discard)) != -1) {
                        total += read;
                        if (total > MAX_IGNORED_ENTRY)
                            throw new IOException("ZIP con archivos adicionales demasiado grandes");
                    }
                    zipped.closeEntry();
                }
            }
        }
        throw new IOException("El ZIP no contiene un juego .gba");
    }

    /** SNES accepts a bounded user-selected raw cartridge. No ZIP extraction. */
    public static byte[] readGameSnes(ContentResolver resolver, Uri uri,
                                      String outerName) throws IOException {
        if (resolver == null || uri == null
                || !("SNES".equals(RetroExpansionFormats.systemFromName(outerName))))
            throw new IOException("Selecciona una ROM SNES propia .sfc/.smc sin comprimir");
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("No se puede leer el cartucho SNES");
            return readBounded(input, 16 * 1024 * 1024, 32768);
        }
    }

    public static byte[] readGameBoy(ContentResolver resolver, Uri uri,
                                      String outerName) throws IOException {
        if (isZip(outerName)) return readGameBoyZip(resolver, uri);
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("Archivo no disponible");
            return readBounded(input, MAX_GAME_BOY, 32768);
        }
    }
    private static byte[] readGameBoyZip(ContentResolver resolver, Uri uri) throws IOException {
        try (InputStream raw = resolver.openInputStream(uri)) {
            if (raw == null) throw new IOException("ZIP no disponible");
            try (ZipInputStream zipped = new ZipInputStream(raw)) {
                ZipEntry entry;
                int count = 0;
                while ((entry = zipped.getNextEntry()) != null) {
                    if (++count > MAX_ENTRIES) throw new IOException("ZIP con demasiados archivos");
                    if (entry.isDirectory()) continue;
                    String system = systemFromName(entry.getName());
                    if ("GB".equals(system) || "GBC".equals(system)) {
                        if (entry.getSize() > MAX_GAME_BOY)
                            throw new IOException("Cartucho Game Boy demasiado grande");
                        // Reading the full entry also lets ZipInputStream verify its CRC.
                        return readBounded(zipped, MAX_GAME_BOY, 32768);
                    }
                    byte[] discard = new byte[4096];
                    int size = 0, read;
                    while ((read = zipped.read(discard)) != -1) {
                        size += read;
                        if (size > MAX_IGNORED_ENTRY)
                            throw new IOException("ZIP con archivos adicionales demasiado grandes");
                    }
                    zipped.closeEntry();
                }
            }
        }
        throw new IOException("No hay ROM GB/GBC dentro del ZIP");
    }
    private static byte[] readBounded(InputStream input, int limit, int minimum)
            throws IOException {
        return RetroRomReader.readBounded(input,limit,minimum);
    }
    public static String safeName(String path) {
        String name = path.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        return name.substring(slash + 1);
    }
}
