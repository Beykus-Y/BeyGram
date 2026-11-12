# Маскот BeyGram на BG Motion — план интеграции

> Статус: Phase 0 и Phase 1 утверждены без финального арта персонажа. В ветке `feat-mascot`
> уже реализован вертикальный срез: общий реестр анализа, 2D-тестовый стенд BG Motion 2.0,
> оверлей в плеере, настройки эксперимента и unit-тесты. Документ описывает
> целевую архитектуру; код в ветке может расходиться с ним.
> Ветка `feat-mascot` создана от актуального `DEV`; локальный `DEV` обновлён до `8c397403`.
> BG Media изучен на коммите `2368c132660d73ee976ec3a50b67249417938a7f` — он же закреплён
> в `app/src/main/cpp/CMakeLists.txt`.
>
> Финальный визуал маскота — VRM-модель на BG Motion 3.x. Временный 2D-персонаж — только
> одноразовый тестовый стенд для проверки движения, синхронизации, seek/pause/смены трека
> и производительности. Это не дизайн маскота и не продуктовый fallback.

## 1. Что уже есть

### 1.1. В BeyGram

- **BG Beat** уже встроен: `media/bgbeat/` + `app/src/main/cpp/bgbeat_jni.cpp`.
  - Анализ запускается для текущего трека, когда включён эксперимент BG Beat.
  - Результат — JSON (`BgBeatAnalysis`): BPM, beats, downbeats, waveform, phrase loudness.
  - Кэш анализа лежит в app-private `filesDir/bgbeat-cache/account_<id>/` и разделён по аккаунтам.
  - Фильтры: только `MediaKind.Audio`, не secret/system-private, файл полностью скачан.
  - `BgBeatManager` уже дедуплицирует задачи по `BgBeatAnalysisIdentity`
    (`accountId`, `itemKey`, `filePath`, `fileLength`, `fileLastModified`, BPM range),
    отменяет анализ через `CancelRegistry` и защищается `sequenceId`.
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

- `bgmedia_core` — чистый C++20: BG Beat, BG Sync, BG Motion 2.0, кэш анализа. Без SDL/GPU.
- `bgmedia_vrm` — импорт VRM 0.x (GLB/glTF 2.0, cgltf + stb_image). Без SDL/GPU.
- `bgmedia_rig` — BG Motion 3.x (`DancePlayer`, `DanceDirector`, `MusicalTimeline`), ретаргетинг
  на humanoid-кости, foot IK, secondary dynamics. Зависит от `bgmedia_core` и `bgmedia_vrm`.
- `bgmedia_sdlgpu` — desktop-рендерер VRM на SDL_GPU. **Android-рендерера нет.**
- `assets/dances/*.json` — авторские клипы idle/groove/hiphop/sidestep/energetic.
- `ProceduralChoreography::showcase()` умеет генерировать showcase-танцы без файлов.
- `models/Model1.vrm` **нельзя распространять**: лицензия `OnlyAuthor` / `Redistribution_Prohibited`.
- Android-документ BG Media (`docs/ANDROID_JNI.md`) прямо говорит: Android-модуля и рендерера
  пока нет, есть только переносимое ядро и desktop-адаптер.

Важно различать два несовместимых формата движения:

- **BG Motion 2.0** возвращает `bgmedia::CharacterPose` через `ChoreographyBuilder` и
  `ChoreographySampler`. Это нормализованная поза для 2D/спрайтов/простого рига.
- **BG Motion 3.x** возвращает `bgmedia::motion::DanceFrame`, внутри которого
  `ClipPose` с кватернионами humanoid-костей, footwork, hands и root motion.
  `DanceFrame`/`ClipPose` нельзя подать в 2D-presenter, а `CharacterPose` — в
  `HumanoidRetargeter::applyClip`.

Поэтому 2D и 3D presenter'ы не взаимозаменяемы. Общий у них только host-контракт:
управление воспроизведением, media time, анализ, жизненный цикл сессии, настройки и тесты.

### 1.3. Что это значит для Android

- Анализ, BG Motion 2.0, BG Motion 3.x и импорт VRM **уже собираются под Android NDK**:
  `bgmedia_core`, `bgmedia_vrm`, `bgmedia_rig` не тянут SDL/GPU.
- Ключевые части Android-интеграции:
  1. Общий нативный реестр анализа, который отдаёт один `AnalysisResult` и BG Beat, и маскоту.
  2. JNI-сессия, которая держит анализ и отдаёт позу/кадр по media time.
  3. Android-presenter: 2D Canvas для тестового стенда или 3D GLES для финальной VRM.

## 2. Цель и границы

- **Финальная цель** — VRM-модель на BG Motion 3.x. Только 3.x даёт authored-клипы,
  footwork, hand layer, ретаргетинг и musical warp.
- **BG Motion 3.x** — постоянная основа финального маскота. 2.0 не становится вторым
  постоянным presenter'ом.
- **BG Motion 2.0** — промежуточный инструмент Phase 0/1. Он позволяет быстро проверить
  синхронизацию и жизненный цикл без VRM, GLES и финального арта. Это не фундамент продукта.
- **2D-персонаж** — одноразовый тестовый риг из примитивов. Никакого арта, концепта или
  продуктовой сцены для него не делаем. После Phase 2 он удаляется.
- **Финальный визуал** — VRM-модель, которая уже находится в разработке. Этот план не
  проектирует персонажа и не фиксирует продуктовые поверхности.

### 2.1. Поведение тестовой сессии

Таблица описывает только тестовый стенд и жизненный цикл сессии; продуктовые поверхности
и финальная композиция сцены здесь не решаются.

| Состояние | Поведение |
|---|---|
| Маскот выключен | Ничего не рисуется, нативная сессия не создаётся |
| Включён, трека нет | Статичная idle-поза |
| Трек готовится/анализируется | Idle-поза + диагностика, без спиннера поверх обложки |
| Играет, анализ готов | 2D-стенд сэмплирует BG Motion 2.0 |
| Пауза | Поза замирает |
| Seek | Поза мгновенно пересчитывается на новое время; при большом скачке — короткий crossfade |
| Смена трека | Старая сессия уничтожается, новая создаётся после готовности анализа |
| BG Mix transition | Продолжает текущий танец, затем переключается на новый item |
| Тишина/нет бита | Idle/Freeze, без выдуманных ударов |
| Низкая уверенность BPM | 2D: уменьшить амплитуду/Freeze; 3D: Auto уходит в Groove/Idle |
| Reduced motion / Motion Off | Статичная поза, без непрерывного движения |
| Свернули приложение/плеер | Рендер и сэмплирование останавливаются |

