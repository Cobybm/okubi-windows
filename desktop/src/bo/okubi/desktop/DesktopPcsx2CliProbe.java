package bo.okubi.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Checks the selected official PCSX2 executable's own -help output before
 * applying optional -gamecfg overrides. This prevents treating older/other
 * builds as compatible and avoids changing external emulator settings.
 *
 * Runs ONLY after user has chosen the .exe and opted into SMART CONFIG.
 * No game is started by the preflight. Timeout fails closed.
 */
public final class DesktopPcsx2CliProbe {
    private DesktopPcsx2CliProbe() { }

    public static boolean supportsGameConfig(String help) {
        if (help == null || help.isBlank()) return false;
        String text = help.toLowerCase(Locale.ROOT);
        return text.contains("-gamecfg") && text.contains("-batch")
                && text.contains("game") && text.contains("--");
    }

    public static void verify(Path executable) throws IOException {
        if (executable == null) throw new IOException("Selecciona PCSX2.");
        Path exe = executable.toAbsolutePath().normalize();
        String name = exe.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.startsWith("pcsx2") || !name.endsWith(".exe")
                || !Files.isRegularFile(exe, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Ejecutable de PCSX2 no válido.");
        Path log = Files.createTempFile("okubi-pcsx2-help-", ".txt");
        Process process = null;
        try {
            process = new ProcessBuilder(exe.toString(), "-help")
                    .directory(exe.getParent().toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .start();
            boolean completed = process.waitFor(8, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new IOException("PCSX2 no respondió a -help: no se aplicaron ajustes.");
            }
            if (Files.size(log) > 128 * 1024)
                throw new IOException("Respuesta de PCSX2 demasiado grande.");
            String response = Files.readString(log, StandardCharsets.UTF_8);
            if (process.exitValue() != 0 || !supportsGameConfig(response))
                throw new IOException("Esta versión de PCSX2 no confirmó "
                        + "compatibilidad con -gamecfg. SMART CONFIG no se aplicó.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Análisis de PCSX2 interrumpido.", interrupted);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(log);
        }
    }
}
