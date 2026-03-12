# Участие в разработке Rupoop

Спасибо, что хотите помочь! 🎉

## Как внести вклад

1. **Fork** этого репозитория
2. Создайте ветку для фичи: `git checkout -b feature/my-feature`
3. Внесите изменения и закоммитьте: `git commit -m "Добавил новую фичу"`
4. Запушьте ветку: `git push origin feature/my-feature`
5. Откройте **Pull Request**

## Требования к коду

- **Kotlin** — используйте идиоматический Kotlin
- **Compose** — новые UI-компоненты только на Jetpack Compose
- **Именование** — `camelCase` для функций/переменных, `PascalCase` для классов/composables
- **Комментарии** — на русском или английском, описывайте «почему», а не «что»
- **Без хардкода** — строки через `strings.xml` (по мере возможности), цвета через тему

## Структура проекта

- `screen/` — полноэкранные composable (Home, Subscriptions, Author, Settings)
- `components/` — переиспользуемые composable-компоненты
- `data/` — модели, менеджеры данных, рекомендации
- `network/` — API-интерфейсы, Retrofit
- `player/` — видеоплеер на ExoPlayer
- `service/` — фоновые сервисы (загрузка, синхронизация)
- `auth/` — авторизация GitHub, Gist sync
- `util/` — утилиты, enum-ы

## Баг-репорты и фичи

Используйте шаблоны Issues на GitHub:
- 🐛 **Bug Report** — для багов
- 💡 **Feature Request** — для идей

## Сборка и тестирование

```bash
# Собрать debug APK
./gradlew assembleDebug

# Запустить тесты
./gradlew test
```

## Лицензия

Отправляя PR, вы соглашаетесь с лицензией [MIT](LICENSE).