### 2.2. Вне рамок

- Дизайн маскота, 2D-арт, спрайты, Live2D, концепт-арт.
- Продуктовые поверхности: где именно маскот живёт в финальном UI, решается после VRM.
- Предрендеренные спрайты/видео как финальный визуал.
- Реакция на voice/UI-события — позже, после музыкальной интеграции.

## 3. Архитектура

### 3.1. Общий контракт и два presenter'а

| Слой | Общее | 2D-стенд (2.0) | 3D-финал (3.x) |
|---|---|---|---|
| Анализ | `AnalysisResult`, общий native handle | тот же | тот же |
| Время | `MascotClock`, media time, seek/pause/speed | тот же | тот же |
| Сессия | create/destroy, generation, видимость | тот же | тот же |
| Настройки | `BgMascotSettingsStore`, `MotionSettings` | те же | те же |
| Поза | — | `CharacterPose` | `DanceFrame` / `ClipPose` |
| Движение | — | `ChoreographyBuilder` / `ChoreographySampler` | `DancePlayer` / `DanceDirector` |
| Ретаргетинг | — | нет | `HumanoidRetargeter::applyClip` |
| Рендер | — | Compose Canvas, примитивы | GLES 3.x + VRM |

Общий код не должен пытаться привести `CharacterPose` к `ClipPose` или наоборот.
Граница — JNI-сессия, а не формат позы.

### 3.2. Kotlin-слой

Файлы слоя (часть уже есть в ветке):

- `media/bgbeat/BgAnalysisRepository.kt` — `BgAnalysisLease` и `acquire` для общего
  native analysis handle.
- `media/bgbeat/BgMediaEligibility.kt` — те же фильтры, что у BG Beat, без дублирования логики.
- `media/bgmotion/BgMascotSettingsStore.kt` — включён/выключен, режим, стиль, интенсивность.
  По умолчанию выключено, как эксперимент.
- `media/bgmotion/BgMascotManager.kt` — singleton, наблюдает `PlaybackEngine.state` и настройки,
  держит одну сессию для текущего трека, отдаёт `StateFlow<BgMascotUiState>`.
- `media/bgmotion/BgMascotNative.kt` — JNI-обёртка сессии.
- `media/bgmotion/MascotClock.kt` — сглаженное media time между 250-мс тиками.
- `media/bgmotion/MascotFrame.kt` — `MascotFrameLayout`/`MascotFrameBuffer`, плоский кадр
  позы и музыки для JNI и Compose.
- `ui/screens/playback/MascotStage.kt` + `MascotTestRig.kt` — Compose-сцена и тестовый риг
  из примитивов.
- `media/bgmotion/Mascot3DPresenter.kt` — будущий GLSurfaceView/AndroidExternalSurface.
- `media/bgmotion/BgMascotDiagnostics.kt` — состояние, cache hit, BPM, стиль, FPS, ошибки.

Правила потока:

- `BgMascotManager` выполняет все переходы состояния **только на Main**.
- `Dispatchers.Default` используется только для блокирующего анализа и создания нативной сессии.
- `BgAnalysisRepository.acquire` вызывается на `Dispatchers.Default`; результат и lease
  возвращаются на Main, состояние обновляется на Main.

### 3.3. Нативная сессия

Один C++ объект на текущий трек:

```cpp
enum class MascotMode { Motion2DTest, Vrm3D };

struct MascotSession {
    std::shared_ptr<const bgmedia::AnalysisResult> analysis;  // общий результат
    MascotMode mode = MascotMode::Motion2DTest;

    // 2D-стенд, BG Motion 2.0
    bgmedia::Choreography choreography;
    bgmedia::motion::MusicalTimeline timeline;

    // 3D, BG Motion 3.x (Phase 2)
    std::unique_ptr<bgmedia::motion::DancePlayer> dance;
    std::unique_ptr<bgmedia::vrm::VrmModel> model;
    std::unique_ptr<bgmedia::animation::HumanoidRetargeter> retargeter;

    float intensity = 1.0f;
    double playbackOffsetSeconds = 0.0;
    std::mutex mutex;        // сериализует все вызовы сессии
    bool destroyed = false;
    std::uint64_t generation = 0;
};
```

Почему сессия, а не «каждый кадр заново»:

- `AnalysisResult` берётся из общего реестра; сессия держит `shared_ptr`, а
  `BgMascotManager` — `BgAnalysisLease` до смены трека, чтобы BG Beat мог присоединиться
  к тому же handle.
- `Choreography`/`DancePlayer` строятся один раз на трек; `DancePlayer` хранит состояние
  переходов/seek и не пересоздаётся на каждый кадр.
- При смене трека/аккаунта/выключении всё освобождается предсказуемо.

`DancePlayer::setAnalysis` хранит указатель на `AnalysisResult`; сессия обязана держать
`shared_ptr` живым всё время жизни `DancePlayer`.

### 3.4. JNI API (эскиз)

Анализ (общий для BG Beat и маскота; методы уже есть в `BgBeatNative`):

```kotlin
@Keep
object BgBeatNative {
    // Синхронный: возвращает handle, когда snapshot готов; 0 — invalid request.
    // Первый вызов для ключа делает decode + FFT, остальные ждут тот же snapshot.
    external fun nativeAcquireAnalysis(
        filePath: String,
        cacheDir: String,
        minBpm: Double,
        maxBpm: Double,
        cancelTokenHandle: Long,
        progressListener: BgBeatProgressListener?,
        forceRefresh: Boolean
    ): Long

    external fun nativeAnalysisJson(handle: Long): String?
    external fun nativeReleaseAnalysis(handle: Long)
    external fun nativeLiveAnalysisHandles(): Int   // диагностика
}
```

Сессия:

