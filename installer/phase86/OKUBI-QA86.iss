; OKUBI QA86 — Non-destructive per-user installer, not a stable release.
; Build with Inno Setup 6; /DSourceRoot="C:\path\to\repo".
; Installs the original, unmodified signed-by-source Windows QA image
; plus a small local-profile-isolating launcher. No ROM/BIOS/user saves.
#ifndef SourceRoot
  #error SourceRoot must be supplied with /DSourceRoot=...
#endif

#define AppExe "OKUBI-QA86.exe"

[Setup]
AppId={{C8F57D2C-379D-42C9-A677-C0F4B0B9E86D}
AppName=OKUBI QA86 - Windows
AppVersion=1.0.13.86
AppVerName=OKUBI QA86 - Windows (versión de prueba)
AppPublisher=OKUBI
AppComments=Versión de prueba separada; conserva otras instalaciones y sus partidas.
DefaultDirName={localappdata}\Programs\OKUBI-QA86
DefaultGroupName=OKUBI QA86
DisableProgramGroupPage=yes
DisableDirPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
UninstallDisplayName=OKUBI QA86 Windows (pruebas)
UninstallDisplayIcon={app}\{#AppExe}
OutputDir={#SourceRoot}\build\phase86\out
OutputBaseFilename=OKUBI-Windows-Instalar-QA86
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
CloseApplications=yes
RestartApplications=no
SetupLogging=no
AllowNoIcons=yes
UsePreviousAppDir=no
VersionInfoDescription=OKUBI QA86 - instalador de prueba con guardados aislados

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"

[Tasks]
Name: "desktopicon"; Description: "Crear acceso directo en el escritorio"; GroupDescription: "Accesos directos:"; Flags: unchecked

[Files]
Source: "{#SourceRoot}\build\phase86\image\OKUBI Desktop\*"; DestDir: "{app}\OKUBI Desktop"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#SourceRoot}\build\phase86\wrapper\{#AppExe}"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#SourceRoot}\installer\phase86\LEEME-OKUBI-QA86.txt"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\OKUBI QA86"; Filename: "{app}\{#AppExe}"; WorkingDir: "{app}"
Name: "{autodesktop}\OKUBI QA86"; Filename: "{app}\{#AppExe}"; Tasks: desktopicon; WorkingDir: "{app}"

[Run]
Filename: "{app}\{#AppExe}"; Description: "Abrir OKUBI QA86"; Flags: nowait postinstall skipifsilent
