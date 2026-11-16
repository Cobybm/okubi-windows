# OKUBI Phase 69 — SMART LIBRARY ENGINE (Windows)

## Scope
Previously `OkubiDesktop.renderGames()` created Swing cards, layout panels and `OnlineCoverArtwork` requests for every filtered game at once (up to 2000 references). Phase 69 changes the rendering strategy, not the library data model.

- **First batch:** up to 48 game cards, or enough to preserve the previously selected game.
- **Additional batches:** 48 cards at a time (rounded up to full grid rows) as the user scrolls near the bottom or presses CARGAR MÁS JUEGOS.
- **Controller/keyboard navigation:** selecting a game beyond the currently instantiated cards materializes the required rows first, then scrolls to the selected game.
- **Sorting, favorites, search, console filters, save paths and game-launch actions:** unchanged.
- **Downloaded covers:** fewer `OnlineCoverArtwork` widgets are constructed initially, so the existing cover service receives fewer initial requests. No artwork format, cache path or network setting is changed.
- **Tests:** `DesktopLibraryBatchTest` covers 0, 3, 48, 2000 games, selected index 400 and boundary conditions. CI compiles Desktop Java and runs legacy Phase 67/68 regressions.

## Limitations
Not full UI virtualization: previously created cards remain in memory as you scroll farther down. No measured memory or startup benchmark has yet been obtained. In this iteration the maximum initial load may exceed 48 if the selected game was deep in the library. GUI scrolling and XInput need Windows testing.

## Safety
No ROM, BIOS, SRAM, RTC, state, save-vault, personal artwork, PCSX2 profile, Android, VIDAS or user data deletion. This is a draft branch stacked on Phase68; `main` remains unchanged.