```kotlin
@Keep
object BgMascotNative {
    val isAvailable: Boolean

    // mode: 0 = BG Motion 2.0 (2D-стенд), 1 = BG Motion 3.x (VRM, Phase 2)
    external fun nativeCreateSession(
        analysisHandle: Long,
        mode: Int,
        intensity: Float
    ): Long
    external fun nativeDestroySession(handle: Long)

    // 2D: заполняет out по MascotFrameLayout (поза + музыка), возвращает число float.
    external fun nativeSample2D(handle: Long, mediaTime: Double, out: FloatArray): Int

    external fun nativeSetIntensity(handle: Long, intensity: Float)
    external fun nativeSetPlaybackOffset(handle: Long, offsetSeconds: Double)
    external fun nativeStatusJson(handle: Long, mediaTime: Double): String?

    // Phase 2, 3D: CPU-загрузка; GL-вызовы только на GL-потоке.
    external fun nativeLoadModel(handle: Long, bytes: ByteArray): Boolean
    external fun nativeRender3D(handle: Long, width: Int, height: Int, mediaTime: Double): Boolean
    external fun nativeRelease3D(handle: Long)
    external fun nativeSetStyle(handle: Long, style: Int, immediate: Boolean)
}
```

Требования к API:

- `nativeAcquireAnalysis` блокирует вызывающий поток до готовности snapshot; вызывать только
  с `Dispatchers.Default`.
- `nativeAnalysisJson` возвращает JSON из того же snapshot; для неизвестного handle —
  error JSON, а не `null`.
- `nativeAnalyzeFile` остаётся тонкой обёрткой над registry на время миграции `BgBeatManager`.
- `nativeSample2D` пишет в переданный `FloatArray` по `MascotFrameLayout`, без аллокаций
  на кадр; layout версионируется.
- `nativeCreateSession` не вызывается на Main.
- `nativeRender3D` и `nativeRelease3D` вызываются только на GL-потоке.

### 3.5. Общий реестр анализа

Реестр — `beygram::bg::AnalysisRegistry` в `app/src/main/cpp/bg_analysis_registry.cpp/h`,
компилируется в ту же `libbgbeat_jni`.

Ключ формирует `analysisKey` в `bgbeat_jni.cpp`:

```text
filePath | file_size | last_write_time | cacheDir | minBpm | maxBpm
```

`cacheDir` уже account-scoped: `filesDir/bgbeat-cache/account_<id>`. Это не даёт
переиспользовать результат одного аккаунта в другом. `forceRefresh` добавляет к ключу
`|force|<steady_clock>`: получается новая generation, старые handle продолжают работать
со старым иммутабельным snapshot.

`AnalysisRegistry::acquire(key, analyzer)`:

1. Под мьютексом реестра ищет живой entry в `byKey_` (`weak_ptr`). Если entry есть —
   присоединяется к ожиданию. Если нет — создаёт entry и становится владельцем расчёта.
2. Владелец вызывает `analyzer()` **один раз**: decode PCM + `AnalysisCache` + `Analyzer`.
   `AnalysisSnapshot` (`ok`, `cacheHit`, `json`, `shared_ptr<const AnalysisResult>`)
   публикуется под мьютексом entry, waiters будят через condition variable.
3. Waiters получают тот же `AnalysisSnapshot`; повторного decode/FFT для того же ключа нет.
4. Каждый `acquire` выдаёт монотонный handle и кладёт его в `handles_` (`shared_ptr`).
   Handle — это ссылка на snapshot, а не копия `AnalysisResult`; refcount ведёт `shared_ptr`.
5. `nativeAnalysisJson(handle)` возвращает JSON из snapshot. `nativeReleaseAnalysis(handle)`
   удаляет handle; когда последний handle исчез, `weak_ptr` в `byKey_` больше не поднимается
   и snapshot освобождается. `nativeLiveAnalysisHandles()` — только диагностика.
6. `AnalysisResult` иммутабелен после публикации; `snapshot`/`result` можно читать с любого
   потока. Числовые handle не переиспользуются: старый handle даёт «неизвестен», не crash.

Kotlin-обёртка:

- `BgAnalysisRepository.acquire(...)` вызывает `BgBeatNative.nativeAcquireAnalysis` на
  `Dispatchers.Default` и возвращает `BgAnalysisLease(handle, json)`.
- `BgAnalysisLease.release()` вызывает `nativeReleaseAnalysis`.
- `BgBeatManager` держит lease текущего трека, пока трек активен, даже если UI BG Beat
  не видим. `BgMascotManager` получает тот же handle через `BgAnalysisRepository` и
  держит свой `BgAnalysisLease` до смены трека/аккаунта; сессия дополнительно держит
  `shared_ptr` на `AnalysisResult`. Поэтому переход «BG Beat посчитал → маскот открыл
  плеер» и обратный переход не запускают второй анализ.
- Если BG Beat выключен, lease создаёт и держит маскот до смены трека.
- При смене трека/аккаунта/выключении оба менеджера отпускают lease; при
  `onTrimMemory`/`onLowMemory` — после остановки рендера. Диск-кэш `AnalysisCache`
  остаётся, память snapshot освобождается.

Отмена:

- Cancel token из существующего `CancelRegistry` передаётся в `analyzer`.
- Если владелец расчёта отменяет его, snapshot получает `cancelled`/`error`, и все waiters
  видят этот результат. Отдельный waiter не может отменить только себя, не затронув
  остальных; для Phase 0/1 это приемлемо. Если понадобится независимая отмена, добавить
  per-waiter lease и отдельный cancel flag.
- `nativeReleaseAnalysis` во время выполнения не освобождает память под работающим
  вызовом: `shared_ptr` держится до возврата из JNI-функции.

Честное ограничение: после холодного старта приложения диск-кэш `AnalysisCache` всё ещё
требует PCM, чтобы вычислить `contentHash`. Реестр убирает повторное декодирование в рамках
живой записи и активного трека; отказ от декодирования на холодном старте — отдельная задача
BG Media, не обещание этого плана.

### 3.6. Потоки, владение и уничтожение

Это обязательный контракт, иначе JNI-сессия будет падать на смене трека или повороте экрана.

1. **Analysis handle — потокобезопасный.** `AnalysisResult` публикуется один раз и дальше
   не мутируется. Реестр защищён мьютексом; каждый JNI-вызов копирует `shared_ptr` и
   работает вне реестрового мьютекса. Один и тот же handle могут одновременно использовать
   BG Beat и маскот.
