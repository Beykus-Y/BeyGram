# Музыка в профиле

Что Telegram показывает в профиле и как это сделано в BeyGram. Документ фиксирует выводы исследования и решения, которые приняты по нему.

## Что известно о Telegram

- Официальный блог Telegram от 31 августа 2025 г. «Music on Profiles, Stickers Mini App, New Profiles, and More» (https://telegram.org/blog/profile-music-gift-themes):
  - аудио из любого чата добавляется в профиль одним нажатием «Add to Profile» в медиаплеере или через «Save to… > Profile»;
  - трек появляется в полосе под фото профиля;
  - новые треки складываются в плейлист профиля, предыдущие остаются в нём;
  - в блоге не сказано про Premium, форматы, лимиты количества и длительности, автовоспроизведение и отдельную настройку приватности.
- TDLib (`td_api.tl` в ветке master и Java-привязки `app/libs/tdlib-java.jar`):
  - `userFullInfo.first_profile_audio:audio` — первый трек профиля, без отдельного запроса;
  - `getUserProfileAudios user_id offset limit = Audios` — список треков, до 100 за запрос;
  - `addProfileAudio` — добавляет трек в начало списка; в ветке master принимает `inputAudio`, а в jar, который собран в BeyGram, поля передаются плоско: `audio:InputFile, duration, title, performer`. Компилятор использует jar, поэтому BeyGram следует ему;
  - `setProfileAudioPosition file_id after_file_id` — порядок, `after_file_id = 0` ставит трек первым;
  - `removeProfileAudio file_id`, `isProfileAudio file_id` (404, если трека нет);
  - `userPrivacySettingShowProfileAudio` — настройка, кто видит музыку профиля.
- В Bot API есть похожий метод `getUserProfileAudios`; для клиента он не нужен.

## Как устроено в BeyGram

| Часть | Файлы |
| --- | --- |
| Модель трека | `domain/model/ProfileAudio.kt`, поле `ProfileInfo.profileAudios` |
| Запросы TDLib и кэш по пользователю | `data/tdlib/TdProfiles.kt`, маппинг в `data/tdlib/TdProfileAudios.kt` |
| Интерфейс репозитория | `domain/repository/TelegramRepository.kt`, `TdLibTelegramRepository.kt`, `UnavailableTelegramRepository.kt` |
| Полоса под аватаром и список | `ui/screens/profile/ProfileMusic.kt`, вставка в `ProfileScreen.kt` |
| Ключ и элемент общего плеера | `ui/screens/profile/ProfileMusicModel.kt` |
| Добавление из плеера | `ui/screens/playback/MusicPlayerSheet.kt`, действие «В профиль» (`LocalAddProfileAudio`, задаётся в `BeyGramApp.kt`) |
| Строки | `res/values*/strings_profile_music.xml` |

Загрузка списка идёт вместе с загрузкой профиля собеседника (`loadUser`) и своего профиля (`refreshMe`). Ошибка приватности не ломает профиль: список просто очищается. Мутации (добавить, удалить, поставить первым) обновляют список своего профиля сразу после ответа TDLib.

## Решения

- Полоса стоит сразу под аватаром, как написано в блоге («beneath your photo»). Если в профиле нет треков, полосы нет.
- Нажатие на кнопку воспроизведения играет очередь из треков профиля, начиная с выбранного. Если играет трек этого профиля, полоса показывает его.
- Нажатие на полосу с несколькими треками открывает список. У своего профиля в списке есть меню: «Переместить первым» и «Удалить из профиля».
- Воспроизведение идёт через `PlaybackEngine` и `PlaybackQueueSource`, как у музыки из чатов. Файл скачивается лениво, при первом запуске.
- Добавить можно только аудиофайл из чата, который TDLib знает у активного аккаунта. Действие скрыто для секретных, защищённых и чужих аккаунтов.
- Строки в UI не упоминают TDLib.

## Не сделано и не проверено

- Настройка приватности `userPrivacySettingShowProfileAudio` в экране «Конфиденциальность» пока не добавлена.
- Не известны условия Premium и лимиты количества треков: TDLib вернёт ошибку, и BeyGram покажет общий текст.
- Ничего из этого не проверено на устройстве: положение полосы, воспроизведение с докачкой, порядок в списке, действие «В профиль» в плеере.
