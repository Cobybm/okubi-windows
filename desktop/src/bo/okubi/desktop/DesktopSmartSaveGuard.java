package bo.okubi.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * User-controllable prelaunch safety copy. Stores only an on/off preference,
 * separately from every ROM, gamepad setting, emulator and save file.
 * The actual verified SRAM/RTC copy is delegated to DesktopSaveVault.
 */
public final class DesktopSmartSaveGuard {
    private static final String KEY = "automatic.prelaunch.enabled";
    private final Path preferenceFile;
    private final DesktopSaveVault vault;
    private boolean enabled = true;

    public DesktopSmartSaveGuard(Path preferences, Path vaultRoot, Path savesRoot)
            throws IOException {
        if (preferences == null) throw new IllegalArgumentException("Preferences are required.");
        preferenceFile = preferences.toAbsolutePath().normalize();
        vault = new DesktopSaveVault(vaultRoot, savesRoot);
        try {
            load();
        } catch (IOException invalidPreferences) {
            // Malformed preferences cannot prevent OKUBI from starting.
            // Default safely OFF until the user explicitly enables backup.
            enabled = false;
        }
    }

    public static Path defaultPreferences() {
        return DesktopLibrary.defaultSettingsFile().getParent()
                .resolve("smart-save-guard-v1.properties");
    }

    public synchronized boolean enabled() { return enabled; }

    public synchronized void setEnabled(boolean next) throws IOException {
        if (next == enabled) return;
        Properties properties = new Properties();
        properties.setProperty(KEY, Boolean.toString(next));
        if (Files.isSymbolicLink(preferenceFile))
            throw new IOException("No se escriben preferencias en enlaces simbólicos.");
        Files.createDirectories(preferenceFile.getParent());
        Path temp = Files.createTempFile(preferenceFile.getParent(),
                ".okubi-guard-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temp)) {
                properties.store(out, "OKUBI Smart Save Guard v1");
            }
            try {
                Files.move(temp, preferenceFile, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, preferenceFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
        enabled = next;
    }

    /**
     * Run off the Swing EDT, after the user pressed JUGAR and before the
     * emulator starts. No automatic restore or silent game cancellation.
     */
    public DesktopSaveVault.AutomaticResult beforePlay(DesktopLibrary.Game game)
            throws IOException {
        if (!enabled()) return DesktopSaveVault.AutomaticResult.DISABLED;
        if (EmbeddedPlayerWindow.hasActiveSession())
            throw new IOException("Hay una partida activa; no se puede respaldar.");
        return vault.backupAutomaticIfChanged(game);
    }

    private void load() throws IOException {
        if (!Files.exists(preferenceFile, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isRegularFile(preferenceFile, LinkOption.NOFOLLOW_LINKS)
                || Files.size(preferenceFile) > 16 * 1024)
            throw new IOException("Preferencias SMART SAVE GUARD inválidas.");
        Properties p = new Properties();
        try (InputStream input = Files.newInputStream(preferenceFile)) { p.load(input); }
        String raw = p.getProperty(KEY, "true");
        if (!raw.equals("true") && !raw.equals("false"))
            throw new IOException("Valor de SMART SAVE GUARD inválido.");
        enabled = Boolean.parseBoolean(raw);
    }
}
