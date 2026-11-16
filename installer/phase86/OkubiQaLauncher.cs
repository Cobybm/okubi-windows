// OKUBI QA86: Windows-only launcher for an isolated, independently installable QA.
 // No console window, no ROM or save migration, no elevated permissions.
 // .NET Framework 4.x launcher targets Windows 10/11 without an SDK requirement.
using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;

internal static class OkubiQaLauncher
{
    [STAThread]
    private static void Main()
    {
        try
        {
            string installedRoot = AppDomain.CurrentDomain.BaseDirectory;
            string imageDirectory = Path.Combine(installedRoot, "OKUBI Desktop");
            string application = Path.Combine(imageDirectory, "OKUBI Desktop.exe");
            if (!File.Exists(application))
            {
                MessageBox.Show("Falta OKUBI Desktop.exe. Reinstala la versión QA sin borrar tus partidas anteriores.",
                    "OKUBI QA", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }

            // Do not reuse production %APPDATA%\OKUBI or any older QA57 profile.
            // The isolation folder persists across QA86 app restarts/uninstalls.
            string actualLocalAppdata = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            if (String.IsNullOrWhiteSpace(actualLocalAppdata))
                throw new InvalidOperationException("Windows no proporcionó LOCALAPPDATA.");

            string qaData = Path.Combine(actualLocalAppdata, "OKUBI-QA86-Perfil");
            string qaLocal = Path.Combine(qaData, "Local");
            string qaRoaming = Path.Combine(qaData, "Roaming");
            Directory.CreateDirectory(qaLocal);
            Directory.CreateDirectory(qaRoaming);

            ProcessStartInfo start = new ProcessStartInfo(application);
            start.UseShellExecute = false;
            start.WorkingDirectory = imageDirectory;
            start.EnvironmentVariables["APPDATA"] = qaRoaming;
            start.EnvironmentVariables["LOCALAPPDATA"] = qaLocal;
            start.EnvironmentVariables["OKUBI_QA86_MODE"] = "1";
            start.EnvironmentVariables["OKUBI_QA57_DATA"] = Path.Combine(qaRoaming, "OKUBI");

            using (Process proc = Process.Start(start))
            {
                if (proc == null)
                    throw new InvalidOperationException("Windows no pudo abrir OKUBI Desktop.");
            }
        }
        catch (Exception)
        {
            // Avoid exposing user directory paths, credentials or ROM filenames.
            MessageBox.Show("No se pudo iniciar OKUBI QA. Comprueba la instalación o vuelve a ejecutar el instalador.",
                "OKUBI QA", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }
}
