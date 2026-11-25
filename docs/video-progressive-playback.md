# Потоковое видео: почему ждёт полной загрузки и что позволяет TDLib (#1179, продолжение #1121)

Исследование по ручному отчёту: в DEV 697 видео начинает играть только после полного скачивания. Документ
исправляет более ранний вывод («TDLib не умеет сообщать о скачанном хвосте») и описывает архитектуру
range-aware источника. Замеров нет, ускорение не заявляется.

## Как сейчас устроено воспроизведение

`MediaViews.kt` (DEV `a189fc11`), просмотрщик медиа:

1. Пока страница открыта, приложение просит `DownloadFile(fileId)` с приоритетом 16 и без диапазона.
2. `Mp4StreamingProbe.inspect(File(downloadPath), downloadedPrefixBytes)` читает только заголовки боксов и `moov`
   из уже скачанного префикса.
3. Если `streamable == true`, создаётся `ProgressiveVideoSource`, и ExoPlayer читает растущий файл через
   `ProgressiveFileDataSource` (`readablePrefix = downloadedPrefixSize`).
4. Иначе остаётся ветка «скачано X из Y»: воспроизведение после полной загрузки.

Вопрос «почему ждёт полной загрузки» = «почему шаг 3 не срабатывает или срывается».

## Что проверено в TDLib 1.8.65 (наша библиотека)

### `getFileDownloadedPrefixSize` существует

Проверено по `app/libs/tdlib-java.jar` (`javap`):

```java
public class TdApi$GetFileDownloadedPrefixSize extends TdApi$Function<TdApi$FileDownloadedPrefixSize> {
    public int fileId;
    public long offset;
}
public class TdApi$FileDownloadedPrefixSize extends TdApi$Object {
    public long size;
}
```

Реализация upstream (`td/telegram/Requests.cpp` на коммите схемы `a17f87c4cff7`):

```cpp
void Requests::on_request(uint64 id, const td_api::getFileDownloadedPrefixSize &request) {
  if (request.offset_ < 0) return send_error_raw(id, 400, "Parameter offset must be non-negative");
  auto file_view = td_->file_manager_->get_file_view(FileId(request.file_id_, 0));
  if (file_view.empty()) return ... "Unknown file ID";
  send_result(id, td_api::make_object<td_api::fileDownloadedPrefixSize>(
                     file_view.downloaded_prefix(request.offset_)));
}
```

`FileNode::downloaded_prefix(offset)` (`FileManager.cpp`):

- полный локальный файл — `size - offset` (0 за концом);
- частичный файл — `Bitmask(ready_bitmask).get_ready_prefix_size(offset, part_size, size)`, то есть **длина
  непрерывного готового участка, начинающегося ровно с `offset`**; 0, если часть с этим смещением ещё не скачана;
- для `SecureEncrypted` до расшифровки — 0.

Запрос синхронный, локальный, без сети: он отвечает по уже записанным данным.

### `downloadFile` умеет диапазон

`DownloadFile(fileId, priority, offset, limit, synchronous)`: `offset` — начало загрузки, `limit` — сколько байт
скачать до авт оотмены (`0` — без ограничения). `download_impl` пишет `node->set_download_offset(offset)` и
`set_download_limit(limit)`; у файла **один пользовательский диапазон**: новый запрос с другими `offset/limit`
заменяет предыдущий (`user_offset_`/`user_limit_`), внутренние загрузки TDLib (`internal_downloads_`) этим не
затрагиваются.

`set_download_offset` **не очищает** битовую карту готовых частей: ранее скачанные части остаются доступными.
Поэтому файл после смены диапазона — разреженный (sparse): часть данных есть, часть нет.

### Важное следствие про `LocalFile.downloadedPrefixSize`

`local_ready_prefix_size_` пересчитывается от **текущего** `download_offset_`. После запроса хвоста
`downloadedPrefixSize` описывает префикс уже от хвоста, а не от нуля. Значит:
- текущий `ProgressiveFileDataSource` нельзя использовать после смены диапазона;
- range-aware чтение обязано спрашивать `getFileDownloadedPrefixSize(fileId, pos)` для конкретной позиции, а не
  доверять `LocalFile`.

## Почему видео ждёт полной загрузки: гипотезы и статус

| # | Причина | Статус |
| --- | --- | --- |
| H1 | `moov` в конце файла: без индекса играть нельзя, а приложение не умеет забрать хвост | **подтверждается кодом**: `MoovAfterMediaData` → `progressiveRejected` навсегда |
| H2 | Сжатые BeyGram видео иногда не fast-start: muxer Media3 лишь «пытается» (`attemptStreamableOutputEnabled = true` по умолчанию, но без гарантии) | требует проверки файла на устройстве |
| H3 | Медленный приоритет 16 и срыв по таймауту `ProgressiveFileDataSource` (45 с ожидания чанка, 10 с без активной загрузки) | требует логов #1176 |
| H4 | Кодек вне `Mp4StreamingProbe.VIDEO_CODECS` → `UnsupportedVideoCodec` | проверяется `inspect` на файле |

H1 объясняет отчёт полностью и до сих пор не лечился: приложение вообще не пробовало качать хвост.

## Архитектура range-aware DataSource (предложение)

Цель: не создавать второй кеш, не обновлять TDLib, не читать непроверенные области разреженного файла.

