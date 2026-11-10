package bo.okubi.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

/**
 * OKUBI Cloud Save 0.9: a deliberately manual BRIDGE to a USER-SELECTED
 * Google Drive for desktop synced folder (or another local synced folder).
 *
 * No OAuth tokens, API calls, networking, file watchers, automatic imports,
 * overwriting cloud versions, or false promises of successful cloud upload.
 * The Google Drive desktop client is responsible for cloud transport.
 */
public final class DesktopCloudSave {
    private static final int MAX_FILES = 250;
    private static final DateTimeFormatter UTC =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                    .withZone(ZoneOffset.UTC);
    private final Path settings;
    private Path cloudFolder;

    public enum Status {
        EMPTY("Sin partida en PC ni en la carpeta"),
        LOCAL_ONLY("Partida solo en PC"),
        FOLDER_ONLY("Partida solo en la carpeta"),
        IDENTICAL("PC y último respaldo idénticos"),
        CONFLICT("CONFLICTO: PC y carpeta son diferentes");

        public final String label;
        Status(String label) { this.label = label; }
    }

    public static final class Revision {
        public final Path file;
        public final String portableSha256;
        public final long modifiedEpochMillis;
        public final int ramBytes;
        public final int rtcBytes;
        public final boolean skippedNonPortableState;

        private Revision(Path file, DesktopCrossSave.Preview preview,
                         long modifiedEpochMillis) {
            this.file = file;
            this.portableSha256 = preview.portableSha256;
            this.modifiedEpochMillis = modifiedEpochMillis;
            this.ramBytes = preview.ramBytes;
            this.rtcBytes = preview.rtcBytes;
            this.skippedNonPortableState = preview.hasNonPortableState;
        }

