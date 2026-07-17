# Raport z Audytu Technicznego: AnonChat Mod

## Wprowadzenie i Kontekst Projektu

Niniejszy dokument przedstawia pełny, statyczny audyt techniczny kodu źródłowego moda **AnonChat** (modyfikacja typu chat-utility dla Minecraft Fabric).

### Kontekst oceny:
Projekt oceniany jest w sposób realistyczny, adekwatny do poważnie rozwijanego, asynchronicznego i wielookienkowego projektu o charakterze open source (portowanego na nowsze wersje Minecrafta). Choć nie jest to system enterprise obsługujący miliony zapytań sieciowych, rygor techniczny został w pełni zachowany. Ocenie poddano przede wszystkim:
1. **Stabilność i niezawodność (Crash & Data Loss prevention):** Ryzyko wywołania awarii klienta (crash), wycieki pamięci, błędy parsowania wejścia i utrata danych użytkownika.
2. **Architektura wątkowa (Concurrency & Multithreading):** Szczegółowa analiza wątków, na których wykonują się zdarzenia Fabric API oraz operacje I/O, a także bezpieczeństwo współdzielonego stanu.
3. **Optymalizacja I/O i zasobów:** Blokowanie głównego wątku (render thread) przez operacje dyskowe.
4. **Różnice i specyfika API dla poszczególnych wersji sub-projektów.**

Audyt obejmuje trzy niezależne wersje kodu znajdujące się w repozytorium:
* **Wersja główna (Root):** Minecraft 1.21.1 – 1.21.5.
* **Wersja 26.1.2:** Nowszy podprojekt portowany na Minecraft 26.1.2 (unobfuscated/intermediary).
* **Wersja 26.2:** Najnowszy podprojekt portowany na Minecraft 26.2.

---

## 1. Analiza Współbieżności i Wątkowości (Wspólna dla wszystkich wersji)

Jednym z najważniejszych wymagań technicznych audytu jest precyzyjne określenie modelu wątkowego. Poniżej znajduje się zweryfikowane przypisanie wątków do kluczowych operacji moda:

### Zdarzenia `ClientReceiveMessageEvents.ALLOW_CHAT` i `ALLOW_GAME`
* **Na jakim wątku się wykonują:** Te zdarzenia Fabric API wykonują się na **głównym wątku klienta (Main Thread / Render Thread)**.
* **Uzasadnienie techniczne (na podstawie kodu Fabric API oraz Minecrafta):**
  Pakiety sieciowe (np. `ClientboundSystemChatPacket` lub `ClientboundDisguisedChatPacket`) są odbierane z sieci na wątku asynchronicznym biblioteki Netty (IO Thread). Jednakże w klasie `ClientPlayNetworkHandler` (metody obsługi pakietów takie jak `onChatMessage` i `onGameMessage`), Minecraft natychmiast przekazuje przetwarzanie pakietu do kolejki zadań klienta za pomocą metody `PacketUtils.ensureRunningOnSameThread` (lub jej odpowiednika w Yarn/Mojang: `forceMainThread`).
  Dopiero po asynchronicznej delegacji na wątek główny (`MinecraftClient.getInstance()`), Minecraft wywołuje procesory wiadomości (takie jak `ChatListener` / `MessageHandler`). Fabric API podpina swoje mixiny właśnie pod metody wywoływane wewnątrz `ChatListener` na wątku głównym, co gwarantuje, że zdarzenia `ClientReceiveMessageEvents` są wywoływane synchronicznie z pętlą renderowania gry.
* **Konsekwencje:** Wykonywanie ciężkich obliczeń, operacji wejścia/wyjścia (zapis do logu, zapis profilu) bezpośrednio w tych zdarzeniach blokuje pętlę renderowania i obniża FPS.

### Blokowanie operacji I/O na Main Thread
* **Zapis logu (`ChatLogger.log`)**: Wywoływany bezpośrednio z `ChatRouter.dispatchMessage`, który z kolei odpala się wewnątrz zdarzenia `ALLOW_CHAT` / `ALLOW_GAME`. Oznacza to, że każda odebrana wiadomość powoduje synchroniczne otwarcie, dopisanie i wywołanie `flush()` na pliku logu **bezpośrednio na wątku renderowania**.
* **Zapis konfiguracji i profili (`ChatConfig.save` i `saveProfile`)**: Wywoływane synchronicznie na wątku renderowania podczas przesuwania okien, zmiany rozmiarów (puszczenie lewego przycisku myszy) lub przełączania opcji w GUI. GSON wykonuje kosztowną serializację całego drzewa obiektów do stringa i zapisuje go synchronicznie na dysku, co przy wolniejszych nośnikach lub dużych konfiguracjach generuje odczuwalne mikroprzycięcia (stutters).

