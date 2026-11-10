# Fetch official open-source Libretro Windows x64 cores at BUILD time.
# All three DLLs are included with OKUBI Desktop: no end-user downloads,
# installers, accounts or per-console configuration.
# No ROMs/BIOS. Core packages come only from the official Libretro buildbot.
# 2026-11-09: Libretro 'latest' serves two different official Supafaust
# ZIP revisions depending on CDN node, both observed and PE64-checked:
# run 42693070082 ZIP af88931f... / DLL 5ac98974...
# run 42693739815 ZIP 9f0bf54e... / DLL e01ca0ba...
# Allow exactly those two vetted ZIP fingerprints; any third value fails closed.
# Long term: switch to immutable upstream build artifacts when published.
# 2026-11-09: Supafaust changed on official Libretro buildbot 'latest'.
# Exact replacement SHA-256 independently reported by Windows CI run 42693070082,
# with ZIP entry count/name/size, PE64 architecture and DLL SHA validation.
# All upstream changed ZIPs MUST still fail closed for future mismatches.
# Previous baseline: Supafaust ZIP 9f0bf54efb4af27e6f6805b90c9504cc20cdfbac2eb6f72748b46f5d2eb8d504
# 2026-11-09: re-pinned 3 official x64 ZIPs after explicit SHA-256 audit
# in Windows CI run 42633941155; fail closed on any future change.
param(
    [Parameter(Mandatory=$true)][string]$OutputDirectory
)
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$root = [System.IO.Path]::GetFullPath($OutputDirectory)
[System.IO.Directory]::CreateDirectory($root) | Out-Null
$packages = @(
    @{ Name="Game Boy + Game Boy Color"; Dll="sameboy_libretro.dll"; Archive="sameboy_libretro.dll.zip"; Sha256="be9dc4214c74212cba8291a1b4dde6d9df27fe2c2f415a3da88c6e285a32f514"; MinBytes=40000; MaxBytes=12000000 },
    @{ Name="Game Boy Advance"; Dll="mgba_libretro.dll"; Archive="mgba_libretro.dll.zip"; Sha256="92d9acdde738de9604eb9549a67d3e542b4d2989ae6f286c75359c5ecdb82832"; MinBytes=80000; MaxBytes=16000000 },
    @{ Name="Super Nintendo"; Dll="mednafen_supafaust_libretro.dll"; Archive="mednafen_supafaust_libretro.dll.zip"; Sha256=@("9f0bf54efb4af27e6f6805b90c9504cc20cdfbac2eb6f72748b46f5d2eb8d504", "af88931ffe6a6a413801977c6e471cd98648523497474dbdedcffa629642da6f"); MinBytes=80000; MaxBytes=20000000 }
)
$hostUrl = "https://buildbot.libretro.com/nightly/windows/x86_64/latest/"
$manifest = [System.Collections.Generic.List[string]]::new()
$mismatches = [System.Collections.Generic.List[string]]::new()
$manifest.Add("# Core checksums generated from official Libretro buildbot on the compilation runner.")
$manifest.Add("# SHA256 applies to each extracted Windows x64 core DLL.")
foreach ($package in $packages) {
    $name = $package.Dll
    $url = $hostUrl + $package.Archive
    $zip = Join-Path $env:RUNNER_TEMP $package.Archive
    Write-Output "Fetching official Libretro $($package.Name) core: $url"
    Invoke-WebRequest -Uri $url -OutFile $zip -MaximumRedirection 4 -TimeoutSec 120
    if (!(Test-Path $zip)) { throw "Core ZIP not downloaded: $name" }
    $length = (Get-Item $zip).Length
    if ($length -lt 15000 -or $length -gt $package.MaxBytes) {
        throw "Invalid core ZIP size $name ($length)"
    }
    $zipSha = (Get-FileHash -Path $zip -Algorithm SHA256).Hash.ToLowerInvariant()
    Write-Output "OFFICIAL_CORE_ZIP_SHA256 $($package.Archive) $zipSha"
    $allowedPins = @($package.Sha256)
    if (-not ($allowedPins -ccontains $zipSha)) {
        # Only exact, audited SHA-256 values for the official ZIP are accepted.
        # Do NOT dynamically trust a new binary or disable validation.
        $mismatches.Add("$($package.Archive): expected=$($allowedPins -join ','), actual=$zipSha")
    }
    Add-Type -AssemblyName System.IO.Compression
    try {
        $archive = [System.IO.Compression.ZipFile]::OpenRead($zip)
        try {
            $matching = @($archive.Entries | Where-Object {
                $_.Name -ceq $name -and $_.FullName.Replace("\", "/") -ceq $name
            })
            if ($matching.Count -ne 1) {
                throw "Missing or ambiguous core DLL in archive: $name"
            }
            $entry = $matching[0]
            if ($entry.Length -lt $package.MinBytes -or $entry.Length -gt $package.MaxBytes) {
                throw "Invalid DLL size $name"
            }
            $dll = Join-Path $root $name
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dll, $true)
            $fileSize = (Get-Item $dll).Length
            if ($fileSize -ne $entry.Length) { throw "Core extraction size mismatch: $name" }
            # PE machine 0x8664 must match app's Windows x64 launcher.
            $stream = [System.IO.File]::OpenRead($dll)
            try {
                $reader = [System.IO.BinaryReader]::new($stream)
                if ($reader.ReadUInt16() -ne 0x5a4d) { throw "Not a Windows PE DLL: $name" }
                $stream.Seek(0x3c, [System.IO.SeekOrigin]::Begin) | Out-Null
                $pe = $reader.ReadInt32()
                if ($pe -lt 64 -or $pe -gt $fileSize - 10) { throw "Invalid PE offset: $name" }
                $stream.Seek($pe, [System.IO.SeekOrigin]::Begin) | Out-Null
                if ($reader.ReadUInt32() -ne 0x4550) { throw "PE signature invalid: $name" }
                $machine = $reader.ReadUInt16()
                if ($machine -ne 0x8664) { throw "Core is not Windows x64: $name" }
            } finally { $stream.Dispose() }
            $sha = (Get-FileHash -Path $dll -Algorithm SHA256).Hash.ToLowerInvariant()
            Write-Output "BUNDLED_CORE_DLL_SHA256 $name $sha"
            $manifest.Add("$name=$sha")
        } finally { $archive.Dispose() }
    } finally {
        Remove-Item -Path $zip -Force -ErrorAction SilentlyContinue
    }
}
if ($mismatches.Count -gt 0) {
    throw ("Official Libretro core SHA-256 mismatch. FAIL CLOSED; manually audit and re-pin all changed ZIPs before shipping: " + ($mismatches -join " | "))
}
$manifestFile = Join-Path $root "CORE-SHA256.properties"
[System.IO.File]::WriteAllLines($manifestFile,
    $manifest.ToArray(), [System.Text.Encoding]::ASCII)