```
RangeAwareFileDataSource(
    fileId, totalSize,
    readableAt(offset) -> Long   // GetFileDownloadedPrefixSize (async TDLib call, кэшируется на кадр)
    requestWindow(offset, limit, priority)
    cancelPending()
)
```

### Открытие файла с `moov` в конце

1. `requestWindow(offset = totalSize - K, limit = K, priority = 32)`, где `K = min(totalSize, MAX_MOOV_BYTES + 16)`
   (16 МБ — существующий лимит `Mp4StreamingProbe`).
2. Ждать, пока `getFileDownloadedPrefixSize(fileId, totalSize - K) >= K` (или пока не станет ясно, что нужный
   `moov` уже внутри скачанного участка — боксы читаются с конца).
3. Разобрать боксы хвоста: найти `moov`, проверить, что он целиком внутри готового участка, и извлечь кодек/индекс.
4. `requestWindow(offset = позиция воспроизведения, limit = 0, priority = 32)` и отдать данные плееру.

Стоимость: один лишний диапазонный запрос к TDLib при открытии; данные остаются в том же кеше TDLib.

### Чтение

- `read(buffer, offset)`: `available = getFileDownloadedPrefixSize(fileId, position)`.
- Если `available == 0` — запросить окно с `position` и ждать (тот же таймаут-цикл, что сейчас, но с явным
  запросом нужного смещения вместо «качай с нуля»).
- Никогда не читать байты вне `[position, position + available)`: разреженный файл может содержать мусор.
- Для `SecureEncrypted` API всегда возвращает 0 — прокси-поток для секретных чатов не делаем.

### Перемотка в нескачанную область

`ProgressiveMediaSource` при seek вызывает `open(DataSpec(position = X))`; DataSource запрашивает окно с `X` и ждёт
готовности. Это укладывается в текущую модель `DataSource`, менять Media3-конвейер не нужно.

### Приоритеты и отмена

- Пользователь смотрит → `priority = 32`; предзагрузка соседней страницы → 16.
- У файла один пользовательский диапазон: новый `DownloadFile(offset, limit)` сам заменяет предыдущий
  (`user_offset_`), этого достаточно для смены окна.
- `CancelDownloadFile(onlyIfPending = true)` отменяет только ещё не начатую загрузку: уже идущий диапазон он не
  прерывает. Поэтому полагаться на него как на «остановить всё» нельзя — окно меняет именно новый `DownloadFile`.
- Один координатор на пару `(accountId, fileId)` с владельцем окна и версией: уход со страницы не должен отменять
  загрузку, которую использует другой потребитель (предзагрузка соседней страницы, второй вьювер). `reset`
  отменяет загрузку только если окно всё ещё принадлежит ушедшему владельцу.
- Устаревший `UpdateFile` не доказывает готовность нового окна: доступность всегда перепроверяется
  `getFileDownloadedPrefixSize` для конкретного смещения, а не по «последнему известному» префиксу.

### Ограничения

- Гранулярность готовности — часть (`part_size`), но `get_ready_prefix_size(offset)` возвращает точную длину от
  `offset`, поэтому читать с середины части можно после её загрузки.
- Пока активен хвостовой диапазон, префикс не качается: последовательность «хвост → индекс → воспроизведение»
  должна быть явной и быстрой (один переключатель окна).
- Один кеш: всё лежит в локальном файле TDLib; второй кеш не нужен и запрещён.

## Сравнение с существующими решениями

| Решение | Подход | Что берём |
| --- | --- | --- |
| Telegram Android | свой загрузчик файлов с диапазонами и `FileStream`; плеер читает поток, загрузчик докачивает нужный участок | модель «один файл, много диапазонов, приоритет по видимости» |
| Telegram iOS | `ChunkMediaPlayer` с чанками и ожиданием `playableDuration` | явное ожидание готовности диапазона |
| Media3 | `ProgressiveMediaSource` + кастомный `DataSource` | оставляем конвейер; меняем только `DataSource`, как сейчас |

## Что нужно от устройства

1. Взять видео, которое не начинает играть до полной загрузки.
2. Снять причину из диагностики #1176 (`cause`, `probe_result`, `layout`, `first_read_ms`) — она уже есть, второй
   журнал не нужен.
3. Если причина `moov_at_end` — это H1 и подтверждение необходимости range-aware источника.
4. Сравнить `getFileDownloadedPrefixSize` для `offset = 0` и `offset = size - K` до и после запроса хвоста.

## План внедрения (после подтверждения)

1. `RangeAwareFileDataSource` рядом с существующим `ProgressiveFileDataSource`, за флагом, без изменения текущего
   поведения по умолчанию.
2. Хвостовой запрос только для файлов с `moov` в конце и размером больше порога (например, 2 МБ).
3. Тесты на JVM: фейковый `getFileDownloadedPrefixSize` (разреженная карта), проверка «не читаем дырки»,
   порядок окон, отмена, поведение при `available == 0`.
4. Замеры TTFF и числа переключений окна на слабом устройстве; только после них — вывод о выигрыше.

## Ограничения задачи

- TDLib не обновляется.
- BG Media и музыкальная подсистема не затрагиваются.
- Политика автозагрузки, приватность и анимации не меняются.
- Диагностика прогрессивных попыток — только в PR #1176; здесь её нет.