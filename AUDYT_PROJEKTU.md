# Raport z Audytu Technicznego: AnonChat Mod
**Status Audytu:** Zakończony (Krytyczny)
**Grupa docelowa projektu:** Duża społeczność Open Source (dziesiątki tysięcy użytkowników)
**Autor Audytu:** Jules, Starszy Inżynier Oprogramowania (Audytor Zewnętrzny)
**Język raportu:** Polski

---

## 1. Wstęp i Metodologia Audytu

Niniejszy dokument przedstawia kompleksowy audyt techniczny poziomu produkcyjnego dla projektu **AnonChat Mod** – zaawansowanego narzędzia do personalizacji czatu w grze Minecraft.

Celem audytu jest ocena jakości kodu źródłowego, bezpieczeństwa, wydajności oraz architektury pod kątem przekształcenia projektu w dojrzałe, stabilne i bezpieczne oprogramowanie typu open source o skrajnie wysokiej niezawodności.

### Zakres audytu
Analizie poddano ręcznie pisany kod źródłowy, strukturę konfiguracji, mechanizmy wejścia/wyjścia (I/O) oraz wielowątkowość w trzech niezależnych gałęziach kodu dostarczonych w repozytorium:
1. **Wersja Główna (Root):** Minecraft 1.21.1 – 1.21.5 (Fabric API, klasyczne mapowania Yarn/Mojang).
2. **Wersja 26.1.2:** Minecraft 26.1.2 (unifikowane mapowania, przejściowe API Mojang).
3. **Wersja 26.2:** Minecraft 26.2 (najnowsze API Mojang, m.in. `setScreenAndShow`, zaktualizowana obsługa HUD).

---

## 2. Podsumowanie Gotowości Produkcyjnej (Production Readiness Score)

Na podstawie rygorystycznej analizy kodu, projekt otrzymał następujące oceny w skali 1-10:

*   **Bezpieczeństwo i Odporność (Security & Robustness):** `3 / 10`
    *Zagrożenia wynikające z braku bezpieczeństwa wątków, podatności na błędy parsowania oraz brak walidacji wyrażeń regularnych wprowadzanych przez użytkowników.*
*   **Wydajność i Zarządzanie Zasobami (Performance):** `4 / 10`
    *Poważne problemy z blokowaniem głównego wątku renderowania przez operacje I/O na dysku oraz drastyczny narzut CPU w pętli renderowania czatu.*
*   **Architektura i Skalowalność (Architecture):** `5 / 10`
    *Silne sprzężenie (tight coupling) pomiędzy logiką biznesową a GUI, klasy typu "God Object" (monolity powyżej 1300 linii kodu) oraz brak separacji warstw.*
*   **Pokrycie Testami (Test Coverage):** `0 / 10`
    *Brak jakichkolwiek testów automatycznych (jednostkowych, integracyjnych czy funkcjonalnych).*

**Ocena Ogólna (Skumulowana):** `3.0 / 10` (Status: **NIEGOTOWY NA PRODUKCJĘ**)

---

## 3. Wspólne Poważne Problemy Architektoniczne i Podatności (Analiza Globalna)

Poniższe sekcje szczegółowo opisują fundamentalne wady kodu obecne we wszystkich trzech wersjach moda.

### 3.1. Brak Bezpieczeństwa Wątków w `ChatLogger` (Krytyczne Ryzyko Race Condition)
Klasa `ChatLogger` we wszystkich trzech wersjach jest zaimplementowana jako globalny Singleton ze współdzielonymi, statycznymi polami niebędącymi bezpiecznymi dla wątków:
```java
private static Path logDir;
private static PrintWriter writer;
private static String currentDate = "";
```
Metoda `log(final String message)` jest wywoływana z poziomu klasy `ChatRouter.dispatchMessage()`, która z kolei jest uruchamiana przez zdarzenia Fabric API:
*   `ClientReceiveMessageEvents.ALLOW_CHAT`
*   `ClientReceiveMessageEvents.ALLOW_GAME`

