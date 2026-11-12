# Маскот BeyGram на BG Motion — план интеграции

> Статус: черновик плана, код ещё не менялся. Ветка `feat-mascot` создана от актуального `DEV`.
> Локальный `DEV` обновлён до `8c397403` (fast-forward с `origin/DEV`).
> BG Media изучен на коммите `2368c132660d73ee976ec3a50b67249417938a7f` — это тот же коммит,
> который сейчас закреплён в `app/src/main/cpp/CMakeLists.txt`.

## 1. Что уже есть

### 1.1. В BeyGram

- **BG Beat** уже встроен: `media/bgbeat/` + `app/src/main/cpp/bgbeat_jni.cpp`.
  - Анализ запускается для текущего трека, когда включён эксперимент BG Beat.
  - Результат — JSON (`BgBeatAnalysis`): BPM, beats, downbeats, waveform, phrase loudness.
  - Кэш анализа лежит в app-private `filesDir/bgbeat-cache/account_<id>/` и разделён по аккаунтам.
  - Есть фильтры: только `MediaKind.Audio`, не secret/system-private, файл должен быть полностью скачан.
- **BG Mix** уже встроен: `media/bgmix/` + `bgmix_jni.cpp`, нативный движок микширует и делает переходы.
- **Плеер**: `ui/screens/playback/MusicPlayerSheet.kt`.
  - Позиция приходит из `PlaybackEngine.state` примерно 4 раза в секунду (`PROGRESS_INTERVAL_MS = 250`).
  - В UI уже есть `rememberSmoothPositionMs()` — экстраполяция позиции между тиками на каждом кадре.
  - Артворк — `PlayerArtwork()`; ниже идут метаданные, seek, транспорт, `BgBeatSection`, действия.
