# Changelog

## [0.2.3] — 2026-07-22

### 🔧 Fixes

- **Profile overwrite bug (critical)** — deleting windows/tabs while a profile was loaded no longer overwrites the profile file with the empty state. Profiles now only change when you explicitly click "Save".
- **Message clearing on new window** — creating a new window no longer clears messages in all existing windows.
- **Hide Message option re-added** — per-filter "Hide Message" checkbox now actually works: messages matched by the filter are hidden from display while the filter still processes routing correctly.
- **Exclude Tags UI removed** — simplified filter editor by removing the rarely-used Exclude Tags section.

### ✨ Features

- **Window position lock** — click the padlock icon on the title bar to lock/unlock window position and size. Locked windows cannot be dragged or resized.
- **Resize indicator** — yellow squares replaced with subtle white edge bars.

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
