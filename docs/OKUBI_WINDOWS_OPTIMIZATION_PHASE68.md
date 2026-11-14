# OKUBI Phase 68 — Windows library responsiveness

The Desktop search box previously rebuilt the full card grid on *every* document edit, causing unnecessary UI allocations when typing quickly. Phase 68 queues one search refresh after 150 milliseconds of inactivity on the Swing event thread.

- Keeps the existing sorting, categories, favorites, selected ROM, cover art and gamepad support.
- Category changes cancel pending search work and redraw immediately.
- Application exit stops the pending timer.
- `DesktopSearchRefreshTest`: a burst of 20 text edits produces one redraw, while a category selection redraws immediately and cancels stale work.
- CI compiles Desktop Java source and runs the Phase 68 search test plus Phase 67 safe-maintenance regression.

No per-ROM save data, application settings, emulators, Android assets or VIDAS files are changed. No claims of measured FPS/startup-time improvements. Real Windows GUI testing and full Windows packaging are still required before merge.
