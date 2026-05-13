# Aajaj Defender 2026 Ultra

Мемный "антивирус" для Windows. Чистый Win32 + GDI, без зависимостей, один `.exe`.

**Пародия. Ничего не сканирует, ничего не защищает, ничего не трогает. Это мем.**

## Фичи

- Фейковое глубокое сканирование с анимированным прогресс-баром и живой лентой "проверяемых" файлов
- Рандомные мемные "угрозы": `your_mom.exe`, `skibidi_toilet.dll`, `amogus_miner.bat`, `ohio_rizz.scr`, и т.д.
- Мемные сигнатуры: `Trojan.Skibidi.Gen`, `Worm.Ohio.Rizz.A`, `Ransom.BruhMoment`
- GDI-эффекты: тряска окна, мигающие красные рамки при находке, инверт, зелёный "матрица"-дождь
- Звуки: `PlaySound` алиасы Windows + `Beep`-мелодии (bruh, vine-boom-ish, metal-pipe-ish, XP error trio)
- Кнопки-приколы:
  - **REMOVE THREATS** — шторм фейковых диалогов
  - **QUARANTINE** — матрица-дождь + смешной попап
  - **PANIC!!!** — комбо всех эффектов сразу
  - **TROLL CURSOR** — процедурный кастомный курсор со стрелкой и красным крестом
- DPI-aware (PerMonitorV2), common-controls v6 визуальные стили
- Один статический `.exe`, без установщика

## Как забрать `.exe`

Сборка делается в CI на `windows-latest`:

1. Вкладка **Actions** → workflow **antivirus-win**
2. Последний успешный run на твоей ветке
3. Скачай артефакт `AajajDefender-windows-x64`
4. Разархивируй, запусти `AajajDefender.exe`

Теги `refs/tags/*` автоматически прикладывают `.exe` к GitHub Release.

## Локальная сборка (Windows)

Нужно: Visual Studio 2022 (Desktop C++) или Build Tools + CMake 3.16+.

```pwsh
cd antivirus-win
cmake -S . -B build -G "Visual Studio 17 2022" -A x64
cmake --build build --config Release
.\build\Release\AajajDefender.exe
```

Или с Ninja в MSVC dev shell:

```pwsh
cd antivirus-win
cmake -S . -B build -G "Ninja" -DCMAKE_BUILD_TYPE=Release
cmake --build build
.\build\AajajDefender.exe
```

## Структура

```
antivirus-win/
  CMakeLists.txt
  src/main.cpp          # окно, GDI, "сканер", эффекты, звуки - всё здесь
  res/
    resources.rc        # version info + манифест
    app.manifest        # common-controls v6, DPI-aware
```

## Дисклеймер

Программа — пародия. Она **не** изменяет файлы, **не** трогает реестр, **не** сканирует ничего, **не** помещает ничего в карантин. Все "угрозы" — рандомно сгенерированные строки. Все "действия" — это `MessageBox` и GDI-эффекты внутри своего окна. Не используй это чтобы реально что-то защищать. Был предупреждён, skibidi.
