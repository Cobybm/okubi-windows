# OKUBI 遊 Phase 83 - local Windows QA verification, no installer or save migration.
# Tested for Windows PowerShell 5.1 / PowerShell 7; requires no Python or admin.
# -Launch must be explicitly selected; each run creates a NEW QA-only profile.
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$Archive,
    [Parameter(Mandatory=$true)][string]$Checksum,
    [switch]$Launch,
    [string]$Report
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$archiveName = 'OKUBI-Desktop-Windows-x64-PHASE81-SOURCE-QA.zip'
$identity = 'OKUBI-QA83'
$allCoreDlls = @('sameboy_libretro.dll', 'mgba_libretro.dll',
                  'mednafen_supafaust_libretro.dll')
$required = @(
  'OKUBI Desktop/OKUBI Desktop.exe',
  'OKUBI Desktop/app/okubi-desktop.jar',
  'OKUBI Desktop/app/RUN-QA57-ISOLATED.cmd',
  'OKUBI Desktop/app/cores/CORE-SHA256.properties',
  'OKUBI Desktop/app/cores/source-packages/SOURCE-TO-BINARY-MANIFEST.json'
)
foreach ($name in $allCoreDlls) {
    $required += ('OKUBI Desktop/app/cores/' + $name)
}
$originalLocal = $env:LOCALAPPDATA
try {
    $package = Get-Item -LiteralPath $Archive -ErrorAction Stop
    $manifest = Get-Item -LiteralPath $Checksum -ErrorAction Stop
    if ($package.PSIsContainer -or $manifest.PSIsContainer -or
        $package.Name -cne $archiveName -or $package.Length -lt 1000000 -or
        $package.Length -gt 1000000000 -or $manifest.Length -gt 300) {
        throw 'Wrong QA package/manifest file or size'
    }
    $lines = @([IO.File]::ReadAllLines($manifest.FullName))
    if ($lines.Count -ne 1 -or
        $lines[0] -cnotmatch '^([a-f0-9]{64})\s+\*?OKUBI-Desktop-Windows-x64-PHASE81-SOURCE-QA\.zip$') {
        throw 'SHA256SUMS entry is missing or malformed'
    }
    $expectedSha = $Matches[1]
    $actualSha = (Get-FileHash -LiteralPath $package.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualSha -cne $expectedSha) {
        throw 'QA ZIP SHA256 does not match the GitHub Actions checksum'
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($package.FullName)
    try {
        if ($zip.Entries.Count -gt 12000) { throw 'Archive exceeds safe file limit' }
        $seen = [Collections.Generic.HashSet[string]]::new(
            [StringComparer]::OrdinalIgnoreCase)
        foreach ($entry in $zip.Entries) {
            $path = $entry.FullName
            if ([string]::IsNullOrWhiteSpace($path) -or
                $path.StartsWith('/') -or $path.Contains('\') -or
                $path.Contains(':') -or
                $path.Contains('../') -or $path.StartsWith('../') -or
                $path.EndsWith('/..') -or
                ($path -match '(^|/)\.(/|$)') -or
                ($path -match '\.(gba|gbc|gb|sfc|smc|iso|chd|sav|srm|rtc|state|okubisave|jks|p12|key)$')) {
                throw 'Unsafe or private file in QA ZIP'
            }
            if ($path.EndsWith('/')) { continue }
            if (-not $seen.Add($path)) { throw 'Duplicate ZIP member' }
        }
        foreach ($name in $required) {
            if (-not $seen.Contains($name)) {
                throw 'Mandatory Windows QA component missing'
            }
        }
        $launcherInfo = $zip.GetEntry('OKUBI Desktop/app/RUN-QA57-ISOLATED.cmd')
        $reader = [IO.StreamReader]::new($launcherInfo.Open())
        try { $launcherText = $reader.ReadToEnd() }
        finally { $reader.Dispose() }
        if ($launcherText -notmatch 'OKUBI-QA57' -or
            $launcherText -notmatch 'set "APPDATA=%QA_ROOT%\\Roaming"' -or
            $launcherText -notmatch 'if not defined LOCALAPPDATA') {
            throw 'Test launcher does not guarantee an isolated profile'
        }
        $summary = [ordered]@{
            phase = 83
            platform = 'windows'
            operation = 'local_read_only_zip_verification'
            package_integrity_sha256 = 'pass'
            source_built_cores_present = 3
            qa_launcher_isolated = $true
            launched_on_real_pc = $false
            physical_gameplay_or_save_test_completed = $false
            original_app_or_saves_modified_by_verifier = $false
            public_release_authorized = $false
        }
    } finally { $zip.Dispose() }
    Write-Host 'PASS: OKUBI Windows Phase81 source-built QA ZIP integrity and isolation.'
    Write-Host ('SHA256: ' + $actualSha)
    if ($Launch) {
        # A new randomly named workspace protects both the canonical profile
        # AND all earlier QA57 save files; nothing is deleted afterward.
        $workspace = Join-Path ([IO.Path]::GetTempPath()) (
            'OKUBI-QA83-' + [guid]::NewGuid().ToString('N'))
        [IO.Directory]::CreateDirectory($workspace) | Out-Null
        $unpacked = Join-Path $workspace 'unpacked'
        [IO.Compression.ZipFile]::ExtractToDirectory($package.FullName, $unpacked)
        $launcher = Join-Path $unpacked 'OKUBI Desktop\app\RUN-QA57-ISOLATED.cmd'
        if (-not (Test-Path -LiteralPath $launcher)) {
            throw 'Isolated QA launcher missing after extraction'
        }
        $env:LOCALAPPDATA = Join-Path $workspace 'local-profile'
        [IO.Directory]::CreateDirectory($env:LOCALAPPDATA) | Out-Null
        Write-Host 'Opening OKUBI in a brand-new QA83 sandbox.'
        Write-Host 'Only use a new TEST game/save. Do not restore your original saves.'
        & cmd.exe /D /C ('"' + $launcher + '"')
        $code = $LASTEXITCODE
        if ($code -ne 0) {
            throw 'Windows QA application exited with a nonzero status'
        }
        $summary.launched_on_real_pc = $true
        $summary.operation = 'local_sandbox_qa_launcher'
        Write-Host 'OKUBI closed. Local QA workspace is retained so any test saves stay separate.'
        Write-Host 'Manual checks still needed: Inicio, Juegos, CINEMA, GB/GBC/GBA/SNES, sound, controller, save/reopen.'
        Write-Host 'Never submit private ROMs, saves or QA workspace to GitHub.'
    } else {
        Write-Host 'No app launched and no folders extracted. Use -Launch ONLY for isolated physical QA.'
    }
    if (-not [string]::IsNullOrWhiteSpace($Report)) {
        $reportPath = [IO.Path]::GetFullPath($Report)
        $reportParent = [IO.Path]::GetDirectoryName($reportPath)
        [IO.Directory]::CreateDirectory($reportParent) | Out-Null
        $summary | ConvertTo-Json -Depth 3 |
            [IO.File]::WriteAllText($reportPath, $_, [Text.Encoding]::UTF8)
    }
    Write-Host 'Stable release authorized: NO.'
    exit 0
} catch {
    # Do not print private paths, user names, ROMs or file contents.
    [Console]::Error.WriteLine('BLOCKED: OKUBI Phase83 local QA integrity or sandbox check failed.')
    exit 1
} finally {
    $env:LOCALAPPDATA = $originalLocal
}
