package bo.okubi.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

/**
 * Offline SAVE VAULT: immutable snapshots of SRAM/RTC for one exact ROM hash.
 * Every filesystem operation is explicit, off the Swing EDT, bounded and
 * distinct from the emulator's live save and Libretro quick states.
 *
 * A restore NEVER happens without the UI confirming it first. Existing saves
 * get their own pre-restore recovery snapshot, and a failed second write
 * triggers best-effort rollback to both original files.
 */
public final class DesktopSaveVault {
    private static final long MAX_RAM = 1024L * 1024;
    private static final long MAX_RTC = 64L * 1024;
    private static final int MAX_SCAN = 600;
    private static final int MAX_HISTORY = 120;
    private static final int MAX_AUTOS_PER_DAY = 8;
    private static final String MANIFEST = "manifest.properties";
    private static final String RAM = "battery.srm";
    private static final String RTC = "clock.rtc";
    private final Path vaultRoot;
    private final Path savesRoot;

    public record Entry(Path folder, long createdEpochMillis,
                        String kind, boolean ram, boolean rtc,
                        boolean valid, String problem) {
        public String title() {
            String date = java.time.format.DateTimeFormatter
                    .ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(java.time.ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(createdEpochMillis));
            return date + "  ·  " + ("RECOVERY".equals(kind)
                    ? "PROTECCIÓN PREVIA" : "AUTO".equals(kind)
                    ? "COPIA AUTOMÁTICA" : "COPIA MANUAL")
                    + "  ·  " + (ram ? "SRAM " : "")
                    + (rtc ? "RTC" : "")
                    + (valid ? "" : "  [NO VERIFICADA]");
        }
    }

    public DesktopSaveVault(Path vaultRoot, Path savesRoot) {
        this.vaultRoot = vaultRoot.toAbsolutePath().normalize();
        this.savesRoot = savesRoot.toAbsolutePath().normalize();
        if (this.vaultRoot.startsWith(this.savesRoot)
                || this.savesRoot.startsWith(this.vaultRoot))
            throw new IllegalArgumentException(
                    "SAVE VAULT must use a directory separate from live saves");
    }

    public static Path defaultVaultRoot() {
        return DesktopLibrary.defaultSettingsFile().getParent()
                .resolve("save-vault-v1");
    }

    private record Identity(DesktopSaves live, Path folder,
                            String romDigest, String console) { }

    private Identity identity(DesktopLibrary.Game game) throws IOException {
        if (game == null || !game.isAvailable())
            throw new IOException("El archivo ROM no está disponible.");
        String digest = DesktopSaves.sha256(game.rom);
        String console = game.console.name();
        Path gameFolder = vaultRoot.resolve(console).resolve(digest);
        Path liveConsole = savesRoot.resolve(console.toLowerCase(Locale.ROOT));
        for (Path path : List.of(vaultRoot, vaultRoot.resolve(console),
                gameFolder, savesRoot, liveConsole)) {
            if (Files.isSymbolicLink(path))
                throw new IOException("No se permiten accesos indirectos de partidas: " + path);
        }
        return new Identity(new DesktopSaves(savesRoot, game.console, game.rom),
                gameFolder, digest, console);
    }