2. **Mascot session — сериализация на сессию.** Все вызовы `MascotSession` берут
   `session->mutex` на всё время вызова. Вызовы могут приходить с разных потоков
   (Main для 2D-сэмпла, GL-поток для 3D-рендера, Default для style/intensity).
   Мьютекс делает их последовательными; поток позы не важен, важна только GL-привязка.
3. **3D GL-ресурсы имеют GL-thread affinity.** `nativeRender3D` и `nativeRelease3D`
   вызываются только на GL-потоке с текущим EGL-контекстом. Нельзя освобождать текстуры,
   VBO/IBO и UBO из Main или Default.
4. **Уничтожение сессии ждёт активные вызовы.** `nativeDestroySession` убирает handle из
   таблицы, затем берёт `session->mutex`; когда мьютекс получен, активных вызовов уже нет.
   После этого ставится `destroyed = true` и ресурсы освобождаются. Новые вызовы со старым
   handle получают ошибку, а не разыменовывают память.
5. **Порядок для 3D:** остановить GL-цикл → вызвать `nativeRelease3D` на GL-потоке →
   дождаться `onSurfaceDestroyed`/остановки `GLSurfaceView` → вызвать `nativeDestroySession`.
   Если `nativeDestroySession` вызвать раньше, GL-ресурсы останутся без владельца.
6. **Ни один JNI-вызов не переживает handle.** Таблица handle'ов хранит `shared_ptr`;
   `destroy`/`release` не освобождают объект, пока выполняется вызов. Повторное использование
   числового handle запрещено: ID монотонные, старый ID возвращает `invalid handle`.
7. **Kotlin-состояние — только Main.** `BgMascotManager` не мутирует `StateFlow` из native-
   или Default-потока. Анализ и создание сессии — `withContext(Dispatchers.Default)`,
   результат возвращается на Main. Никаких синхронных native-callback'ов в UI.
8. **Generation guard.** При смене трека/аккаунта `generation++`; поздний результат старой
   сессии или старого анализа не применяется, даже если JNI-вызов уже завершился.

### 3.7. Media time

`PlaybackEngine.state.positionMs` обновляется 4 раза в секунду. Для 60 fps этого мало.

`MascotClock`:

- Хранит `anchorMediaMs`, `anchorFrameNanos`, `speed`, `playing`, `itemKey`.
- На каждом кадре: `estimate = anchorMediaMs + (frameNanos - anchorFrameNanos) * speed`.
- На каждом тике `PlaybackEngine.state`:
  - при смене item/seek/большом расхождении — snap;
  - иначе плавно подтягивает estimate к target, не давая времени идти назад во время игры.
- При паузе — freeze.
- При `speed` 0.75/1.0/1.25 — учитывает скорость.
- `audioOffsetMs` — настраиваемая поправка (по умолчанию 0; подобрать на устройстве).
- Для BG Mix position приходит из нативного статуса; clock работает так же, но при handoff
  сбрасывается по смене `item.key`.

Тесты clock — отдельно, без UI: 250-мс тики, пауза/резюм, seek, смена скорости, откат назад,
BG Mix handoff.

### 3.8. Жизненный цикл

- `BgMascotManager` не работает постоянно. UI вызывает `setVisible(true/false)`:
  - `true` — тестовая сцена видима;
  - `false` — сцена скрыта/свернута.
- При `visible = true` и включённом маскоте:
  1. проверить eligibility;
  2. получить `BgAnalysisLease` через `BgAnalysisRepository`;
  3. если сессия для этого item уже есть — переиспользовать;
  4. иначе отменить старую, создать новую в `Dispatchers.Default`;
  5. по готовности — `Ready`.
- При `visible = false` — остановить рендер-цикл; сессию можно сохранить до смены трека,
  чтобы быстрое открытие плеера не запускало анализ заново. Ограничить одной сессией.
- При `onTrimMemory`/`onLowMemory` — остановить рендер, `nativeRelease3D` (если 3D),
  `nativeDestroySession`, отпустить `BgAnalysisLease`.
- При reduced motion — не запускать рендер-цикл; отдать статичную позу.
- При смене аккаунта — `onAccountChanged`: stop render, destroy session, release lease,
  cancel job, сброс состояния.

### 3.9. CMake

В `app/src/main/cpp/CMakeLists.txt`:

- Добавить `bgmotion_jni.cpp` и `bg_analysis_registry.cpp` в `add_library(bgbeat_jni SHARED ...)`.
- Оставить имя библиотеки `bgbeat_jni`: его грузит `BgMediaSdk`, контракт не ломать.
- Для 3D добавить `EGL` и `GLESv3` в `target_link_libraries`.
- `bgmedia_rig` уже тянет `bgmedia_vrm`; отдельный таргет не нужен.
- `BGMEDIA_BUILD_DESKTOP=OFF`, без SDL.
- Проверить сборку `std::filesystem`, `std::span`, `cgltf`, `stb_image` на текущем NDK;
  они уже собираются для `bgmedia_rig`/`bgmedia_vrm`.

## 4. Музыкальная реакция

Реакция маскота не должна сводиться к BPM/биту. Нужен нативный кадр музыкального состояния,
который отдаёт фразу, акцент, бас, тишину, бар и секцию.

```cpp
struct MascotMusicFrame {
    float phraseLoudness = -1.0f;  // -1 = нет данных, иначе 0..1
    float accent = 0.0f;           // 0..1, MusicalTimeline::accentAt
    float bass = 0.0f;             // 0..1, MusicalTimeline::bassAt
    float silenceWeight = 1.0f;    // 1 = звук, 0 = тишина
    double bpm = 0.0;
    int beatIndex = -1;
    int beatInBar = 0;
    int barIndex = 0;
    float beatPhase = 0.0f;

    float sectionEnergy = 0.0f;    // 0..1, из AnalysisResult::sections
    bgmedia::SectionKind sectionKind = bgmedia::SectionKind::Unknown;
    bool analyzed = false;

    // Только для 2D-стенда: диагностика позы.
    bgmedia::MoveKind move = bgmedia::MoveKind::Idle;
    bool accented = false;
};
```

На JNI-границе кадр сериализуется в `MascotFrameLayout` (`MascotFrameBuffer.values`,
37 float: 24 поля позы/движения + 13 полей музыки/интенсивности), чтобы не аллоцировать
массивы на каждом кадре. Порядок индексов фиксирован; новые поля только дописываются.

Источники:

