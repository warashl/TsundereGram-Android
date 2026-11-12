# TsundereGram для Android

Неофициальный клиент Telegram для Android. Основан на открытом исходном коде
[Telegram for Android](https://github.com/DrKLO/Telegram).

[Сайт](https://warashl.github.io) ·
[Скачать APK](https://github.com/warashl/warashl.github.io/releases/latest/download/TsundereGram.apk) ·
[Канал @tsunderegram](https://t.me/tsunderegram)

> An unofficial Telegram client for Android based on the official Telegram for Android source code.
> Build instructions are below. Licensed under GPL-3.0.

## Возможности

- **Режим призрака** — читай без «прочитано», заходи без «в сети», пиши без «печатает», смотри истории незаметно.
- **Удалённые сообщения и история правок** — остаются у тебя и хранятся только на устройстве.
- **Экспорт чатов** — вся переписка в одном HTML-файле, который выглядит как настоящий чат.
- **Голосовые в текст, ИИ-помощник и переводчик** — без Premium.
- **Оформление** — баннеры профиля, значки, градиентные ники, свои пузыри сообщений, рамки аватарок, иконки приложения, настройка плеера, анимации отправки.
- **Удобства** — лента каналов, «Сейчас играет» из Last.fm, курсы валют, своё боковое меню, Glyph для Nothing Phone.
- **Плагины**, бэкап настроек и обмен темой одним файлом.

## Сборка

Понадобятся:

- JDK 21;
- Android SDK (platform 37, build-tools 36.0.0) и NDK 27.2.12479018;
- CMake, bash и Python 3.11 (для нативной части движка плагинов).

1. Склонируй репозиторий вместе с подмодулями:

   ```sh
   git clone --recursive https://github.com/warashl/TsundereGram-Android.git
   # если уже склонировал без подмодулей:
   git submodule update --init --recursive
   ```

2. Получи свои `api_id` и `api_hash` на [my.telegram.org](https://my.telegram.org) и пропиши их
   в `local.properties` в корне проекта:

   ```properties
   sdk.dir=/path/to/Android/Sdk
   TELEGRAM_APP_ID=123456
   TELEGRAM_APP_HASH=0123456789abcdef0123456789abcdef
   # необязательно:
   # MAPS_API_KEY=...
   # CHAQUOPY_BUILD_PYTHON=python3.11
   ```

3. Замени `TMessagesProj/google-services.json` на свой из Firebase Console (пакет
   `com.tsunderegram.app`) — без него не будут работать push-уведомления.

4. Собери APK:

   ```sh
   ./gradlew TMessagesProj:assembleDebug
   ```

   Готовый файл: `TMessagesProj/build/outputs/apk/debug/TsundereGram.apk`.

Адрес сервера TsundereGram (значки, баннеры, рамки) подставляется в
`TMessagesProj/src/main/java/app/tsunderegram/security/TsundereApi.java` вместо `@TSUNDERE_API_URL@`.
Если его не указать, адрес можно ввести в самом приложении.

Чтобы подписать сборку своим ключом, положи `release.keystore` в `TMessagesProj/` и добавь
в `local.properties` параметры `KEYSTORE_PASS`, `ALIAS_NAME` и `ALIAS_PASS`. Без них APK
подписывается отладочным ключом Android.

## Лицензия

[GPL-3.0](LICENSE). Отдельные файлы распространяются на условиях, указанных в их заголовках;
сторонние библиотеки — под своими лицензиями.

TsundereGram — неофициальный клиент и не связан с Telegram FZ-LLC.
