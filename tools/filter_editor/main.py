#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AnonChat Filter Editor
======================
Edytor filtrów/tagów dla configu moda AnonChat (profile).

Funkcje:
  - Skanuje profile z folderu profiles/ (%APPDATA%/AnonChatMC/profiles)
  - Główny chat.json jest CHRONIONY — nie można go edytować ani nadpisać
  - Analizuje pliki chatlog (dzienne logi moda) i pokazuje unikalne
    wiadomości z licznikiem wystąpień
  - Pozwala dodawać wiadomości z loga jako tagi do filtrów
    (z zachowaniem znaku sekcji § — czego nie umożliwia UI w grze)
  - Dodawanie/usuwanie tagów include, edycja nazwy filtra
  - Dwujęzyczny interfejs (PL/EN) — automatycznie wykrywa język systemu,
    można zmienić w przełączniku w górnym pasku; wybór jest zapamiętywany
  - Domyślny folder chatloga: %APPDATA%/AnonChatMC/chatlog (+ obsługa *.log)
  - Okienko ostrzegawcze o niezapisanych zmianach przy zamykaniu
  - Opcjonalny backup przy zapisie (pytanie Tak/Nie/Anuluj)
  - Zapis w UTF-8 (ensure_ascii=False)

Budowanie standalone exe (PyInstaller):
    pip install customtkinter pyinstaller
    pyinstaller --noconfirm --onefile --windowed --name AnonChatFilterEditor main.py
