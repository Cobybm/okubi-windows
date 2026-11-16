package bo.okubi.desktop;

import bo.nexoplay.app.retro.OkubiSaveArchive;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.UUID;

/**
 * Desktop <-> Android transfer based on the actual Android OkubiSaveArchive v1.
 *
 * Only cartridge battery RAM and RTC can be transferred across Libretro cores.
 * Save states are intentionally NOT portable: Android and Windows cores may
 * differ by version/architecture. No Android, ROM or external-emulator writes.
 */
public final class DesktopCrossSave {
    private static final long MAX_ARCHIVE_BYTES =
            8L + 2L * 80 + OkubiSaveArchive.MAX_RAM
            + OkubiSaveArchive.MAX_RTC + OkubiSaveArchive.MAX_STATE + 3L * 36 + 4096;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern(
            "yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private DesktopCrossSave() { }

    public static final class Preview {
        public final String system;
        public final String romSha256;
        public final int ramBytes;
        public final int rtcBytes;
        public final boolean hasNonPortableState;
        public final Path archiveFile;
        /** SHA-256 of SRAM + RTC, deliberately excludes nonportable save-states. */
        public final String portableSha256;
        private final OkubiSaveArchive.Snapshot contents;

        private Preview(OkubiSaveArchive.Snapshot contents, Path path) {
            this.contents = contents;
            archiveFile = path;
            system = contents.system;
            romSha256 = contents.romSha256;
            portableSha256 = portableDigest(contents);
            ramBytes = contents.ram == null ? 0 : contents.ram.length;
            rtcBytes = contents.rtc == null ? 0 : contents.rtc.length;
            hasNonPortableState = contents.state != null;
        }
    }

    public static final class ImportResult {
        public final Path recoveryBackup;
        public final boolean ramInstalled;
        public final boolean rtcInstalled;
        public final boolean skippedState;

        private ImportResult(Path recovery, boolean ram, boolean rtc,
                             boolean skip) {
            recoveryBackup = recovery;
            ramInstalled = ram;
            rtcInstalled = rtc;
            skippedState = skip;
        }
    }

    private static DesktopSaves saves(DesktopLibrary.Game game, Path root)
            throws IOException {
        if (game == null || game.console == null || game.rom == null
                || !game.isAvailable())
            throw new IOException("Selecciona primero una ROM que exista en tu biblioteca.");
        return new DesktopSaves(root, game.console, game.rom);
    }

    /**
     * Compare immutable cloud revisions against Windows battery data without
     * counting Libretro states (which are not portable).
     */
    public static String localPortableSha256(DesktopLibrary.Game game, Path saveRoot)
            throws IOException {
        DesktopSaves target = saves(game, saveRoot);
        byte[] ram = readPart(target.ramFile, OkubiSaveArchive.MAX_RAM);
        byte[] rtc = readPart(target.rtcFile, OkubiSaveArchive.MAX_RTC);
        if (ram == null && rtc == null) return null;
        return portableDigest(new OkubiSaveArchive.Snapshot(game.console.name(),
                target.ramFile.getFileName().toString().replace(".srm", ""),
                ram, rtc, null));
    }

    private static void digestPart(java.security.MessageDigest digest, byte[] payload) {
        digest.update(java.nio.ByteBuffer.allocate(4).putInt(
                payload == null ? -1 : payload.length).array());
        if (payload != null) digest.update(payload);
    }

    private static String portableDigest(OkubiSaveArchive.Snapshot snapshot) {
        try {
            java.security.MessageDigest digest =
                    java.security.MessageDigest.getInstance("SHA-256");
            digest.update(snapshot.system.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            digest.update((byte) 0);
            digest.update(snapshot.romSha256.getBytes(
                    java.nio.charset.StandardCharsets.US_ASCII));
            digestPart(digest, snapshot.ram);
            digestPart(digest, snapshot.rtc);
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest.digest()) hex.append(String.format(
                    java.util.Locale.ROOT, "%02x", b & 255));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 no disponible", error);
        }
    }

    private static byte[] readPart(Path path, int maxBytes) throws IOException {
        if (!Files.exists(path)) return null;
        if (!Files.isRegularFile(path) || !Files.isReadable(path))
            throw new IOException("No se puede leer el guardado: " + path);
        long size = Files.size(path);
        if (size <= 0 || size > maxBytes)
            throw new IOException("Guardado vacío o tamaño no permitido: " + path);
        return Files.readAllBytes(path);
    }

    private static OkubiSaveArchive.Snapshot cartridge(
            DesktopSaves target, DesktopLibrary.Console console, String hash)
            throws IOException {
        byte[] ram = readPart(target.ramFile, OkubiSaveArchive.MAX_RAM);
        byte[] rtc = readPart(target.rtcFile, OkubiSaveArchive.MAX_RTC);
        if (ram == null && rtc == null)
            throw new IOException("Aún no existe una partida de cartucho en Windows. "
                    + "Guarda dentro del juego y cierra OKUBI antes de exportar.");
        // No .state: Libretro serialization is not a cross-platform contract.
        return new OkubiSaveArchive.Snapshot(console.name(), hash, ram, rtc, null);
    }

    private static void verifySource(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file) || !Files.isReadable(file))
            throw new IOException("Selecciona un respaldo .okubisave válido.");
        long size = Files.size(file);
        if (size < 16 || size > MAX_ARCHIVE_BYTES)
            throw new IOException("Tamaño de respaldo fuera de los límites de OKUBI.");
    }

    public static Preview inspect(DesktopLibrary.Game game, Path from,
                                  Path saveRoot) throws IOException {
        DesktopSaves local = saves(game, saveRoot);
        verifySource(from);
        try (InputStream input = Files.newInputStream(from)) {
            OkubiSaveArchive.Snapshot read = OkubiSaveArchive.read(input,
                    local.ramFile.getFileName().toString().replace(".srm", ""),
                    game.console.name());
            if (read.ram == null && read.rtc == null)
                throw new IOException("El archivo solo contiene un estado de emulación. "
                        + "Android y Windows no comparten estados rápidos. "
                        + "Exporta una partida guardada dentro del juego.");
            return new Preview(read, from);
        }
    }

    public static Path export(DesktopLibrary.Game game, Path saveRoot,
                              Path destination) throws IOException {
        DesktopSaves local = saves(game, saveRoot);
        if (destination == null)
            throw new IOException("Selecciona un destino para el respaldo.");
        if (Files.exists(destination))
            throw new IOException("El archivo de destino ya existe. "
                    + "Elige otro nombre para no sobrescribir un respaldo.");
        Path out = destination.toAbsolutePath().normalize();
        Path parent = out.getParent();
        if (parent == null || !Files.isDirectory(parent))
            throw new IOException("La carpeta de exportación no existe.");
        String digest = local.ramFile.getFileName().toString()
                .replace(".srm", "");
        OkubiSaveArchive.Snapshot snapshot = cartridge(local, game.console, digest);
        Path temp = Files.createTempFile(parent, ".okubi-export-", ".tmp");
        try {
            try (OutputStream stream = Files.newOutputStream(temp)) {
                OkubiSaveArchive.write(stream, snapshot);
            }
            try (InputStream verify = Files.newInputStream(temp)) {
                OkubiSaveArchive.Snapshot checked = OkubiSaveArchive.read(verify,
                        digest, game.console.name());
                if (!OkubiSaveArchive.samePayload(snapshot, checked))
                    throw new IOException("La verificación del respaldo exportado falló.");
            }
            // Never replace any existing destination; fail safely on races.
            Files.move(temp, out);
            return out;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static Path recoveryFile(DesktopSaves target) throws IOException {
        Path recovery = target.directory.resolve("cross-save-recovery");
        Files.createDirectories(recovery);
        String name = "antes-importar-" + STAMP.format(Instant.now())
                + "-" + UUID.randomUUID() + OkubiSaveArchive.EXTENSION;
        return recovery.resolve(name);
    }

    private static void verifyExistingSize(byte[] source, byte[] current,
                                           String label) throws IOException {
        if (source != null && current != null && source.length != current.length)
            throw new IOException(label + " tiene distinto tamaño a la partida "
                    + "actual de Windows. No se reemplazó nada.");
    }

    /**
     * Import a fully verified Android .okubisave; preserve a separate recovery
     * file from the old PC battery, then write only SRAM and RTC. If a later
     * write fails, roll back both components (best effort) and leave the
     * recovery archive for manual restoration regardless of the outcome.
     *
     * Caller MUST confirm explicitly with the user and enforce no live game.
     */
    public static ImportResult install(DesktopLibrary.Game game, Path saveRoot,
                                       Preview preview) throws IOException {
        DesktopSaves target = saves(game, saveRoot);
        if (preview == null || preview.archiveFile == null)
            throw new IOException("Inspecciona el respaldo antes de importar.");
        // Re-read to detect an archive replaced between preview and confirmation.
        Preview fresh = inspect(game, preview.archiveFile, saveRoot);
        if (!Arrays.equals(fresh.contents.ram, preview.contents.ram)
                || !Arrays.equals(fresh.contents.rtc, preview.contents.rtc)
                || !Arrays.equals(fresh.contents.state, preview.contents.state))
            throw new IOException("El respaldo cambió después de la vista previa.");

        byte[] originalRam = readPart(target.ramFile, OkubiSaveArchive.MAX_RAM);
        byte[] originalRtc = readPart(target.rtcFile, OkubiSaveArchive.MAX_RTC);
        verifyExistingSize(fresh.contents.ram, originalRam, "SRAM");
        verifyExistingSize(fresh.contents.rtc, originalRtc, "RTC");

        boolean changesRam = fresh.contents.ram != null
                && !Arrays.equals(fresh.contents.ram, originalRam);
        boolean changesRtc = fresh.contents.rtc != null
                && !Arrays.equals(fresh.contents.rtc, originalRtc);
        if (!changesRam && !changesRtc)
            return new ImportResult(null, false, false, fresh.hasNonPortableState);

        Path recovery = null;
        if (originalRam != null || originalRtc != null) {
            // Create and verify the old save outside the target files BEFORE
            // modifying either component.
            recovery = export(game, saveRoot, recoveryFile(target));
        }
        try {
            if (changesRam) DesktopSaves.storeData(target.ramFile,
                    fresh.contents.ram, OkubiSaveArchive.MAX_RAM);
            if (changesRtc) DesktopSaves.storeData(target.rtcFile,
                    fresh.contents.rtc, OkubiSaveArchive.MAX_RTC);
            // Verify all installed bytes before returning any success.
            if (changesRam && !Arrays.equals(Files.readAllBytes(target.ramFile),
                    fresh.contents.ram))
                throw new IOException("SRAM no coincide tras la importación.");
            if (changesRtc && !Arrays.equals(Files.readAllBytes(target.rtcFile),
                    fresh.contents.rtc))
                throw new IOException("RTC no coincide tras la importación.");
            return new ImportResult(recovery, changesRam, changesRtc,
                    fresh.hasNonPortableState);
        } catch (IOException failure) {
            try {
                if (changesRam) rollback(target.ramFile, originalRam,
                        OkubiSaveArchive.MAX_RAM);
                if (changesRtc) rollback(target.rtcFile, originalRtc,
                        OkubiSaveArchive.MAX_RTC);
            } catch (IOException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw new IOException("No se pudo importar. Se intentó restaurar "
                    + "la partida anterior; conserva el respaldo de recuperación: "
                    + recovery, failure);
        }
    }

    private static void rollback(Path path, byte[] original, int limit)
            throws IOException {
        if (original == null) {
            // Only delete a freshly installed target when it did not exist.
            Files.deleteIfExists(path);
        } else {
            DesktopSaves.storeData(path, original, limit);
        }
    }
}
