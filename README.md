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
- 🏠 **Главная** — Персонализированный микс и умные рекомендации на основе истории просмотров и выбранных жанров в разнобой (как на YouTube) с параллельной загрузкой категорий.
- 🚫 **Скрытие контента** — Видео, помеченные как «Не интересно» или дизлайкнутые, гарантированно удаляются из всех лент, поиска, каналов авторов и подписок в реальном времени. В настройках доступен удобный раздел «Скрытые и неинтересные видео» для их разблокировки.
- 🔍 **Поиск** — Текстовый и голосовой ввод
- 📺 **Подписки** — Подписывайтесь на каналы и следите за обновлениями
- 📚 **Библиотека** — История, лайки, «Смотреть позже», плейлисты, загрузки
- ⬇️ **Скачивание** — Загрузка видео и аудиодорожек (m4a) с отслеживанием прогресса в приложении
- 📻 **Фоновое воспроизведение** — Прослушивание видео в фоновом режиме с поддержкой системных элементов управления Media3 (даже при заблокированном экране)
- 🔔 **Управление уведомлениями** — Детальная настройка уведомлений для процессов скачивания и фонового воспроизведения
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

Проект спроектирован по канонам чистой архитектуры и принципам SOLID. Все исходные Kotlin-файлы строго ограничены лимитом **не более 300 строк кода**, что повышает читаемость, упрощает тестирование и расширяемость.