---

## 2. Sekcja Audytu: Wersja Główna (Root / 1.21.1–1.21.5)

### [ROOT-01] [CRITICAL] Ryzyko uszkodzenia danych i wycieku zasobów w `ChatLogger`
* **Plik:** `src/main/java/net/anonchat/client/chatlog/ChatLogger.java` (metody `log` i `close`)
* **Opis błędu:**
  1. Klasa inicjalizuje statyczny `PrintWriter writer`. Przy zmianie daty (nowy dzień) wywoływana jest metoda `close()`, która zamyka poprzedni plik i otwiera nowy. Jednakże **mod nigdy nie zamyka pliku przy wyjściu z gry**. Nie ma zarejestrowanego ani `ShutdownHook` w JVM, ani nasłuchiwania na zdarzenie zatrzymania klienta w Fabric (np. `ClientLifecycleEvents.CLIENT_STOPPING`).
  2. W efekcie, strumień zapisu do pliku pozostaje otwarty aż do ubicia procesu JVM przez system operacyjny. W systemach Windows może to prowadzić do zablokowania pliku logu. W przypadku nagłego zamknięcia lub crasha gry, ostatnie linijki wiadomości zapisane w buforze `PrintWriter` mogą nigdy nie trafić na dysk (mimo wywołań `flush()`, brak poprawnego zamknięcia strumienia niesie ryzyko utraty danych).
  3. Metoda `log()` twierdzi w komentarzu, że jest *"Thread-safe-ish"*, ponieważ jest wywoływana z głównego wątku. To niebezpieczne założenie: jeśli jakikolwiek inny mod wywoła zdarzenie asynchronicznie, lub jeśli dodane zostaną asynchroniczne powiadomienia, dojdzie do race condition na niestabilnym statycznym obiekcie `writer`.
* **Rekomendacja:** Zarejestrować listener `ClientLifecycleEvents.CLIENT_STOPPING` w `AnonChatMod.onInitializeClient()` i wywołać w nim `ChatLogger.close()`. Zsynchronizować metodę `log()` lub przenieść zapisywanie logów do dedykowanej asynchronicznej kolejki (`BlockingQueue`) obsługiwanej przez osobny jednowątkowy Executor Service.

### [ROOT-02] [HIGH] Potencjalne crashe (awaria klienta) w `AnonChatConfigScreen` przez brak walidacji pól tekstowych
* **Plik:** `src/main/java/net/anonchat/client/gui/AnonChatConfigScreen.java` (metoda `tick()` oraz handlery pól tekstowych)
* **Opis błędu:**
  Podczas edycji ustawień karty (np. Message Limit, BG Color, Unfocused BG Color, Message Timeout) mod używa pól `TextFieldWidget` i w metodzie `tick()` lub w listenerach próbuje parsować ich zawartość za pomocą `Integer.parseInt` oraz `Long.parseLong` z określonym radiksem (16 dla hex).
  1. Choć pola takie jak `chatLimitField` posiadają filtr znaku `s.matches("\\d*")`, to pole może stać się **puste** (użytkownik usuwa całą zawartość klawiszem Backspace). Wtedy `Integer.parseInt("")` rzuci nieobsługiwany wyjątek `NumberFormatException`.
  2. W polach kolorów hex, takich jak `bgColorField` i `unfocusedBgColorField`, walidacja dopuszcza znaki hex, ale brak jest walidacji długości oraz wartości granicznych. Wpisanie pustego ciągu znaków lub niepełnego hex rzuci wyjątek przy próbie parsowania w `tick()`. Ponieważ wyjątek ten nie jest przechwytywany wewnątrz metody `tick()`, gra natychmiast ulegnie awarii (crash na render threadzie).
* **Rekomendacja:** Każde parsowanie wewnątrz metody `tick()` oraz w listenerach pól tekstowych musi być bezwzględnie otoczone blokiem `try-catch (NumberFormatException)`. W przypadku błędu należy zignorować zapis i ewentualnie podświetlić pole na czerwono, zamiast dopuszczać do wycieku wyjątku i crashowania gry.