"""

import json
import locale
import os
import re
import datetime
import shutil
import tkinter as tk
from tkinter import ttk, filedialog, messagebox

import customtkinter as ctk

# ── Stałe ──────────────────────────────────────────────────────────────
APP_TITLE = "AnonChat Filter Editor"
SECTION_PATTERN = re.compile(r"\u00a7[0-9a-fk-or]", re.IGNORECASE)
TIMESTAMP_PATTERN = re.compile(r"^\s*\[\d{2}:\d{2}:\d{2}\]\s*")
BASE_DIR = os.path.join(
    os.environ.get("APPDATA", os.path.expanduser("~")), "AnonChatMC"
)
DEFAULT_CONFIG = os.path.join(BASE_DIR, "chat.json")
PROFILES_DIR = os.path.join(BASE_DIR, "profiles")
CHATLOG_DIR = os.path.join(BASE_DIR, "chatlog")
SETTINGS_PATH = os.path.join(BASE_DIR, "filter_editor.json")

TAG_COLORS = {
    "include": ("#2e7d32", "#a5d6a7"),
    "exclude": ("#c62828", "#ef9a9a"),
}


# ── Tłumaczenia ────────────────────────────────────────────────────────
LANG = {
    "en": {
        "profile": "🗂  Profile:",
        "lock_label": "🔒 chat.json is protected — you can only edit profiles",
        "load_chatlog": "📖 Load chatlog…",
        "save": "💾 Save",
        "language": "Language:",
        "tree_title": "🧩 Windows / Tabs / Filters",
        "messages_title": "📜 Log messages (unique + counter)",
        "search": "🔍 Search…",
        "msg_column": "Message (color codes removed)",
        "add_msg_tag": "➕ Add selected message to tags…",
        "filter_title": "⚙️ Filter",
        "no_filter_hint": "Select a filter in the tree\nto edit its tags.",
        "filter_name": "Filter name:",
        "include_section": "INCLUDE tags (show / capture)",
        "exclude_section": "EXCLUDE tags (hide)",
        "manual_tag": "Add tag manually:",
        "manual_tag_ph": "e.g. VOTE",
        "hide_message": "Hide message",
        "play_sound": "Play sound",
        "delete_filter": "🗑 Delete filter",
        "empty": "  (empty)",
        "no_filters": "  (no filters)",
        "window": "🪟 Window %d",
        "main": " (MAIN)",
        "tab": "📑 %s [%s]",
        "unnamed": "Unnamed",
        "filter": "Filter",
        "tags_count": "tag(s)",
        "tag_dialog_title": "Add as tag",
        "tag_dialog_label": "Tag (you can shorten / edit):",
        "tag_dialog_hint": "💡 Tip: the text contains § codes — a tag with § is precise (matches only where that color occurs). Remove § to match more loosely.",
        "include_btn": "➕ INCLUDE",
        "exclude_btn": "➖ EXCLUDE",
        "cancel": "Cancel",
        "err_title": "Error",
        "load_config_err": "Could not load config:\n%s",
        "load_chatlog_err": "Could not load chatlog:\n%s",
        "not_config_err": "File does not look like an AnonChat config (missing 'windows' key).",
        "loaded": "Loaded: %s",
        "no_profiles": "No profiles — save a profile in-game (config → profiles) to start",
        "info_title": "Info",
        "select_filter_first": "First select a filter in the tree.",
        "delete_filter_title": "Delete filter",
        "delete_filter_msg": "Delete this filter?",
        "chatlog_dialog_title": "Select a chatlog file (or load all in folder)",
        "chatlog_filter": "Chat logs",
        "mc_log_filter": "Minecraft logs",
        "all_files": "All files",
        "chatlog_status": "Chatlog: %s — %d unique messages",
        "select_message_first": "First select a message from the list.",
        "no_config": "No config loaded.",
        "protect_title": "Protection",
        "protect_msg": "The main chat.json is protected — cannot overwrite.\nEdit only profiles from the profiles/ folder.",
        "save_title": "Save",
        "backup_ask": "Create a backup before saving?\n\nYes → backup + save\nNo → save without backup\nCancel → do nothing",
        "save_err": "Could not save:\n%s",
        "saved": "Saved.",
        "backup_info": " Backup: %s",
        "no_backup": " (no backup)",
        "reload_hint": "\n\nIn-game: Config → Profiles → click ▶ Reload,\nto apply changes without restarting the game.",
        "saved_title": "Saved",
        "unsaved_title": "Unsaved changes",
        "unsaved_msg": "You have unsaved changes.\nSave before closing?",
    },
    "pl": {
        "profile": "🗂  Profil:",
        "lock_label": "🔒 chat.json chroniony — edytujesz tylko profile",
        "load_chatlog": "📖 Wczytaj chatlog…",
        "save": "💾 Zapisz",
        "language": "Język:",
        "tree_title": "🧩 Okna / Taby / Filtry",
        "messages_title": "📜 Wiadomości z loga (unikalne + licznik)",
        "search": "🔍 Szukaj…",
        "msg_column": "Wiadomość (kody kolorów usunięte)",
        "add_msg_tag": "➕ Dodaj zaznaczoną wiadomość do tagów…",
        "filter_title": "⚙️ Filtr",
        "no_filter_hint": "Wybierz filtr w drzewie,\naby edytować jego tagi.",
        "filter_name": "Nazwa filtra:",
        "include_section": "Tagi INCLUDE (pokaż / przechwyć)",
        "exclude_section": "Tagi EXCLUDE (ukryj)",
        "manual_tag": "Dodaj tag ręcznie:",
        "manual_tag_ph": "np. VOTE",
        "hide_message": "Ukryj wiadomość",
        "play_sound": "Odtwórz dźwięk",
        "delete_filter": "🗑 Usuń filtr",
        "empty": "  (brak)",
        "no_filters": "  (brak filtrów)",
        "window": "🪟 Okno %d",
        "main": " (MAIN)",
        "tab": "📑 %s [%s]",
        "unnamed": "Bez nazwy",
        "filter": "Filtr",
        "tags_count": "tagów",
        "tag_dialog_title": "Dodaj jako tag",
        "tag_dialog_label": "Tag (możesz skrócić / edytować):",
        "tag_dialog_hint": "💡 Wskazówka: tekst zawiera kody § — tag z § jest precyzyjny (pasuje tylko tam, gdzie ten kolor występuje). Usuń §, aby tag pasował luźniej.",
        "include_btn": "➕ INCLUDE",
        "exclude_btn": "➖ EXCLUDE",
        "cancel": "Anuluj",
        "err_title": "Błąd",
        "load_config_err": "Nie udało się wczytać configu:\n%s",
        "load_chatlog_err": "Nie udało się wczytać chatloga:\n%s",
        "not_config_err": "Plik nie wygląda na config AnonChat (brak klucza 'windows').",
        "loaded": "Wczytano: %s",
        "no_profiles": "Brak profili — zapisz profil w grze (config → profiles), aby zacząć",
        "info_title": "Info",
        "select_filter_first": "Najpierw wybierz filtr w drzewie.",
        "delete_filter_title": "Usuń filtr",
        "delete_filter_msg": "Na pewno usunąć ten filtr?",
        "chatlog_dialog_title": "Wybierz plik chatloga (lub wczytaj wszystkie w folderze)",
        "chatlog_filter": "Chatlogi",
        "mc_log_filter": "Logi Minecrafta",
        "all_files": "Wszystkie pliki",
        "chatlog_status": "Chatlog: %s — %d unikalnych wiadomości",
        "select_message_first": "Zaznacz najpierw wiadomość na liście.",
        "no_config": "Brak wczytanego configu.",
        "protect_title": "Ochrona",
        "protect_msg": "Główny chat.json jest chroniony — nie można go nadpisać.\nEdytuj wyłącznie profile z folderu profiles/.",
        "save_title": "Zapis",
        "backup_ask": "Zrobić kopię zapasową przed zapisem?\n\nTak → backup + zapis\nNie → zapis bez backupu\nAnuluj → nic nie zapisuję",
        "save_err": "Nie udało się zapisać:\n%s",
        "saved": "Zapisano.",
        "backup_info": " Backup: %s",
        "no_backup": " (bez backupu)",
        "reload_hint": "\n\nW grze: Config → Profiles → kliknij ▶ Reload,\naby załadować zmiany bez restartu gry.",
        "saved_title": "Zapisano",
        "unsaved_title": "Niezapisane zmiany",
        "unsaved_msg": "Masz niezapisane zmiany.\nZapisać przed zamknięciem?",
    },
}

LANG_NAMES = {"en": "English", "pl": "Polski"}


def detect_system_language() -> str:
    """Zwraca 'pl', gdy język systemu to polski, w przeciwnym razie 'en'.

    Bezpieczny domyślny język: angielski (gdy wykrywanie zawiedzie).
    Nie używa deprecated locale.getdefaultlocale().
    """
    # POSIX / macOS — zmienne środowiskowe locale (LC_ALL, LC_MESSAGES, LANG)
    for var in ("LC_ALL", "LC_MESSAGES", "LANG"):
        val = os.environ.get(var, "")
        if val and val.lower().startswith("pl"):
            return "pl"
    # POSIX / macOS — bieżące locale
    try:
        lc, _enc = locale.getlocale()
        if lc and lc.lower().startswith("pl"):
            return "pl"
    except Exception:
        pass
    # Windows — GetUserDefaultUILanguage (0x0415 = pl-PL)
    try:
        import ctypes
        lang_id = ctypes.windll.kernel32.GetUserDefaultUILanguage()
        if lang_id == 0x0415:
            return "pl"
    except Exception:
        pass
    # Bezpieczny domyślny język: angielski
    return "en"


def load_saved_lang() -> str:
    """Wczytuje zapisany język z filter_editor.json (jeśli istnieje)."""
    try:
        with open(SETTINGS_PATH, encoding="utf-8") as f:
            data = json.load(f)
        if isinstance(data, dict) and data.get("lang") in LANG:
            return data["lang"]
    except (OSError, json.JSONDecodeError, ValueError):
        pass
    return ""


def save_lang(lang: str) -> None:
    """Zapisuje wybrany język do filter_editor.json (obok chat.json)."""
    try:
        os.makedirs(BASE_DIR, exist_ok=True)
        data = {}
        if os.path.exists(SETTINGS_PATH):
            with open(SETTINGS_PATH, encoding="utf-8") as f:
                data = json.load(f)
        data["lang"] = lang
        with open(SETTINGS_PATH, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    except (OSError, json.JSONDecodeError, ValueError):
        pass


current_lang = load_saved_lang() or detect_system_language()


def tr(key: str, *args) -> str:
    """Zwraca tłumaczenie klucza w bieżącym języku (z opcjonalnym % formattingiem)."""
    text = LANG.get(current_lang, LANG["en"]).get(key)
    if text is None:
        text = LANG["en"].get(key, key)
    if args:
        try:
            text = text % args
        except (TypeError, ValueError):
            pass
    return text


# ── Helpers ────────────────────────────────────────────────────────────
def clean_mc(text: str) -> str:
    """Usuwa kody kolorów Minecraft (§x) z tekstu."""
    return SECTION_PATTERN.sub("", text or "").strip()


def parse_log_line(line: str) -> str:
    """Czyści linię z chatloga: usuwa znacznik czasu i kody kolorów."""
    line = (line or "").rstrip("\r\n")
    line = TIMESTAMP_PATTERN.sub("", line)
    return clean_mc(line)


def profile_name(path: str) -> str:
    """Zwraca nazwę profilu z pełnej ścieżki (bez .json)."""
    base = os.path.basename(path)
    return base[:-5] if base.endswith(".json") else base


# ── Aplikacja ──────────────────────────────────────────────────────────
class FilterEditorApp(ctk.CTk):
    def __init__(self):
        super().__init__()
        ctk.set_appearance_mode("dark")
        ctk.set_default_color_theme("blue")

        self.title(APP_TITLE)
        self.geometry("1280x760")
        self.minsize(1000, 620)

        # Stan
        self.config_path = None
        self.config_data = None          # dict chat.json
        self.messages = []               # [(cleaned, raw, count)]
        self.selected_filter = None      # dict filtra
        self._sel_path = None            # ścieżka do filtra w config_data
        self._msg_raw_by_iid = {}        # iid -> surowy tekst wiadomości
        self.dirty = False               # czy są niezapisane zmiany

        self.protocol("WM_DELETE_WINDOW", self._on_close)
        self._build_ui()
        self._setup_tree_style()
        self._load_initial_config()

    # ── UI ─────────────────────────────────────────────────────────
    def _build_ui(self):
        self.grid_columnconfigure(0, weight=1)
        self.grid_rowconfigure(1, weight=1)

        # Górny pasek
        top = ctk.CTkFrame(self, corner_radius=0)
        top.grid(row=0, column=0, sticky="ew")
        top.grid_columnconfigure(1, weight=1)

        ctk.CTkLabel(top, text=tr("profile"), font=ctk.CTkFont(size=13, weight="bold")).grid(
            row=0, column=0, padx=(12, 6), pady=10)
        self.profile_menu = ctk.CTkOptionMenu(top, values=["—"], width=220,
                                              command=self._on_profile_selected)
        self.profile_menu.grid(row=0, column=1, padx=6, pady=10)
        ctk.CTkLabel(top, text=tr("lock_label"),
                     text_color="#888888", font=ctk.CTkFont(size=12)).grid(
            row=0, column=2, padx=6, pady=10)
        ctk.CTkButton(top, text=tr("load_chatlog"), width=150,
                      command=self._open_chatlog_dialog).grid(row=0, column=3, padx=6, pady=10)
        ctk.CTkButton(top, text=tr("save"), width=120, fg_color="#2e7d32",
                      hover_color="#1b5e20", command=self._save_config).grid(
            row=0, column=4, padx=6, pady=10)
        # Przełącznik języka
        ctk.CTkLabel(top, text=tr("language"), font=ctk.CTkFont(size=12)).grid(
            row=0, column=5, padx=(6, 2), pady=10)
        self.lang_menu = ctk.CTkOptionMenu(top, values=[LANG_NAMES["pl"], LANG_NAMES["en"]],
                                           width=110, command=self._on_lang_change)
        self.lang_menu.set(LANG_NAMES.get(current_lang, LANG_NAMES["en"]))
        self.lang_menu.grid(row=0, column=6, padx=(0, 12), pady=10)

        # Główna siatka: 3 panele
        main = ctk.CTkFrame(self, corner_radius=0)
        main.grid(row=1, column=0, sticky="nsew", padx=6, pady=(0, 6))
        main.grid_columnconfigure(1, weight=1)
        main.grid_rowconfigure(0, weight=1)

        # Lewy: drzewo configu
        left = ctk.CTkFrame(main, width=330)
        left.grid(row=0, column=0, sticky="nsew", padx=(0, 6))
        left.grid_propagate(False)
        ctk.CTkLabel(left, text=tr("tree_title"),
                     font=ctk.CTkFont(size=13, weight="bold")).pack(anchor="w", padx=10, pady=(10, 4))
        self.tree = ttk.Treeview(left, show="tree", selectmode="browse")
        self.tree.pack(fill="both", expand=True, padx=8, pady=(0, 8))
        self.tree.bind("<<TreeviewSelect>>", self._on_tree_select)

        # Środkowy: wiadomości z loga
        center = ctk.CTkFrame(main)
        center.grid(row=0, column=1, sticky="nsew", padx=(0, 6))
        center.grid_rowconfigure(1, weight=1)
        center.grid_columnconfigure(0, weight=1)

        head = ctk.CTkFrame(center, fg_color="transparent")
        head.grid(row=0, column=0, sticky="ew", padx=10, pady=(10, 4))
        head.grid_columnconfigure(1, weight=1)
        ctk.CTkLabel(head, text=tr("messages_title"),
                     font=ctk.CTkFont(size=13, weight="bold")).grid(row=0, column=0, sticky="w")
        self.search_entry = ctk.CTkEntry(head, placeholder_text=tr("search"), width=220)
        self.search_entry.grid(row=0, column=1, sticky="e", padx=(10, 0))
        self.search_entry.bind("<KeyRelease>", lambda e: self._refresh_messages())

        self.msg_tree = ttk.Treeview(center, columns=("count", "message"), show="headings",
                                     selectmode="browse")
        self.msg_tree.heading("count", text="×")
        self.msg_tree.heading("message", text=tr("msg_column"))
        self.msg_tree.column("count", width=60, anchor="center", stretch=False)
        self.msg_tree.column("message", width=500)
        self.msg_tree.grid(row=1, column=0, sticky="nsew", padx=10, pady=4)

        msg_btns = ctk.CTkFrame(center, fg_color="transparent")
        msg_btns.grid(row=2, column=0, sticky="ew", padx=10, pady=(4, 10))
        msg_btns.grid_columnconfigure(0, weight=1)
        ctk.CTkButton(msg_btns, text=tr("add_msg_tag"), width=280,
                      command=self._add_message_as_tag).grid(row=0, column=0, sticky="w")
        self.status_label = ctk.CTkLabel(msg_btns, text="", font=ctk.CTkFont(size=12))
        self.status_label.grid(row=0, column=1, sticky="e", padx=8)

        # Prawy: edycja filtra
        right = ctk.CTkFrame(main, width=360)
        right.grid(row=0, column=2, sticky="nsew")
        right.grid_propagate(False)
        ctk.CTkLabel(right, text=tr("filter_title"),
                     font=ctk.CTkFont(size=13, weight="bold")).pack(anchor="w", padx=10, pady=(10, 4))
        self.filter_container = ctk.CTkFrame(right, fg_color="transparent")
        self.filter_container.pack(fill="both", expand=True, padx=8, pady=(0, 8))
        self._render_no_filter()

    def _setup_tree_style(self):
        style = ttk.Style(self)
        try:
            style.theme_use("clam")
        except tk.TclError:
            pass
        style.configure("Treeview",
                        background="#1e1e1e", foreground="#e0e0e0",
                        fieldbackground="#1e1e1e", rowheight=24,
                        bordercolor="#2b2b2b")
        style.configure("Treeview.Heading",
                        background="#2b2b2b", foreground="#ffffff",
                        relief="flat", font=("Segoe UI", 10, "bold"))
        style.map("Treeview", background=[("selected", "#1565c0")])

    # ── Przełącznik języka ────────────────────────────────────────
    def _on_lang_change(self, value):
        global current_lang
        new_lang = "en" if value == LANG_NAMES["en"] else "pl"
        if new_lang == current_lang:
            return
        current_lang = new_lang
        save_lang(current_lang)
        # Zachowaj stan
        state = {
            "config_path": self.config_path,
            "config_data": self.config_data,
            "messages": self.messages,
            "sel_path": self._sel_path,
            "search": self.search_entry.get() if hasattr(self, "search_entry") else "",
        }
        for w in self.winfo_children():
            w.destroy()
        self._build_ui()
        self._setup_tree_style()
        # Przywróć stan
        self.config_path = state["config_path"]
        self.config_data = state["config_data"]
        self.messages = state["messages"]
        if state["search"]:
            self.search_entry.insert(0, state["search"])
        # Odśwież listę profili — nowy dropdown po odbudowie UI ma tylko ["—"]
        self._refresh_profiles()
        if self.config_data is not None:
            self._refresh_tree()
            self._refresh_messages()
            sel_path = state["sel_path"]
            if sel_path:
                try:
                    flt = self.config_data
                    for part in sel_path:
                        flt = flt[part]
                    self.selected_filter = flt
                    self._sel_path = sel_path
                    self._render_filter()
                except (KeyError, IndexError, TypeError):
                    self._render_no_filter()
            else:
                self._render_no_filter()
            self.status_label.configure(text=tr("loaded", self.config_path))
        else:
            self._render_no_filter()
            if self.profile_menu.cget("values") == ["—"]:
                self.status_label.configure(text=tr("no_profiles"))

    # ── Konfiguracja ───────────────────────────────────────────────
    def _load_initial_config(self):
        self._refresh_profiles()
        # Auto-wczytaj pierwszy dostępny profil (jeśli istnieje)
        values = self.profile_menu.cget("values")
        if values and values != ["—"]:
            self._load_config(os.path.join(PROFILES_DIR, values[0] + ".json"))
        else:
            self.status_label.configure(text=tr("no_profiles"))

    def _load_config(self, path):
        try:
            with open(path, encoding="utf-8") as f:
                data = json.load(f)
        except (json.JSONDecodeError, OSError) as e:
            messagebox.showerror(tr("err_title"), tr("load_config_err") % e)
            return
        if not isinstance(data, dict) or "windows" not in data:
            messagebox.showerror(tr("err_title"), tr("not_config_err"))
            return
        self.config_path = path
        self.config_data = data
        self.selected_filter = None
        self._sel_path = None
        self.dirty = False               # nowy profil — start czysto
        self._refresh_profiles()
        self._refresh_tree()
        self.status_label.configure(text=tr("loaded", path))

    def _refresh_profiles(self):
        values = []
        if os.path.isdir(PROFILES_DIR):
            for name in sorted(os.listdir(PROFILES_DIR)):
                if name.endswith(".json"):
                    values.append(name[:-5])
        if not values:
            values = ["—"]
        self.profile_menu.configure(values=values)
        if self.config_path:
            self.profile_menu.set(profile_name(self.config_path))

    def _on_profile_selected(self, value):
        if value == "—":
            return
        path = os.path.join(PROFILES_DIR, value + ".json")
        if os.path.exists(path):
            self._load_config(path)

    # ── Drzewo configu ─────────────────────────────────────────────
    def _refresh_tree(self):
        self.tree.delete(*self.tree.get_children())
        self.selected_filter = None
        self._sel_path = None
        if not self.config_data:
            return
        windows = self.config_data.get("windows", [])
        for wi, win in enumerate(windows):
            tabs = win.get("tabs", [])
            is_main = any(t.get("type", "").upper() == "SERVER" for t in tabs)
            win_label = tr("window", wi + 1) + (tr("main") if is_main else "")
            win_id = self.tree.insert("", "end", text=win_label, open=True)
            for ti, tab in enumerate(tabs):
                props = tab.get("config", {}) or {}
                tab_type = tab.get("type", "CUSTOM")
                tab_label = tr("tab", props.get("name") or tr("unnamed"), tab_type)
                tab_id = self.tree.insert(win_id, "end", text=tab_label, open=True)
                filters = props.get("filters", []) or []
                if not filters:
                    self.tree.insert(tab_id, "end", text=tr("no_filters"))
                for fi, flt in enumerate(filters):
                    tags = flt.get("includeTags", []) or []
                    flt_label = "🔻 %s (%d %s)" % (
                        flt.get("name") or tr("filter"), len(tags), tr("tags_count"))
                    fid = self.tree.insert(tab_id, "end", text=flt_label)
                    self.tree.item(fid, tags=(wi, ti, fi))

    def _on_tree_select(self, _event):
        sel = self.tree.selection()
        if not sel:
            return
        item = self.tree.item(sel[0])
        tag_ids = item.get("tags")
        if tag_ids:
            try:
                wi, ti, fi = (int(x) for x in tag_ids)
            except (TypeError, ValueError):
                return
            try:
                flt = self.config_data["windows"][wi]["tabs"][ti]["config"]["filters"][fi]
            except (IndexError, KeyError, TypeError):
                return
            self.selected_filter = flt
            self._sel_path = ["windows", wi, "tabs", ti, "config", "filters", fi]
            self._render_filter()
        else:
            self._render_no_filter()

    # ── Prawy panel filtra ─────────────────────────────────────────
    def _render_no_filter(self):
        for w in self.filter_container.winfo_children():
            w.destroy()
        ctk.CTkLabel(self.filter_container,
                     text=tr("no_filter_hint"),
                     text_color="#888888", justify="left").pack(pady=30)

    def _render_filter(self):
        for w in self.filter_container.winfo_children():
            w.destroy()
        if not self.selected_filter:
            self._render_no_filter()
            return
        flt = self.selected_filter
        c = self.filter_container

        # Nazwa
        ctk.CTkLabel(c, text=tr("filter_name"), font=ctk.CTkFont(size=12, weight="bold")).pack(
            anchor="w", pady=(2, 2))
        name_entry = ctk.CTkEntry(c)
        name_entry.insert(0, flt.get("name") or "")
        name_entry.pack(fill="x", pady=(0, 8))
        name_entry.bind("<KeyRelease>", lambda e: self._set_field("name", name_entry.get()))

        # Tagi include
        self._render_tag_section(c, tr("include_section"), "include",
                                 flt.get("includeTags", []) or [])
        # ── Tagi exclude — WYŁĄCZONE (mod obecnie nie używa excludeTags) ──
        # self._render_tag_section(c, tr("exclude_section"), "exclude",
        #                          flt.get("excludeTags", []) or [])

        # Ręczne dodanie taga
        ctk.CTkLabel(c, text=tr("manual_tag"), font=ctk.CTkFont(size=12, weight="bold")).pack(
            anchor="w", pady=(6, 2))
        row = ctk.CTkFrame(c, fg_color="transparent")
        row.pack(fill="x")
        self.manual_tag_entry = ctk.CTkEntry(row, placeholder_text=tr("manual_tag_ph"))
        self.manual_tag_entry.pack(side="left", fill="x", expand=True)
        self.manual_tag_entry.bind("<Return>", lambda e: self._manual_add_tag())
        ctk.CTkButton(row, text="+", width=34, command=self._manual_add_tag).pack(
            side="left", padx=(6, 0))

        # Opcje
        opts = ctk.CTkFrame(c, fg_color="transparent")
        opts.pack(fill="x", pady=(8, 0))
        hide_var = tk.BooleanVar(value=bool(flt.get("hideMessage")))
        sound_var = tk.BooleanVar(value=bool(flt.get("shouldPlaySound")))
        ctk.CTkCheckBox(opts, text=tr("hide_message"), variable=hide_var,
                        command=lambda: self._set_field("hideMessage", hide_var.get())
                        ).pack(anchor="w")
        ctk.CTkCheckBox(opts, text=tr("play_sound"), variable=sound_var,
                        command=lambda: self._set_field("shouldPlaySound", sound_var.get())
                        ).pack(anchor="w", pady=(2, 0))

        ctk.CTkButton(c, text=tr("delete_filter"), fg_color="#c62828", hover_color="#8e0000",
                      command=self._delete_filter).pack(fill="x", pady=(10, 0))

    def _render_tag_section(self, parent, title, kind, tags):
        bg, fg = TAG_COLORS[kind]
        ctk.CTkLabel(parent, text=title, font=ctk.CTkFont(size=12, weight="bold")).pack(
            anchor="w", pady=(4, 2))
        box = ctk.CTkFrame(parent, fg_color="#181818", corner_radius=6)
        box.pack(fill="x", pady=(0, 4))
        if not tags:
            ctk.CTkLabel(box, text=tr("empty"), text_color="#666666").pack(anchor="w", padx=6, pady=2)
        for i, tag in enumerate(tags):
            row = ctk.CTkFrame(box, fg_color="transparent")
            row.pack(fill="x", padx=4, pady=1)
            chip = ctk.CTkLabel(row, text=f"  {tag}  ", fg_color=bg, text_color=fg,
                                corner_radius=4, font=ctk.CTkFont(size=12))
            chip.pack(side="left")
            btn = ctk.CTkButton(row, text="✕", width=24, fg_color="transparent",
                                text_color="#ff8a80", command=lambda i=i: self._remove_tag(kind, i))
            btn.pack(side="left", padx=(2, 0))

    # ── Modyfikacje filtra ─────────────────────────────────────────
    def _set_field(self, key, value):
        if self.selected_filter is not None:
            self.selected_filter[key] = value
            self.dirty = True

    def _add_tag(self, kind, tag):
        if self.selected_filter is None:
            messagebox.showinfo(tr("info_title"), tr("select_filter_first"))
            return
        tag = (tag or "").strip()
        if not tag:
            return
        key = "includeTags" if kind == "include" else "excludeTags"
        tags = self.selected_filter.get(key)
        if not isinstance(tags, list):
            tags = []
            self.selected_filter[key] = tags
        if tag not in tags:
            tags.append(tag)
            self.dirty = True
        self._after_tag_edit()

    def _remove_tag(self, kind, index):
        if self.selected_filter is None:
            return
        key = "includeTags" if kind == "include" else "excludeTags"
        tags = self.selected_filter.get(key)
        if isinstance(tags, list) and 0 <= index < len(tags):
            del tags[index]
            self.dirty = True
        self._after_tag_edit()

    def _after_tag_edit(self):
        """Odświeża drzewo i przywraca selekcję filtra po edycji tagów."""
        path = list(self._sel_path) if self._sel_path else None
        self._refresh_tree()
        if path:
            try:
                flt = self.config_data
                for part in path:
                    flt = flt[part]
                self.selected_filter = flt
                self._sel_path = path
                self._render_filter()
            except (KeyError, IndexError, TypeError):
                self._render_no_filter()

    def _manual_add_tag(self):
        text = getattr(self, "manual_tag_entry", None)
        if text:
            self._add_tag("include", text.get())
            text.delete(0, "end")

    def _delete_filter(self):
        if not self._sel_path:
            return
        if not messagebox.askyesno(tr("delete_filter_title"), tr("delete_filter_msg")):
            return
        path = self._sel_path
        try:
            parent = self.config_data
            for part in path[:-1]:
                parent = parent[part]
            if isinstance(parent, list) and 0 <= path[-1] < len(parent):
                del parent[path[-1]]
        except (KeyError, IndexError, TypeError):
            return
        self.selected_filter = None
        self._sel_path = None
        self.dirty = True
        self._refresh_tree()
        self._render_no_filter()

    # ── Chatlog ────────────────────────────────────────────────────
    def _open_chatlog_dialog(self):
        path = filedialog.askopenfilename(
            title=tr("chatlog_dialog_title"),
            filetypes=[(tr("chatlog_filter"), "*.txt"),
                       (tr("mc_log_filter"), "*.log"),
                       (tr("all_files"), "*.*")],
            initialdir=CHATLOG_DIR if os.path.isdir(CHATLOG_DIR) else None)
        if not path:
            return
        self._load_chatlog(path)

    def _load_chatlog(self, path):
        counts = {}
        raws = {}
        try:
            with open(path, encoding="utf-8", errors="replace") as f:
                for line in f:
                    msg = parse_log_line(line)
                    if not msg:
                        continue
                    counts[msg] = counts.get(msg, 0) + 1
                    raws.setdefault(msg, line.rstrip("\r\n"))
        except OSError as e:
            messagebox.showerror(tr("err_title"), tr("load_chatlog_err") % e)
            return
        self.messages = [(m, raws[m], counts[m]) for m in counts]
        self.messages.sort(key=lambda x: (-x[2], x[0].lower()))
        self._refresh_messages()
        self.status_label.configure(
            text=tr("chatlog_status", os.path.basename(path), len(self.messages)))

    def _refresh_messages(self):
        self.msg_tree.delete(*self.msg_tree.get_children())
        self._msg_raw_by_iid = {}
        query = self.search_entry.get().strip().lower()
        for cleaned, raw, count in self.messages:
            if query and query not in cleaned.lower():
                continue
            iid = self.msg_tree.insert("", "end", values=(count, cleaned))
            self._msg_raw_by_iid[iid] = raw

    def _add_message_as_tag(self):
        sel = self.msg_tree.selection()
        if not sel:
            messagebox.showinfo(tr("info_title"), tr("select_message_first"))
            return
        raw = self._msg_raw_by_iid.get(sel[0], "")
        self._show_tag_dialog(raw)

    def _show_tag_dialog(self, raw_text):
        dlg = ctk.CTkToplevel(self)
        dlg.title(tr("tag_dialog_title"))
        dlg.geometry("620x300")
        dlg.transient(self)
        dlg.grab_set()
        dlg.grid_columnconfigure(0, weight=1)
        dlg.grid_rowconfigure(0, weight=1)

        ctk.CTkLabel(dlg, text=tr("tag_dialog_label"),
                     font=ctk.CTkFont(size=12, weight="bold")).grid(
            row=0, column=0, sticky="w", padx=12, pady=(12, 4))
        box = ctk.CTkTextbox(dlg, wrap="word")
        box.insert("1.0", raw_text)
        box.grid(row=1, column=0, sticky="nsew", padx=12, pady=(0, 8))

        hint = ctk.CTkLabel(
            dlg,
            text=tr("tag_dialog_hint"),
            wraplength=580, text_color="#888888", justify="left")
        hint.grid(row=2, column=0, sticky="w", padx=12, pady=(0, 8))

        btns = ctk.CTkFrame(dlg, fg_color="transparent")
        btns.grid(row=3, column=0, sticky="ew", padx=12, pady=(0, 12))

        def add(kind):
            self._add_tag(kind, box.get("1.0", "end").strip())
            dlg.destroy()

        ctk.CTkButton(btns, text=tr("include_btn"), fg_color="#2e7d32", hover_color="#1b5e20",
                      command=lambda: add("include")).pack(side="left")
        # ── EXCLUDE — WYŁĄCZONE (mod obecnie nie używa excludeTags) ──
        # ctk.CTkButton(btns, text=tr("exclude_btn"), fg_color="#c62828", hover_color="#8e0000",
        #               command=lambda: add("exclude")).pack(side="left", padx=(8, 0))
        ctk.CTkButton(btns, text=tr("cancel"), command=dlg.destroy).pack(side="right")

    # ── Zapis ──────────────────────────────────────────────────────
    def _save_config(self):
        if not self.config_path or self.config_data is None:
            messagebox.showinfo(tr("info_title"), tr("no_config"))
            return
        # Ochrona głównego chat.json — nigdy nie nadpisujemy
        if os.path.basename(self.config_path).lower() == "chat.json":
            messagebox.showwarning(tr("protect_title"), tr("protect_msg"))
            return
        # Pytanie o backup (Tak = backup + zapis, Nie = tylko zapis, Anuluj = nic)
        choice = messagebox.askyesnocancel(tr("save_title"), tr("backup_ask"))
        if choice is None:  # Anuluj
            return
        backup = None
        if choice:  # Tak
            ts = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
            backup = f"{self.config_path}.bak-{ts}"
            try:
                shutil.copy2(self.config_path, backup)
            except OSError:
                backup = None
        # Zapis (UTF-8, bez ASCII-escapingu → § zostaje)
        try:
            with open(self.config_path, "w", encoding="utf-8") as f:
                json.dump(self.config_data, f, ensure_ascii=False, indent=2)
        except OSError as e:
            messagebox.showerror(tr("err_title"), tr("save_err") % e)
            return
        self.dirty = False
        short = tr("saved")
        if backup:
            short += tr("backup_info", os.path.basename(backup))
        else:
            short += tr("no_backup")
        hint = tr("reload_hint")
        self.status_label.configure(text=short)
        messagebox.showinfo(tr("saved_title"), short + hint)

    def _on_close(self):
        """Zamykanie okna — ostrzega o niezapisanych zmianach."""
        if self.dirty:
            choice = messagebox.askyesnocancel(tr("unsaved_title"), tr("unsaved_msg"))
            if choice is None:      # Anuluj → zostań w oknie
                return
            if choice:              # Tak → zapisz
                self._save_config()
                if self.dirty:      # zapis nieudany/anulowany → zostań
                    return
        self.destroy()


def main():
    app = FilterEditorApp()
    app.mainloop()


if __name__ == "__main__":
    main()