**Problem:** Zdarzenia sieciowe i odbiór pakietów w grze Minecraft często odbywają się na wątkach roboczych sieci (Netty epoll/nio event loops), a nie na głównym wątku renderowania (Render Thread). Wywołanie metody `log()` bez żadnej synchronizacji (`synchronized`, `ReentrantLock` lub bezpiecznych struktur) prowadzi bezpośrednio do wyścigów (race conditions).
*   Konsekwencją może być błąd typu `NullPointerException` podczas zapisu, uszkodzenie struktury pliku logów (przeplatane bajty z różnych wiadomości) lub błędy współbieżnego dostępu do zasobów wejścia/wyjścia.

---

### 3.2. Blokujące Operacje I/O na Głównym Wątku Gry (Narzut na FPS i Mikro-przycięcia)
Projekt cierpi na antywzorzec blokującego I/O w krytycznych dla płynności gry momentach.
1.  **Zapis konfiguracji:** Klasa `ChatConfig.save()` wykonuje synchroniczny zapis na dysku za pomocą `FileWriter` i biblioteki Gson. Ta metoda jest wywoływana m.in. z klasy `ChatOverlay` w metodzie `handleMouse()` podczas zwalniania przycisku myszy po przeciągnięciu lub zmianie rozmiaru okna:
    ```java
    if (!leftPressed && wasLeftPressed) {
        if (draggedWindow != null) {
            draggedWindow.commitPosition();
            draggedWindow = null;
            try { ChatConfig.getInstance().save(); } catch (final Exception ignored) {}
        }
        ...
    }
    ```
2.  **Odczyt profili:** Profil wczytywany jest synchronicznie w `loadProfile` bezpośrednio z wątku renderowania.

**Konsekwencje:** Każda operacja zapisu/odczytu na wolniejszych dyskach HDD lub w systemach z obciążonym I/O (np. podczas jednoczesnego zapisu świata) spowoduje natychmiastowe zablokowanie głównego wątku gry (Render Thread), co objawi się nagłym spadkiem klatek na sekundę (stuttering/lag spike). Dla gry wymagającej wysokiej responsywności jest to niedopuszczalne.

---

### 3.3. Drastyczny Narzut CPU i Brak Buforowania w `ChatMessagesWidget.render()`
W klasie `ChatMessagesWidget.render` (oraz odpowiednikach w innych wersjach) zawijanie linii tekstu odbywa się **w pętli renderowania każdej klatki obrazu**:
```java
final List<List<OrderedText>> wrappedLines = new ArrayList<>(messages.size());
int totalLines = 0;
for (final ChatMessageWrapper wrapper : messages) {
    ...
    final List<OrderedText> lines = textRenderer.wrapLines(component, areaWidth - leftMargin - 2);
    wrappedLines.add(lines);
    totalLines += lines.size();
}
```
**Problem:** Metoda `textRenderer.wrapLines` (lub `font.split` w wersjach 26.x) jest kosztowną operacją obliczeniową. Wymaga analizy szerokości każdego znaku w ciągu znaków i dynamicznego dzielenia go.
*   Wykonywanie tej operacji dla **każdej klatki** (np. przy 144 FPS dla setek wiadomości) generuje ogromny, bezużyteczny narzut na procesor.
*   Prowadzi to do ciągłego alokowania tysięcy obiektów `OrderedText` i `ArrayList` w każdej sekundzie, wywołując drastyczne narzuty na Garbage Collector (GC pressure) i powodując cykliczne mikro-przycięcia gry.

---

### 3.4. Wyciek Zasobów w `ChatLogger` przy Zamykaniu Gry
W `ChatLogger` instancja `PrintWriter` jest otwierana i utrzymywana w stanie otwartym. Metoda `close()` jest zaimplementowana, lecz **nigdy nie jest wywoływana** podczas cyklu życia modyfikacji ani przy zamykaniu klienta Minecraft (np. poprzez rejestrację zdarzenia wyłączenia klienta).