### [ROOT-03] [MEDIUM] Blokowanie wątku renderowania przy auto-zapisie konfiguracji (I/O Bottleneck)
* **Pliki:** `src/main/java/net/anonchat/client/config/ChatConfig.java` (metoda `save()` i `saveProfile()`), `src/main/java/net/anonchat/client/gui/ChatOverlay.java` (metoda `handleMouse()`)
* **Opis błędu:**
  Auto-zapis wywoływany jest po zakończeniu przesuwania okna (zdarzenie uwalniania przycisku myszy `!leftPressed && wasLeftPressed`). Metoda `save()` nie tylko zapisuje plik główny, ale także automatycznie nadpisuje aktywny profil poprzez `saveProfile(currentProfile)`.
  Operacje te wykonują się synchronicznie na wątku renderowania. Serializacja dużych konfiguracji przez GSON oraz synchroniczny zapis na dysku za pomocą `FileWriter` trwają od kilkunastu do kilkudziesięciu milisekund. Na słabszych konfiguracjach sprzętowych (lub dyskach HDD) spowoduje to chwilowe zacięcie obrazu (lag spike / stutter).
* **Rekomendacja:** Przenieść wywołania `gson.toJson` oraz operacje zapisu do pliku na asynchroniczny wątek (np. dedykowany `CompletableFuture.runAsync` lub jednowątkowy Executor). Należy zadbać o wykonanie głębokiej kopii (deep copy) obiektu konfiguracji przed przekazaniem jej do wątku asynchronicznego, aby uniknąć współdzielenia stanu modyfikowanego w tym samym czasie przez wątek renderowania.

### [ROOT-04] [MEDIUM] Brak mechanizmu zapobiegania "Race Condition" przy asynchronicznym wczytywaniu profili
* **Plik:** `src/main/java/net/anonchat/client/config/ChatConfig.java`
* **Opis błędu:**
  Metoda `loadProfile()` bezpośrednio podmienia referencje list obiektów konfiguracji (np. `this.windows = loaded.windows;`, `this.macros = loaded.macros;`). Brak jakiejkolwiek synchronizacji (`synchronized` lub blokady typu ReadWriteLock) na obiekcie `ChatConfig` sprawia, że jeśli wątek renderowania (lub asynchroniczny logger) odczytuje te listy w trakcie ich podmieniania, może napotkać stan niespójny lub rzucić `ConcurrentModificationException` / `NullPointerException`.
* **Rekomendacja:** Uprościć architekturę poprzez unikanie bezpośredniej podmiany referencji pól wewnątrz żyjącej instancji `INSTANCE` lub zsynchronizować dostęp do list okien i makr przy użyciu bezpiecznych kolekcji (np. `CopyOnWriteArrayList`).

---

## 3. Sekcja Audytu: Wersja Portowana 26.1.2

Wersja portowana 26.1.2 wprowadza spore zmiany strukturalne w związku z przystosowaniem kodu do nowszych API Fabric i Minecrafta.

### [PORT-2612-01] [CRITICAL] Ryzyko wycieku zasobów w asynchronicznym `ChatLogger`
* **Plik:** `26.1.2/src/main/java/net/anonchat/client/chatlog/ChatLogger.java`
* **Opis błędu:**
  Podobnie jak w wersji głównej, w podprojekcie 26.1.2 implementacja `ChatLogger` cierpi na całkowity brak zamykania pliku przy zamknięciu sesji gry. Brak powiązania z cyklem życia klienta niesie identyczne zagrożenia – zablokowanie plików w systemie operacyjnym Windows, utrata buforowanych danych przy nagłym wyjściu.
* **Rekomendacja:** Zaimplementować poprawne zamykanie w asynchronicznym handlerze cyklu życia gry.

### [PORT-2612-02] [HIGH] Niestabilność pól tekstowych `EditBox` w konfiguracji (Wprowadzone w Minecraft 26.1.2)
* **Plik:** `26.1.2/src/main/java/net/anonchat/client/gui/AnonChatConfigScreen.java` (metoda `tick()`)
* **Opis błędu:**
  Wersja 26.1.2 zastępuje vanilla `TextFieldWidget` nowym komponentem `EditBox`.
  1. Pola `tabNameField`, `chatLimitField`, `bgColorField`, `unfocusedBgColorField` oraz `timeoutField` są odczytywane asynchronicznie co tick gry w metodzie `tick()`.
  2. Brak jakiejkolwiek walidacji wejściowej dla `chatLimitField` i `timeoutField` (w tej wersji kodu **usunięto** predykaty tekstowe filtrujące znaki nie-numeryczne, które istniały w wersji głównej). Wpisanie jakiejkolwiek litery do pola limitu wiadomości lub timeoutu i pozostawienie tam fokusu natychmiast wywoła crash gry z błędem `NumberFormatException` w najbliższym ticku pętli renderowania.
  3. Puste pole w `bgColorField` lub wpisanie wartości nienumerycznej również skutkuje natychmiastową awarią klienta przy parsowaniu wartości szesnastkowej `Long.parseLong(bgColorField.getValue(), 16)`.
