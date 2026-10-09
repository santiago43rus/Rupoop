# 🏗️ Архитектура Rupoop

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
│   ├── BackupManager.kt       # Резервное копирование и перенос данных без аккаунта
│   ├── ContentFeedController.kt # Управление контентом рекомендаций, подписок и истории
│   ├── DownloadIndexer.kt     # Индексатор ранее скачанных медиафайлов на устройстве
│   ├── DownloadTracker.kt     # Менеджер отслеживания скачанных файлов
│   ├── MainFeedRecommendationStrategy.kt # Стратегия рекомендаций для главной страницы
│   ├── Models.kt              # Модели данных (UserRegistry, SearchResult, Author, Playlist)
│   ├── NavigationController.kt  # Управление навигацией в приложении
│   ├── RecommendationUtils.kt # Общие утилиты рекомендательной системы
│   ├── RelatedVideoRecommendationStrategy.kt # Умный поиск сиквелов и рекомендаций для 4 платформ
│   ├── SearchController.kt    # Управление поиском, историей запросов и фильтрами по источникам
│   ├── SequelPredictor.kt     # Базовый алгоритм предсказания серий и частей
│   ├── SettingsManager.kt     # Хранилище настроек приложения (SharedPreferences)
│   ├── TagWeightCalculator.kt # Калькулятор весов тегов для персонализации рекомендаций
│   └── UserRegistryManager.kt # Локальный менеджер реестра пользователя (лайки, скрытые, история)
├── network/
│   ├── ApiInterfaces.kt       # API-интерфейсы Retrofit для Rutube, GitHub, Gist
│   ├── LordfilmSearchEngine.kt # Парсер поисковой выдачи DLE зеркал Lordfilm
│   ├── NetworkMonitor.kt      # Мониторинг сетевой активности устройства
│   ├── OkSearchEngine.kt      # Поисковый движок по видео Одноклассников (OK.ru)
│   ├── PlatformVideoSearchEngine.kt # Абстракция и резолвер поиска сиквелов по платформам (SOLID)
│   ├── RetrofitClient.kt      # Настройка HTTP-клиента OkHttp и десериализации JSON
│   └── VkSearchEngine.kt      # Поисковый движок по видео ВКонтакте (VK Video)
├── parser/
│   ├── JsPackerUnpacker.kt    # Деобфускация упакованных JavaScript-скриптов плееров Dean Edwards
│   ├── OkVideoParser.kt       # Извлечение MP4/HLS потоков из видео Одноклассников
│   ├── ParsedVideo.kt         # Унифицированная модель распарсенного медиапотока
│   ├── UnifiedWebVideoParser.kt # Парсер веб-страниц, iframe и балансеров (Lordfilm, AnimeGo и др.)
│   ├── UniversalVideoParser.kt  # Маршрутизатор потоков по платформам и прямым ссылкам
│   └── VkVideoParser.kt       # Извлечение прямых HLS/MP4 потоков из VK Видео
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
