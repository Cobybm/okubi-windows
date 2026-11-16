# Fase 85: run the actual packaged Windows EXE integrity CLI without games or saves.
# Read-only source ZIP, separate random QA workspace; never installs or uninstalls.
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$Archive,
    [Parameter(Mandatory=$true)][string]$Checksum,
    [switch]$TamperProof,
    [string]$Report
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$originalAppdata = $env:APPDATA
$originalLocal = $env:LOCALAPPDATA
$result = [ordered]@{
    phase = 85
    mode = 'packaged_windows_executable_cli_smoke'
    integrity_check = 'pending'
    windows_cli_verifier = 'pending'
    tampered_core_rejected = 'not_requested'
    physical_display_or_controller_tested = $false
    game_or_save_opened = $false
    production_profile_accessed = $false
    user_installation_modified = $false
    public_release_authorized = $false
}
function Invoke-AppVerifier {
    param([string]$Executable)
    # Java app-image may have GUI subsystem; rely on real process exit status.
    $arguments = @{
        FilePath = $Executable
        ArgumentList = '--verify-windows-release'
        WorkingDirectory = (Split-Path -Parent $Executable)
        PassThru = $true
    }
    $process = Start-Process @arguments
    try {
        if (-not $process.WaitForExit(45000)) {
            try { $process.Kill() } catch { }
            throw 'Verification CLI timed out'
        }
        return [int]$process.ExitCode
    } finally { $process.Dispose() }
}
try {
    if ([IO.Path]::GetFileName($Archive) -cne 'OKUBI-Desktop-Windows-x64-PHASE81-SOURCE-QA.zip') {
        throw 'Wrong QA ZIP filename'
    }
    $zipFile = Get-Item -LiteralPath $Archive
    $sumFile = Get-Item -LiteralPath $Checksum
    if ($zipFile.PSIsContainer -or $sumFile.PSIsContainer -or
        $zipFile.Length -lt 1000000 -or $zipFile.Length -gt 1000000000 -or
        $sumFile.Length -gt 300) { throw 'Unrecognized input package size' }
    $line = @([IO.File]::ReadAllLines($sumFile.FullName))
    if ($line.Count -ne 1 -or
        $line[0] -cnotmatch '^([a-f0-9]{64})\s+\*?OKUBI-Desktop-Windows-x64-PHASE81-SOURCE-QA\.zip$') {
        throw 'Trusted checksum missing'
    }
    $expected = $Matches[1]
    $sourceStream = [IO.File]::OpenRead($zipFile.FullName)
    $digest = [Security.Cryptography.SHA256]::Create()
    try {
        $actual = [BitConverter]::ToString($digest.ComputeHash($sourceStream)).Replace('-', '').ToLowerInvariant()
    } finally { $digest.Dispose(); $sourceStream.Dispose() }
    if ($actual -cne $expected) { throw 'Original QA ZIP checksum mismatch' }
    $result.integrity_check = 'pass'
    $scratch = if (-not [string]::IsNullOrWhiteSpace($env:RUNNER_TEMP)) {
        $env:RUNNER_TEMP
    } else { [IO.Path]::GetTempPath() }
    $workspace = Join-Path $scratch ('OKUBI-QA85-' + [guid]::NewGuid().ToString('N'))
    [IO.Directory]::CreateDirectory($workspace) | Out-Null
    $unpacked = Join-Path $workspace 'unpacked'
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::ExtractToDirectory($zipFile.FullName, $unpacked)
    $exe = Join-Path $unpacked 'OKUBI Desktop\OKUBI Desktop.exe'
    $targetDll = Join-Path $unpacked 'OKUBI Desktop\app\cores\mgba_libretro.dll'
    if (-not [IO.File]::Exists($exe) -or -not [IO.File]::Exists($targetDll)) {
        throw 'Source-built Windows EXE/DLL missing'
    }
    $qaLocal = Join-Path $workspace 'qa-local'
    $qaRoaming = Join-Path $workspace 'qa-roaming'
    [IO.Directory]::CreateDirectory($qaLocal) | Out-Null
    [IO.Directory]::CreateDirectory($qaRoaming) | Out-Null
    $env:LOCALAPPDATA = $qaLocal
    $env:APPDATA = $qaRoaming
    # The app's --verify-windows-release explicitly avoids GUI and user saves.
    if ((Invoke-AppVerifier -Executable $exe) -ne 0) {
        throw 'Actual Windows packaged runtime self-test failed'
    }
    $result.windows_cli_verifier = 'pass'
    if ($TamperProof) {
        # Modify a disposable copied DLL only, never the ZIP or user files.
        $original = [IO.File]::ReadAllBytes($targetDll)
        if ($original.Length -lt 100000) { throw 'Invalid source-built DLL' }
        try {
            $modified = [byte[]]$original.Clone()
            $modified[$modified.Length - 1] = [byte]($modified[$modified.Length - 1] -bxor 1)
            [IO.File]::WriteAllBytes($targetDll, $modified)
            if ((Invoke-AppVerifier -Executable $exe) -eq 0) {
                throw 'Tampered DLL erroneously accepted by app self-test'
            }
            $result.tampered_core_rejected = 'pass'
        } finally {
            [IO.File]::WriteAllBytes($targetDll, $original)
        }
        if ((Invoke-AppVerifier -Executable $exe) -ne 0) {
            throw 'Restored source-built DLL failed retest'
        }
    }
    if ((Test-Path -LiteralPath (Join-Path $qaRoaming 'OKUBI')) -or
        (Test-Path -LiteralPath (Join-Path $qaLocal 'OKUBI'))) {
        throw 'Unexpected profile access by no-GUI verifier'
    }
    Write-Host 'PASS: real OKUBI Windows EXE ran source-built DLL SHA/PE/license verifier.'
    if ($TamperProof) { Write-Host 'PASS: modified core rejected and original copy restored.' }
    Write-Host 'No games, saves, physical display or controller tested.'
    Write-Host 'STABLE RELEASE: NOT AUTHORIZED.'
    if (-not [string]::IsNullOrWhiteSpace($Report)) {
        $destination = [IO.Path]::GetFullPath($Report)
        [IO.Directory]::CreateDirectory((Split-Path -Parent $destination)) | Out-Null
        [IO.File]::WriteAllText($destination, ($result | ConvertTo-Json -Depth 5), [Text.Encoding]::UTF8)
    }
    exit 0
} catch {
    # Fail closed without printing any user-specific paths.
    [Console]::Error.WriteLine('BLOCKED: QA85 packaged Windows runtime self-test failed.')
    exit 1
} finally {
    $env:APPDATA = $originalAppdata
    $env:LOCALAPPDATA = $originalLocal
    # Workspaces are not automatically deleted to avoid touching any test saves.
}
