# OKUBI Phase 66 - Windows generated files cleanup
# DRY RUN by default. -Apply removes ONLY the two fixed build-output folders.
[CmdletBinding()]
param([switch]$Apply)
$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$build = Join-Path $root 'build'
$targets = @('build/desktop', 'build/java-qa')

function Assert-RealDirectory([string]$candidate) {
  if (-not (Test-Path -LiteralPath $candidate)) { return }
  $item = Get-Item -LiteralPath $candidate -Force
  if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0 -or $item.LinkType) {
    throw "Safety stop: symbolic link / junction detected: $candidate"
  }
  if (-not $item.PSIsContainer) { throw "Safety stop: expected directory: $candidate" }
}

Write-Host "OKUBI maintenance: $root"
Write-Host "Mode: $(if ($Apply) { 'APPLY' } else { 'PREVIEW ONLY' })"
# Check parents too. A linked 'build' directory could redirect an apparently safe path.
Assert-RealDirectory $root
Assert-RealDirectory $build
foreach ($relative in $targets) {
  $path = [System.IO.Path]::GetFullPath((Join-Path $root $relative))
  if (-not $path.StartsWith($root + [System.IO.Path]::DirectorySeparatorChar,
      [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Safety stop: target outside repository: $path"
  }
  if (-not (Test-Path -LiteralPath $path)) {
    Write-Host "Absent: $relative"; continue
  }
  Assert-RealDirectory $path
  # Reject any linked file/directory inside the target, before considering removal.
  $children = @(Get-ChildItem -LiteralPath $path -Force -Recurse -ErrorAction Stop)
  foreach ($child in $children) {
    if (($child.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0 -or $child.LinkType) {
      throw "Safety stop: nested symbolic link / junction: $($child.FullName)"
    }
  }
  $files = @($children | Where-Object { -not $_.PSIsContainer })
  $bytes = ($files | Measure-Object -Property Length -Sum).Sum
  Write-Host ("{0}: {1} files; {2:N2} MiB" -f $relative, $files.Count, ($bytes / 1MB))
  if ($Apply) {
    Remove-Item -LiteralPath $path -Recurse -Force -ErrorAction Stop
    Write-Host "Deleted generated build output: $relative"
  }
}
Write-Host "Untouched: user data, saves, ROMs, BIOS, AppData, Android, source, other branches."
