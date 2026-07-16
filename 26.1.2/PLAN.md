# AnonChatMod — Plan portowania na Minecraft 26.1.2

## Struktura projektu

```
anon-chat-mod/
  ├── src/                    # (oryginalny mod 1.21.x — nie ruszamy)
  ├── build.gradle            # (oryginalny — nie ruszamy)
  ├── port-26x/               # NOWY folder — osobne środowisko Gradle
  │   ├── build.gradle        # nowy build na 26.1.2
  │   ├── settings.gradle
  │   ├── gradle.properties
  │   ├── gradlew / gradlew.bat
  │   ├── src/main/java/net/anonchat/   # przepisany kod
  │   ├── src/main/resources/fabric.mod.json
  │   └── PLAN.md             # ten plik
```

---

## Mapping nazw: Yarn (1.21.x) → Mojang (26.1.2)

| Yarn / Stara nazwa | Mojang / 26.1.2 | Import |
|---|---|---|
| `MinecraftClient` | `Minecraft` | `net.minecraft.client.Minecraft` |
| `DrawContext` | `GuiGraphics` | `net.minecraft.client.gui.GuiGraphics` |
| `TextRenderer` | `Font` | `net.minecraft.client.gui.Font` |
| `Text` | `Component` | `net.minecraft.network.chat.Component` |
| `Text.literal()` | `Component.literal()` | j.w. |
| `Text.translatable()` | `Component.translatable()` | j.w. |
| `OrderedText` | `FormattedCharSequence` | `net.minecraft.util.FormattedCharSequence` |
| `Identifier` | `ResourceLocation` | `net.minecraft.resources.ResourceLocation` |
| `Screen` | `Screen` | `net.minecraft.client.gui.screens.Screen` |
| `ButtonWidget` | `Button` | `net.minecraft.client.gui.components.Button` |
| `TextFieldWidget` | `EditBox` | `net.minecraft.client.gui.components.EditBox` |
| `TextWidget` | `StringWidget` | `net.minecraft.client.gui.components.StringWidget` |
| `InputUtil` | `InputMappings` | `net.minecraft.client.InputMappings` |
| `SoundEvents` | `SoundEvents` | `net.minecraft.sounds.SoundEvents` |

### Fabric API

| Yarn | 26.1.2 |
|---|---|
| `HudRenderCallback.EVENT.register(...)` | **USUNIĘTY** → `HudElementRegistry.addLast(...)` |
| `.dimensions(x, y, w, h)` | `.bounds(x, y, w, h)` (Button builder) |
| `ClientReceiveMessageEvents` | nadal działa (Fabric API, to samo FQN) |
| `ClientTickEvents` | nadal działa (Fabric API, to samo FQN) |

---

## Zależności (build.gradle)

```groovy
plugins {
    id "net.fabricmc.fabric-loom" version "1.17.14"
    id "base"
}

minecraft "com.mojang:minecraft:26.1.2"
// Brak 'mappings' — kod jest nieobfuskowany!

implementation "net.fabricmc:fabric-loader:0.19.3"
implementation "net.fabricmc.fabric-api:fabric-api:0.154.2+26.1.2"
implementation "com.terraformersmc:modmenu:18.0.0"
implementation "com.google.code.gson:gson:2.11.0"

// Java 25
java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}
```

Uwagi:
- **Brak `modImplementation`** → używamy `implementation`
- **Brak `remapJar`** → używamy zwykłego `jar`
- **Brak `mappings`** → 26.1.2 jest nieobfuskowane

---

## Fazy implementacji

### Faza 0: Przygotowanie środowiska ✅ ZROBIONE

### Faza 1: Logika biznesowa (łatwe — mało zależności od MC API)

Pliki do przepisania (głównie zmiana `Text → Component`):

| Plik | Zmiany |
|---|---|
| `ChatMessage.java` | `Text` → `Component` |
| `ChatMessageWrapper.java` | `Text` → `Component` |
| `ChatTab.java` | brak zmian (tylko `String`) |
| `ChatTabImpl.java` | `Text` → `Component`, `SoundEvents` do weryfikacji |
| `ChatRouter.java` | brak zmian (czysta Java) |
| `DefaultChatWindow.java` | brak zmian (czysta Java) |
| `ChatWindow.java` | brak zmian (interfejs) |
| `ChatConfig.java` | brak zmian (Gson) |
| `ChatFilter.java` | brak zmian (regex) |
| `ChatMacro.java` | brak zmian (POJO) |
| `ChatTabConfig.java` | brak zmian (POJO) |
| `ChatTabProperties.java` | brak zmian (POJO) |
| `ChatWindowSettings.java` | brak zmian (POJO) |

### Faza 2: Główna klasa moda