- `MusicalTimeline::fromAnalysis(analysis)` — `accentAt`, `bassAt`, `silenceWeightAt`,
  `beatIndexAt`, `beatInBarAt`, `barAt`, `bpmAt`, `phraseLoudnessAt`, `analyzed`.
  `bpmAt` меняется на границах `TempoSegment`, поэтому смена темпа видна в кадре.
- `AnalysisResult::sections` — `sectionEnergy`/`sectionKind` для текущего media time;
  `tempoTransitions` — для диагностики, а не для жёсткого переключения позы.
- `CharacterPose` (2.0) — `move`, `accented`, `impact`, `energy` для 2D-стенда.

Что уже делает BG Motion 3.x:

- `DanceDirector` использует accent/downbeat/bass и `silenceWeightAt` для musical warp;
  `MusicalTimeline::downbeatTrusted()` не даёт принимать слабый downbeat за факт.
- `phraseLoudnessAt` уже масштабирует амплитуду верха в `0.75..1.15` в `DanceDirector`;
  низ и planted contacts не трогаются.
- `DanceFrame` отдаёт `musicAccent`, `bassStrength`, `downbeat`, `beatInBar`, `barIndex`,
  `bpm`, `musicBeat`, `alignmentErrorMs`.

Поэтому host не должен заново «ритмить» позу по BPM. Этот кадр (`MascotFrameLayout`
на JNI-границе) нужен для:

- проверки синхронизации на 2D-стенде;
- визуальных эффектов, не меняющих тайминг позы: rim light, плотность частиц, emission,
  лёгкий camera push, интенсивность сцены;
- диагностики `analyzed`/секций/фраз.

Правило: **акцентная поза и её тайминг не сдвигаются** из-за phrase loudness или смены
секции. Фраза/секция меняют интенсивность эффектов после сэмплирования позы; акцентный
pose guarantee BG Motion остаётся в силе.

2D-стенд обязан визуализировать музыкальные поля `MascotFrameLayout`, иначе качество
синхронизации не проверить: шкала `phraseLoudness`, вспышка `accent`, кольцо `bass`,
затемнение по `silenceWeight`, подпись `sectionKind` + `sectionEnergy`, `beat/bar`, BPM,
флаг `analyzed`, а также `pose.move`/`pose.accented`.

## 5. Phase 0/1: 2D-тестовый стенд

### 5.1. Нативная часть

- `nativeCreateSession(analysisHandle, mode = 0, intensity)`:
  - берёт `shared_ptr<const AnalysisResult>` из handle;
  - `ChoreographyBuilder().build(analysis, style)`;
  - строит `MusicalTimeline::fromAnalysis(analysis)`;
  - хранит `Choreography`/`MusicalTimeline` в сессии.
- `nativeSample2D(handle, mediaTime, out)`:
  - `ChoreographySampler().sample(choreography, mediaTime + playbackOffsetSeconds)`;
  - складывает позу и музыку в `out` по `MascotFrameLayout` (37 float);
  - `move` — ordinal, `accented` — 0/1; layout нельзя менять без версии.
- Стиль 2.0 один; разнообразие — только `ChoreographyStyle.intensity`/`bassInfluence`.
  Это не authored-клипы и не финальная хореография.

### 5.2. Тестовый риг

- Никакого арта. Персонаж — набор примитивов (круги/капсулы/прямоугольники), нарисованный
  кодом. Это измерительный прибор, а не концепт.
- Части: голова, торс, две руки, две ноги, таз. Для каждой — `pivot`, `offset`, `z-order`.
- База — 1000×1000 условных единиц, масштабируется под сцену.
- Маппинг `CharacterPose`:

| Поле | Куда идёт |
|---|---|
| `bob` | вертикальное смещение всего персонажа |
| `lean` | наклон торса вокруг таза |
| `hipShift` | сдвиг таза по X |
| `chestTwist` | лёгкий поворот/скос торса и плеч |
| `rotation` | небольшой наклон тела + параллакс (полный yaw в 2D не изображаем) |
| `headNod` / `headTilt` / `headTurn` | наклон/поворот/сдвиг головы |
| `armLeft/Right`, `elbowLeft/Right` | плечо и предплечье |
| `legLeft/Right`, `kneeLeft/Right`, `footLeft/Right` | бедро, голень, стопа |
| `impact` | squash/stretch + акцентное кольцо |
| `energy` | общая амплитуда |

### 5.3. Compose-рендер

- `MascotStage.kt` — `Canvas(Modifier.fillMaxSize())`; внутри
  `withTransform`/`rotate`/`translate`/`scale`.
- `MascotTestRig.kt` рисует примитивы по `MascotFrameBuffer.values`.
- Поза обновляется в `LaunchedEffect` через `withFrameNanos`; `MascotFrameBuffer.version`
  читается внутри `draw`-блока, чтобы не рекомпозировать весь плеер.
- `FloatArray` переиспользуется, без аллокаций на кадр.
- Тень — эллипс под ногами, масштабируется от `bob`.
- HUD с музыкальными полями `MascotFrameLayout` — только в тестовом стенде, не в продуктовом UI.
- 60 fps — цель; 30 fps — допустимый минимум на слабом устройстве.

### 5.4. Что проверяет Phase 0/1

- Попадание в бит и акцент на слух и на глаз.
- Поведение на pause/seek/смене трека/BG Mix handoff.
- Переиспользование одного анализа BG Beat и маскотом (без второго decode/FFT).
- CPU/память/батарею на слабом и среднем устройстве.
- Не проверяет финальный визуал, арт и продуктовую композицию.

## 6. Phase 2: 3D VRM на BG Motion 3.x

### 6.1. Модель

Требования к VRM:

- VRM 0.x (не VRM 1.0 — импортёр его не поддерживает).
- Humanoid-кости: hips, spine, chest, upperChest, neck, head, shoulders, arms, hands,
  legs, feet, toes.
- 10–20k треугольников, 1–4 материала, 1–4 текстуры 1024×1024.
- Высота 1.2–1.5 м, forward = -Z.
- Лицензия разрешает распространение в APK; `OnlyAuthor`/`Redistribution_Prohibited` не подходят.
- Spring bones/хвост/волосы — опционально; secondary dynamics уже есть.