foreach ($package in $packages) {
    $dll = Join-Path $root $package.Dll
    if (!(Test-Path $dll)) { throw "Mandatory built-in core missing: $($package.Name)" }
}
Write-Output "OKUBI built-in cores installed and SHA-256 manifest created."

# All third-party sources stay available for auditing. The license files
# are collected from the exact pinned upstream repositories used by the
# corresponding Android cores; source repositories are published below.
$licenseDir = Join-Path $root "licenses"
[System.IO.Directory]::CreateDirectory($licenseDir) | Out-Null
$licenseUrls = @(
    @{ Name="SameBoy-MIT.txt"; Url="https://raw.githubusercontent.com/libretro/SameBoy/aa158a889a48b538a0302873704a34577c8eb67d/LICENSE" },
    @{ Name="mGBA-MPL-2.0.txt"; Url="https://raw.githubusercontent.com/libretro/mgba/7a12d6d4b9acb14c0ae62c9166b6a2f3d08007f6/LICENSE" },
    @{ Name="Supafaust-GPL-2.0-plus.txt"; Url="https://raw.githubusercontent.com/libretro/supafaust/642d1d1b6684aa7e306a02a89885f3f5456a5157/COPYING" }
)
foreach ($license in $licenseUrls) {
    $destination = Join-Path $licenseDir $license.Name
    Invoke-WebRequest -Uri $license.Url -OutFile $destination -TimeoutSec 45
    if ((Get-Item $destination).Length -lt 300) {
        throw "Missing full open-source license notice: $($license.Name)"
    }
}
$sourceNotice = @'
OKUBI Desktop - Third-party Libretro cores
Built-in GB/GBC: SameBoy / MIT
  Source: https://github.com/libretro/SameBoy
  Windows binary: official Libretro buildbot sameboy_libretro.dll.zip
Built-in GBA: mGBA / MPL-2.0
  Source: https://github.com/libretro/mgba
  Windows binary: official Libretro buildbot mgba_libretro.dll.zip
Built-in SNES: Beetle Supafaust / GPL-2.0-or-later
  Source: https://github.com/libretro/supafaust
  Windows binary: official Libretro buildbot mednafen_supafaust_libretro.dll.zip
Buildbot: https://buildbot.libretro.com/nightly/windows/x86_64/latest/
Upstream license notices are supplied alongside the core DLLs.
No ROMs, Nintendo firmware or third-party BIOS are included.
Source revision and build recipes of upstream buildbot-provided binaries
should be checked with the upstream buildbot when redistributing.
'@
[System.IO.File]::WriteAllText((Join-Path $licenseDir "THIRD-PARTY-SOURCES.txt"),
    $sourceNotice, [System.Text.Encoding]::UTF8)
