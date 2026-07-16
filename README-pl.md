# 🗨️ AnonChat Mod

**Zaawansowana personalizacja czatu dla Minecraft 1.21.1 – 1.21.5, 26.1.2 i 26.2**

Przejmij pełną kontrolę nad swoim czatem. Organizuj wiadomości w osobnych oknach, filtruj niepotrzebny hałas, podświetlaj wzmianki o graczach, zapisuj historię czatu i przełączaj się między całymi profilami konfiguracji — wszystko bez wychodzenia z gry.

![Fabric](https://img.shields.io/badge/Fabric-dbd0b4?style=flat-square&logo=fabricmc)
![Licencja](https://img.shields.io/badge/Licencja-AGPL--3.0-blue?style=flat-square)

![Demo AnonChat Mod](images/Animation3.gif)

---

## ✨ Funkcje

### 📑 Wielookienkowy czat
- Twórz **nieograniczoną liczbę okien** — przeciągaj, zmieniaj rozmiar, ustawiaj w dowolnym miejscu ekranu
- Każde okno zawiera **wiele kart** do organizowania tematów
- Niezależne **ustawienia czcionki** dla każdej karty (rozmiar, odstępy, wyrównanie, cień)
- Inteligentne **przyciąganie do krawędzi** ekranu

### 🔍 Zaawansowane filtry
Zdefiniuj **tagi włączające i wykluczające** dla każdej karty, by kierować wiadomości dokładnie tam, gdzie chcesz:
- **Tagi włączające** — tylko wiadomości zawierające te słowa trafią do tej karty
- **Tagi wykluczające** — wiadomości z tymi słowami zostaną zablokowane
- Filtry są **w pełni kompatybilne z formatem JSON LabyMod** — możesz przenieść swoje dotychczasowe ustawienia
- Opcjonalnie: odtwórz dźwięk, zmień kolor tła lub podświetl przy dopasowaniu

### 🔔 Wzmianka o graczu (Ping)
- Automatycznie **podświetla twoją nazwę** w czacie, gdy ktoś cię wspomni
- Wybierz **kolor podświetlenia** i **dźwięk powiadomienia** (6 dźwięków do wyboru)
- Twoje własne wiadomości nie wywołują pingu — tylko gdy inni o tobie wspominają

### 💾 Profile konfiguracji
- Zapisz całą konfigurację (okna, karty, filtry, makra, czcionkę) jako **nazwany profil**
- Przełączaj się między profilami błyskawicznie — idealne na różne serwery
- Eksport/import przez kopiowanie plików z folderu `profiles/`
- Profil **"domyślny"** jest zawsze dostępny i chroniony przed usunięciem

### ⌨️ Autotekst (Makra)
- Przypisz **skróty klawiszowe** do wysyłania wiadomości lub poleceń
- Idealne do często używanych komend lub odpowiedzi na czacie

### 📝 Historia czatu (Chatlog)
- Automatycznie zapisuje wszystkie wiadomości do dziennych plików tekstowych
- Znajduje się w folderze `chatlog/` obok pliku konfiguracyjnego
- Opcja włącz/wyłącz w Ustawieniach

### 🌐 Internacjonalizacja
- Pełne wsparcie języka **polskiego** i **angielskiego**
- Język wykrywany automatycznie z ustawień gry

---

## 📦 Instalacja

1. Zainstaluj [Fabric Loader](https://fabricmc.net/use/) — `0.16.9+` dla 1.21.1–1.21.5, `0.19.3+` dla 26.1.2/26.2
2. Zainstaluj [Fabric API](https://modrinth.com/mod/fabric-api) — `0.110.0+1.21.1` dla 1.21.x, `0.154.2+26.1.2` / `0.154.2+26.2` dla 26.x
3. Upewnij się, że masz wymaganą wersję Javy — `Java 21+` dla 1.21.x, `Java 25+` dla 26.x
4. Pobierz plik JAR moda AnonChat dla swojej wersji
5. Umieść plik JAR w folderze `mods/`
6. (Opcjonalnie) Zainstaluj [ModMenu](https://modrinth.com/mod/modmenu) dla dodatkowego przycisku konfiguracji

---

## 🎮 Jak używać

### Pierwsze uruchomienie
- Kliknij **ikonę hamburgera (☰)** na dowolnym oknie czatu
- Kliknij **> Ustawienia**, by otworzyć ekran konfiguracji
- Lub użyj klawisza skrótu (jeśli ustawiony przez ModMenu)

![Ekran ustawień](images/settings.png)

### Tworzenie okien i kart
- Użyj **menu hamburgera** na dowolnym oknie, by dodać/usunąć karty lub utworzyć nowe okno
- W Ustawieniach → lewy panel, rozwijaj/zwijaj okna i zarządzaj poszczególnymi kartami

![Ustawienia karty](images/tab-settings.png)

### Konfiguracja filtrów
1. Wybierz kartę w lewym panelu
2. Kliknij **▶ Edytuj filtry**
3. Dodaj **tagi włączające** — wiadomości z tymi słowami trafią TYLKO do tej karty
4. Dodaj **tagi wykluczające** — wiadomości z tymi słowami zostaną zablokowane
5. Skonfiguruj dodatkowe akcje: dźwięk, zmiana tła

![Konfiguracja filtrów](images/filters.png)

### Korzystanie z profili
1. W Ustawieniach → **☰ Profile**
2. Kliknij **■ Zapisz**, by zapisać obecną konfigurację jako profil
3. Kliknij **▶ Wczytaj**, by przełączyć się na inny profil
4. Użyj przycisku **✕**, by usunąć własny profil

### Migracja z LabyMod
Twój istniejący plik `chat.json` z LabyMod jest **w pełni kompatybilny**. Wystarczy:
1. Skopiuj `chat.json` do `%APPDATA%/AnonChatMC/chat.json` (Windows)
2. Lub użyj funkcji **profilów**, by zaimportować konkretne konfiguracje filtrów
3. Wszystkie filtry, okna i ustawienia będą działać od razu

---

## 🖥️ Lokalizacja pliku konfiguracyjnego

| System | Ścieżka |
|---|---|
| **Windows** | `%APPDATA%/AnonChatMC/chat.json` |
| **macOS** | `~/Library/Application Support/AnonChatMC/chat.json` |
| **Linux** | `~/.local/share/AnonChatMC/chat.json` |

> **Wskazówka:** Użyj przycisku **"Otwórz folder ustawień"** w Ustawieniach, by szybko uzyskać dostęp do plików.

---

## 📜 Licencja

Ten projekt jest licencjonowany na **GNU Affero General Public License v3.0 (AGPL-3.0)**.

> **W skrócie:** Możesz swobodnie używać, modyfikować i rozpowszechniać ten mod. Jeśli go zmodyfikujesz i udostępnisz jako usługę (np. serwer lub launcher), musisz udostępnić swój zmodyfikowany kod źródłowy użytkownikom.

---

## 🤝 Podziękowania

- **Autor:** [AnonBOTpl](https://github.com/AnonBOTpl)
- **Zbudowano z:** Fabric Loom, Minecraft mappings
- **Specjalne podziękowania dla:** Zespołu LabyMod za ich świetny system filtrów czatu, który zainspirował projekt tego moda. AnonChat jest w pełni kompatybilny z formatem `chat.json` z LabyMod, co ułatwia migrację.
