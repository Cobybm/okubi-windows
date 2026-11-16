// OKUBI QA87: Windows GUI-only launcher with non-destructive QA86 profile continuity.
// Built with the Windows .NET Framework C# compiler; no admin or internet required.
// Does not install, uninstall, migrate or modify any previous OKUBI profile.
using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;

internal static class OkubiQaLauncher
{
    private const string ProfileName = "OKUBI-QA86-Perfil";
    private const string VerifyFlag = "--verify-installation";
    private const int TestTimeoutMilliseconds = 45000;
    private const int EarlyExitMilliseconds = 1800;

    [STAThread]
    private static int Main(string[] args)
    {
        bool verifying = args != null && args.Length == 1 &&
            String.Equals(args[0], VerifyFlag, StringComparison.OrdinalIgnoreCase);
        if (args != null && args.Length > 0 && !verifying)
        {
            if (!verifying) ShowError("Parámetros no admitidos.", false);
            return 2;
        }

        try
        {
            string installedRoot = AppDomain.CurrentDomain.BaseDirectory;
            string imageFolder = Path.Combine(installedRoot, "OKUBI Desktop");
            string application = Path.Combine(imageFolder, "OKUBI Desktop.exe");
            if (!File.Exists(application))
                throw new IOException("No se encuentra el ejecutable del juego.");

            string local = Environment.GetFolderPath(
                Environment.SpecialFolder.LocalApplicationData);
            if (String.IsNullOrWhiteSpace(local))
                throw new IOException("La carpeta de perfil local no está disponible.");

            // Explicitly KEEP the QA86 profile to avoid abandoning or overwriting
            // test game saves when a QA86 installation is updated to QA87.
            // The original OKUBI app / old QA57 saves are never read or moved.
            string qaRoot = Path.Combine(local, ProfileName);
            string qaLocal = Path.Combine(qaRoot, "Local");
            string qaRoaming = Path.Combine(qaRoot, "Roaming");
            Directory.CreateDirectory(qaLocal);
            Directory.CreateDirectory(qaRoaming);

            ProcessStartInfo start = new ProcessStartInfo(application);
            start.UseShellExecute = false;
            start.WorkingDirectory = imageFolder;
            if (verifying)
                start.Arguments = "--verify-windows-release";

            // Every actual child process, including the diagnostic verifier,
            // gets isolated locations. NEVER inherit the canonical APPDATA.
            start.EnvironmentVariables["APPDATA"] = qaRoaming;
            start.EnvironmentVariables["LOCALAPPDATA"] = qaLocal;
            start.EnvironmentVariables["OKUBI_QA86_MODE"] = "1";
            start.EnvironmentVariables["OKUBI_QA57_DATA"] =
                Path.Combine(qaRoaming, "OKUBI");

            using (Process child = Process.Start(start))
            {
                if (child == null)
                    throw new IOException("No se pudo iniciar el proceso de OKUBI.");

                if (verifying)
                {
                    if (!child.WaitForExit(TestTimeoutMilliseconds))
                    {
                        try { child.Kill(); } catch (InvalidOperationException) { }
                        return 3;
                    }
                    return child.ExitCode == 0 ? 0 : 4;
                }

                // A previous wrapper reported success even when the app crashed
                // instantly. Detect a failed startup and give understandable UI.
                if (child.WaitForExit(EarlyExitMilliseconds))
                {
                    ShowError("OKUBI se cerró inmediatamente. No se han borrado tus partidas. " +
                        "Repara la instalación desde el mismo instalador y vuelve a intentarlo.", false);
                    return 5;
                }
                // Swing app is alive, but physical gameplay is NOT claimed tested.
                return 0;
            }
        }
        catch (Exception)
        {
            ShowError("No se pudo iniciar OKUBI. Comprueba los archivos instalados; " +
                "tu aplicación anterior y sus partidas no se han modificado.", verifying);
            return 6;
        }
    }

    private static void ShowError(string explanation, bool silent)
    {
        if (silent) return;
        MessageBox.Show(explanation, "OKUBI 遊 · Versión de prueba",
            MessageBoxButtons.OK, MessageBoxIcon.Warning);
    }
}