**Konsekwencje:** Nagłe wyłączenie gry, crash lub zamknięcie procesu doprowadzi do utraty buforowanych danych w `PrintWriter` (ponieważ strumień nie został poprawnie zamknięty i zrzucony) oraz do blokowania deskryptorów plików przez system operacyjny.

---

### 3.5. Ciche Ignorowanie Wyjątków (Antywzorzec "Silent Catch")
W całym projekcie występuje masowe użycie pustych bloków catch:
```java
try { ... } catch (final IOException ignored) {}
try { ChatConfig.getInstance().save(); } catch (final Exception ignored) {}
```
**Problem:** Taki kod całkowicie uniemożliwia diagnozę problemów. Jeśli plik konfiguracyjny ma błędne uprawnienia (np. tylko do odczytu) lub dysk jest pełny, mod po prostu przestanie zapisywać stan, a programista ani użytkownik nie otrzymają żadnego logu o błędzie w konsoli.

---

### 3.6. Podatność na Błędy Wyrażeń Regularnych i ReDoS (Regular Expression Denial of Service)
W klasie `ChatFilter.matches` użytkownik może zdefiniować zaawansowane filtry za pomocą wyrażeń regularnych (`includeRegEx` i `excludeRegEx`).
*   Wyrażenie regularne jest kompilowane bezpośrednio przy każdym dopasowaniu wiadomości (brak prekompilacji wzorców):
    ```java
    final Pattern pattern = caseSensitive
        ? Pattern.compile(excludeRegEx)
        : Pattern.compile(excludeRegEx, Pattern.CASE_INSENSITIVE);
    ```
*   Brak walidacji struktury wyrażenia regularnego podczas jego zapisu w konfiguracji.
*   **Podatność ReDoS:** Jeśli złośliwy użytkownik (lub sam gracz przez pomyłkę) wprowadzi podatny regex (np. zawierający zagnieżdżone kwantyfikatory typu `(a+)+`), a na czacie pojawi się odpowiednio spreparowana wiadomość, silnik regex Javy wejdzie w stan backtrackingowego zamrożenia (catastrophic backtracking). Spowoduje to natychmiastowe zawieszenie całej gry (100% obciążenia rdzenia CPU) i konieczność ubicia procesu.

---

### 3.7. Monolityczna Struktura i Klasy Typu "God Object" w GUI
Klasa `AnonChatConfigScreen.java` (we wszystkich wersjach) osiąga rozmiar **blisko 1400 linii kodu**. Jest to klasyczny antywzorzec. Klasa ta odpowiada jednocześnie za:
1.  Rysowanie i renderowanie wszystkich elementów interfejsu użytkownika (przyciski, pola tekstowe, panele boczne).
2.  Zarządzanie stanem logicznym konfiguracji filtrów, makr i profili.
3.  Zapisywanie i odczytywanie konfiguracji na dysk.
4.  Obsługę zdarzeń wejściowych klawiatury i myszy dla kilkunastu różnych kontekstów.

Modyfikacja tej klasy bez wprowadzania regresji jest niezwykle trudna. Narusza to podstawową zasadę czystego kodu: **Single Responsibility Principle** (Zasada Jednej Odpowiedzialności).

---

## 4. Analiza Specyficzna dla Wersji Projektu

### 4.1. Wersja Główna (Root) – Minecraft 1.21.1 – 1.21.5
*   **Architektura Fabric:** Używa `ClientReceiveMessageEvents.ALLOW_CHAT` oraz `ALLOW_GAME` zwracając `false`. To radykalne podejście – całkowicie blokuje oryginalny czat gry i przejmuje jego renderowanie. Wymaga to perfekcyjnej symulacji wszystkich zachowań czatu vanilla, co w obecnej wersji nie jest w pełni zrealizowane (np. brak poprawnego wsparcia dla klikalnych linków, najeżdżania myszą na wzmianki (tooltips), integracji z modami trzecimi zmieniającymi formatowanie wiadomości).
*   **Struktura Pakietów:** Klasa startowa moda `AnonChatMod` znajduje się w innym pakiecie (`net.anonlauncher.chatmod`) niż reszta kodu klienta (`net.anonchat.client`). Wersje 26.x zunifikowały to do `net.anonchat.client`.