    private static boolean regular(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static byte[] optional(Path path, long maximum) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!regular(path)) throw new IOException("Archivo de guardado no regular: " + path);
        long size = Files.size(path);
        if (size <= 0 || size > maximum) throw new IOException(
                "Tamaño inválido de guardado; no se modificó: " + path);
        return Files.readAllBytes(path);
    }

    private static byte[] required(Path path, long maximum) throws IOException {
        byte[] bytes = optional(path, maximum);
        if (bytes == null) throw new IOException("Falta una parte del respaldo: " + path);
        return bytes;
    }

    private static String digest(byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 no disponible", impossible);
        }
    }

    private static void writeAtomically(Path path, byte[] bytes) throws IOException {
        if (bytes == null) return;
        if (Files.isSymbolicLink(path))
            throw new IOException("No se sobrescriben enlaces simbólicos: " + path);
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), ".okubi-vault-", ".tmp");
        try {
            Files.write(temp, bytes);
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }

    private Entry create(Identity id, String kind) throws IOException {
        byte[] ram = optional(id.live.ramFile, MAX_RAM);
        byte[] rtc = optional(id.live.rtcFile, MAX_RTC);
        if (ram == null && rtc == null)
            throw new IOException("Todavía no hay una partida SRAM o RTC para respaldar.");
        Files.createDirectories(id.folder);
        Path temp = Files.createTempDirectory(id.folder, ".okubi-staging-");
        Path finalDir = id.folder.resolve("snap-"
                + UUID.randomUUID().toString().replace("-", ""));
        boolean moved = false;
        long created = Instant.now().toEpochMilli();
        try {
            Properties p = new Properties();
            p.setProperty("format", "1");
            p.setProperty("console", id.console);
            p.setProperty("rom.sha256", id.romDigest);
            p.setProperty("created.millis", Long.toString(created));
            p.setProperty("kind", kind);
            if (ram != null) {
                Files.write(temp.resolve(RAM), ram, StandardOpenOption.CREATE_NEW);
                p.setProperty("ram.sha256", digest(ram));
                p.setProperty("ram.length", Integer.toString(ram.length));
            }
            if (rtc != null) {
                Files.write(temp.resolve(RTC), rtc, StandardOpenOption.CREATE_NEW);
                p.setProperty("rtc.sha256", digest(rtc));
                p.setProperty("rtc.length", Integer.toString(rtc.length));
            }
            try (OutputStream out = Files.newOutputStream(temp.resolve(MANIFEST),
                    StandardOpenOption.CREATE_NEW)) {
                p.store(out, "OKUBI SAVE VAULT v1 - exact-ROM backup");
            }
            // Verify staged bytes before making the history entry visible.
            verify(temp, id);
            try {
                Files.move(temp, finalDir, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, finalDir);
            }
            moved = true;
            return new Entry(finalDir, created, kind, ram != null, rtc != null,
                    true, "");
        } finally {
            if (!moved) {
                // Temp folder only; never delete or clean an existing vault entry.
                Files.deleteIfExists(temp.resolve(RAM));
                Files.deleteIfExists(temp.resolve(RTC));
                Files.deleteIfExists(temp.resolve(MANIFEST));
                Files.deleteIfExists(temp);
            }
        }
    }

    public synchronized Entry backup(DesktopLibrary.Game game) throws IOException {
        return create(identity(game), "MANUAL");
    }

    public enum AutomaticResult {
        CREATED, UNCHANGED, NO_SAVE, DAILY_LIMIT, DISABLED
    }

    /**
     * One prelaunch snapshot for every new SRAM/RTC state, at most eight
     * automatic versions per UTC day. Never deletes a manual or older backup.
     * If throttled, caller explicitly asks whether to launch unprotected.
     */
    public synchronized AutomaticResult backupAutomaticIfChanged(
            DesktopLibrary.Game game) throws IOException {
        Identity id = identity(game);
        byte[] ram = optional(id.live.ramFile, MAX_RAM);
        byte[] rtc = optional(id.live.rtcFile, MAX_RTC);
        if (ram == null && rtc == null) return AutomaticResult.NO_SAVE;

        List<Entry> entries = history(game);
        // Identical SRAM and RTC already protected in ANY recent verified
        // snapshot (manual, recovery or auto): never create a duplicate.
        for (Entry entry : entries) {
            if (!entry.valid()) continue;
            Payload existing = verify(entry.folder(), id);
            if (java.util.Arrays.equals(ram, existing.ram)
                    && java.util.Arrays.equals(rtc, existing.rtc))
                return AutomaticResult.UNCHANGED;
        }
        long utcDay = Math.floorDiv(System.currentTimeMillis(), 86_400_000L);
        long countToday = entries.stream().filter(entry -> entry.valid()
                        && "AUTO".equals(entry.kind())
                        && Math.floorDiv(entry.createdEpochMillis(), 86_400_000L) == utcDay)
                .count();
        if (countToday >= MAX_AUTOS_PER_DAY) return AutomaticResult.DAILY_LIMIT;
        create(id, "AUTO");
        return AutomaticResult.CREATED;
    }

    private record Payload(byte[] ram, byte[] rtc, long created, String kind) { }

    private static Payload verify(Path folder, Identity id) throws IOException {
        if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Entrada de vault no válida.");
        if (!regular(folder.resolve(MANIFEST)))
            throw new IOException("Manifiesto del respaldo no encontrado.");
        if (Files.size(folder.resolve(MANIFEST)) > 64L * 1024)
            throw new IOException("Manifiesto inesperadamente grande: copia no verificada.");
        Properties p = new Properties();
        try (InputStream stream = Files.newInputStream(folder.resolve(MANIFEST))) {
            p.load(stream);
        }
        if (!"1".equals(p.getProperty("format"))
                || !id.console.equals(p.getProperty("console"))
                || !id.romDigest.equals(p.getProperty("rom.sha256")))
            throw new IOException("El respaldo no pertenece a esta ROM exacta.");
        byte[] ram = null, rtc = null;
        if (p.containsKey("ram.sha256")) {
            ram = required(folder.resolve(RAM), MAX_RAM);
            if (!digest(ram).equals(p.getProperty("ram.sha256"))
                    || !Integer.toString(ram.length).equals(p.getProperty("ram.length")))
                throw new IOException("SRAM del respaldo modificada o corrupta.");
        } else if (Files.exists(folder.resolve(RAM), LinkOption.NOFOLLOW_LINKS))
            throw new IOException("SRAM sin verificación de integridad.");
        if (p.containsKey("rtc.sha256")) {
            rtc = required(folder.resolve(RTC), MAX_RTC);
            if (!digest(rtc).equals(p.getProperty("rtc.sha256"))
                    || !Integer.toString(rtc.length).equals(p.getProperty("rtc.length")))
                throw new IOException("RTC del respaldo modificado o corrupto.");
        } else if (Files.exists(folder.resolve(RTC), LinkOption.NOFOLLOW_LINKS))
            throw new IOException("RTC sin verificación de integridad.");
        if (ram == null && rtc == null)
            throw new IOException("Respaldo vacío.");
        String kind = p.getProperty("kind", "MANUAL");
        if (!"RECOVERY".equals(kind) && !"MANUAL".equals(kind)
                && !"AUTO".equals(kind))
            throw new IOException("Tipo de respaldo desconocido.");
        long created;
        try { created = Long.parseLong(p.getProperty("created.millis", "-1")); }
        catch (NumberFormatException error) { throw new IOException("Fecha de respaldo inválida."); }
        if (created < 1) throw new IOException("Fecha de respaldo inválida.");
        return new Payload(ram, rtc, created, kind);
    }

    public synchronized List<Entry> history(DesktopLibrary.Game game) throws IOException {
        Identity id = identity(game);
        if (!Files.isDirectory(id.folder, LinkOption.NOFOLLOW_LINKS)) return List.of();
        List<Path> paths;
        try (var files = Files.list(id.folder)) {
            paths = files.filter(path -> path.getFileName().toString().startsWith("snap-")
                    && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .limit(MAX_SCAN).toList();
        }
        List<Entry> entries = new ArrayList<>();
        for (Path path : paths) {
            if (!path.getParent().equals(id.folder)) continue;
            try {
                Payload data = verify(path, id);
                entries.add(new Entry(path, data.created, data.kind,
                        data.ram != null, data.rtc != null, true, ""));
            } catch (IOException damaged) {
                entries.add(new Entry(path, Files.getLastModifiedTime(path).toMillis(),
                        "MANUAL", false, false, false, damaged.getMessage()));
            }
        }
        entries.sort(Comparator.comparingLong(Entry::createdEpochMillis).reversed());
        if (entries.size() > MAX_HISTORY) return List.copyOf(entries.subList(0, MAX_HISTORY));
        return List.copyOf(entries);
    }

    /**
     * Must be called only after explicit user confirmation AND when no game
     * is running. Re-verifies the selected archive at time of restoration.
     * Returns the protected PRE-RESTORE snapshot, or null if no old saves.
     */
    public synchronized Entry restore(DesktopLibrary.Game game, Entry selected)
            throws IOException {
        Identity id = identity(game);
        if (selected == null || !selected.valid()
                || !selected.folder().getParent().equals(id.folder)
                || !selected.folder().getFileName().toString().startsWith("snap-"))
            throw new IOException("Selecciona un respaldo válido de este juego.");
        Payload incoming = verify(selected.folder(), id);
        byte[] oldRam = optional(id.live.ramFile, MAX_RAM);
        byte[] oldRtc = optional(id.live.rtcFile, MAX_RTC);

        // No old save is ever removed: each replaced component is protected.
        Entry safetyCopy = (oldRam != null || oldRtc != null)
                ? create(id, "RECOVERY") : null;
        try {
            if (incoming.ram != null) writeAtomically(id.live.ramFile, incoming.ram);
            if (incoming.rtc != null) writeAtomically(id.live.rtcFile, incoming.rtc);
            // Verify written parts without modifying absent components.
            if (incoming.ram != null
                    && !MessageDigest.isEqual(incoming.ram,
                        required(id.live.ramFile, MAX_RAM))) throw new IOException("Verificación SRAM falló.");
            if (incoming.rtc != null
                    && !MessageDigest.isEqual(incoming.rtc,
                        required(id.live.rtcFile, MAX_RTC))) throw new IOException("Verificación RTC falló.");
        } catch (IOException failed) {
            try {
                if (incoming.ram != null) {
                    if (oldRam != null) writeAtomically(id.live.ramFile, oldRam);
                    else Files.deleteIfExists(id.live.ramFile);
                }
                if (incoming.rtc != null) {
                    if (oldRtc != null) writeAtomically(id.live.rtcFile, oldRtc);
                    else Files.deleteIfExists(id.live.rtcFile);
                }
            } catch (IOException rollbackFailed) {
                failed.addSuppressed(rollbackFailed);
                throw new IOException("Restauración interrumpida y recuperación incompleta. "
                        + "Conserva la copia PROTECCIÓN PREVIA en SAVE VAULT.", failed);
            }
            throw new IOException("No se completó la restauración: se intentó recuperar "
                    + "la partida anterior, sin borrar el respaldo.", failed);
        }
        return safetyCopy;
    }
}
