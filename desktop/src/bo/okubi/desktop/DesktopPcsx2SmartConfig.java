package bo.okubi.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Properties;

/**
 * PS2 optional per-game graphics profile, using the documented PCSX2
 * "-gamecfg <file>" flag. All files are stored in an isolated OKUBI-only
 * folder; no installed PCSX2, BIOS, memory card, emulator settings or
 * user ROM is read or written.
 *
 * Deliberate opt-in per game AND hardware fingerprint. Default is OFF.
 * Disable is instant (omits -gamecfg on the next run) and leaves all existing
 * external PCSX2 settings unchanged.
 */
public final class DesktopPcsx2SmartConfig {
    private static final String FILE_NAME = "smart-config-pcsx2-v1.properties";
    private static final int MAX_RECORDS = 1000;
    private final Path home;
    private final Path settings;
    private final Properties approvals = new Properties();

    public DesktopPcsx2SmartConfig(Path home) throws IOException {
        this.home = home.toAbsolutePath().normalize();
        this.settings = this.home.resolve(FILE_NAME);
        if (Files.isSymbolicLink(this.home) || Files.isSymbolicLink(this.settings))
            throw new IOException("Directorio de SMART CONFIG no seguro.");
        if (Files.exists(this.settings, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(this.settings, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(this.settings) > 128 * 1024)
                throw new IOException("Preferencias SMART CONFIG inválidas.");
            try (InputStream stream = Files.newInputStream(this.settings)) {
                approvals.load(stream);
            } catch (IOException | IllegalArgumentException error) {
                // Do not rewrite corrupt user settings and do not activate
                // previously approved profiles by guessing their state.
                approvals.clear();
            }
        }
    }

    public static Path defaultFolder() {
        String appData = System.getenv("APPDATA");
        Path root = appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".okubi")
                : Path.of(appData);
        return root.resolve("OKUBI").resolve("smart-config-v1");
    }

    private static boolean ps2(DesktopMulticonsoleCatalog.Game game) {
        return game != null && game.console() == DesktopSmartPerformance.Console.PS2;
    }