`models/Model1.vrm` из BG Media использовать нельзя. Для тестов — `tests/fixtures/minimal.vrm`.
Финальная VRM-модель маскота уже в разработке; этот план её не проектирует.

Бюджет скелета: чиби-модель должна целиться в **≤128 костей** (humanoid + волосы/аксессуары).
Больше 128 — только через bone texture/CPU pre-skinning или уменьшение скелета.

### 6.2. Загрузка

- Kotlin читает `assets/mascot/mascot.vrm` в `ByteArray` (или стримит в native).
- `nativeLoadModel(handle, bytes)` → `VrmLoader::loadFromMemory`.
- Модель и текстуры живут в сессии; `releaseTexturePixels()` после загрузки в GPU,
  чтобы не держать RGBA в RAM.
- Клипы: `assets/mascot/dances/*.json` распаковать в `filesDir/mascot/dances-v1/`
  при первом запуске. Публичного `DancePlayer::loadLibraryFromJson` сейчас нет; если
  добавлять — только осознанным bump pin BG Media.
- `DancePlayer::loadLibrary(dir)` + `setLegGeometry(retargeter.legGeometry())`.

### 6.3. Сэмплирование

На каждый кадр (GL-поток):

```cpp
const double t = mediaTime + audioOffsetMs / 1000.0;
bgmedia::motion::DanceFrame frame = dance->update(t);
bgmedia::animation::RetargetedPose pose = retargeter->applyClip(frame.pose, t);
renderer.draw(model, pose, camera, viewport);
```

- `DancePlayer::update` сам обрабатывает seek/backward jump, crossfade и one-shot.
- `setAnalysis(analysisPtr, now)` — указатель на `AnalysisResult` из сессии.
- `setMusicalTimeline(MusicalTimeline::fromAnalysis(analysis), now)`.
- `setMode(Auto, now)` для авто-плана; `select(style, now)` для ручного выбора.
- `setOverlayMode(Full/Light/HandsOnly/Off)` — интенсивность/сложность.
- `setExpression(...)`/`setWarpProfile(...)` — musical warp; не отключать accent guarantee.

### 6.4. GLES-рендерер

Минимальный порт desktop-рендерера:

- EGL-контекст создаёт `GLSurfaceView` (Kotlin); native использует текущий контекст.
- Один vertex/fragment shader:
  - атрибуты: position, normal, uv, joints (`uvec4`), weights (`vec4`);
  - скиннинг: `sum(jointMatrix[j] * pos * weight)` (до 4 влияний на вершину);
  - toon-ramp как в desktop `adapters/sdlgpu/shaders/mesh.frag.hlsl`, но упрощённый;
  - alpha cutoff/blend, double-sided.
- VBO/IBO на каждый primitive; текстуры RGBA8 + mipmaps.
- Проходы: opaque (depth write) → alpha blend (depth test, no write, sort back-to-front).
- Сцена: прозрачный фон, мягкая тень-эллипс, key light + rim.
- Viewport: не больше 720–1024 px по длинной стороне.

#### Передача скелета: UBO, а не `uJoints[192]` в vertex uniforms

Ключевое ограничение GLES 3.0: минимальный `GL_MAX_VERTEX_UNIFORM_VECTORS` = **256 vec4**.
Одна `mat4` занимает 4 vec4, поэтому 192 матрицы = 768 vec4 — это втрое больше минимума.
`uniform mat4 uJoints[192]` как обычный vertex uniform на таком драйвере не помещается.
Даже 64 матрицы = 256 vec4 не оставляют места под остальные uniform'ы, поэтому plain-array
вариант не годится как основной.

Основной путь — **UBO std140**:

```glsl
layout(std140) uniform JointBlock {
    mat4 uJoints[128];  // 128 * 64 = 8192 байт
};
```

- Проверить runtime-лимиты: `GL_MAX_VERTEX_UNIFORM_VECTORS`,
  `GL_MAX_VERTEX_UNIFORM_BLOCKS`, `GL_MAX_UNIFORM_BLOCK_SIZE`,
  `GL_MAX_UNIFORM_BUFFER_BINDINGS`.
- Минимумы GLES 3.0: 12 vertex uniform blocks и 16384 байта на блок. 128 матриц
  (8192 байта) помещаются с запасом; 192 матрицы (12288 байт) тоже, но 192 — только
  диагностический предел, не цель.
- Загружать матрицы через `glBindBufferBase(GL_UNIFORM_BUFFER, 0, ubo)` и
  `glBufferSubData`; обновлять раз в кадр, без readback.
- Если драйвер плохо индексирует UBO-массив по joint index — переходить на fallback,
  а не на `glUniformMatrix4fv` с сотней матриц.

Fallback'и, в порядке предпочтения:

1. **Меньшая палитра на draw** — 64 кости на draw (4096 байт UBO), меш разбит на чанки
   по группам костей; несколько draw call'ов с разными палитрами.
2. **Разбиение draw'ов** по регионам скелета (торс/руки/ноги/голова), каждая группа
   со своей палитрой.
3. **Bone texture + `texelFetch`** — `GL_RGBA32F` текстура, 4 texel'а на матрицу
   (или 2 на dual quaternion); vertex shader читает матрицу по joint index. Проверить
   `GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS` и `GL_MAX_TEXTURE_SIZE`; фильтрация — `NEAREST`.
4. **CPU pre-skinning** — считать трансформированные позиции/нормали на CPU и заливать
   dynamic VBO. Дороже по CPU, но не упирается в uniform-лимиты; годится для слабых GPU
   и небольших мешей.

Для чиби-модели целевой бюджет — ≤128 костей. Этого достаточно для VRM humanoid,
волос и пары аксессуаров; 192 — верхняя граница для проверки, а не проектное требование.

### 6.5. Surface и lifecycle

Два варианта:

1. `GLSurfaceView` внутри `AndroidView` — проще всего.
   - `setEGLContextClientVersion(3)`;
   - `setEGLConfigChooser(8, 8, 8, 8, 16, 0)`;
   - `holder.setFormat(PixelFormat.TRANSLUCENT)`;
   - `renderMode = RENDERMODE_WHEN_DIRTY`;
   - `Choreographer` вызывает `requestRender()` только когда играет и сцена видима.
2. `AndroidExternalSurface` + EGL в native — больше контроля, но больше кода.

Для MVP — `GLSurfaceView`. Обязательно:

- `onPause`/`onResume` по lifecycle;
- при `onDispose`: остановить GL-цикл, `nativeRelease3D` на GL-потоке, дождаться
  `onSurfaceDestroyed`, затем `nativeDestroySession`;
- не уничтожать нативную сессию, пока GL-поток может рисовать;
- проверить прозрачность и z-order SurfaceView относительно Compose-слоёв.

### 6.6. Производительность

- 30 fps минимум на среднем устройстве, 60 fps — цель.
- `RENDERMODE_WHEN_DIRTY`: нет кадров на паузе/в фоне.
- Текстуры — только нужные; `releaseTexturePixels()` после upload.
- Ограничить viewport и не делать readback.
- Проверять UBO-лимиты и bone count при загрузке модели; при нехватке — bone texture
  или CPU pre-skinning, а не падение.
- Профилировать `dumpsys gfxinfo`, `dumpsys meminfo`, GPU time через
  `GL_EXT_disjoint_timer_query` (если доступно).

## 7. Ошибки, fallback, приватность, reduced motion

- Нативная библиотека не загрузилась / нет BG Media core — маскот не показываем, пишем
  один лог, плеер не ломаем.
- Трек не подходит (не audio, secret, не скачан) — сессия не создаётся.
- Анализ упал — статичная поза/скрыть; ошибка только в диагностике.
- 3D-рендерер не инициализировался (нет GLES 3.x/EGL/UBO) — маскот скрывается.
  **2D-риг не является продуктовым fallback**: это тестовый стенд Phase 1.
- 3D-модель не загрузилась/не VRM 0.x — скрыть маскота, ошибка в диагностике.
- Не показывать пользователю технические ошибки в плеере; максимум — «маскот недоступен»
  в настройках.
- Приватность: те же фильтры, что у BG Beat; secret/system-private не анализируются;
  кэш только app-private, per-account; в диагностику не попадают названия треков, пути
  и имена аккаунтов; никаких сетевых запросов.
- При смене аккаунта — `onAccountChanged`: destroy session, release lease, cancel job,
  clear state.
- Reduced motion: `MotionPreset.Off` или системный reduced motion → статичная поза,
  без `withFrameNanos`-цикла. `MotionCategory.Media` отключена → то же самое.
- `intensityPercent` → масштаб амплитуды 2D-позы или `RetargetOptions`/эффектов 3D.
  Никаких вспышек, резких миганий и «тряски» на весь экран.

## 8. Тесты и приёмка

### Unit / Robolectric

- `BgMediaEligibilityTest`: audio/secret/system-private/not downloaded/unsupported.
- `MascotSessionPolicyTest`: dedupe, смена трека, смена аккаунта, выключение, reduced motion.
- `MascotClockTest`: 250-мс тики, pause/resume, seek, speed 0.75/1.25, откат назад,
  BG Mix handoff.
- `BgMascotSettingsStoreTest`: default off, persist, account cache dir.
- `BgAnalysisRepositoryTest`: один acquire на ключ, handle/lease, release, cancellation,
  stale handle, `nativeLiveAnalysisHandles`.
- Native registry test (host/NDK): два acquire одного ключа → один decode/FFT; release
  последнего → snapshot освобождён; отмена владельца видна waiters.
- `MascotTestRigTest`: маппинг полей `MascotFrameLayout` в трансформации (чистая функция).
- `MascotFrameTest`: phrase/accent/bass/silence/section/bar/BPM из фиктивного
  `AnalysisResult`; `analyzed=false` при отсутствии анализа.

### Compose

- Маскот не показывается при выключенном флаге.
- Не показывается/замирает при reduced motion.
- HUD тестового стенда показывает музыкальные поля `MascotFrameLayout`; это не продуктовый UI.
- Screenshot-тест стенда с фейковой позой (Roborazzi) в портрете/ландшафте.

### Native / device

- BG Media уже проверяет детерминизм и seek для motion; добавить JNI-smoke на эмуляторе.
- **Не запускать `connectedAndroidTest` на пакете `com.beykus.beygram.dev`** (AGENTS.md).
  Только эмулятор или отдельный test application ID с изолированными данными.
- Проверка `adb install -r` и сохранности аккаунта — перед выдачей Dev-сборки.
- GLES-проверка: `GL_MAX_VERTEX_UNIFORM_VECTORS`, `GL_MAX_VERTEX_UNIFORM_BLOCKS`,
  `GL_MAX_UNIFORM_BLOCK_SIZE`, `GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS`; выбрать
  UBO/текстуру/CPU-путь до первого кадра.

### Ручной QA-матрица

- BPM: 60, 90, 120, 128, 140, 174; 6/8; смена темпа; тишина в начале.
- Треки: локальный MP3/FLAC, Yandex (скачанный), Telegram audio, voice note (не маскот),
  secret chat audio (не маскот).
- Управление: play/pause, seek вперёд/назад, next/prev, repeat, shuffle, speed.
- BG Mix: переход, смена item, pause во время перехода.
- Lifecycle: свернуть/развернуть, поворот, блокировка, низкая память.
- Настройки: Off, Subtle, Balanced, Expressive, отключённая Media-категория,
  системный reduced motion.
- Устройства: минимум один слабый/средний ARM64 и один эмулятор x86_64.

### Цели производительности

| Метрика | 2D-стенд | 3D VRM |
|---|---|---|
| FPS | 60 (минимум 30) | 30–60 |
| Время кадра (main/GL) | < 2 мс | < 8 мс |
| Подготовка сессии | < 1.5 с при готовом анализе | < 2 с + модель |
| Память | < 30 МБ | < 100 МБ сверх baseline |
| Батарея | без рендера в фоне/паузе | без рендера в фоне/паузе |

### Приёмка Phase 0/1

Phase 0 и Phase 1 считаются принятыми, если:

1. Поза попадает в бит/акцент на слух и на глаз на треках из QA-матрицы.
2. Seek, pause/resume, next/prev и BG Mix handoff не дают скачков и зависаний.
3. BG Beat и маскот используют один `AnalysisResult`; второго decode/FFT нет.
4. На слабом/среднем устройстве держится 30+ fps, нет роста памяти и перегрева.
5. Reduced motion и `MotionSettings` соблюдаются.

Финальный арт для этой приёмки не нужен.

## 9. Этапы