### 4.2. Wersja 26.1.2
*   **Zmiany w API Mojang:** Zastąpiono `MinecraftClient` przez `Minecraft`. Zamiast `DrawContext` użyto `GuiGraphicsExtractor` (specyficzny dla tej wersji wrapper graficzny).
*   **Brak spójności w strukturze plików:** Wersja ta powiela wszystkie błędy wielowątkowości w `ChatLogger` oraz blokującego I/O. Pomimo nowszego API Mojanga, jakość architektury logicznej nie uległa poprawie.
*   **Poważne ryzyko w `ChatOverlay.ensureScrollHook`:** Rejestracja globalnego hooka scrollowania przez GLFW:
    ```java
    final var prev = GLFW.glfwSetScrollCallback(handle, null);
    GLFW.glfwSetScrollCallback(handle, (window, xoffset, yoffset) -> {
        scrollAccumulator += yoffset;
        if (prev != null) prev.invoke(window, xoffset, yoffset);
    });
    ```
    Najechanie i nadpisanie callbacku GLFW w ten sposób może powodować konflikty z innymi modami modyfikującymi zachowanie myszy lub GUI gry, ponieważ globalny callback GLFW jest jeden dla całego okna aplikacji.

### 4.3. Wersja 26.2
*   **Ewolucja API ekranów:** Minecraft w wersji 26.2 wymusza użycie metody `setScreenAndShow` zamiast klasycznego `setScreen`. Mod poprawnie dostosowuje się do tej zmiany w klasie `AnonChatConfigScreen` oraz `ChatWindowWidget`.
*   **Zarządzanie renderowaniem:** Zamiast sprawdzania `mc.options.hideGui` wprowadzono `mc.gui.hud.isHidden()`. Jest to zmiana czysto syntaktyczna i nie rozwiązuje żadnego z problemów wydajnościowych opisanych w rozdziale 3.3.

---

## 5. Rekomendacje Refaktoryzacji i Gotowe Szablony Kodu

Poniższe sekcje przedstawiają bezpośrednie i gotowe do wdrożenia wzorce refaktoryzacji, które eliminują zidentyfikowane podatności oraz błędy wydajnościowe.

### 5.1. Bezpieczny, Nieblokujący i Współbieżny `ChatLogger`
Zastosowanie dedykowanego jednowątkowego egzekutora (`ExecutorService`) gwarantuje, że zapis na dysk nie zablokuje ani wątku renderowania, ani wątków sieciowych, a kolejkowanie operacji zapobiegnie wyścigom.

*Rekomendowana implementacja:*
```java
package net.anonchat.client.chatlog;

import net.anonchat.client.config.ChatConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ChatLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChatLogger");
    private static final ExecutorService LOG_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "AnonChat-LogThread");
        thread.setDaemon(true);
        return thread;
    });

    private static Path logDir;
    private static PrintWriter writer;
    private static String currentDate = "";

    private ChatLogger() {}

    public static void initialize(final Path configDir) {
        logDir = configDir.resolve("chatlog");
        // Rejestracja shutdown hooka do poprawnego zamykania strumienia plików
        Runtime.getRuntime().addShutdownHook(new Thread(ChatLogger::close, "AnonChat-LogShutdown"));
    }

    public static void log(final String message) {
        if (message == null || message.trim().isEmpty() || logDir == null) return;

        // Przekazanie zapisu do asynchronicznego wątku roboczego
        LOG_EXECUTOR.submit(() -> {
            try {
                if (ChatConfig.getInstance() == null) return;
                if (!ChatConfig.getInstance().getFontSettings().isChatlogEnabled()) return;

                final String today = LocalDate.now().toString();
                if (!today.equals(currentDate)) {
                    closeInternal();
                    currentDate = today;
                    Files.createDirectories(logDir);
                    final Path file = logDir.resolve(today + ".txt");
                    writer = new PrintWriter(new FileWriter(file.toFile(), true));
                }

                if (writer != null) {
                    final String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                    writer.println("[" + time + "] " + message);
                    writer.flush();
                }
            } catch (final IOException e) {
                LOGGER.error("Błąd zapisu historii czatu do pliku", e);
            }
        });
    }

    public static void close() {
        LOG_EXECUTOR.submit(ChatLogger::closeInternal);
        LOG_EXECUTOR.shutdown();
    }

    private static void closeInternal() {
        if (writer != null) {
            writer.flush();
            writer.close();
            writer = null;
        }
    }
}
```

