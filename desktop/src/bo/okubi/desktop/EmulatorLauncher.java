package bo.okubi.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Launches a user-selected, independently installed emulator.
 * No shell, cmd.exe, PowerShell, ROM copying, save editing or arbitrary flags.
 */
public final class EmulatorLauncher {
    private EmulatorLauncher() { }

    public static List<String> command(Path executable, Path rom) throws IOException {
        if (executable == null || rom == null)
            throw new IOException("Configura un emulador y selecciona una ROM.");
        Path exe = executable.toAbsolutePath().normalize();
        Path game = rom.toAbsolutePath().normalize();
        if (!Files.isRegularFile(exe)
                || !exe.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe"))
            throw new IOException("No existe el emulador (.exe). Configúralo primero.");
        if (!Files.isRegularFile(game) || !Files.isReadable(game)
                || DesktopLibrary.Console.fromFile(game) == null)
            throw new IOException("La ROM ya no está disponible o su formato es incompatible.");
        return List.of(exe.toString(), game.toString());
    }

    public static Process launch(Path executable, Path rom) throws IOException {
        List<String> command = command(executable, rom);
        // Every path stays a separate argv item, including spaces and accents.
        // Never execute with a command shell or through a generated batch file.
        return new ProcessBuilder(command)
                .directory(executable.toAbsolutePath().normalize().getParent().toFile())
                .start();
    }
}
