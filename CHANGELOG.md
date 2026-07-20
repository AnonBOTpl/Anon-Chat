# Changelog

## [0.2.2] — 2026-07-21

### ✨ New Features

- **Clickable messages** (26.1.2 + 26.2) — click on player names, commands, links, and other clickable chat components. Hover over text to see tooltips.
- **Auto-scroll on chat close** — when you close the chat, scroll automatically returns to the latest messages.
- **Show chat on all screens** (1.21.x) — new option in Settings to keep chat visible while inventory or other GUIs are open.
- **Player name highlight (Ping)** — configurable color + sound effect when your name appears in chat. Per-tab font override support.

### 🔧 Fixes

- **Filter logic refactored** — removed confusing "Hide Message" checkbox. Include criteria are now checked first, then exclude. Cleaner code, same behavior.
- **Spam Cleaner module removed** — experimental feature didn't work reliably with server color codes. Removed entirely.

## [0.2.1] — 2026-07-17

### 🔧 Fixes

- **Chat log file** — now properly closed when the game shuts down (no more orphaned file handles)
- **Message repeat indicator** — `[×N]` prefix works correctly with the new font cache

### ✨ Improvements

- **Resize indicator** — yellow squares replaced with subtle white edge bars on the border. Less intrusive, easier to hit.
- **Font split cache** — message text is no longer re-wrapped every frame (better FPS with large chat history)
