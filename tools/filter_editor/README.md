# AnonChat Filter Editor

A graphical filter/tag editor for the **AnonChat** mod's config (profiles).

Solves a problem you can't work around in-game: **Minecraft's UI strips the §
character** from typed tags (a `StringUtil.filterText` safeguard). This editor
writes JSON in UTF-8 with a literal § — so tags like `Money§8` actually work.

> 🔒 **The main `chat.json` is protected** — the editor only lets you work on
> profiles from the `profiles/` folder. It never overwrites `chat.json`.

## Features

- 🗂 Dropdown scans only profiles from `%APPDATA%/AnonChatMC/profiles/`
  (the first profile loads automatically on startup)
- 📖 Parses files from `%APPDATA%/AnonChatMC/chatlog/` (the mod's daily logs)
  and shows **unique messages with an occurrence counter** (sorted by frequency)
- 📜 Also supports **raw Minecraft logs** (`*.log`, e.g. `latest.log`) — just
  pick the file in the file picker (opens the chatlog folder by default)
- ➕ Add a message from the log as an INCLUDE tag on the selected filter
  (preserves §; you can shorten the text in the dialog)
- ✏️ Manually add/remove tags, edit filter name, toggle options (hide, sound)
- 🌐 Bilingual interface (PL/EN) — detects system language, choice is **remembered**
- ⚠️ Warns about **unsaved changes** when closing the window (Save / Don't Save / Cancel)
- 💾 Optional backup on save — Yes/No/Cancel prompt (`chat.json.bak-<timestamp>`)
- ℹ️ Tag changes aren't saved automatically — click **Save** to write to disk

## Running (Python)

```bash
pip install -r requirements.txt
python main.py
```

The editor always reads profiles from `%APPDATA%/AnonChatMC/profiles/`.
If the folder is empty, the dropdown shows "—" — save a profile in-game first.

The main `chat.json` (the same folder the mod uses) is read-only —
it is never edited or overwritten.

## Building a standalone exe (no Python required for the end user)

```bash
pip install pyinstaller
pyinstaller --noconfirm --onefile --windowed --name AnonChatFilterEditor main.py
```

Resulting file: `dist/AnonChatFilterEditor.exe` — this can be distributed;
the user doesn't need Python installed.

## Tips

- A tag with § (e.g. `Money§8`) is **precise** — it only matches where the
  message has exactly that color code. Removing § gives a looser match.
- Filters work the same as in-game: a message matching an INCLUDE tag goes
  to that filter's tab. If **"Hide message"** is enabled on that filter, the
  message is routed only to that tab and removed from the main window,
  instead of appearing in both places.
- After saving, reload the config in-game (switch profiles back and forth,
  or use the reload key) so the mod picks up the changes.
