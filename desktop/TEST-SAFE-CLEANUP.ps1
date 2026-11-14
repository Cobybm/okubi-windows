# Test cleanup against isolated synthetic files; NEVER user data.
$ErrorActionPreference = 'Stop'
$fixture = Join-Path ([System.IO.Path]::GetTempPath()) ("okubi-cleanup-qa-" + [guid]::NewGuid().ToString('N'))
try {
  $desktop = Join-Path $fixture 'desktop'
  $build = Join-Path $fixture 'build'
  New-Item -ItemType Directory -Force $desktop, (Join-Path $build 'desktop'), (Join-Path $build 'java-qa') | Out-Null
  Copy-Item (Join-Path $PSScriptRoot 'SAFE-CLEANUP.ps1') (Join-Path $desktop 'SAFE-CLEANUP.ps1')
  Set-Content (Join-Path $build 'desktop/temporary.bin') 'temporary build data'
  Set-Content (Join-Path $build 'java-qa/classes.tmp') 'temporary test data'
  Set-Content (Join-Path $fixture 'SAVED-GAME.srm') 'protected user data'
  $cleaner = Join-Path $desktop 'SAFE-CLEANUP.ps1'
  & $cleaner
  if (-not (Test-Path (Join-Path $build 'desktop/temporary.bin'))) { throw 'Preview deleted data!' }
  if (-not (Test-Path (Join-Path $build 'java-qa/classes.tmp'))) { throw 'Preview deleted QA!' }
  & $cleaner -Apply
  if (Test-Path (Join-Path $build 'desktop')) { throw 'Apply failed to clean desktop build' }
  if (Test-Path (Join-Path $build 'java-qa')) { throw 'Apply failed to clean java-qa build' }
  if ((Get-Content (Join-Path $fixture 'SAVED-GAME.srm') -Raw).Trim() -ne 'protected user data') { throw 'User save modified!' }

  # Attempt redirect through symlinked build parent. Safety stop must happen.
  $outside = Join-Path $fixture 'outside'
  New-Item -ItemType Directory -Force (Join-Path $outside 'desktop') | Out-Null
  Set-Content (Join-Path $outside 'desktop/never-delete.bin') 'outside data'
  New-Item -ItemType SymbolicLink -Path $build -Target $outside -ErrorAction Stop | Out-Null
  $blocked = $false
  try { & $cleaner -Apply } catch { $blocked = $true }
  if (-not $blocked) { throw 'Symlinked build parent was NOT blocked!' }
  if (-not (Test-Path (Join-Path $outside 'desktop/never-delete.bin'))) { throw 'Outside data was deleted!' }
  Write-Host 'PASS: dry-run, cleanup, save isolation and symlink guard'
} finally {
  if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force }
}
