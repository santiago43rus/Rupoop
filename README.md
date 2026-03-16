# 🎬 Rupoop — Альтернативный клиент Rutube

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="128" alt="Rupoop Logo"/>
</p>

<p align="center">
  <a href="https://github.com/santiago43rus/Rupoop/actions"><img src="https://github.com/santiago43rus/Rupoop/workflows/Build%20APK/badge.svg" alt="Build Status"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License: MIT"/></a>
  <a href="https://github.com/santiago43rus/Rupoop/releases"><img src="https://img.shields.io/github/v/release/santiago43rus/Rupoop?include_prereleases" alt="Release"/></a>
</p>

Современный Android-клиент для [Rutube](https://rutube.ru), вдохновлённый дизайном YouTube. Написан на Kotlin с использованием Jetpack Compose и Material 3.

---

## ✨ Возможности

- 🎥 **Видеоплеер** — ExoPlayer (Media3) с HLS, качество до 1080p, управление жестами (двойной тап для перемотки, long press для 2x скорости)
- 🏠 **Главная** — Персонализированные рекомендации на основе истории просмотра
- 🔍 **Поиск** — Текстовый и голосовой ввод
- 📺 **Подписки** — Подписывайтесь на каналы и следите за обновлениями
- 📚 **Библиотека** — История, лайки, «Смотреть позже», плейлисты, загрузки
- ⬇️ **Скачивание** — Загрузка видео с отслеживанием прогресса в приложении
- 🔄 **Синхронизация** — Все данные синхронизируются через GitHub Gist (авторизация OAuth)
- 🌙 **Темы** — Тёмная и светлая, переключение иконки приложения
- 💝 **Поддержка** — Возможность поддержать разработчика

---


## 🚀 Быстрый старт

### Требования

- Android Studio Ladybug+ (или Gradle 9.x CLI)
- JDK 17+
- Android SDK 36
- Min SDK 29 (Android 10)

### Сборка

```bash
git clone https://github.com/santiago43rus/Rupoop.git
cd Rupoop
cp local.properties.example local.properties
# отредактируйте local.properties — заполните ключи (инструкция ниже)
./gradlew assembleDebug
```

APK → `app/build/outputs/apk/debug/`

---

## 🔐 Настройка `local.properties`

Файл `local.properties` хранит секретные ключи, которые **не коммитятся в Git** (указан в `.gitignore`). Каждая переменная на этапе сборки попадает в `BuildConfig` и используется в коде.

### `sdk.dir`

| | |
|---|---|
| **Что это** | Путь к Android SDK на вашем компьютере |
| **Зачем** | Gradle использует его, чтобы найти компилятор, build-tools, системные библиотеки Android |
| **Как получить** | Android Studio прописывает автоматически при первом открытии проекта. Если нет — откройте AS → **Settings** → **Appearance & Behavior** → **System Settings** → **Android SDK** → скопируйте путь из поля «Android SDK Location» |

```properties
sdk.dir=C\:\\Users\\kompu\\AppData\\Local\\Android\\Sdk
```

---

### `GH_CLIENT_ID` и `GH_CLIENT_SECRET`

| | |
|---|---|
| **Что это** | Учётные данные GitHub OAuth App — Client ID и Client Secret |
| **Зачем** | Приложение авторизует пользователя через GitHub, чтобы получить доступ к его Gist-ам. Через Gist-ы происходит синхронизация данных (история просмотров, подписки, плейлисты) между устройствами. Без этих ключей кнопка «Войти» не работает |

**Как получить:**

1. Откройте **https://github.com/settings/developers**
2. Нажмите **«New OAuth App»**
3. Заполните форму:
   - **Application name:** `Rupoop` (любое)
   - **Homepage URL:** `https://github.com/santiago43rus/Rupoop` (любой URL)
   - **Authorization callback URL:** **`rupoop://auth`** ← именно так, это redirect-схема из кода
4. Нажмите **«Register application»**
5. Скопируйте **Client ID** со страницы приложения
6. Нажмите **«Generate a new client secret»** → скопируйте секрет (показывается **только один раз**!)

```properties
GH_CLIENT_ID=Ov23liNg8BumTncLXG8e
GH_CLIENT_SECRET=fb80bc91c32112b740a076a7f8a8685dcb23be4f
```

---

### `PROXY_URL`

| | |
|---|---|
| **Что это** | URL прокси-сервера на Cloudflare Workers |
| **Зачем** | GitHub API не позволяет мобильным приложениям обмениваться OAuth-кодом на токен напрямую (CORS, ограничения). Прокси принимает запросы от приложения, отправляет их на `api.github.com`, и возвращает ответ обратно. Без прокси синхронизация через GitHub не работает |

**Как получить:**

1. Зарегистрируйтесь на **https://dash.cloudflare.com/**
2. Перейдите в **Workers & Pages** → **Create application** → **Create Worker**
3. Назовите (например `rupoop-proxy`)
4. Замените код на CORS-прокси для `api.github.com` и `github.com/login/oauth/access_token`
5. Нажмите **Save and Deploy**
6. Скопируйте URL воркера

```properties
PROXY_URL=https://rupoop-proxy.ijonmarston.workers.dev/
```

---

### `DONATE_URL`

| | |
|---|---|
| **Что это** | Ссылка на страницу приёма донатов |
| **Зачем** | Показывается в настройках приложения как кнопка «Поддержать рублём». Нажатие → открывается браузер. Если пустая — кнопка не показывается |

**Как получить:**

1. Зарегистрируйтесь на **https://cloudtips.ru/** (или Boosty, ЮMoney, и т.д.)
2. Создайте страницу для приёма платежей
3. Скопируйте публичную ссылку

```properties
DONATE_URL=https://pay.cloudtips.ru/p/1988b19b
```

---

## ⚙️ Настройка GitHub Secrets (для автосборки)

GitHub Actions автоматически собирает APK при каждом пуше в `main` или `dev`. Чтобы он мог собрать приложение с рабочими ключами, нужно передать те же переменные, что в `local.properties`, но через зашифрованное хранилище GitHub.

### Что такое Secrets?

**GitHub Secrets** — зашифрованные переменные окружения, привязанные к репозиторию. Они:
- доступны **только** в GitHub Actions (в рантайме CI/CD)
- **не видны** в логах, коде или другим пользователям (GitHub маскирует их звёздочками)
- это стандартный безопасный способ передать пароли/ключи в CI/CD

### Как настроить

1. Откройте ваш репозиторий на GitHub
2. **Settings** → **Secrets and variables** → **Actions**
3. Для каждого секрета: **«New repository secret»** → введите имя и значение

### Обязательные секреты

Без них CI/CD **соберёт APK**, но авторизация и синхронизация в нём работать не будут.

| Имя секрета | Что вставить | Откуда взять |
|-------------|-------------|--------------|
| `GH_CLIENT_ID` | `Ov23liNg8BumTncLXG8e` | Страница OAuth App на GitHub (см. выше) |
| `GH_CLIENT_SECRET` | `fb80bc91c32112b...` | Там же, Client Secret |
| `PROXY_URL` | `https://rupoop-proxy.ijonmarston.workers.dev/` | URL вашего Cloudflare Worker |
| `DONATE_URL` | `https://pay.cloudtips.ru/p/1988b19b` | Ваша страница CloudTips |

### Секреты для подписи APK (опционально)

Нужны **только для Release-сборки** (подписанный APK для публикации). Для Debug-сборки можно не настраивать.

| Имя секрета | Что вставить | Как получить |
|-------------|-------------|--------------|
| `KEYSTORE_BASE64` | Base64-строка файла keystore | `base64 -w 0 keystore.jks` (Linux/Mac) или онлайн-конвертер |
| `KEYSTORE_PASSWORD` | Пароль хранилища | Тот, что задали при создании keystore |
| `KEY_ALIAS` | Имя ключа | Обычно `upload` или `key0` |
| `KEY_PASSWORD` | Пароль ключа | Часто совпадает с KEYSTORE_PASSWORD |

**Как создать keystore** (если его ещё нет):

```bash
keytool -genkeypair -v -keystore keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

Вас попросят ввести пароли и данные (имя, город, организация) — можно заполнить произвольно, на работу приложения не влияет.

Затем закодировать в Base64:

```bash
# Linux / Mac:
base64 -w 0 keystore.jks > keystore_base64.txt

# Windows PowerShell:
[Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore.jks")) > keystore_base64.txt
```

Содержимое `keystore_base64.txt` → вставить как значение секрета `KEYSTORE_BASE64`.

---


## 🏗️ Архитектура

```
app/src/main/java/com/santiago43rus/rupoop/
├── MainActivity.kt            # Entry point, темы
├── AppViewModel.kt            # Основной ViewModel (плеер, навигация, синхронизация)
├── RutubeApp.kt               # Root composable, Scaffold, навигация
├── RupoopApplication.kt       # Application class (Coil, WorkManager)
├── auth/
│   ├── GitHubAuthManager.kt   # OAuth через AppAuth
│   └── GistSyncManager.kt     # Синхронизация данных через Gist
├── components/
│   ├── VideoComponents.kt     # VideoItem, VideoDetails
│   ├── LibraryComponents.kt   # LibraryScreen, LibraryRow
│   ├── DownloadComponents.kt  # Раздел загрузок
│   └── Dialogs.kt             # Диалоги (контент, плейлисты)
├── data/
│   ├── Models.kt              # Data classes
│   ├── SettingsManager.kt     # SharedPreferences
│   ├── UserRegistryManager.kt # Управление данными пользователя
│   ├── RecommendationEngine.kt# Рекомендации
│   └── DownloadTracker.kt     # Трекер загрузок
├── network/
│   ├── ApiInterfaces.kt       # Retrofit interfaces
│   └── RetrofitClient.kt      # HTTP клиент
├── player/
│   └── VideoPlayerComponents.kt # ExoPlayer обёртка
├── screen/
│   ├── HomeScreen.kt
│   ├── SubscriptionsScreen.kt
│   ├── AuthorScreen.kt
│   └── SettingsScreen.kt
├── service/
│   ├── DownloadService.kt     # Foreground service загрузок
│   └── SyncWorker.kt          # WorkManager для фоновой синхронизации
└── util/
    └── Utils.kt               # Утилиты, enums
```

---

## 🤝 Участие в разработке

Подробности в [CONTRIBUTING.md](CONTRIBUTING.md).

1. Fork → создайте ветку → внесите изменения → откройте PR
2. Используйте Kotlin code style
3. Описывайте коммиты на русском или английском

---

## 💝 Поддержать

Если проект полезен — поддержите разработчика:

- 💳 [**CloudTips** — быстрый перевод](https://pay.cloudtips.ru/p/1988b19b)

---

## 📄 Лицензия

[MIT License](LICENSE) © 2026 santiago43rus