- **Эксперименты**: `ui/screens/settings/ExperimentsScreen.kt`, группа «Музыка» с переключателями BG Mix и BG Beat.
- **Motion-настройки**: `ui/motion/MotionSettings.kt` и `ui/accessibility/MotionPreferences.kt`.
  - Есть master switch, системный reduced motion, категория `MotionCategory.Media` и intensity 0..150%.
  - Маскот обязан уважать эти настройки (см. issue #728).

### 1.2. В BG Media

- `bgmedia_core` — чистый C++20: BG Beat, BG Sync, BG Motion 2.0 (`CharacterPose`, `ChoreographyBuilder`,
  `ChoreographySampler`), кэш анализа. Без SDL/GPU.
- `bgmedia_vrm` — импорт VRM 0.x (GLB/glTF 2.0, cgltf + stb_image). Без SDL/GPU.
- `bgmedia_rig` — BG Motion 3.x (`DancePlayer`, `DanceDirector`, `MusicalTimeline`), ретаргетинг на
  humanoid-кости, foot IK, secondary dynamics. Зависит от `bgmedia_core` и `bgmedia_vrm`, но не от SDL.
- `bgmedia_sdlgpu` — desktop-рендерер VRM на SDL_GPU. **Android-рендерера нет.**
- `assets/dances/*.json` — 5 авторских клипов (idle, groove, hiphop, sidestep, energetic), ~700 КБ.
- `ProceduralChoreography::showcase()` умеет генерировать showcase-танцы без файлов.
- `models/Model1.vrm` **нельзя распространять**: лицензия `OnlyAuthor` / `Redistribution_Prohibited`.
- Android-документ BG Media (`docs/ANDROID_JNI.md`) прямо говорит: Android-модуля и рендерера пока нет,
  есть только переносимое ядро и desktop-адаптер.

### 1.3. Что это значит для Android

- Анализ, BG Motion 2.0, BG Motion 3.x и импорт VRM **уже собираются под Android NDK**:
  `bgmedia_core`, `bgmedia_vrm`, `bgmedia_rig` не тянут SDL/GPU.
- Не хватает трёх вещей:
  1. JNI-сессии, которая держит `AnalysisResult` и отдаёт позу/кадр по media time.
  2. Android-presenter'а: 2D Canvas или 3D GLES.
  3. Продуктового слоя: где маскот живёт, когда включается, как ведёт себя при паузе/seek/смене трека.

## 2. Продуктовая идея

**Маскот «Бей» (рабочее имя)** — чиби-персонаж, связанный с брендом BeyGram: округлое тело-«B»,
сложенное из бумаги, или бумажный самолётик с глазами и наушниками. Цвета — циан/синий/белый из логотипа.
Характер: энергичный, но не навязчивый; танцует под музыку, а не «крутится всегда».

### 2.1. Где маскот живёт

Приоритет 1 — **полноэкранный плеер**:

- Сцена вместо/поверх квадратного артворка.
- Обложка остаётся фоном (затемнённая/с лёгким blur), маскот стоит на «сцене» внизу.
- Маленький чип/кнопка в углу: показать/скрыть маскота, долгое нажатие — стиль танца.
- В landscape — та же сцена слева, где сейчас артворк.

Приоритет 2 — **BG Mix**:

- Во время перехода маскот может делать жест «диджея»/смены пластинки.
- После handoff — переключается на новый трек без резкого скачка позы.

Приоритет 3 — **мини-плеер и профиль**:

- Мини-плеер: только голова/маленькая фигурка, лёгкий bob по битам, без полноценного анализа.
- Профиль: маскот рядом с профильной музыкой, статичный idle или та же сцена.

Приоритет 4 — **онбординг/пустые состояния**:

- Маскот машет/дышит, реагирует на tap. Это уже не музыкальный сценарий, а общий animation layer;
  делать после того, как музыкальная интеграция стабильна.

### 2.2. Поведение по состояниям

| Состояние | Поведение маскота |
|---|---|
| Маскот выключен | Ничего не рисуется, нативная сессия не создаётся |
| Включён, трека нет | Статичная idle-поза или лёгкое дыхание |
| Трек готовится/анализируется | Idle-поза + ненавязчивый индикатор, без спиннера поверх обложки |
| Играет, анализ готов | Танец по BG Motion (2D — 2.0, 3D — 3.x Auto) |
| Пауза | Поза замирает; через 1–2 с можно включить очень лёгкое дыхание/моргание |
| Seek | Поза мгновенно пересчитывается на новое время; короткий crossfade, если скачок большой |
| Смена трека | Старый маскот fade-out, новый fade-in после готовности анализа |
| BG Mix transition | Продолжает текущий танец, затем переключается на новый item |
| Тишина/нет бита | Idle/Freeze, без выдуманных ударов (BG Motion это уже умеет) |
| Низкая уверенность BPM | Auto уходит в Groove/Idle, без «дёрганого» танца |
| Reduced motion / Motion Off | Статичная поза, никакого непрерывного движения |
| Свернули приложение/плеер | Рендер и сэмплирование останавливаются |

## 3. Два технических маршрута

### Вариант A — 2D-маскот на BG Motion 2.0 (рекомендуемый MVP)

```
BG Beat AnalysisResult
        │
        ▼
ChoreographyBuilder (BG Motion 2.0)
        │
        ▼
ChoreographySampler.sample(mediaTime) → CharacterPose
        │
        ▼
MascotRig (2D-части: голова, торс, руки, ноги)
        │
        ▼
Compose Canvas / DrawScope
```

Почему это хороший первый шаг:

- `CharacterPose` специально сделан backend-independent; в BG Media прямо упомянуты спрайты и будущий
  BeyGram-аватар (`render/CharacterPresenter.hpp`).
- Не нужен VRM, GLES, ретаргетинг и лицензия на 3D-модель.
- Работает на любом устройстве, почти не влияет на APK и батарею.
- Быстро проверяет главное: нравится ли продукт, попадает ли маскот в ритм, как ведёт себя UI.
- Позже 2D-presenter можно заменить на 3D, не меняя session/clock/UI-контракт.

Минусы:

- Нет authored-клипов 3.x (Groove/Hip-Hop/Side Step/Energetic), нет footwork и 3D-объёма.
- `CharacterPose` — это 6 движений 2.0, а не полноценная хореография.
- Для «вау-эффекта» 3D может быть убедительнее.

### Вариант B — 3D VRM на BG Motion 3.x

```
BG Beat AnalysisResult
        │
        ▼
MusicalTimeline → DancePlayer.update(mediaTime) → DanceFrame (ClipPose)
        │
        ▼
HumanoidRetargeter.applyClip(...) → RetargetedPose (nodeGlobals)
        │
        ▼
Android GLES 3.x renderer (VRM mesh + skinning + textures)
```

Что уже готово:

- `DancePlayer` (Auto/Manual, entry beats, crossfade, seek/reseed, overlays).
- `HumanoidRetargeter` (bone mapping, foot IK, arm collision, secondary dynamics).
- `VrmModel` (меши, материалы, текстуры, скелет, humanoid mapping).
- Desktop-рендерер как референс: `adapters/sdlgpu/src/SdlGpuVrmRenderer.cpp` + шейдеры.

Что нужно сделать:

- Android GLES 3.x рендерер: EGL-контекст, VBO/IBO, скиннинг, текстуры, alpha-blend, toon-шейдер.
- Загрузка VRM из assets в память (`VrmLoader::loadFromMemory` уже есть).
- Surface/GLSurfaceView + lifecycle + потокобезопасное уничтожение сессии.
- Лицензированная VRM-модель маскота (VRM 0.x, не VRM 1.0).
- Fallback на 2D/статичную позу, если GLES/GPU/модель не подходят.

Минусы:

- 3–6 недель работы, а не 1–2.
- +2–10 МБ модели и +1–2 МБ нативных библиотек/шрифтов/шейдеров.
- Больше риск батареи, перегрева и jank на средних устройствах.
- Нет Android-рендерера в BG Media — его нужно либо писать в BeyGram, либо добавлять в BG Media
  отдельным адаптером.

### Вариант C — заранее отрендеренные спрайты/видео

Можно оффлайн отрендерить танец в 3D и проигрывать WebP/видео-луп. Плюс — простота. Минус — нет
реакции на реальный трек, большой размер, невозможность менять стиль/интенсивность. Не рекомендуется
как основной путь; годится только как временный арт-плейсхолдер.

### Сравнение

| Критерий | A: 2D BG Motion 2.0 | B: 3D BG Motion 3.x |
|---|---|---|
| Арт-зависимость | векторные части, дни | лицензированная VRM, недели |
| Нативная работа | JNI + сэмплер, мало | + GLES-рендерер, много |
| APK | ~100 КБ арта | +2–10 МБ модели |
| Производительность | очень лёгкая | GPU/батарея |
| Синхронизация | биты/акценты 2.0 | authored-клипы, footwork, стили |
| Риск | низкий | средний/высокий |
| Время до демо | ~1 неделя | ~4–6 недель |
| Fallback | всегда работает | нужен 2D fallback |

**Рекомендация:** делать A как вертикальный срез, но сразу проектировать общий контракт сессии,
часов и UI так, чтобы B подключался как второй presenter. Если 3D-модель и арт уже есть и 3D — жёсткое
требование, можно начинать с B, но 2D-fallback всё равно понадобится.

## 4. Общая архитектура

### 4.1. Kotlin-слой

Новый пакет `app/src/main/java/com/beykus/beygram/media/bgmotion/`:

- `BgMascotSettingsStore.kt` — включён/выключен, режим (2D/3D/Auto), стиль, интенсивность, показывать
  в плеере. По умолчанию выключено, как эксперимент.
- `BgMascotManager.kt` — singleton, наблюдает `PlaybackEngine.state` и настройки, готовит/держит одну
  нативную сессию для текущего трека, отдаёт `StateFlow<BgMascotUiState>`.
- `BgMascotNative.kt` — JNI-обёртка.
- `MascotClock.kt` — сглаженное media time между 250-мс тиками.
- `MascotEligibility.kt` — общие фильтры (audio/secret/system-private/not downloaded), чтобы не
  дублировать логику `BgBeatManager`.
- `MascotRig.kt` — данные 2D-рига (пивоты, порядок частей, картинки).
- `MascotStage.kt` — Compose-компонент сцены в плеере.
- `Mascot2DPresenter.kt` — DrawScope-рендер `CharacterPose` на 2D-риг.
- `Mascot3DPresenter.kt` — GLSurfaceView/AndroidExternalSurface + JNI-рендер.
- `BgMascotDiagnostics.kt` — состояние, cache hit, BPM, стиль, FPS, ошибки.

### 4.2. Нативная сессия

Один C++ объект на текущий трек:

```cpp
struct MascotSession {
    bgmedia::AnalysisResult analysis;
    // 2D
    bgmedia::Choreography choreography;
    // 3D
    std::unique_ptr<bgmedia::motion::DancePlayer> dance;
    std::unique_ptr<bgmedia::vrm::VrmModel> model;
    std::unique_ptr<bgmedia::animation::HumanoidRetargeter> retargeter;
    // общее
    std::uint64_t generation = 0;
};
```

Почему сессия, а не «каждый кадр заново»:

- `AnalysisResult` и `Choreography`/`DancePlayer` строятся один раз на трек.
- `DancePlayer` хранит состояние переходов/seek; его нельзя пересоздавать на каждый кадр.
- Легко освободить всё при смене трека/аккаунта/выключении.

### 4.3. JNI API (эскиз)

```kotlin
@Keep
object BgMascotNative {
    val isAvailable: Boolean

    // mode: 0 = BG Motion 2.0 (2D), 1 = BG Motion 3.x (3D)
    external fun nativeCreateSession(
        filePath: String,
        cacheDir: String,
        mode: Int,
        cancelToken: Long
    ): Long

    external fun nativeDestroySession(handle: Long)

    // 2D: заполняет out значениями CharacterPose (поза + phase/blend/impact,
    // move как ordinal и accented как 0/1), возвращает число записанных значений или 0
    external fun nativeSample2D(handle: Long, mediaTime: Double, out: FloatArray): Int

    // 3D: загрузить VRM (ByteArray из assets) и отдать модели в сессию
    external fun nativeLoadModel(handle: Long, bytes: ByteArray): Boolean

    // 3D: обновить позу и нарисовать в текущий GL-контекст
    external fun nativeRender3D(handle: Long, width: Int, height: Int, mediaTime: Double): Boolean

    external fun nativeSetStyle(handle: Long, style: Int, immediate: Boolean)
    external fun nativeSetIntensity(handle: Long, intensity: Float)
    external fun nativeStatusJson(handle: Long): String
}
```

Важно:

- Все вызовы с одним `handle` идут с одного потока-владельца (main для 2D, GL-поток для 3D).
  Это проще и безопаснее, чем локи на каждый кадр.
- `nativeCreateSession` — блокирующий, вызывать только с `Dispatchers.Default`.
- `cancelToken` — тот же `CancelRegistry`, что у BG Beat, чтобы можно было отменить анализ при смене трека.
- При уничтожении сессии, которую использует GL-поток, сначала останавливаем GL-цикл и дожидаемся
  `onDrawFrame`, только потом `nativeDestroySession`.

### 4.4. CMake

В `app/src/main/cpp/CMakeLists.txt`:

- Добавить `bgmotion_jni.cpp` в `add_library(bgbeat_jni SHARED ...)`.
- Оставить имя библиотеки `bgbeat_jni`: его грузит `BgMediaSdk`, ломать контракт не нужно.
- Для 3D добавить `EGL` и `GLESv3` в `target_link_libraries`.
- `bgmedia_rig` уже тянет `bgmedia_vrm`; отдельный таргет не нужен.
- При необходимости добавить `-DANDROID_STL=c++_shared` уже есть.
- Проверить, что `std::filesystem`, `std::span`, `cgltf`, `stb_image` собираются NDK r30 — они уже
  собираются для `bgmedia_rig`/`bgmedia_vrm` в текущей конфигурации.

### 4.5. Анализ и кэш

- Сессия использует тот же `BgBeatSettingsStore.getAccountCacheDir(context, accountId)`, что и BG Beat.
  Если BG Beat уже проанализировал трек, сессия получит cache hit и не будет считать FFT заново.
- Декодирование PCM всё равно происходит (нужен content hash для кэша). Это ~0.1–0.5 с на трек;
  приемлемо для MVP.
- Если появятся жалобы на двойной анализ, следующим шагом сделать общий `BgAnalysisCoordinator`:
  - один нативный `AnalysisResult` на трек;
  - BG Beat и маскот подписываются на него;
  - JSON для BG Beat UI строится из того же результата.
- Пока маскот не виден (плеер закрыт), анализ не запускается.
- При смене трека/аккаунта старая сессия уничтожается, новая готовится с `generation++`, чтобы
  опоздавшие колбэки не перезаписали состояние.

### 4.6. Media time

`PlaybackEngine.state.positionMs` обновляется 4 раза в секунду. Для 60 fps этого мало.

`MascotClock`:

- Хранит `anchorMediaMs`, `anchorFrameNanos`, `speed`, `playing`, `itemKey`.
- На каждом кадре: `estimate = anchorMediaMs + (frameNanos - anchorFrameNanos) * speed`.
- На каждом тике `PlaybackEngine.state`:
  - при смене item/seek/большом расхождении — snap;
  - иначе плавно подтягивает estimate к target, не давая времени идти назад во время игры.
- При паузе — freeze.
- При `speed` 0.75/1.0/1.25 — учитывает скорость.
- `audioOffsetMs` — настраиваемая поправка (по умолчанию 0; на устройстве подобрать, если маскот
  визуально опаздывает/спешит).
- Для BG Mix position приходит из нативного статуса; clock работает так же, но при handoff
  сбрасывается по смене `item.key`.

Тесты clock — отдельно, без UI: 250-мс тики, пауза/резюм, seek, смена скорости, откат назад,
BG Mix handoff.

### 4.7. Жизненный цикл

- `BgMascotManager` не работает постоянно. UI вызывает `setVisible(true/false)`:
  - `true` — плеер открыт и сцена видима;
  - `false` — плеер закрыт/свернут/маскот скрыт.
- При `visible = true` и включённом маскоте:
  1. проверить eligibility;
  2. если сессия для этого item уже есть — переиспользовать;
  3. иначе отменить старую, создать новую в фоне;
  4. по готовности — `Ready`.
- При `visible = false` — остановить рендер-цикл, но сессию можно сохранить до смены трека
  (быстрое открытие плеера не должно запускать анализ заново). Ограничить одной сессией.
- При `onTrimMemory`/`onLowMemory` — уничтожить сессию и текстуры.
- При reduced motion — не запускать рендер-цикл; отдать статичную позу.

### 4.8. Ошибки и fallback

- Нативная библиотека не загрузилась / нет BG Media core — маскот не показываем, пишем один лог,
  в UI не ломаем плеер.
- Трек не подходит (не audio, secret, не скачан) — статичная idle-поза или скрыть.
- Анализ упал — статичная поза, ошибка только в диагностике.
- 3D-рендерер не инициализировался (нет GLES 3.x/EGL) — автоматический fallback на 2D.
- 3D-модель не загрузилась/не VRM 0.x — fallback на 2D.
- Не показывать пользователю технические ошибки в плеере; максимум — «маскот недоступен» в настройках.

### 4.9. Приватность и аккаунты

- Те же фильтры, что у BG Beat: secret/system-private не анализируются и не показываются.
- Кэш — только app-private, per-account.
- В диагностику не попадают названия треков, пути и имена аккаунтов.
- Никаких сетевых запросов: маскот работает только с локальным файлом.
- При смене аккаунта — `onAccountChanged`: destroy session, cancel job, clear state, как в `BgBeatManager`.

### 4.10. Reduced motion и интенсивность

- `MotionPreset.Off` или системный reduced motion → статичная поза, без `withFrameNanos`-цикла.
- `MotionCategory.Media` отключена → то же самое.
- `intensityPercent` → масштаб амплитуды `CharacterPose` (2D) или `RetargetOptions`/амплитуда (3D).
- Никаких вспышек, резких миганий и «тряски» на весь экран.
- Анимация только пока сцена видима; в фоне — stop.

## 5. Вариант A: 2D-маскот

### 5.1. Нативная часть

- `nativeCreateSession(mode = 0)`:
  - декодирует файл (тем же miniaudio-путём, что BG Beat);
  - загружает/считает `AnalysisResult` с кэшем;
  - `ChoreographyBuilder().build(analysis, style)`;
  - хранит `Choreography`.
- `nativeSample2D(handle, mediaTime, out)`:
  - `ChoreographySampler().sample(choreography, mediaTime + offset)`;
  - складывает поля `CharacterPose` в `out` в фиксированном порядке, включая `move` (ordinal)
    и `accented` (0/1); контракт версионируется;
  - возвращает количество записанных значений.
- Стиль 2.0 один; если хочется разнообразия, можно менять `ChoreographyStyle.intensity` и
  `bassInfluence`, но это не authored-клипы.
- Никаких VRM/клипов/GLES — только `bgmedia_core`.

### 5.2. 2D-риг

Арт — отдельные части с прозрачностью:

- задняя рука, задняя нога, торс, передняя нога, передняя рука, голова, волосы/наушники/аксессуар.
- Для каждой части: `pivot` (точка вращения), `offset`, `z-order`, `parent`.
- Формат — маленький JSON или Kotlin data class; художник меняет цифры, код не трогаем.
- База — 1000×1000 условных единиц, масштабируется под размер сцены.
- Части экспортируются в PNG @2x/@3x или вектор; для начала можно нарисовать плейсхолдер-робота
  примитивами, как в desktop BG Media, чтобы не блокировать нативную работу.

Маппинг `CharacterPose` → 2D:

| Поле | Куда идёт |
|---|---|
| `bob` | вертикальное смещение всего персонажа |
| `lean` | наклон торса вокруг таза |
| `hipShift` | сдвиг таза по X |
| `chestTwist` | лёгкий поворот/скос торса и плеч |
| `rotation` | небольшой наклон всего тела + смещение головы/глаз (полный yaw в 2D не изображаем) |
| `headNod` | наклон головы вперёд/назад (в 2D — вертикальный сдвиг + поворот) |
| `headTilt` | поворот головы вокруг шеи |
| `headTurn` | сдвиг головы/лица по X |
| `armLeft/Right` | поворот плеча |
| `elbowLeft/Right` | поворот предплечья |
| `legLeft/Right` | поворот бедра |
| `kneeLeft/Right` | поворот голени |
| `footLeft/Right` | подъём/поворот стопы |
| `impact` | squash/stretch + акцентное кольцо/искры |
| `energy` | общая амплитуда/яркость сцены |

Ограничение: `rotation` в BG Motion — yaw в turns; для фронтального 2D-персонажа это не полноценный
3D-поворот. Лучше отображать его в лёгкий наклон и параллакс, а не пытаться крутить персонажа.

### 5.3. Compose-рендер

- `Canvas(Modifier.fillMaxSize())`; внутри `withTransform`/`rotate`/`translate`/`scale`.
- Поза обновляется в `LaunchedEffect` через `withFrameNanos`; читается внутри `draw`-блока,
  чтобы не рекомпозировать весь плеер.
- `MutableState<MascotPose>` или переиспользуемый `FloatArray`, без аллокаций на кадр.
- Тень — эллипс под ногами, масштабируется от `bob`.
- Акценты: маленькие ноты/искры на downbeat, если включена интенсивность; отключаются reduced motion.
- Сцена: прозрачный фон поверх обложки; мягкий градиентный scrim снизу для контраста.
- 60 fps — цель; на слабых устройствах можно 30 fps, если `Choreographer` пропускает кадры.

### 5.4. UI в плеере

В `MusicPlayerSheet.kt`:

- `PlayerArtwork` оставить как есть, но добавить слой `MascotStage` внутри того же `Box`.
- Обложка остаётся фоном; сверху — scrim и маскот.
- В правом нижнем углу — маленький чип с иконкой маскота:
  - tap — включить/выключить сцену;
  - long press — выбрать стиль/интенсивность (позже).
- Если маскот выключен — поведение плеера не меняется.
- В landscape — тот же `PlayerArtwork` слева, значит сцена автоматически работает и там.
- Высота маскота — 55–70% высоты артворка, чтобы не закрывать обложку полностью.

### 5.5. Что проверяет MVP

- Попадание в бит на слух и на глаз.
- Комфортность амплитуды на разных BPM.
- Не мешает ли маскот управлению и чтению метаданных.
- Не растёт ли батарея/CPU.
- Нравится ли концепт до вложений в 3D.

## 6. Вариант B: 3D-маскот

### 6.1. Модель

Требования к VRM:

- VRM 0.x (не VRM 1.0 — импортёр его не поддерживает).
- Humanoid-кости: hips, spine, chest, upperChest, neck, head, shoulders, arms, hands, legs, feet, toes.
- 10–20k треугольников, 1–4 материала, 1–4 текстуры 1024×1024.
- Высота 1.2–1.5 м, forward = -Z.
- Лицензия разрешает распространение в APK; `OnlyAuthor`/`Redistribution_Prohibited` не подходят.
- Spring bones/хвост/волосы — опционально; secondary dynamics уже есть, но для MVP можно без них.

`models/Model1.vrm` из BG Media использовать нельзя. Для тестов — `tests/fixtures/minimal.vrm`.
Для продукта нужен оригинальный чиби-маскот, заказанный/сделанный под BeyGram.

### 6.2. Загрузка

- Kotlin читает `assets/mascot/mascot.vrm` в `ByteArray` (или стримит в native).
- `nativeLoadModel(handle, bytes)` → `VrmLoader::loadFromMemory`.
- Модель и текстуры живут в сессии; `releaseTexturePixels()` после загрузки в GPU, чтобы не держать
  RGBA в RAM.
- Клипы: `assets/mascot/dances/*.json` распаковать в `filesDir/mascot/dances-v1/` при первом запуске
  (или добавить upstream-метод `DancePlayer::loadLibraryFromJson` и не распаковывать).
- `DancePlayer::loadLibrary(dir)` + `setLegGeometry(retargeter.legGeometry())`.

### 6.3. Сэмплирование

На каждый кадр (GL-поток):

```cpp
const double t = mediaTime + audioOffset;
bgmedia::motion::DanceFrame frame = dance->update(t);
bgmedia::animation::RetargetedPose pose = retargeter->applyClip(frame.pose, t);
renderer.draw(model, pose, camera, viewport);
```

- `DancePlayer::update` сам обрабатывает seek/backward jump, crossfade и one-shot.
- `setAnalysis(&analysis, now)` держит указатель на `AnalysisResult` — сессия обязана жить дольше.
- `setMusicalTimeline(MusicalTimeline::fromAnalysis(analysis), now)`.
- `setMode(Auto, now)` для авто-плана; `select(style, now)` для ручного выбора.
- `setOverlayMode(Full/Light/HandsOnly/Off)` — настройка интенсивности/сложности.

### 6.4. GLES-рендерер

Минимальный порт desktop-рендерера:

- EGL-контекст создаёт `GLSurfaceView` (Kotlin), native использует текущий контекст.
- Один vertex/fragment shader:
  - атрибуты: position, normal, uv, joints (uvec4), weights (vec4);
  - uniform: `uViewProj`, `uJoints[192]`, `uModel`, material uniforms;
  - скиннинг: `sum(jointMatrix[j] * pos * weight)`;
  - toon-ramp как в `mesh.frag.hlsl`, но упрощённый;
  - alpha cutoff/blend, double-sided.
- VBO/IBO на каждый primitive; текстуры RGBA8 + mipmaps.
- Проходы: opaque (depth write) → alpha blend (depth test, no write, sort back-to-front).
- Сцена: прозрачный фон, мягкая тень-эллипс, key light + rim.
- Камера: фиксированный портретный кадр, модель по центру; позже — лёгкий parallax.
- Viewport: не больше 720–1024 px по длинной стороне; на телефоне этого достаточно.

### 6.5. Surface и lifecycle

Два варианта:

1. `GLSurfaceView` внутри `AndroidView` — проще всего.
   - `setEGLContextClientVersion(3)`;
   - `setEGLConfigChooser(8, 8, 8, 8, 16, 0)`;
   - `holder.setFormat(PixelFormat.TRANSLUCENT)`;
   - `renderMode = RENDERMODE_WHEN_DIRTY`;
   - `Choreographer` вызывает `requestRender()` только когда играет и сцена видима.
2. `AndroidExternalSurface` + EGL в native — больше контроля, но больше кода.

Для MVP — GLSurfaceView. Обязательно:

- `onPause`/`onResume` по lifecycle;
- при `onDispose` сначала остановить цикл, потом destroy session;
- не уничтожать нативную сессию, пока GL-поток может рисовать;
- проверить прозрачность и z-order SurfaceView относительно Compose-слоёв.

### 6.6. Производительность

- 30 fps минимум на среднем устройстве, 60 fps — цель.
- `RENDERMODE_WHEN_DIRTY`: нет кадров на паузе/в фоне.
- Текстуры — только нужные; `releaseTexturePixels()` после upload.
- Ограничить размер viewport и не делать readback.
- Если GLES 3.0/3.1 недостаточно (dynamic uniform indexing, texture units) — fallback на 2D.
- Профилировать `dumpsys gfxinfo`, `dumpsys meminfo`, GPU time через `GL_EXT_disjoint_timer_query`
  (если доступно).

## 7. Что менять в BG Media, а что — в BeyGram

### В BeyGram (обязательно)

- `media/bgmotion/` — весь Kotlin-слой.
- `app/src/main/cpp/bgmotion_jni.cpp` — JNI-сессия.
- `CMakeLists.txt` — добавить файл и GLES/EGL.
- `MusicPlayerSheet.kt` — сцена/чип.
- `ExperimentsScreen.kt` — переключатель.
- `strings*.xml` — тексты на 5 языках (values, values-en, de, es, fr).
- Тесты: `app/src/test/.../media/bgmotion/`.

### В BG Media (по необходимости)

- `DancePlayer::loadLibraryFromJson` / `addAuthoredClips` — чтобы не распаковывать JSON в кэш.
- Публичный API для 2D-сэмплера уже есть (`ChoreographyBuilder`/`Sampler`).
- Android GLES-адаптер (`adapters/gles`) — если хотим переиспользовать рендерер вне BeyGram.
- Тесты на детерминизм 2D-позы уже есть в BG Media; добавить тест на стабильность на 30/60/120 fps
  и seek, если ещё нет.

Если BG Media не трогаем — pin остаётся `2368c13`, а клипы распаковываются в кэш. Это самый быстрый
путь для MVP. Если добавляем API — поднимаем pin осознанно и проверяем, что BG Beat/BG Mix не
регрессировали.

## 8. Этапы

### Phase 0 — спайк (2–3 дня)

- JNI-метод, который по файлу строит `AnalysisResult` + `Choreography` и отдаёт `CharacterPose`.
- Debug-экран/канвас с плейсхолдер-роботом (без продуктового UI).
- Проверить: 60 fps, попадание в бит, pause/seek/resume, смену трека, reduced motion.
- Замерить: время анализа, cache hit, CPU, память.
- **Выход:** решение, годится ли 2D-подход по ощущениям.

### Phase 1 — 2D MVP (1–2 недели)

- Арт-концепт «Бей» + 2D-риг (или плейсхолдер, если арт ещё не готов).
- `BgMascotSettingsStore`, `BgMascotManager`, `MascotClock`, `BgMascotNative`.
- `Mascot2DPresenter` + `MascotStage` в `PlayerArtwork`.
- Фильтры eligibility, account isolation, error fallback.
- Переключатель в Experiments + строки.
- Unit-тесты clock/manager/eligibility, Compose screenshot, ручной QA.
- **Выход:** Dev-сборка с маскотом за флагом, можно показывать.

### Phase 2 — 3D (3–6 недель)

- Лицензированная VRM-модель.
- GLES-рендерер: EGL/шейдеры/скиннинг/текстуры/alpha.
- 3D-сессия: `DancePlayer` + `HumanoidRetargeter`.
- Surface lifecycle, потокобезопасное уничтожение.
- Fallback на 2D, capability checks.
- Профилирование и QA на средних/слабых устройствах.
- **Выход:** 3D-режим как отдельный эксперимент, 2D остаётся дефолтом.

### Phase 3 — продукт (1–2 недели)

- BG Mix: жест/смена стиля на переходе.
- Мини-плеер: маленькая фигурка/голова.
- Профиль: маскот рядом с профильной музыкой.
- Стили/интенсивность в настройках, запоминание выбора.
- Share/скриншот сцены (опционально).

### Phase 4 — полировка

- Idle/blink/дыхание, моргание, аксессуары.
- Spring bones/хвост, facial expressions (если 3D).
- A/B стилей, авто-выбор по energy/BPM.
- Диагностика и телеметрия (без приватных данных).
- Возможный upstream Android-адаптер в BG Media.

## 9. Тесты и приёмка

### Unit / Robolectric

- `MascotEligibilityTest`: audio/secret/system-private/not downloaded/unsupported.
- `MascotSessionPolicyTest`: dedupe, смена трека, смена аккаунта, выключение, reduced motion.
- `MascotClockTest`: 250-мс тики, pause/resume, seek, speed 0.75/1.25, откат назад, BG Mix handoff.
- `BgMascotSettingsStoreTest`: default off, persist, account cache dir.
- `MascotRigTest`: маппинг полей в трансформации (чистая функция).

### Compose

- Маскот не показывается при выключенном флаге.
- Не показывается/замирает при reduced motion.
- Чип доступен по semantics, не перекрывает управление.
- Screenshot-тест сцены с фейковой позой (Roborazzi) в портрете/ландшафте.

### Native / device

- BG Media уже проверяет детерминизм и seek для motion; добавить JNI-smoke на эмуляторе.
- **Не запускать `connectedAndroidTest` на пакете `com.beykus.beygram.dev`** (AGENTS.md).
  Только эмулятор или отдельный test application ID с изолированными данными.
- Проверка `adb install -r` и сохранности аккаунта — перед выдачей Dev-сборки.

### Ручной QA-матрица

- BPM: 60, 90, 120, 128, 140, 174; 6/8; смена темпа; тишина в начале.
- Треки: локальный MP3/FLAC, Yandex (скачанный), Telegram audio, voice note (не маскот),
  secret chat audio (не маскот).
- Управление: play/pause, seek вперёд/назад, next/prev, repeat, shuffle, speed.
- BG Mix: переход, смена item, pause во время перехода.
- Lifecycle: свернуть/развернуть, поворот, блокировка, низкая память.
- Настройки: Off, Subtle, Balanced, Expressive, отключённая Media-категория, системный reduced motion.
- Устройства: минимум один слабый/средний ARM64 и один эмулятор x86_64.

### Цели производительности

| Метрика | 2D | 3D |
|---|---|---|
| FPS | 60 (минимум 30) | 30–60 |
| Время кадра (main/GL) | < 2 мс | < 8 мс |
| Подготовка сессии | < 1.5 с cache hit | < 2 с cache hit + модель |
| Память | < 30 МБ | < 100 МБ сверх baseline |
| Батарея | без рендера в фоне/паузе | без рендера в фоне/паузе |

## 10. Риски и решения

| Риск | Решение |
|---|---|
| Арта маскота нет | Сначала плейсхолдер-робот; арт — параллельно. 2D-риг позволяет заменить картинки без кода |
| 3D-рендерер затянется | 2D MVP уже в проде; 3D — отдельный эксперимент |
| Двойной анализ с BG Beat | Кэш per-account; позже общий `BgAnalysisCoordinator` |
| Расхождение аудио/визуала | `MascotClock` + `audioOffsetMs` + snap на seek; тесты |
| BG Mix handoff | Смена сессии по `item.key`; fade-out/in; не убивать GL-сессию во время кадра |
| SurfaceView/Compose z-order | GLSurfaceView + translucent; проверить на устройствах; fallback на 2D |
| APK/память | 2D по умолчанию; 3D-модель ленивая/по желанию; `releaseTexturePixels()` |
| Лицензия VRM | Только оригинальная модель с разрешением на распространение; не `Model1.vrm` |
| Reduced motion | Статичная поза; учитывать `MotionSettings` и системный флаг |
| Приватность | Те же фильтры, что BG Beat; per-account cache; без сети |
| Слабые GPU | Capability check + 2D fallback |
| Перегрев/батарея | RENDERMODE_WHEN_DIRTY, стоп в фоне/паузе, ограничение viewport |

## 11. Открытые вопросы

1. **Арт:** есть ли уже концепт маскота? Если нет — делаем «Бей» (бумажный самолётик/чиби-B)?
2. **2D или 3D:** 2D MVP с последующим 3D или сразу 3D как жёсткое требование?
3. **Где показывать в MVP:** только полноэкранный плеер или сразу мини-плеер/профиль?
4. **Флаг:** Dev-only эксперимент или сразу в Stable с default off?
5. **Модель 3D:** кто предоставит лицензированный VRM? Готовы ли к +2–10 МБ APK?
6. **BG Media:** можно ли поднимать pin ради `loadLibraryFromJson`/Android-адаптера, или всё
   делаем в BeyGram без изменений ядра?
7. **Стили:** Auto или пользовательский выбор Groove/Hip-Hop/Side Step/Energetic/Idle?
8. **Реакция:** только музыка или позже voice/UI-события?
9. **Целевые устройства:** минимальный FPS и самый слабый телефон для приёмки?

## 12. Предлагаемый первый PR

Не начинать с 3D-рендерера. Первый PR — вертикальный срез без продукта:

1. `BgMascotSettingsStore` + флаг в Experiments (по умолчанию off).
2. `BgMascotNative` + `bgmotion_jni.cpp`: create/sample/destroy для 2D.
3. `MascotClock` + unit-тесты.
4. Debug-канвас с плейсхолдер-роботом (только в Dev/по флагу).
5. Замеры: анализ, cache hit, FPS, память.

Если спайк показывает, что попадание в ритм и ощущение хорошие — делаем арт и продуктовую сцену.
Если нет — меняем концепт до того, как вложиться в 3D.
