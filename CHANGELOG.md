# Changelog

## [0.2.1] — 2026-07-17

### 🔧 Fixes

- **Chat log file** — now properly closed when the game shuts down (`ClientLifecycleEvents.CLIENT_STOPPING`)
- **Message repeat indicator** — `[×N]` prefix works correctly with the new font cache

### ✨ Improvements

- **Resize indicator** — yellow squares replaced with subtle white edge bars on the border
- **Font split cache** — message text is no longer re-wrapped every frame (better FPS with large chat history)

### ✨ Improvements

- **Resize indicator** — replaced yellow squares with subtle white edge bars. Less intrusive, easier to hit.
- **Font split cache** — message text is no longer re-wrapped every frame (improves FPS with large chat history)

### 🔧 Fixes

- **Chat log file** — now properly closed when the game shuts down (no more orphaned file handles)
- **Message repeat indicator** — `[×N]` prefix works correctly with the new font cache (regression fix)
