# BG Wave — Глубокое ревью системы

- **Дата аудита**: 2026-11-15
- **Проверенные ревизии**:
  - `Beykus-Y/BeyGram`: ветка `origin/DEV` (`3ff7e50e`, включая PR `#799`, `#829`, `#838`, `#843`, `#849`, `#851`, `#852`, `#853`, `#855`)
  - `Beykus-Y/BG-Media`: ветка `origin/dev` (`28a54a8affe6e96a7231854127e312d8455ac578`, PR `#68`, `#69`)
- **Связанные задачи**: `#799`, `#854`, `#856`, `#857`, `#858`, `BG-Media#67`, `BG-Media#70`, `BG-Media#71`.

---

## 1. Сводка критических и высокоприоритетных находок

### `BG-Media` (`BG-Media#70`, `BG-Media#71`)
1. **Джиттер-буфер фактически отключён (`WaveAudioPipeline.cpp`, `WaveJitterBuffer.cpp`)**: `processListenerStep()` выгребает `jitterBuffer_` до нуля на каждом 10 мс тике, сбрасывая `primed_ = false` и провоцируя ложные `MissingPlc` при любом переупорядочивании пакетов (объясняет симптомы `#854`).
2. **OOB read в RTP и игнорирование padding (`WebRtcWaveTransport.cpp`)**: `rtp->getExtensionHeaderSize()` вызывается до проверки границ буфера; флаг `rtp->padding()` и `rtp->ssrc()` не проверяются.
3. **Гонка `Failed` vs `Closed` (`WebRtcWaveTransport.cpp`, `WaveAudioPipeline.cpp`)**: при несовпадении DTLS SHA-256 fingerprint или таймауте `connectTimeoutMs` вызывается `peer->close()` до `setState(Failed)`, из-за чего пайплайн переходит в `Ended` вместо `P2PFailed` и блокирует `Retry`/fallback.
4. **SDP-инъекция и валидация (`WaveSignaling.cpp`)**: не запрещаются `\r`, `\n`, `\0` и пробелы в строковых полях (`iceUfrag`, `icePwd`, `dtlsFingerprint`, `foundation`, `address`); `parseSdpToWaveSignal()` молча приводит неизвестные типы/протоколы к `Udp`/`Host`; `base64UrlDecode()` игнорирует padding-биты.
5. **Безопасность `acceptOffer()` и `nowUnixSeconds == 0` (`WaveAudioPipeline.cpp`)**: `acceptOffer()` не проверяет активное состояние и не защищён от replay `(sid, nonce)`; передача `0` в `acceptOffer`/`acceptAnswer`/`handleRemoteSignal` молча отключает проверку TTL.
6. **Потокобезопасность (`WaveAudioPipeline.cpp`, `WaveTransport.cpp`, `WebRtcWaveTransport.cpp`)**:
   - Не-`seq_cst` синхронизация `audioInFlight_`/`audioPausedForReset_` допускает гонку на ARM64 при сбросе колец.
   - Сырой захват `[this]` в `wireTransportCallbacks()` и `switchToExternalCallFallback()`.
   - Гонки `lastLocalSignal_`, `externalCall_`, отсутствие синхронизации в `SimulatedWaveTransport`.
   - Неперехваченные исключения `libdatachannel` в `startOffer()` и конструкторе `PeerConnection` в `acceptOffer()`.
7. **NAT/TURN и CMake**: отсутствует TURN-конфигурация и fallback, в `adapters/*/CMakeLists.txt` используется `${CMAKE_SOURCE_DIR}` вместо `${PROJECT_SOURCE_DIR}`, расхождения в `docs/BG_WAVE.md`.

### `BeyGram` (`#856`, `#857`, `#858`)
1. **Use-After-Free по сырому `Session*` (`bgwave_session_jni.cpp`, `BgWaveSessionNative.kt`, `bgmix_jni.cpp`, `BgWaveHub.kt`)**: нет таблицы хендлов для `Session`, `nativeGuard.read` отсутствует в большинстве методов `BgWaveSession`, а `syncAudio()` передаёт голый указатель в `bgmix_jni.cpp` вне замка.
2. **Дедлок `AudioTrack.WRITE_BLOCKING` (`BgWaveListenerAudio.kt`)**: `stop()` вызывает `pump.join(500)` до `track.stop()`/`flush()` (фриз при `AUDIOFOCUS_LOSS_TRANSIENT`), затем освобождает `AudioTrack` под работающим `pumpLoop`; при underrun в буфер набивается блокирующая тишина; при `AUDIOFOCUS_LOSS` `BgWaveHub` отбирает фокус обратно через 1 с.
3. **Поломанный жизненный цикл (`BgWaveController.kt`, `BgWaveHub.kt`, `ShareWave.kt`)**:
   - `stop()` в фазе `Failed` не закрывает нативную сессию → вечный `BUSY`.
   - `BgWaveHub.retry()` не перезапускает `ensureTicker()`.
   - 5-секундный ICE gathering выполняется синхронно на UI-потоке под `controller.lock`.
   - `forgetAccount()`/`dismiss()` выходят из тикера до `syncAudio()` и сносят инвайты чужих аккаунтов; `ShareWaveDialog` показывает «Отправлено» при `BUSY`.
   - Нет `ForegroundService` / `WakeLock` / `WifiLock` на стороне слушателя.