* **Rekomendacja:** Dodać filtry znaków do obiektów `EditBox` (jeśli API na to pozwala) oraz bezwzględnie otoczyć całą logikę parsowania w metodzie `tick()` blokami `try-catch`.

### [PORT-2612-03] [MEDIUM] Wydajność renderowania a synchroniczne obliczenia w `ChatMessagesWidget`
* **Plik:** `26.1.2/src/main/java/net/anonchat/client/gui/ChatMessagesWidget.java` (metoda `render`)
* **Opis błędu:**
  W metodzie `render` dla każdego renderowanego okna czatu wykonywane jest pełne zawijanie tekstu (`font.split(component, ...)`) dla **wszystkich** wiadomości w historii aktywnej karty przy każdym przejściu pętli renderowania.
  Mimo że wiadomości się nie zmieniają, proces podziału tekstu na linie (`FormattedCharSequence`) jest powtarzany w każdym kadrze (60 lub więcej razy na sekundę). Może to dramatycznie obniżyć wydajność (FPS drop) w przypadku posiadania wielu okien z setkami wiadomości.
* **Rekomendacja:** Zaimplementować buforowanie (caching) zawiniętych linii bezpośrednio w obiekcie `ChatMessageWrapper`. Tekst powinien być dzielony na linie tylko raz – w momencie odebrania wiadomości lub przy zmianie szerokości okna czatu (resizing).

---

## 4. Sekcja Audytu: Wersja Portowana 26.2

Wersja 26.2 opiera się na nowej architekturze renderowania z Minecrafta 26.2 i Fabric API, przenosząc renderowanie HUD na dedykowane obiekty `HudElement` i wprowadzając potok przygotowywania stanu (`GuiGraphicsExtractor`).

### [PORT-262-01] [CRITICAL] Naruszenie założeń wielowątkowości w nowym potoku renderowania 26.2 (`GuiGraphicsExtractor`)
* **Plik:** `26.2/src/main/java/net/anonchat/client/gui/ChatOverlay.java` (metoda `register` oraz klasa anonimowa `HudElement`)
* **Opis błędu:**
  W wersji 26.2 Mojang wprowadził rewolucyjne zmiany w potoku renderowania GUI (tzw. "preparation phase" i "drawing phase"). Nowe API `HudElementRegistry` i `GuiGraphicsExtractor` mają na celu ekstrakcję stanu renderowania, tak aby **sam proces rysowania mógł odbywać się na osobnym wątku renderującym**, podczas gdy główny wątek gry przygotowuje kolejną ramkę.
  1. Klasa `ChatOverlay` rejestruje element HUD, który w metodzie `extractRenderState(GuiGraphicsExtractor extractor, DeltaTracker tickCounter)` bezpośrednio wywołuje metodę `ChatOverlay.this.render(extractor)`.
  2. Wewnątrz metody `render(extractor)` oraz w renderach pod-widgetów, mod odczytuje na żywo stan gry, w tym aktywną kartę, kolekcję wiadomości (`activeTab.getMessages()`), ustawienia z `ChatConfig` oraz pozycje myszy.
  3. Ponieważ `extractRenderState` może być wywoływany asynchronicznie przez silnik gry w fazie przygotowania ramki, odczytywanie bezpośrednio mutowalnych struktur danych (takich jak lista wiadomości modyfikowana przy nadejściu pakietu czatu na głównym wątku) bez synchronizacji prowadzi do **wyścigu danych (data races)**. Może to objawiać się losowymi wyjątkami `ConcurrentModificationException` podczas renderowania lub uszkodzeniem pamięci podręcznej OpenGL/Vulkan.
* **Rekomendacja:** Zgodnie z architekturą Minecraft 26.2, metoda `extractRenderState` powinna wyłącznie pobierać (kopiować) niezbędne dane do niemutowalnego obiektu stanu renderowania (Render State). Następnie to ten asynchronicznie przygotowany stan powinien być rysowany, bez bezpośredniego odwoływania się do żywych obiektów `ChatWindow` i `ChatTabImpl` w trakcie renderowania.

### [PORT-262-02] [HIGH] Krytyczne błędy parsowania pól tekstowych (Identoczne z wersją 26.1.2)
* **Plik:** `26.2/src/main/java/net/anonchat/client/gui/AnonChatConfigScreen.java` (metoda `tick()`)
* **Opis błędu:**
  Wersja 26.2 dziedziczy problem braku walidacji pól numerycznych i hex z wersji 26.1.2. Wpisanie błędnych danych wejściowych w konfiguracji (np. pustego pola lub znaku nienumerycznego) natychmiast powoduje awarię klienta gry przy najbliższym wywołaniu `tick()`. Jest to błąd o wysokim priorytecie z uwagi na łatwość jego wywołania przez zwykłego gracza.