### Phase 0 — спайк (2–3 дня, утверждён)

- `BgAnalysisRepository` + `bg_analysis_registry.cpp/h`: `nativeAcquireAnalysis`,
  `nativeAnalysisJson`, `nativeReleaseAnalysis`, `nativeLiveAnalysisHandles`; миграция
  `BgBeatManager` на repository.
- `MascotClock`.
- JNI-сессия 2.0: create/sample/destroy.
- Debug-канвас с примитивами (без арта и продуктового UI).
- Замеры: анализ, cache hit, CPU, память, fps.
- **Выход:** подтверждение, что sync/seek/pause/track-switch/analysis reuse работают.

### Phase 1 — 2D-тестовый стенд (1–2 недели, утверждён без финального арта)

- `BgMascotSettingsStore`, `BgMascotManager`, `BgMediaEligibility`, `BgMascotNative`.
- `MascotStage`/`MascotTestRig` + debug-сцена за флагом (Dev).
- `MascotFrameLayout` + HUD для проверки фразы/акцента/баса/тишины/секции.
- Фильтры eligibility, account isolation, error handling.
- Переключатель в Experiments + строки.
- Unit-тесты clock/manager/eligibility/repository, Compose screenshot, ручной QA.
- **Выход:** Dev-сборка с тестовым стендом, пригодная для проверки синхронизации.
  Это не продуктовый маскот и не финальный визуал.

### Phase 2 — 3D VRM и BG Motion 3.x (3–6 недель, после готовности модели)

- VRM-модель из разработки; проверка лицензии, bone count, материалов.
- GLES-рендерер: EGL/шейдеры/скиннинг/UBO/текстуры/alpha.
- 3D-сессия: `DancePlayer` + `HumanoidRetargeter::applyClip`.
- Surface lifecycle, GL-thread release, destroy sequence.
- UBO-лимиты и fallback'и; профилирование на средних/слабых устройствах.
- Удаление 2D-стенда из продуктового пути; 2D остаётся только в истории/тестах.
- **Выход:** 3D-режим как эксперимент; 2D не используется как fallback.

### Phase 3 — продуктовая интеграция (после VRM)

- Поверхности, композиция сцены, стили, интенсивность и полировка решаются отдельно,
  когда есть финальная модель. В этом плане они не фиксируются.

## 10. Риски и решения

| Риск | Решение |
|---|---|
| 2D-стенд примут за продуктовый маскот | Только Dev/флаг, примитивы, без арта; удаление после Phase 2 |
| VRM-модель задержится | Phase 0/1 не блокируются: sync/seek/analysis reuse проверяются на 2.0 |
| Двойной анализ с BG Beat | Общий native registry, handle + refcount + in-flight dedupe; lease живёт до смены трека |
| Lease отпущен слишком рано | Держать `BgAnalysisLease` до смены трека/аккаунта; сессия держит `shared_ptr` |
| JNI use-after-free/deadlock | `shared_ptr` в таблице, per-session mutex, destroy ждёт вызовы |
| GL-ресурсы освобождены не в том потоке | `nativeRelease3D` только на GL-потоке, затем destroy |
| `uJoints[192]` не влезает в vertex uniforms | UBO std140 + runtime-лимиты + fallback'и |
| Скелет >128 костей | Уменьшить/упростить, bone texture или CPU pre-skinning |
| Расхождение аудио/визуала | `MascotClock` + `audioOffsetMs` + snap на seek; тесты |
| BG Mix handoff | Смена сессии по `item.key`; generation guard; не убивать GL во время кадра |
| SurfaceView/Compose z-order | Проверить на устройствах; при проблеме скрыть маскота, не подменять 2D |
| APK/память | 2D-стенд лёгкий; VRM ленивая; `releaseTexturePixels()`; viewport cap |
| Лицензия VRM | Только модель с разрешением на распространение; не `Model1.vrm` |
| Reduced motion | Статичная поза; учитывать `MotionSettings` и системный флаг |
| Приватность | Те же фильтры, что BG Beat; per-account cache; без сети |
| Слабые GPU | Capability check + bone texture/CPU pre-skinning; иначе скрыть |
| Перегрев/батарея | RENDERMODE_WHEN_DIRTY, стоп в фоне/паузе, ограничение viewport |

## 11. Открытые вопросы

1. **VRM:** финальный bone count, материалы, размер ассетов и лицензия — когда модель
   будет готова для интеграции?
2. **2D-стенд:** отдельный debug-экран или Dev-плеер за флагом? Оба варианта допустимы,
   но стенд не должен выглядеть как продукт.
3. **Registry:** оставляем общий native registry в BeyGram JNI или поднимаем его в BG Media
   как upstream API? Пока быстрее в BeyGram, без bump pin.
4. **Lease:** держать ли `BgAnalysisLease` текущего трека, пока плеер закрыт? Это ускоряет
   повторное открытие, но держит `AnalysisResult` в памяти.
5. **Стили:** Auto или ручной выбор Groove/Hip-Hop/Side Step/Energetic/Idle?
6. **Целевые устройства:** минимальный FPS и самый слабый телефон для приёмки?
7. **BG Media pin:** нужен ли `DancePlayer::loadLibraryFromJson`/Android-адаптер, или
   распаковываем клипы в `filesDir` и не трогаем pin?
8. **После VRM:** voice/UI-реакции и продуктовые поверхности — отдельный этап.

## 12. Предлагаемый первый PR

Не начинать с 3D-рендерера и не делать 2D-арт. Первый PR — вертикальный срез
инфраструктуры:

1. `bg_analysis_registry.cpp` + `BgAnalysisRepository`: `nativeAcquireAnalysis` /
   `nativeAnalysisJson` / `nativeReleaseAnalysis`, in-flight dedupe, refcount;
   миграция `BgBeatManager` на repository.
2. `MascotClock` + unit-тесты.
3. `bgmotion_jni.cpp`: create/sample/destroy для BG Motion 2.0 + `MascotFrameLayout`.
4. Debug-канвас с примитивами (Dev/флаг), без арта и продуктового UI.
5. Замеры: анализ, cache hit, FPS, память, поведение на seek/смене трека.

Если спайк показывает хорошую синхронизацию и производительность — Phase 1 доводит
тестовый стенд до удобного инструмента. Финальный визуал начинается в Phase 2, когда
приходит VRM-модель.
