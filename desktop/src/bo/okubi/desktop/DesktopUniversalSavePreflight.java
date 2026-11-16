package bo.okubi.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * Non-destructive Android ↔ Windows save-compatibility analysis.
 * The archive's exact ROM SHA-256, console and section hashes are checked by
 * DesktopCrossSave.inspect; this preflight also examines existing PC section
 * sizes before a confirmation dialog is shown.
 *
 * Read-only; run off the Swing EDT. Never imports, exports or deletes saves.
 */
public final class DesktopUniversalSavePreflight {
    public enum Status { READY, IDENTICAL, SIZE_MISMATCH }

    public record Result(Status status, boolean existingRam, boolean existingRtc,
                         int incomingRamBytes, int incomingRtcBytes,
                         boolean willSkipQuickState, String message) {
        public boolean canInstall() { return status == Status.READY; }
    }

    private DesktopUniversalSavePreflight() { }

    public static Result inspect(DesktopLibrary.Game game,
                                 Path archive, Path saveRoot) throws IOException {
        // Verifies ROM SHA-256 and container integrity using exactly
        // the shared Android OkubiSaveArchive.java serialization.
        DesktopCrossSave.Preview preview =
                DesktopCrossSave.inspect(game, archive, saveRoot);
        return evaluate(game, preview, saveRoot);
    }

    public static Result evaluate(DesktopLibrary.Game game,
                                  DesktopCrossSave.Preview preview,
                                  Path saveRoot) throws IOException {
        if (game == null || preview == null || saveRoot == null)
            throw new IOException("Falta el juego, respaldo o carpeta de partidas.");
        if (!game.isAvailable())
            throw new IOException("La ROM ya no está disponible.");
        // The caller may have kept the preview while changing selection.
        // Compare exact console/ROM again, not just the displayed game name.
        if (!preview.system.equals(game.console.name())
                || !preview.romSha256.equals(DesktopSaves.sha256(game.rom)))
            throw new IOException("Este respaldo pertenece a otra ROM o consola.");
        DesktopSaves target = new DesktopSaves(saveRoot, game.console, game.rom);
        long ram = size(target.ramFile);
        long rtc = size(target.rtcFile);
        boolean hasRam = ram > 0, hasRtc = rtc > 0;

        if ((hasRam && preview.ramBytes > 0 && ram != preview.ramBytes)
                || (hasRtc && preview.rtcBytes > 0 && rtc != preview.rtcBytes)) {
            return new Result(Status.SIZE_MISMATCH, hasRam, hasRtc,
                    preview.ramBytes, preview.rtcBytes,
                    preview.hasNonPortableState,
                    "Las dimensiones SRAM/RTC del respaldo no coinciden con "
                    + "las partidas actuales. No se importará ni sobrescribirá nada.");
        }

        // Deliberately calculate portable content hash only after size
        // validation, and only if a local save exists.
        if (hasRam || hasRtc) {
            String localDigest = DesktopCrossSave.localPortableSha256(game, saveRoot);
            if (preview.portableSha256.equals(localDigest)) {
                return new Result(Status.IDENTICAL, hasRam, hasRtc,
                        preview.ramBytes, preview.rtcBytes,
                        preview.hasNonPortableState,
                        "El progreso SRAM/RTC ya es idéntico al del PC. "
                        + "No necesitas importar una copia.");
            }
        }

        String message = !hasRam && !hasRtc
                ? "No hay partida SRAM/RTC previa en Windows. "
                  + "Solo se instalarán los componentes portátiles del respaldo."
                : "La copia pertenece a esta ROM. Antes de importar, OKUBI "
                  + "creará un respaldo de recuperación de la partida actual.";
        if (preview.hasNonPortableState)
            message += " El estado rápido incluido se omitirá porque no es portátil.";
        return new Result(Status.READY, hasRam, hasRtc,
                preview.ramBytes, preview.rtcBytes,
                preview.hasNonPortableState, message);
    }

    private static long size(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return 0L;
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(path))
            throw new IOException("No es seguro leer el guardado local: " + path);
        long size = Files.size(path);
        if (size <= 0) throw new IOException("Guardado local vacío: " + path);
        // Follow the exact limits of Android's portable SRAM/RTC format.
        long max = path.getFileName().toString().endsWith(".rtc")
                ? bo.nexoplay.app.retro.OkubiSaveArchive.MAX_RTC
                : bo.nexoplay.app.retro.OkubiSaveArchive.MAX_RAM;
        if (size > max) throw new IOException("Guardado local excede el límite permitido.");
        return size;
    }
}
