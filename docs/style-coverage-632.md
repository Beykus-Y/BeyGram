# Охват экранов пятью стилями (#632)

Матрица покрытия для зонтика #620. Стили: Default (D), Material 2 (M2), Material 3 (M3), Liquid Glass (LG), Signature (S).
Статусы:

- **style-aware**: компонент читает токены `LocalDesignSystem` или общий адаптер (форма, поверхность, типографика, глубина);
- **тема**: компонент следует стилю через `MaterialTheme` (`shapes`, `colorScheme` с тональной лестницей стиля), своих токенов нет;
- **палитра**: меняются только цвета;
- **намеренно**: стиль не применяется по причине, записанной в таблице;
- **не проверено**: устройство и эмулятор не смотрели (облачная сессия не собирает APK).

На устройстве не проверено ничего из таблицы: светлая, тёмная и AMOLED темы, fontScale 1×/1.3×/2×, закреплённый и плавающий бар. Это остаётся приёмкой на устройстве.

## Общие адаптеры

| Адаптер | Где | Что отличает стили |
|---|---|---|
| `styledShape` / `styledTopShape` | `ui/design/DesignSystem.kt` | M2 срезает радиусы до 4dp, остальные без изменений (#656) |
| `groupCardShape` / `groupSurfaceColor` | `DesignSystem.kt`, `components/SurfaceColors.kt` | карточки настроек: M2 4dp, M3 тональный контейнер 12dp, LG 24dp, S 18dp |
| `sheetSurfaceColor` | `components/SurfaceColors.kt` | M3 кладёт листы на surfaceContainerLow |
| `topAppBarSurface` | `components/AppBarSurface.kt` | M2 приподнятая панель 4dp |
| `ProfileToolbar` | `screens/profile/ProfileScreen.kt` | панель вложенных страниц: M2 приподнятая, LG полупрозрачная, S сплошная, вес заголовка по стилю |
| `ProxyTopBar` | `screens/proxy/ProxyComponents.kt` | через `topAppBarSurface` |
| `MainTabTitle`, `listRowTitleWeight`, `StyledListDivider` | `components/MainTabTitle.kt` | заголовки вкладок по `mainScreen`, разделители строк в M2 (#660) |
| `profileCardSurface` | `screens/profile/ProfileCardStyle.kt` | форма `groupCard`, тональная заливка, `cardElevation` (#660) |
| `Button`, `OutlinedButton`, `TextButton`, `Switch` | `components/StyledControls.kt` | обёртки по стилю (#653) |
| Material-виджеты (`AlertDialog`, `DropdownMenu`, поля ввода) | `ui/theme/Theme.kt` | шкала форм стиля и тональная лестница `containerTonalStep` |

## Матрица экранов

| Экран / компонент | D | M2 | M3 | LG | S | Источник |
|---|---|---|---|---|---|---|
| Список чатов, папки, поиск, архив | style-aware | style-aware | style-aware | style-aware | style-aware | #627–#631 |
| Нижняя навигация | style-aware | style-aware | style-aware | style-aware | style-aware | #625, #628–#631 |
| Планшетная рейка | style-aware | style-aware | style-aware | палитра | палитра | этот PR: M3 тональный контейнер, M2 тень; вкладки общие с нижним баром |
| Контакты | style-aware | style-aware | style-aware | style-aware | style-aware | #660 |
| Звонки | style-aware | style-aware | style-aware | style-aware | style-aware | #660 |
| Настройки (корень и ~35 вложенных страниц) | style-aware | style-aware | style-aware | style-aware | style-aware | #638, #640, #655, #660, `ProfileToolbar` |
| Звонки, Контакты (корень вкладок) | style-aware | style-aware | style-aware | style-aware | style-aware | #670: список в одной групповой карточке `groupCardShape`/`groupSurfaceColor`, без стекла на строках; цвет статуса звонка по роли (danger/success/нейтральный) |
| Обновления, маршрут прокси второго аккаунта | style-aware | style-aware | style-aware | style-aware | style-aware | #662 |
| Прокси | style-aware | style-aware | style-aware | style-aware | style-aware | `ProxyTopBar` |
| Профиль пользователя, группы | style-aware | style-aware | style-aware | style-aware | style-aware | `ProfileToolbar`, #660 |
| Premium, Stars, подарки | style-aware | style-aware | style-aware | style-aware | style-aware | #660 (`profileCardSurface`) |
| Чат: верхняя панель | style-aware | style-aware | style-aware | style-aware | style-aware | `ChatTopBar` по `components.topBar` |
| Чат: пузыри, ответ | style-aware | style-aware | style-aware | style-aware | style-aware | `design.bubbles`, `replyRadius` |
| Чат: композер, кнопка отправки | style-aware | style-aware | style-aware | style-aware | style-aware | `shapes.composer`, `squareSendButton`, `components.composer` |
| Чат: меню сообщения (полноэкранное) | style-aware | style-aware | style-aware | style-aware | style-aware | этот PR: `topAppBarSurface` |
| Чат: реакции, плавающая дата, фильтры поиска | намеренно | намеренно | намеренно | намеренно | намеренно | капсулы 50% одинаковы во всех стилях: это форма-метка, а не поверхность |
| Чат: вложения, стикеры, эмодзи, боты (листы) | тема | тема | style-aware | тема | тема | `sheetSurfaceColor`, формы Material |
| Mini App | style-aware | style-aware | style-aware | style-aware | style-aware | #662 |
| Публикация истории | style-aware | style-aware | style-aware | style-aware | style-aware | #662 |
| Просмотр историй, камера, медиа-просмотрщик | намеренно | намеренно | намеренно | намеренно | намеренно | иммерсивный тёмный слой поверх медиа, стиль не должен его перекрашивать |
| Музыка: поиск, мои треки, плейлисты | style-aware | style-aware | style-aware | style-aware | style-aware | `topAppBarSurface`, `styledShape` |
| Мини-плеер, Now Playing, очередь | style-aware | style-aware | style-aware | style-aware | style-aware | `sheetSurfaceColor`, `styledShape` |
| Диалоги (`AlertDialog`, ~60 файлов) | тема | тема | тема | тема | тема | `shapes.extraLarge`, `surfaceContainerHigh` |
| Контекстные меню (`DropdownMenu`) | тема | тема | тема | тема | тема | `shapes.extraSmall` |
| Snackbar / Toast | намеренно | намеренно | намеренно | намеренно | намеренно | системные Toast, своего снекбара в приложении нет |
| Пустые, загрузочные, ошибочные состояния | палитра | палитра | палитра | палитра | палитра | текст и индикаторы без поверхностей, менять нечего |
| Вход (телефон, код, пароль) | style-aware | style-aware | style-aware | style-aware | style-aware | `styledShape`, StyledControls |
| Стартовый экран | намеренно | намеренно | намеренно | намеренно | намеренно | брендовый сплэш с логотипом |
| Оформление (выбор стиля, превью) | намеренно | намеренно | намеренно | намеренно | намеренно | превью рисует каждый стиль своими токенами |
| Диагностика, ресурсы | style-aware | style-aware | style-aware | style-aware | style-aware | `ProfileToolbar`, `styledShape` |
| Системные status/navigation bars | палитра | палитра | палитра | палитра | палитра | цвет экрана, edge-to-edge |

## Намеренные исключения

- Капсулы (реакции, дата, счётчики, фильтры) остаются капсулами: это метки, а не поверхности.
- Медиа-слои (истории, камера, просмотрщик) остаются тёмными во всех стилях.
- Default не меняется. Исключение: в #662 заголовки «Обновлений» и маршрута прокси второго аккаунта приведены к общей панели настроек.

## Что осталось

- Приёмка на устройстве по всей таблице (темы, fontScale, режим бара).
- Скриншот-тесты: #634.