    private static String gameHardwareKey(DesktopMulticonsoleCatalog.Game game,
                                          DesktopSmartHardware.Snapshot hardware)
            throws IOException {
        if (!ps2(game) || hardware == null
                || DesktopSmartPlayHistory.hardwareId(hardware).isBlank())
            throw new IOException("SMART CONFIG requiere un juego PS2 y una PC detectada.");
        String value = DesktopSmartPlayHistory.hardwareId(hardware) + "|"
                + game.file().toAbsolutePath().normalize()
                        .toString().toLowerCase(Locale.ROOT);
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    sha.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 no disponible.", impossible);
        }
    }

    public synchronized boolean enabled(DesktopMulticonsoleCatalog.Game game,
                                         DesktopSmartHardware.Snapshot hardware) {
        try {
            return Boolean.parseBoolean(
                    approvals.getProperty("enabled." + gameHardwareKey(game, hardware),
                            "false"));
        } catch (IOException ignored) {
            return false;
        }
    }

    /**
     * Changes ONLY the opt-in for this game and hardware. Never affects
     * external PCSX2 state; disabling takes effect on the next launch.
     */
    public synchronized void setEnabled(DesktopMulticonsoleCatalog.Game game,
                                        DesktopSmartHardware.Snapshot hardware,
                                        boolean enabled) throws IOException {
        String key = "enabled." + gameHardwareKey(game, hardware);
        if (!approvals.containsKey(key) && approvals.size() >= MAX_RECORDS)
            throw new IOException("Límite de perfiles SMART CONFIG alcanzado.");
        approvals.setProperty(key, Boolean.toString(enabled));
        saveApprovals();
    }

    private void saveApprovals() throws IOException {
        verifyRoot();
        Files.createDirectories(home);
        Path temporary = Files.createTempFile(home, ".okubi-pcsx2-approval-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                approvals.store(output,
                        "OKUBI PCSX2 opt-ins only, no ROM, BIOS or memory-card contents");
            }
            try {
                Files.move(temporary, settings, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, settings, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * A minimal one-setting INI. Does not touch PCSX2's own per-title configs.
     * Passing -gamecfg temporarily overrides that title's game settings for
     * this launch; users must opt in because existing custom fixes may not
     * apply during the session.
     */
    public synchronized Path prepareProfile(DesktopMulticonsoleCatalog.Game game,
                                            DesktopSmartHardware.Snapshot hardware,
                                            int scale) throws IOException {
        String id = gameHardwareKey(game, hardware);
        if (!enabled(game, hardware))
            throw new IOException("Activa primero SMART CONFIG para este juego.");
        if (scale < 1 || scale > 2)
            throw new IOException("SMART CONFIG solo permite escalas 1× o 2×.");
        verifyRoot();
        Path profiles = home.resolve("profiles");
        if (Files.isSymbolicLink(profiles))
            throw new IOException("Carpeta de perfiles no segura.");
        Files.createDirectories(profiles);
        Path target = profiles.resolve("pcsx2-" + id + "-scale-" + scale + ".ini");
        byte[] contents = ("; OKUBI controlled profile; only temporary PS2 graphics override\r\n"
                + "; Game-specific settings in PCSX2 are not modified on disk.\r\n"
                + "[EmuCore/GS]\r\n"
                + "upscale_multiplier = " + scale + "\r\n")
                .getBytes(StandardCharsets.UTF_8);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(target) != contents.length
                    || !MessageDigest.isEqual(Files.readAllBytes(target), contents))
                throw new IOException("El perfil fue modificado: no se sobrescribe.");
            return target;
        }
        Path temporary = Files.createTempFile(profiles, ".okubi-ps2-", ".tmp");
        try {
            Files.write(temporary, contents);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return target;
    }

    /**
     * Phase64 graphics-v2 profiles are immutable and NEVER replace Phase63
     * files, existing PCSX2 per-title settings, memory cards or the ROM.
     * FXAA and MaxAnisotropy use PCSX2's documented EmuCore/GS settings.
     */
    public synchronized Path prepareGraphicsProfile(
            DesktopMulticonsoleCatalog.Game game,
            DesktopSmartHardware.Snapshot hardware,
            DesktopSmartGraphicsPlanner.Plan plan) throws IOException {
        String id = gameHardwareKey(game, hardware);
        if (plan == null || !enabled(game, hardware))
            throw new IOException("SMART GRAPHICS necesita consentimiento por juego.");
        int scale = plan.scale();
        int filtering = plan.anisotropy();
        if (scale < 1 || scale > 2
                || (filtering != 0 && filtering != 2 && filtering != 4))
            throw new IOException("Plan de gráficos fuera de los límites seguros.");
        verifyRoot();
        Path profiles = home.resolve("profiles");
        if (Files.isSymbolicLink(profiles))
            throw new IOException("Directorio de perfiles inseguro.");
        Files.createDirectories(profiles);
        Path target = profiles.resolve("pcsx2-" + id
                + "-graphics-v2-s" + scale
                + "-a" + filtering + "-f" + (plan.fxaa() ? "1" : "0") + ".ini");
        byte[] bytes = ("; OKUBI graphics-v2: per-game opt-in, temporary only\r\n"
                + "; No global PS2 settings, BIOS or memory cards are edited.\r\n"
                + "[EmuCore/GS]\r\n"
                + "upscale_multiplier = " + scale + "\r\n"
                + "fxaa = " + (plan.fxaa() ? "true" : "false") + "\r\n"
                + "MaxAnisotropy = " + filtering + "\r\n")
                .getBytes(StandardCharsets.UTF_8);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(target) != bytes.length
                    || !MessageDigest.isEqual(Files.readAllBytes(target), bytes))
                throw new IOException("Perfil SMART GRAPHICS alterado; no se sobrescribe.");
            return target;
        }
        Path temp = Files.createTempFile(profiles, ".okubi-graphics-v2-", ".tmp");
        try {
            Files.write(temp, bytes);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
        return target;
    }

    private void verifyRoot() throws IOException {
        if (Files.isSymbolicLink(home) || Files.isSymbolicLink(settings))
            throw new IOException("El perfil apunta a un vínculo simbólico inseguro.");
        if (Files.exists(home, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(home, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("No hay un directorio válido de SMART CONFIG.");
    }
}