| Plik | Zmiany |
|---|---|
| `AnonChatMod.java` | `Text` → `Component`, `MinecraftClient` → `Minecraft`, `Text.literal()` → `Component.literal()` |

### Faza 3: GUI — ChatOverlay (największa zmiana)

- `HudRenderCallback.EVENT.register(...)` → `HudElementRegistry.addLast(...)`
- `DrawContext` → `GuiGraphics`
- `int tickCounter` → `DeltaTracker tickCounter`

### Faza 4: GUI — ChatWindowWidget

| Yarn | 26.1.2 |
|---|---|
| `DrawContext` | `GuiGraphics` |
| `TextRenderer` | `Font` |
| `textRenderer.getWidth(...)` | `font.width(...)` |
| `MinecraftClient.getInstance().textRenderer` | `Minecraft.getInstance().font` |
| `Text.literal(...)` | `Component.literal(...)` |

### Faza 5: GUI — ChatMessagesWidget

J.w. + dodatkowo:

| Yarn | 26.1.2 |
|---|---|
| `textRenderer.wrapLines(...)` | `font.wrapLines(...)` |
| `textRenderer.fontHeight` | `font.lineHeight` |
| `OrderedText` | `FormattedCharSequence` |
| `context.fill(...)` | `guiGraphics.fill(...)` |
| `context.drawText(...)` | `guiGraphics.drawString(...)` |
| `context.drawCenteredTextWithShadow(...)` | `guiGraphics.drawCenteredString(...)` |

### Faza 6: GUI — ChatTabWidget

Analogicznie jak ChatMessagesWidget.

### Faza 7: GUI — AnonChatConfigScreen (uproszczony)

**Usuwamy z drzewa nawigacyjnego:**
- ❌ `"+ New Tab"` (jest w hamburger menu na HUD)
- ❌ `"✕"` (Delete Tab) (jest w hamburger menu)
- ❌ `"+ Add Window"` (jest w hamburger menu)
- ❌ `"✕"` (Delete Window) (jest w hamburger menu)

**Zostawiamy:**
- ✅ Drzewo nawigacyjne (tylko do wyboru zakładki, bez add/delete)
- ✅ Tab settings (nazwa, combine, shadow, background, kolory, timeout, exclude)
- ✅ Filter List (lista filtrów + Add Filter)
- ✅ Filter Detail (tagi, hide, sound, change background)
- ✅ Macros (autotext z key capture)

**Zmiany nazw widgetów:**

| Yarn | 26.1.2 |
|---|---|
| `ButtonWidget.builder(...).dimensions(x,y,w,h)` | `Button.builder(...).bounds(x,y,w,h)` |
| `TextFieldWidget(textRenderer, x, y, w, h, label)` | `EditBox(font, x, y, w, h, label)` |
| `TextWidget(x, y, w, h, text, textRenderer)` | `StringWidget(x, y, w, h, text, font)` |
| `InputUtil.fromKeyCode(code, 0).getLocalizedText()` | `InputMappings.getKey(code, 0).getDisplayName()` |
| `this.client.setScreen(parent)` | `this.minecraft.setScreen(parent)` |

### Faza 8: GUI — AnonChatModMenu

Prawdopodobnie brak zmian w kodzie.

### Faza 9: Kompilacja + testy w grze

---

## Kolejność prac

```
Faza 0: Środowisko Gradle ✅
     ↓
Faza 1: Config + model (ChatConfig, ChatFilter, ChatTab*, ChatMessage*, ChatRouter, ...)
     ↓
Faza 2: AnonChatMod (entry point)
     ↓
Faza 3: ChatOverlay (największa zmiana — HudElementRegistry)
     ↓
Faza 4: ChatWindowWidget
     ↓
Faza 5: ChatMessagesWidget
     ↓
Faza 6: ChatTabWidget
     ↓
Faza 7: AnonChatConfigScreen (uproszczony)
     ↓
Faza 8: AnonChatModMenu
     ↓
Faza 9: Kompilacja + testy w grze
```

Każda faza powinna się kompilować osobno.

---

## Potencjalne problemy do zweryfikowania w trakcie

1. **`HudElement` interfejs** — sprawdzić sygnaturę `render(GuiGraphics, DeltaTracker)`
2. **`font.wrapLines()`** — czy metoda istnieje w 26.1.2
3. **`ClientReceiveMessageEvents.ALLOW_CHAT`** — czy callback ma te same parametry
4. **`EditBox` API** — sprawdzić `setMaxLength()`, `setChangedListener()`, `setFilter()`, `setHint()`
5. **`Button.builder()`** — czy `.bounds()` działa
6. **`StringWidget`** — konstruktor i metody