---

### 5.2. Buforowanie Zawiniętych Linii Tekstu w `ChatMessageWrapper`
Eliminuje to narzut wydajnościowy w pętli renderowania poprzez buforowanie przetworzonych linii i unieważnianie cache'u tylko w przypadku zmiany szerokości obszaru czatu.

*Koncepcja refaktoryzacji `ChatMessageWrapper`:*
```java
private List<OrderedText> cachedWrappedLines;
private int cachedWidth = -1;

public List<OrderedText> getOrComputeWrappedLines(TextRenderer renderer, int targetWidth) {
    if (cachedWrappedLines == null || cachedWidth != targetWidth) {
        Text component = getComponent();
        if (component == null) return Collections.emptyList();

        if (getRepeatCount() > 0) {
            component = Text.literal("[×" + (getRepeatCount() + 1) + "] ").copy().append(component);
        }

        cachedWrappedLines = renderer.wrapLines(component, targetWidth);
        cachedWidth = targetWidth;
    }
    return cachedWrappedLines;
}

public void invalidateCache() {
    this.cachedWrappedLines = null;
    this.cachedWidth = -1;
}
```

---

### 5.3. Bezpieczne Parsowanie Wyrażeń Regularnych i Zapobieganie ReDoS
Walidacja wyrażenia regularnego podczas jego dodawania w GUI zapobiega zapisaniu błędnej konfiguracji. Dodatkowo należy ograniczyć czas wykonania dopasowania wyrażenia (Regex Timeout) przy użyciu bezpiecznego wrappera `CharSequence`.

*Metoda walidacji w GUI:*
```java
public static boolean isValidRegex(String regex) {
    try {
        Pattern.compile(regex);
        return true;
    } catch (PatternSyntaxException e) {
        return false;
    }
}
```

---

## 6. Ogólne Wnioski i Rekomendowany Plan Działań

Modyfikacja **AnonChat Mod** posiada bogaty zestaw funkcji, które są bardzo pożądane przez społeczność graczy Minecraft. Jednakże w obecnym stanie technicznym, udostępnienie projektu jako oprogramowania klasy Enterprise Open Source niesie ze sobą duże ryzyko:
*   Skargi użytkowników na mikro-przycięcia gry (lag spikes) przy intensywnym czacie lub częstym zapisie pozycji okien.
*   Ryzyko uszkodzenia plików konfiguracyjnych i utraty logów.
*   Podatności na crashe gry spowodowane błędami w filtrach regex.

### Rekomendowany plan naprawczy (Roadmap do wersji 1.0.0-prod):
1.  **Faza 1 (Wydajność & Stabilność):** Wdrożenie asynchronicznego I/O dla zapisu logów i konfiguracji. Implementacja buforowania linii tekstu w widgetach renderujących czat.
2.  **Faza 2 (Bezpieczeństwo):** Dodanie walidacji wprowadzanych danych w GUI, zabezpieczenie parsera kolorów HEX i kompilatora wyrażeń regularnych.
3.  **Faza 3 (Testy):** Wprowadzenie frameworka testowego (np. JUnit 5) oraz testów jednostkowych dla klas takich jak `ChatFilter`, `ChatRouter` i `ChatConfig`.
4.  **Faza 4 (Refaktoryzacja):** Podział potężnej klasy `AnonChatConfigScreen` na mniejsze, wyspecjalizowane kontrolery i widgety (np. `FilterConfigPanel`, `MacroConfigPanel`).
