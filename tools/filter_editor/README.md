# AnonChat Filter Editor

Graficzny edytor filtrów/tagów dla configu moda **AnonChat** (profile).

Rozwiązuje problem, którego nie da się obejść w grze: **UI Minecrafta kasuje znak §**
z wpisywanych tagów (zabezpieczenie `StringUtil.filterText`). Ten edytor zapisuje JSON
w UTF-8 z literalnym § — więc tagi takie jak `Money§8` działają.

> 🔒 **Główny `chat.json` jest chroniony** — edytor pozwala pracować wyłącznie na
> profilach z folderu `profiles/`. Nigdy nie nadpisuje `chat.json`.

## Funkcje

- 🗂 Dropdown skanuje tylko profile z `%APPDATA%/AnonChatMC/profiles/`
  (pierwszy profil ładuje się automatycznie przy starcie)
- 📖 Analizuje pliki z `%APPDATA%/AnonChatMC/chatlog/` (dzienne logi moda) i pokazuje
  **unikalne wiadomości z licznikiem wystąpień** (sortowane po częstotliwości)
- 📜 Obsługuje też **surowe logi Minecrafta** (`*.log`, np. `latest.log`) — wystarczy
  wybrać plik w okienku (domyślnie otwiera się folder chatlogu)
- ➕ Dodaje wiadomość z loga jako tag INCLUDE/EXCLUDE do wybranego filtra
  (z zachowaniem §; możesz skrócić tekst w oknie dialogowym)
- ✏️ Dodawanie/usuwanie tagów ręcznie, edycja nazwy filtra, opcje (ukryj, dźwięk)
- 🌐 Dwujęzyczny interfejs (PL/EN) — wykrywa język systemu, wybór jest **zapamiętywany**
- ⚠️ Ostrzega o **niezapisanych zmianach** przy zamykaniu okna (Zapisz / Nie zapisuj / Anuluj)
- 💾 Opcjonalny backup przy zapisie — pytanie Tak/Nie/Anuluj (`chat.json.bak-<timestamp>`)
- ℹ️ Zmiany tagów nie zapisują się automatycznie — kliknij **Zapisz**, aby zapisać na dysku

## Uruchomienie (Python)

```bash
pip install -r requirements.txt
python main.py
```

Edytor zawsze czyta profile z `%APPDATA%/AnonChatMC/profiles/`.
Jeśli folder jest pusty, dropdown pokazuje „—” — najpierw zapisz profil w grze.

Główny `chat.json` (ten sam folder, którego używa mod) jest tylko do odczytu —
nigdy nie jest edytowany ani nadpisywany.

## Budowanie standalone exe (bez Pythona u użytkownika)

```bash
pip install pyinstaller
pyinstaller --noconfirm --onefile --windowed --name AnonChatFilterEditor main.py
```

Gotowy plik: `dist/AnonChatFilterEditor.exe` — można go rozpowszechniać;
użytkownik nie musi mieć zainstalowanego Pythona.

## Wskazówki

- Tag z § (np. `Money§8`) jest **precyzyjny** — pasuje tylko tam, gdzie w wiadomości
  występuje dokładnie ten kolor. Usunięcie § daje luźniejsze dopasowanie.
- Filtry działają jak w grze: wiadomość z tagiem INCLUDE trafia do taba filtra
  (i jest usuwana z maina), tag EXCLUDE ukrywa.
- Po zapisie zrób reload configu w grze (przełącz profil tam i z powrotem albo
  użyj klawisza reload), aby mod wczytał zmiany.
