# 🛠️ Сборка и настройка проекта Rupoop

Инструкция для разработчиков по локальной сборке и конфигурации ключей.

## 📋 Требования

- **Android Studio:** Ladybug+ (или Gradle 9.x CLI)
- **JDK:** 17+
- **Android SDK:** 37
- **Min SDK:** 29 (Android 10)

## 🚀 Сборка проекта

### 1. Клонирование и переключение на стабильный релиз (тег)

Чтобы собрать **стабильную релизную версию**, а не текущий рабочий коммит ветки:

```bash
git clone https://github.com/santiago43rus/Rupoop.git
cd Rupoop

# Автоматически переключиться на самый свежий стабильный релизный тег:
git checkout $(git describe --tags `git rev-list --tags --max-count=1`)

# Либо переключиться на конкретный тег вручную:
# git checkout v1.1.0
```

### 2. Сборка через Gradle

```bash
cp local.properties.example local.properties
# Отредактируйте local.properties — заполните ключи (инструкция ниже)

# Сборка Debug APK:
./gradlew assembleDebug

# Сборка подписанного Release APK (требуется файл ключа и пароли в local.properties):
./gradlew assembleRelease
```

Готовые APK появятся в директориях:
- Debug: `app/build/outputs/apk/debug/Rupoop-vX.X.X-debug.apk`
- Release: `app/build/outputs/apk/release/Rupoop-vX.X.X-release.apk`

---

## 🔐 Настройка `local.properties`

Файл `local.properties` хранит приватные ключи и конфигурации, которые **не коммитятся в Git** (указан в `.gitignore`).

### `sdk.dir`
Путь к установленному Android SDK на локальном компьютере:
```properties
sdk.dir=C\:\\Users\\Username\\AppData\\Local\\Android\\Sdk
```

### `GH_CLIENT_ID` и `GH_CLIENT_SECRET`
Учётные данные GitHub OAuth App для синхронизации пользовательских данных через GitHub Gist:
1. Перейдите в [GitHub Developer Settings](https://github.com/settings/developers).
2. Создайте **New OAuth App**.
3. Укажите Authorization callback URL: `rupoop://auth`.
4. Скопируйте `Client ID` и сгенерированный `Client Secret`.

```properties
GH_CLIENT_ID=ваш_client_id
GH_CLIENT_SECRET=ваш_client_secret
```

### `PROXY_URL`
URL CORS-прокси (Cloudflare Workers) для обмена OAuth-кода на токен GitHub. Исходный код воркера доступен в директории `server/`.
```properties
PROXY_URL=https://rupoop-proxy.example.workers.dev/
```

### `DONATE_URL`
Ссылка на страницу приёма донатов (CloudTips, Boosty и т.д.):
```properties
DONATE_URL=https://pay.cloudtips.ru/p/xxxxxx
```

### Ключи для релизной подписи (Keystore)
Для сборки подписанного релизного APK локально:
```properties
KEYSTORE_PASSWORD=пароль_хранилища
KEY_ALIAS=алиас_ключа
KEY_PASSWORD=пароль_ключа
```
Файл ключа помещается в `app/keystore.jks`.

---

## ⚙️ Настройка GitHub Secrets (для CI/CD)

В репозитории настроен автоматический пайплайн `.github/workflows/build.yml`. При пуше тега с версией (`v1.1.0` и т.д.) проект автоматически собирается и публикуется в GitHub Releases.

Для успешной релизной сборки добавьте в **Settings → Secrets and variables → Actions**:
- `GH_CLIENT_ID`
- `PROXY_URL`
- `DONATE_URL`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`
- `KEYSTORE_BASE64` (файл `keystore.jks`, закодированный в base64: `base64 -w 0 keystore.jks`)