        @Override public String toString() {
            String time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneOffset.systemDefault())
                    .format(Instant.ofEpochMilli(modifiedEpochMillis));
            return time + "  ·  SRAM " + ramBytes
                    + " / RTC " + rtcBytes + " bytes  ·  " + file.getFileName();
        }
    }

    public static final class Report {
        public final Status status;
        public final String localSha256;
        public final List<Revision> revisions;
        public final int ignoredFiles;

        private Report(Status status, String local, List<Revision> revisions,
                       int ignored) {
            this.status = status;
            this.localSha256 = local;
            this.revisions = java.util.Collections.unmodifiableList(
                    new ArrayList<>(revisions));
            this.ignoredFiles = ignored;
        }
    }

    public static final class Upload {
        public final Path file;
        public final boolean alreadyPresent;
        public final Status previousStatus;

        private Upload(Path file, boolean alreadyPresent, Status previousStatus) {
            this.file = file;
            this.alreadyPresent = alreadyPresent;
            this.previousStatus = previousStatus;
        }
    }

    public static Path defaultSettingsFile() {
        return DesktopLibrary.defaultSettingsFile().getParent()
                .resolve("cloud-save-v1.properties");
    }

    public DesktopCloudSave(Path settings) throws IOException {
        this.settings = settings.toAbsolutePath().normalize();
        if (Files.isRegularFile(this.settings)) {
            Properties p = new Properties();
            try (InputStream source = Files.newInputStream(this.settings)) {
                p.load(source);
            }
            String value = p.getProperty("folder");
            if (value != null && !value.isBlank()) {
                try { cloudFolder = Path.of(value).toAbsolutePath().normalize(); }
                catch (RuntimeException ignored) { /* User can choose again. */ }
            }
        }
    }

    public synchronized Path folder() { return cloudFolder; }

    /** No folder is ever created implicitly. User must pick an existing sync folder. */
    public synchronized void selectFolder(Path proposed, Path saveRoot)
            throws IOException {
        if (proposed == null || saveRoot == null)
            throw new IOException("Selecciona una carpeta de Drive sincronizada.");
        Path chosen = proposed.toAbsolutePath().normalize();
        Path local = saveRoot.toAbsolutePath().normalize();
        validateFolder(chosen);
        if (chosen.startsWith(local) || local.startsWith(chosen))
            throw new IOException("No utilices la carpeta de partidas internas "
                    + "ni una carpeta superior que las contenga.");
        Properties p = new Properties();
        p.setProperty("folder", chosen.toString());
        Path parent = settings.getParent();
        Files.createDirectories(parent);
        Path tmp = Files.createTempFile(parent, ".okubi-cloud-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(tmp)) {
                p.store(output, "OKUBI Cloud folder bridge - not a Google login or upload");
            }
            try {
                Files.move(tmp, settings, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(tmp, settings, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            cloudFolder = chosen;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static void validateFolder(Path folder) throws IOException {
        if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(folder) || !Files.isReadable(folder)
                || !Files.isWritable(folder))
            throw new IOException("La carpeta no está disponible o no permite "
                    + "lectura/escritura. Comprueba Google Drive para escritorio.");
    }

    private synchronized Path checkedFolder(Path saveRoot) throws IOException {
        if (cloudFolder == null)
            throw new IOException("Primero selecciona tu carpeta sincronizada "
                    + "de Google Drive desde OKUBI.");
        Path local = saveRoot.toAbsolutePath().normalize();
        validateFolder(cloudFolder);
        if (cloudFolder.startsWith(local) || local.startsWith(cloudFolder))
            throw new IOException("La carpeta sincronizada no puede contener "
                    + "partidas internas de OKUBI.");
        return cloudFolder;
    }

    private static DesktopSaves target(DesktopLibrary.Game game, Path saveRoot)
            throws IOException {
        if (game == null || !game.isAvailable())
            throw new IOException("Selecciona una ROM existente de la biblioteca.");
        return new DesktopSaves(saveRoot, game.console, game.rom);
    }

    /**
     * Inspects matching Android/Windows .okubisave files, without trusting
     * their filenames or timestamps to decide which one wins a conflict.
     * Each archive is validated by Android's actual format implementation.
     */
    public Report inspect(DesktopLibrary.Game game, Path saveRoot) throws IOException {
        DesktopSaves local = target(game, saveRoot);
        Path folder = checkedFolder(saveRoot);
        String localSha = DesktopCrossSave.localPortableSha256(game, saveRoot);
        List<Revision> revisions = new ArrayList<>();
        int ignored = 0;
        int scanned = 0;
        try (DirectoryStream<Path> files =
                     Files.newDirectoryStream(folder, "*.okubisave")) {
            for (Path file : files) {
                if (++scanned > MAX_FILES)
                    throw new IOException("Hay más de " + MAX_FILES
                            + " respaldos en esta carpeta. Organiza versiones "
                            + "antiguas manualmente antes de continuar.");
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(file)) {
                    ignored++;
                    continue;
                }
                try {
                    DesktopCrossSave.Preview preview =
                            DesktopCrossSave.inspect(game, file, saveRoot);
                    revisions.add(new Revision(file, preview,
                            Files.getLastModifiedTime(file).toMillis()));
                } catch (IOException invalidOrOtherGame) {
                    ignored++;
                    // Other ROMs in the same folder are normal; no file is touched.
                }
            }
        }
        revisions.sort(Comparator.comparingLong(
                (Revision version) -> version.modifiedEpochMillis)
                .reversed().thenComparing(r -> r.file.toString()));
        Status status;
        if (revisions.isEmpty())
            status = localSha == null ? Status.EMPTY : Status.LOCAL_ONLY;
        else if (localSha == null)
            status = Status.FOLDER_ONLY;
        else if (localSha.equals(revisions.get(0).portableSha256))
            status = Status.IDENTICAL;
        else
            status = Status.CONFLICT;
        return new Report(status, localSha, revisions, ignored);
    }

    /**
     * Creates an IMMUTABLE, verified backup in the selected synced folder.
     * A conflict NEVER overwrites the previous cloud file. Caller/UI must
     * explicitly confirm version creation by passing allowConflict=true.
     */
    public Upload upload(DesktopLibrary.Game game, Path saveRoot,
                         boolean allowConflict) throws IOException {
        DesktopSaves local = target(game, saveRoot);
        Path folder = checkedFolder(saveRoot);
        Report report = inspect(game, saveRoot);
        if (report.localSha256 == null)
            throw new IOException("Esta ROM aún no tiene SRAM/RTC guardados "
                    + "en PC. Guarda dentro del juego y cierra la partida.");
        if (report.status == Status.IDENTICAL)
            return new Upload(report.revisions.get(0).file, true, report.status);
        if (report.status == Status.CONFLICT && !allowConflict)
            throw new IOException("CONFLICTO: la partida de PC es diferente "
                    + "del último respaldo. Revisa las versiones y confirma "
                    + "si deseas subir otra copia sin sobrescribir ninguna.");

        String digest = local.ramFile.getFileName().toString().replace(".srm", "");
        String name = "OKUBI-" + game.console.name() + "-" + digest
                + "-" + UTC.format(Instant.now()) + "-"
                + UUID.randomUUID().toString() + ".okubisave";
        Path output = folder.resolve(name);
        // export verifies the full Android-format archive after writing,
        // rejects any attempt to replace an existing file, and leaves no tmp.
        DesktopCrossSave.export(game, saveRoot, output);
        return new Upload(output, false, report.status);
    }

    /**
     * Uses the exact shared Cross Save installer: makes a separate verified
     * recovery point before touching PC SRAM/RTC. Never restores states.
     */
    public DesktopCrossSave.ImportResult download(
            DesktopLibrary.Game game, Path saveRoot, Revision revision)
            throws IOException {
        if (revision == null) throw new IOException("Selecciona una versión.");
        Path folder = checkedFolder(saveRoot);
        Path source = revision.file.toAbsolutePath().normalize();
        if (!source.getParent().equals(folder)
                || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(source))
            throw new IOException("La versión ya no está en la carpeta autorizada.");
        DesktopCrossSave.Preview current =
                DesktopCrossSave.inspect(game, source, saveRoot);
        if (!revision.portableSha256.equals(current.portableSha256))
            throw new IOException("El respaldo cambió tras mostrarse. "
                    + "No se modificó ninguna partida.");
        return DesktopCrossSave.install(game, saveRoot, current);
    }
}
