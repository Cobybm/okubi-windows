# OKUBI Phase 67 — SMART MAINTENANCE (Windows prototype)

## Interface
- Open via **Ctrl + Shift + M** inside OKUBI Desktop.
- The window asynchronously reads **only** the application's online cover-art cache from `OnlineCoverService.defaultDirectory()`.
- It shows how many generated images / negative lookup markers can be removed and their size.
- **LIMPIAR CACHÉ TEMPORAL** requires a separate Yes/No confirmation. It does not run automatically.
- Restart/download artwork as needed: missing cover images are retrieved again when the existing online artwork feature is used.
- The panel is unavailable while a game is active, to avoid interrupting gameplay.

## Strict selection
Eligible cached file names: SHA-256-style lowercase hash followed by `.png` or `.png.missing`, immediately within a recognized console directory below `%APPDATA%/OKUBI/cover-art-v1`. Unrecognized filenames (including manually placed covers) are never deleted. Symlinks, junctions and unexpected folder nesting fail closed. Only empty console directories may be removed.

## Separate legacy build maintenance
The Phase 66 `desktop/SAFE-CLEANUP.ps1` handles generated checkout build folders (preview or explicit `-Apply`). Phase 67 does **not** remove old ZIPs, GitHub Actions artifacts, branches, APKs, downloaded ROMs, save states, BIOS, emulator executables, or games.

## Tests and release status
- `DesktopMaintenanceServiceTest` covers missing cache, dry-run size, two generated entries, confirmed deletion, user artwork preservation, unchanged synthetic ROM/SRAM, rejected saves directory and symlink guard.
- `.github/workflows/okubi-maintenance-java-qa.yml` compiles all Java desktop sources plus shared save serializer and executes the synthetic test.
- Pending real Windows packaging and live UI verification. No stable release and no merge to `main`. No change to Android or VIDAS.
