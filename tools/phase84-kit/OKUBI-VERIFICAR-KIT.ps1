# OKUBI Phase 84: offline kit integrity verifier; Windows PowerShell 5.1.
# Read-only: never installs, uninstalls, launches apps, opens games or reads saves.
[CmdletBinding()]
param(
    [ValidateSet('Both', 'Windows', 'Android')][string]$Mode = 'Both',
    [string]$KitRoot,
    [string]$Report
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if ([string]::IsNullOrWhiteSpace($KitRoot)) {
    if (-not [string]::IsNullOrWhiteSpace($env:OKUBI_QA84_ROOT)) {
        # cmd.exe may escape a final backslash when passed in a quoted argv;
        # environment variables preserve the full path unchanged.
        $KitRoot = $env:OKUBI_QA84_ROOT
    } else {
        $KitRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
    }
}
$kitRootFull = [IO.Path]::GetFullPath($KitRoot)
function Assert-ArtifactSha256 {
    param([string]$Subfolder, [string]$Filename, [long]$MinimumSize, [long]$MaximumSize)
    $folder = Join-Path $kitRootFull $Subfolder
    $file = Join-Path $folder $Filename
    $sumFile = Join-Path $folder 'SHA256SUMS.txt'
    if (-not (Test-Path -LiteralPath $file -PathType Leaf) -or
        -not (Test-Path -LiteralPath $sumFile -PathType Leaf)) {
        throw 'Missing approved QA package or checksum file'
    }
    $item = Get-Item -LiteralPath $file
    $manifest = Get-Item -LiteralPath $sumFile
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -or
        ($manifest.Attributes -band [IO.FileAttributes]::ReparsePoint) -or
        $item.Length -lt $MinimumSize -or $item.Length -gt $MaximumSize -or
        $manifest.Length -gt 300) {
        throw 'Unsafe QA package or checksum size'
    }
    $lines = @([IO.File]::ReadAllLines($sumFile))
    $regex = '^([0-9a-f]{64})\s+\*?' + [regex]::Escape($Filename) + '$'
    if ($lines.Count -ne 1 -or $lines[0] -cnotmatch $regex) {
        throw 'Bad checksum name or manifest contents'
    }
    $expected = $Matches[1]
    $actual = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -cne $expected) {
        throw 'Artifact checksum does not match approved QA manifest'
    }
    return $actual
}
$phase84Step = 'KIT_ROOT'
try {
    if (-not (Test-Path -LiteralPath $kitRootFull -PathType Container)) {
        throw 'QA kit directory not available'
    }
    $results = [ordered]@{
        phase = 84
        operation = 'read_only_one_click_qa_integrity'
        windows_qa_zip_integrity = 'not_requested'
        android_qa_apk_integrity = 'not_requested'
        android_certificate_rechecked_locally = $false
        real_device_testing_completed = $false
        cross_save_physical_test_completed = $false
        previous_windows_or_android_installations_modified = $false
        private_roms_or_saves_accessed = $false
        stable_release_authorized = $false
    }
    if ($Mode -ne 'Android') {
        $phase84Step = 'WINDOWS_PACKAGE_CHECKSUM'
        $windowsZip = 'OKUBI-Desktop-Windows-x64-PHASE81-SOURCE-QA.zip'
        if ($env:CI -eq 'true') {
            # CI-safe booleans; never print actual filesystem paths or user names.
            $expectedFolder = [string]::Equals(
                [IO.Path]::GetFileName($kitRootFull.TrimEnd([char[]]@('\','/'))),
                'OKUBI-QA84-TEST-KIT', [StringComparison]::OrdinalIgnoreCase)
            Write-Host ('CI QA84 root leaf is expected: ' + $expectedFolder)
            Write-Host ('CI QA84 Windows ZIP exists: ' +
                (Test-Path -LiteralPath (Join-Path $kitRootFull ('Windows\' + $windowsZip)) -PathType Leaf))
            Write-Host ('CI QA84 Windows checksum exists: ' +
                (Test-Path -LiteralPath (Join-Path $kitRootFull 'Windows\SHA256SUMS.txt') -PathType Leaf))
        }
        $winSha = Assert-ArtifactSha256 -Subfolder Windows -Filename $windowsZip -MinimumSize 1000000 -MaximumSize 1000000000
        $windowsTool = Join-Path $kitRootFull 'tools\okubi_phase83_windows_local_qa.ps1'
        if (-not (Test-Path -LiteralPath $windowsTool -PathType Leaf)) {
            throw 'Isolated Windows safety inspector unavailable'
        }
        $phase84Step = 'WINDOWS_INTERNAL_CHECK'
        $argsForInspector = @(
            '-NoProfile', '-ExecutionPolicy', 'Bypass',
            '-File', $windowsTool,
            '-Archive', (Join-Path $kitRootFull ('Windows\' + $windowsZip)),
            '-Checksum', (Join-Path $kitRootFull 'Windows\SHA256SUMS.txt')
        )
        & powershell.exe @argsForInspector
        if ($LASTEXITCODE -ne 0) {
            throw 'Isolated Windows ZIP structure or launcher rejected'
        }
        $results.windows_qa_zip_integrity = 'pass'
        Write-Host 'PASS: Windows x64 SHA256, 3 source-built cores and isolated launcher.'
    }
    if ($Mode -ne 'Windows') {
        $phase84Step = 'ANDROID_PACKAGE_CHECKSUM'
        $apkName = 'OKUBI-Android-Migration-QA57.apk'
        $apkSha = Assert-ArtifactSha256 -Subfolder Android -Filename $apkName -MinimumSize 100000 -MaximumSize 200000000
        $phase84Step = 'ANDROID_APK_ARCHIVE_CHECK'
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $zip = [IO.Compression.ZipFile]::OpenRead(
            (Join-Path $kitRootFull ('Android\' + $apkName)))
        try {
            $manifest = $zip.GetEntry('AndroidManifest.xml')
            $classes = $zip.GetEntry('classes.dex')
            if ($null -eq $manifest -or $null -eq $classes -or
                $manifest.Length -lt 20 -or $classes.Length -lt 20) {
                throw 'Incomplete Android QA application contents'
            }
            if ($zip.Entries.Count -gt 12000) {
                throw 'Excessive APK archive member count'
            }
            foreach ($abi in @('arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64')) {
                foreach ($core in @('sameboy', 'mgba', 'supafaust')) {
                    if ($null -eq $zip.GetEntry(
                        ('lib/' + $abi + '/lib' + $core + '_libretro_android.so'))) {
                        throw 'Android QA is missing required retro core/architecture'
                    }
                }
            }
        } finally {
            $zip.Dispose()
        }
        $results.android_qa_apk_integrity = 'pass'
        Write-Host 'PASS: Android QA57 APK SHA256 and 4-ABI core structure.'
        Write-Host 'NOTE: This verifier does not independently check the Android certificate.'
        Write-Host 'Android signature/package was checked in GitHub Actions Phase 82.'
        Write-Host 'Never uninstall canonical Android or older QA57 containing saves.'
    }
    $phase84Step = 'WRITE_REPORT'
    if (-not [string]::IsNullOrWhiteSpace($Report)) {
        $target = [IO.Path]::GetFullPath($Report)
        $parent = [IO.Path]::GetDirectoryName($target)
        [IO.Directory]::CreateDirectory($parent) | Out-Null
        $json = $results | ConvertTo-Json -Depth 3
        [IO.File]::WriteAllText($target, $json, [Text.Encoding]::UTF8)
    }
    Write-Host 'PASS: requested downloaded QA kit components intact.'
    Write-Host 'No application was installed, launched, uninstalled or upgraded.'
    Write-Host 'PUBLIC RELEASE AUTHORIZED: NO.'
    exit 0
} catch {
    $reason = 'unidentified'
    $safeReasons = @(
        'Missing approved QA package or checksum file',
        'Unsafe QA package or checksum size',
        'Bad checksum name or manifest contents',
        'Artifact checksum does not match approved QA manifest',
        'Isolated Windows safety inspector unavailable',
        'Isolated Windows ZIP structure or launcher rejected'
    )
    if ($safeReasons -contains $_.Exception.Message) {
        $reason = $_.Exception.Message
    }
    [Console]::Error.WriteLine(
        'BLOCKED: Phase84 QA kit integrity rejected (' + $phase84Step + '): ' + $reason)
    exit 1
}
