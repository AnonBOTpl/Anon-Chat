# 🔧 Naprawy i nowe funkcje — 2026-07-24

---

## 🚨 Krytyczne

### 1. Profil auto-save bug

**Opis:** `ChatConfig.save()` automatycznie nadpisuje plik profilu przy każdej zmianie.

**Flow błędu:**
1. Wczytujesz profil "laby" → `currentProfile = "laby"`
2. Usuwasz okna/taby → `save()` → zapisuje **pusty stan** do `profiles/laby.json`
3. Wracasz do "laby" → profil jest pusty 😱

**Przyczyna:** W `ChatConfig.save()` na końcu metody:
```java
// Auto-save to current profile
if (currentProfile != null && !currentProfile.isEmpty()) {
    saveProfile(currentProfile);
}
```

**Fix:** Usunąć te 3 linijki. Profil ma być snapshootem — zmienia się TYLKO gdy użytkownik kliknie "Zapisz" w panelu Profili.

**Pliki do zmiany:** `ChatConfig.java` (×3 wersje)

---

### 2. Wiadomości debug (F3+B, F3+G) pod overlayem

**Opis:** Komunikaty typu `"[Debug] Hitboxes: shown"` po wciśnięciu F3+B lądują w vanilla ChatHud i są widoczne POD naszym overlayem.

**Przyczyna:** Debug wiadomości omijają `ClientReceiveMessageEvents.ALLOW_CHAT` — idą bezpośrednio przez `ChatHud.addMessage()`. Normalny chat udaje się blokować bo Fabric API ma gotowy mixin na `ALLOW_CHAT`, ale dla `addMessage()` Fabric nie ma gotowca.

**Rozwiązanie:** Mixin w `ChatHud.addMessage()` (`@Inject(at = @At("HEAD"), cancellable = true)`) — przechwytuje absolutnie wszystkie wiadomości łącznie z debug, wysyła do naszego routera i anuluje oryginał. To samo co `ALLOW_CHAT` tylko na niższym poziomie.

**Pliki do stworzenia:**
- `src/main/java/net/anonchat/mixin/ChatHudMixin.java`
- `26.1.2/src/main/java/net/anonchat/mixin/ChatHudMixin.java`
- `26.2/src/main/java/net/anonchat/mixin/ChatHudMixin.java`

**Pliki do zmiany:**
- `src/main/resources/anonchat.mixins.json` (dodać wpis mixina)
- `26.1.2/src/main/resources/anonchat.mixins.json`
- `26.2/src/main/resources/anonchat.mixins.json`

---

## 🟡 Średnie

### 3. Hide Message checkbox + usunięcie excludeTags

**Opis:**
- Usunąć z UI sekcję **excludeTags** (tagi wykluczające) z filtrów
- Przywrócić **checkbox "Hide Message"** (domyślnie odznaczony) w opcjach filtra
- excludeWords i excludeRegEx zostają

**Logika:**
- Include first → exclude second → hideMessage decyduje czy całkiem ukryć czy tylko przenieść

**Pliki do zmiany:**
- `ChatFilter.java` — dodać pole `hideMessage`, usunąć getExcludeTags/setExcludeTags z logiki matches()
- `ChatFilter.java` — dodać `@SerializedName("hideMessage") private boolean hideMessage;`
- `AnonChatConfigScreen.java` — w UI filtrów: usunąć pola excludeTags, dodać checkbox Hide Message
- Lang files — dodać klucze dla hide message
- ×3 wersje

---

### 4. Kłódka — blokada pozycji okna

**Opis:** Szybkie klikanie w obszarze okna czasem przesuwa okno (drag). Dodać przycisk kłódki na pasku tytułu okna.

**Implementacja:**
- Dodać pole `positionLocked` do `ChatWindowSettings`
- W `ChatWindowWidget`: przycisk 🔒/🔓 na pasku tytułu
- Gdy zablokowane → `mouseDragged` nie zmienia pozycji (x, y)
- Stan zapisywany do configu

**Pliki do zmiany:**
- `ChatWindowSettings.java` — dodać `positionLocked` z getter/setter
- `ChatWindowWidget.java` — dodać przycisk kłódki + logika blokady drag'a
- Lang files — dodać klucze tooltip
- ×3 wersje

---

## Priorytet implementacji

1. **Fix profilu** (krytyczny — utrata danych)
2. **Fix debug wiadomości** (średni — brzydkie, ale nie niszczy danych)
3. **Hide Message + excludeTags** (średni — poprawka UX)
4. **Kłódka** (niski — dodatek)
