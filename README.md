# Aajaj Defender — prebuilt .exe

Эта ветка (`prebuilt-exe`) содержит только готовый бинарь для скачивания.

## Скачать

[AajajDefender.exe](https://github.com/Givalimer/Aajaj/raw/prebuilt-exe/AajajDefender.exe) (~640 KB)

- target: Windows x64
- собрано: llvm-mingw (clang 20 + UCRT), статическая линковка, один файл
- исходники: ветка `feature/antivirus-win-meme`, папка `antivirus-win/`

## Запуск

Скачай `AajajDefender.exe`, запусти на Windows 10 / 11.

SmartScreen может ругаться (бинарь не подписан) — "More info" → "Run anyway".
Это нормально для любой неподписанной самодельной программы, не связано с контентом.

## Что это

Мемный "антивирус". Ничего не сканирует, ничего не трогает. Только рисует красивый GUI,
имитирует сканирование, кидает мемные попапы и крутит GDI-эффекты в своём окне.
Подробнее — в README на ветке `feature/antivirus-win-meme`.