4. **Захват аудио хоста (`BgWaveTapProcessor.kt`, `BgWaveHostTap.kt`, `PlaybackEngine.kt`)**: тишина на 24/32-bit Hi-Res, утечка голосовых/видеокружков/`systemPrivate` в волну, окно double-tap при handover в BG Mix, задержка до 1с на старт BG Mix тапа, отсутствие тапа на `newCrossfadePlayer()`.
5. **TDLib и UI (`TdLibTelegramRepository.kt`, `TdNotifications.kt`, `BgWaveCardModel.kt`, `ChatScreen.kt`, `NowPlayingBar.kt`)**:
   - Отправка сигналов без `sendNowConsent = true` (ломается в Stealth Mode); не отслеживается `UpdateMessageSendFailed`.
   - Пропуск инвайтов при `profiles.chat == null` и при загрузке истории `GetChatHistory`; нет обработки удаления/редактирования.
   - Уведомления и превью чатов показывают «Служебное сообщение BeyGram» для скрытых `ANSWER`/`CANCEL`/`END`.
   - Несовпадение строк ошибок `WaveSignaling` и `BgWaveCardModel`; `nonce.toLong()` падает при MSB=1 у `uint64_t`.
   - `ChatScreen` рисует/скрывает wave-сообщения в группах и пересылках; `NowPlayingBar` скрывает `WaveListenerBar` при поставленном на паузу треке.
6. **Сборка и CI (`scripts/build-openssl-android.sh`, `CMakeLists.txt`)**: скрипт сборки OpenSSL зашит под `linux-x86_64`, статические `libssl.a`/`libcrypto.a` не скрыты через `--exclude-libs`.

---

## 2. План отдельных PR (без автослияния в `DEV` / `dev`)

### `Beykus-Y/BG-Media` (целевая ветка `dev`)
- `fix/wave-jitter-and-pipeline-audio` — джиттер-буфер, ресемплер, lifecycle-гард `WaveAudioPipeline`, ARM64 `seq_cst`, `SimulatedWaveTransport` (`#70`, `#67`).
- `fix/wave-signaling-and-security` — валидация SDP-инъекций, `acceptOffer` state/replay, base64url padding, overflow (`#70`, `#67`).
- `fix/wave-webrtc-and-opus` — RTP bounds/padding/ssrc, гонка `Failed`/`Closed`, `try/catch` вокруг `libdatachannel`, `Disconnected` grace, `OpusWaveCodec` (`#71`, `#67`).
- `fix/wave-cmake-and-docs` — `PROJECT_SOURCE_DIR` в `adapters/*/CMakeLists.txt` и актуализация `docs/BG_WAVE.md` (`#71`, `#67`).

### `Beykus-Y/BeyGram` (целевая ветка `DEV`)
- `docs/bg-wave-review` — фиксация данного отчёта в `docs/bg-wave-review.md`.
- `fix/bgwave-jni-session-safety` — таблица хендлов `Session`, `nativeGuard`, `try/catch`, UTF-8, `ULong` nonce, `@Keep` (`#856`).
- `fix/bgwave-controller-hub-lifecycle` — `BUSY`-фикс, `retry` тикер, non-blocking IO gathering, `AudioTrack` deadlock/underruns, `AUDIOFOCUS_LOSS`, `PlaybackService` + `WakeLock`/`WifiLock` (`#858`, `#856`).
- `fix/bgwave-host-audio-tap` — 24/32-bit PCM, приватность медиа, handover без double-tap, `newCrossfadePlayer` (`#856`, `#854`).
- `fix/bgwave-tdlib-and-chat-ui` — Stealth Mode `sendNowConsent`, `UpdateMessageSendFailed`, история/кэш чатов, подавление уведомлений, маппинг ошибок, `ShareWave`, `ChatScreen`, `NowPlayingBar` (`#858`).
- `fix/bgwave-android-build-and-ci` — кроссплатформенный `build-openssl-android.sh` и `--exclude-libs` для OpenSSL (`#857`).