```
app/src/main/java/com/santiago43rus/rupoop/
├── MainActivity.kt            # Точка входа в приложение, управление системными экранами
├── RupoopApplication.kt       # Класс приложения, инициализация Coil и WorkManager
├── RutubeApp.kt               # Корневой Composable (Scaffold, управление жестами и BottomBar)
├── AppViewModel.kt            # Главный ViewModel (разделен с использованием паттерна делегирования свойств)
├── AppViewModelActions.kt     # Расширение ViewModel для пользовательских действий (подписки, плейлисты)
├── AppViewModelDelegates.kt   # Делегирование свойств и вспомогательные функции для ViewModel
├── auth/
│   ├── AuthController.kt      # Контроллер процесса OAuth-авторизации
│   ├── GitHubAuthManager.kt   # Интеграция OAuth через AppAuth
│   └── GistSyncManager.kt     # Менеджер синхронизации данных пользователя через GitHub Gist
├── components/
│   ├── AppTopBar.kt           # Компонент верхней панели (поиск, профиль, синхронизация)
│   ├── Dialogs.kt             # Диалоговые окна плейлистов, донатов и подтверждения действий
│   ├── DownloadCard.kt        # Карточка отображения отдельной загрузки с прогрессом
│   ├── DownloadComponents.kt  # Экраны и карточки раздела скачанных файлов
│   ├── LibraryComponents.kt   # Списки разделов библиотеки
│   ├── VideoCardItem.kt       # Карточка видео для списков (адаптивная, с меню действий)
│   ├── VideoDetailsScreen.kt  # Блок описания видео, лайков, автора под плеером
│   └── VideoListScreen.kt     # Экран вывода списков видео (история, watch later, плейлисты)
├── data/
│   ├── Models.kt              # Модели данных (UserRegistry, SearchResult, Author, Playlist)
│   ├── SettingsManager.kt     # Хранилище настроек приложения (SharedPreferences)
│   ├── UserRegistryManager.kt # Локальный менеджер реестра пользователя (лайки, скрытые, история)
│   ├── ContentFeedController.kt # Управление контентом рекомендаций, подписок и истории
│   ├── NavigationController.kt  # Управление навигацией в приложении
│   ├── SearchController.kt      # Управление поиском, историей запросов и подсказками
│   ├── SequelPredictor.kt       # Алгоритм подбора следующего видео для автовоспроизведения
│   ├── TagWeightCalculator.kt   # Калькулятор весов тегов для персонализации рекомендаций
│   ├── DownloadTracker.kt       # Менеджер отслеживания скачанных файлов
│   ├── DownloadIndexer.kt       # Индексатор ранее скачанных медиафайлов на устройстве
│   ├── MainFeedRecommendationStrategy.kt # Стратегия рекомендаций для главной страницы
│   ├── RelatedVideoRecommendationStrategy.kt # Стратегия рекомендаций похожих видео
│   └── RecommendationUtils.kt   # Общие утилиты рекомендательной системы
├── network/
│   ├── ApiInterfaces.kt       # API-интерфейсы Retrofit для Rutube, GitHub, Gist
│   ├── RetrofitClient.kt      # Настройка HTTP-клиента OkHttp и десериализации JSON
│   └── NetworkMonitor.kt      # Мониторинг сетевой активности устройства
├── player/
│   ├── PlaybackController.kt  # Главный контроллер плеера (ExoPlayer)
│   ├── PlaybackControllerLocal.kt   # Контроллер воспроизведения локальных (скачанных) файлов
│   ├── PlaybackControllerService.kt # Синхронизация воспроизведения с фоновой службой
│   ├── ControlsOverlay.kt     # Элементы управления плеера поверх видео (Play, Next, Seek)
│   ├── MoreVideosOverlay.kt   # Плейлист похожих видео поверх плеера (кнопка «Еще видео»)
│   ├── PlayerHelperComponents.kt # Диалог качества, мини-плеер, формат времени
│   ├── PlayerOverlayComponents.kt # Голосовой поиск, анимация перемотки, индикатор скорости
│   └── VideoPlayerComponents.kt # ExoPlayer контейнер и детекторы жестов (swipe, double tap)
├── screen/
│   ├── AuthorScreen.kt        # Страница автора/канала (видео, плейлисты, о канале)
│   ├── HiddenVideosScreen.kt  # Экран управления скрытыми и неинтересными видео
│   ├── LibraryContent.kt      # Экран Библиотеки (история, загрузки, плейлисты, watch later)
│   ├── MainFeedScreen.kt      # Экран главной ленты видео с вкладками категорий
│   ├── SubscriptionsScreen.kt # Экран подписок на каналы
│   ├── SearchOverlay.kt       # Полноэкранный оверлей результатов поиска
│   ├── SearchSuggestionsOverlay.kt # Оверлей поисковых подсказок (suggestions)
│   ├── SettingsScreen.kt      # Экран общих настроек приложения
│   ├── SettingsSections.kt    # Секции экрана настроек (история, кеш, поддержка)
│   ├── NotificationSettingsScreen.kt # Экран управления уведомлениями (скачивание, фоновое воспроизведение)
│   ├── RelatedVideosList.kt   # Список похожих видео под плеером в портретной ориентации
│   ├── RutubeAppOverlays.kt   # Координатор диалогов и оверлеев на уровне всего приложения
│   ├── RutubeBottomBar.kt     # Нижняя панель навигации
│   └── RutubePlayerContainer.kt # Контейнер плеера со сложной анимацией перетягивания (swipe-to-collapse)
├── service/
│   ├── DownloadService.kt     # Foreground-служба фонового скачивания файлов
│   ├── DownloadTask.kt        # Логика загрузки HLS-сегментов и извлечения аудиодорожки
│   ├── DownloadServiceNotifications.kt # Расширение службы для управления уведомлениями скачивания
│   ├── PlaybackService.kt     # Фоновая Foreground-служба воспроизведения (Media3 MediaSession)
│   └── SyncWorker.kt          # Периодическая фоновая синхронизация с GitHub Gist (WorkManager)
├── theme/
│   ├── Color.kt               # Константы цветовой схемы (Dark/Light)
│   ├── Theme.kt               # Настройка темы RupoopTheme
│   └── Type.kt                # Типографика Jetpack Compose
└── util/
    ├── FormatterExtensions.kt # Расширения для форматирования просмотров, лайков и дат
    └── Utils.kt               # Утилиты проверки сети, кеша и системных панелей
```

---

## 🌐 Авторизационный Прокси-Сервер (Cloudflare Worker)

Для выполнения безопасной OAuth-авторизации через GitHub в приложении используется легковесный прокси-сервер, развернутый на платформе **Cloudflare Workers**. 

Исходный код прокси открыт и расположен в директории [`/server`](server/):
*   [`index.js`](server/index.js) — оптимизированный, высокопроизводительный JS-код воркера со встроенной **защитой от ботов** (белый список разрешает запросы только на роуты `/auth/token`, `/user` и `/gists`, блокируя сканеры мгновенно на уровне CDN с кодом `403 Forbidden`).
*   [`wrangler.toml.example`](server/wrangler.toml.example) — шаблон конфигурации для быстрого деплоя воркера через Wrangler.

### Зачем нужен прокси-сервер?
По спецификации OAuth 2.0 для обмена кода на `access_token` требуется передать секретный ключ приложения (`Client Secret`). Вшивать секретный ключ в клиентское Android-приложение **категорически запрещено**, так как злоумышленники могут легко извлечь его через декомпиляцию APK. Прокси-сервер скрывает `Client Secret` внутри безопасного окружения переменных среды Cloudflare, обеспечивая 100% безопасность авторизации.

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
