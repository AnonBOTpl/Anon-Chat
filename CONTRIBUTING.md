# Contributing to AnonChat

First off, thank you for considering contributing! 🎉

## 📋 Reporting Bugs

1. **Check existing issues** — search if the bug was already reported
2. **Use the Bug Report template** — it helps us understand the issue faster
3. **Include logs** — crash reports, `latest.log` or screenshots are super helpful
4. **Specify your environment** — Minecraft version, mod version, other mods installed

## 💡 Suggesting Features

1. **Check existing issues** — maybe it's already planned
2. **Use the Feature Request template**
3. **Explain the use case** — why would this be useful?
4. **Be specific** — the more details, the better

## 🛠 Development Setup

### Prerequisites
- Java 21 (for 1.21.x builds)
- Java 25 (for 26.x builds)
- Git

### Building all versions

```bash
# 1.21.1-1.21.5
gradlew build

# 26.1.2
cd 26.1.2
export JAVA_HOME=/path/to/jdk-25
./gradlew build

# 26.2
cd ../26.2
export JAVA_HOME=/path/to/jdk-25
./gradlew build
```

### Project structure

```
/
├── src/                    # 1.21.1-1.21.5 (Yarn mappings, Java 21)
├── 26.1.2/                 # Minecraft 26.1.2 (Mojang mappings, Java 25)
├── 26.2/                   # Minecraft 26.2 (Mojang mappings, Java 25)
├── DEVLOG.md               # Development log (gitignored)
├── README.md               # English docs
└── README-pl.md            # Polish docs
```

## 🔀 Pull Request Process

1. Fork the repo
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Build all 3 versions before committing
4. Commit your changes (`git commit -m 'Add amazing feature'`)
5. Push to the branch (`git push origin feature/amazing-feature`)
6. Open a Pull Request

## 🌍 Translations

AnonChat supports **English** and **Polish**. Translation files are in:
- `assets/anonchat/lang/en_us.json`
- `assets/anonchat/lang/pl_pl.json`

To add a new language, create a new JSON file following the same key structure.

## 📜 License

By contributing, you agree that your contributions will be licensed under the [AGPL-3.0 License](LICENSE).
