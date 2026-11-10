package bo.okubi.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Verified official desktop command syntaxes:
 * DuckStation -batch -nofullscreen -- file
 * PCSX2      -batch -nofullscreen -- file
 * RPCS3      --no-gui EBOOT.BIN (experimental; may vary by build).
 * Never edits external emulator files/configs, game bytes, firmware or saves.
 */
public final class DesktopMulticonsoleLauncher {
    private DesktopMulticonsoleLauncher() { }

    public static List<String> arguments(DesktopSmartPerformance.Console console,
                                         DesktopSmartPerformance.Mode mode,
                                         Path file) throws IOException {
        if (console == null || mode == null || file == null
                || !DesktopSmartPerformance.acceptedFile(
                        console, file.getFileName().toString()))
            throw new IOException("Consola y juego no compatibles.");
        Path safe = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(safe, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(safe))
            throw new IOException("Archivo de juego no disponible o inseguro.");
        List<String> args = new ArrayList<>();
        switch (console) {
            case PS1, PS2 -> {
                args.add("-batch");
                args.add(mode == DesktopSmartPerformance.Mode.QUALITY
                        ? "-fullscreen" : "-nofullscreen");
                args.add("--");
                args.add(safe.toString());
            }
            case PS3 -> {
                // PS3 ISO/PKG isn't supported as a direct boot argument here.
                // Install legitimate firmware and games in RPCS3 first.
                if (!safe.getFileName().toString().equalsIgnoreCase("EBOOT.BIN"))
                    throw new IOException("RPCS3 requiere EBOOT.BIN válido.");
                args.add("--no-gui");
                args.add(safe.toString());
            }
        }
        return List.copyOf(args);
    }

    public static List<String> command(DesktopSmartPerformance.Console console,
                                       DesktopSmartPerformance.Mode mode,
                                       Path executable, Path file) throws IOException {
        if (console == null || executable == null)
            throw new IOException("Selecciona el emulador oficial para esa consola.");
        Path exe = executable.toAbsolutePath().normalize();
        String filename = exe.getFileName().toString().toLowerCase(Locale.ROOT);
        String required = switch (console) {
            case PS1 -> "duckstation";
            case PS2 -> "pcsx2";
            case PS3 -> "rpcs3";
        };
        if (!filename.startsWith(required) || !filename.endsWith(".exe")
                || !Files.isRegularFile(exe, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(exe))
            throw new IOException("Emulador externo no configurado o no confiable.");
        List<String> command = new ArrayList<>();
        command.add(exe.toString());
        command.addAll(arguments(console, mode, file));
        return List.copyOf(command);
    }

    /**
     * The officially documented PCSX2 "-gamecfg <file>" option overrides
     * game settings only while this particular external process is running.
     * No global PCSX2 INI, memory card, firmware, save state or ROM is touched.
     */
    public static List<String> commandWithGameConfig(
            DesktopSmartPerformance.Console console,
            DesktopSmartPerformance.Mode mode,
            Path executable, Path file, Path gameConfig) throws IOException {
        if (console != DesktopSmartPerformance.Console.PS2 || gameConfig == null)
            throw new IOException("Per-game config supported only for PCSX2.");
        Path cfg = gameConfig.toAbsolutePath().normalize();
        String managed = cfg.getFileName().toString();
        boolean oldV1 = managed.matches("pcsx2-[0-9a-f]{64}-scale-[12]\\.ini");
        boolean graphicsV2 = managed.matches(
                "pcsx2-[0-9a-f]{64}-graphics-v2-s[12]-a[024]-f[01]\\.ini");
        if ((!oldV1 && !graphicsV2)
                || !Files.isRegularFile(cfg, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(cfg)
                || Files.size(cfg) > 4096)
            throw new IOException("Perfil PCSX2 inexistente o no válido.");
        List<String> args = new ArrayList<>(command(console, mode, executable, file));
        int delimiter = args.indexOf("--");
        if (delimiter < 0) throw new IOException("Falta el delimitador seguro de juego.");
        args.add(delimiter, cfg.toString());
        args.add(delimiter, "-gamecfg");
        return List.copyOf(args);
    }

    public static Process launchWithGameConfig(
            DesktopSmartPerformance.Console console,
            DesktopSmartPerformance.Mode mode,
            Path executable, Path file, Path gameConfig) throws IOException {
        List<String> command = commandWithGameConfig(
                console, mode, executable, file, gameConfig);
        return new ProcessBuilder(command)
                .directory(executable.toAbsolutePath().normalize()
                        .getParent().toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    public static Process launch(DesktopSmartPerformance.Console console,
                                 DesktopSmartPerformance.Mode mode,
                                 Path executable, Path file) throws IOException {
        List<String> command = command(console, mode, executable, file);
        Path exe = executable.toAbsolutePath().normalize();
        return new ProcessBuilder(command)
                .directory(exe.getParent().toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }
}