* **Rekomendacja:** Zastosować pełne przechwytywanie wyjątków `NumberFormatException` w handlerach `EditBox` w klasie `AnonChatConfigScreen`.

### [PORT-262-03] [MEDIUM] Wyciek pamięci w rejestracji Callbacków GLFW przy częstym przeładowywaniu HUD
* **Plik:** `26.2/src/main/java/net/anonchat/client/gui/ChatOverlay.java` (metoda `ensureScrollHook`)
* **Opis błędu:**
  Metoda `ensureScrollHook` próbuje zarejestrować globalny callback przewijania myszy w GLFW:
  ```java
  final var prev = GLFW.glfwSetScrollCallback(handle, (window, xoffset, yoffset) -> {
      scrollAccumulator += yoffset;
      if (prev != null) prev.invoke(window, xoffset, yoffset);
  });
  ```
  Zmienna `prev` odnosi się do wcześniej zarejestrowanego callbacku. Jednak w przypadku, gdy mod jest przeładowywany (np. przy zmianie profilu, rozłączeniu z serwerem, itp.), wielokrotne wywołanie tej metody tworzy niekończący się łańcuch delegacji (łańcuch callbacków opakowujących poprzednie callbacki).
  Prowadzi to do wycieku pamięci na stercie JVM (oraz natywnej pamięci GLFW) oraz może skutkować przepełnieniem stosu (StackOverflowError) przy dłuższych sesjach gry ze względu na rekurencyjne wywołania `prev.invoke()`.
* **Rekomendacja:** Zaimplementować mechanizm czyszczenia i rejestrować callback GLFW tylko raz przy starcie moda, zamiast wielokrotnie nadpisywać go w pętli ticków.

---

## 5. Podsumowanie i Priorytety Naprawcze

Aby ułatwić zespołowi deweloperskiemu szybkie i efektywne usunięcie zidentyfikowanych problemów, poniżej zestawiono znaleziska według stopnia ryzyka dla użytkowników końcowych:

| Identyfikator | Priorytet | Wersja | Opis Problemu | Rekomendowane Działanie |
|---|---|---|---|---|
| **PORT-262-01** | **KRYTYCZNY** | 26.2 | Data race i naruszenie asynchronicznego modelu renderowania 26.2. | Przepisanie systemu renderowania w oparciu o niemutowalne obiekty Render State. |
| **ROOT-01** / **PORT-2612-01** | **KRYTYCZNY** | Wszystkie | Wyciek strumieni i blokowanie plików dziennych w `ChatLogger`. | Dodanie nasłuchiwania na zamknięcie klienta (`CLIENT_STOPPING`) i wywołanie `close()`. |
| **ROOT-02** / **PORT-2612-02** / **PORT-262-02** | **WYSOKI** | Wszystkie | Crashe gry przy wpisaniu błędnych danych w konfiguracji (NFE). | Otoczenie parsowania blokami `try-catch` i dodanie walidacji wejścia. |
| **ROOT-03** | **ŚREDNI** | Wszystkie | Blokowanie wątku głównego (lagi) przy zapisie plików GSON. | Asynchroniczny zapis na osobnym wątku (np. `CompletableFuture`). |
| **PORT-2612-03** | **ŚREDNI** | 26.1.2 / 26.2 | Spadek wydajności (FPS drop) przez ciągłe zawijanie tekstu w pętli renderowania. | Zaimplementowanie buforowania (cache) linii wewnątrz wrapowanego komunikatu. |
| **PORT-262-03** | **ŚREDNI** | 26.2 | Wyciek pamięci i ryzyko StackOverflow przy rejestracji callbacków GLFW. | Zabezpieczenie przed wielokrotnym owijaniem callbacków GLFW. |

### Uwagi końcowe:
Mod AnonChat posiada bardzo dobrze zaprojektowaną strukturę wielookienkową opartą o zdarzenia Fabric API zamiast inwazyjnych mixinów ASM, co ułatwia jego rozwój. Jednakże przejście na nowsze wersje Minecrafta (szczególnie 26.2 z asynchronicznym potokiem renderowania) oraz hobbystyczny charakter projektu wymagają pilnego wdrożenia powyższych poprawek w zakresie bezpieczeństwa wątkowego oraz odporności na błędy użytkownika (NFE), aby zapewnić stabilną dystrybucję na platformach Modrinth i CurseForge.
