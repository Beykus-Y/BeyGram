# Media Picker Performance 2.0 (#1342, #1343)

This document tracks the optimization stages for the unified Emoji / Sticker / GIF picker (`StickerPanel`, `DockCustomEmoji`, `ChatComposerStickerPanel`) following physical testing of DEV build 824 (`697302d21`) on Samsung Galaxy M21 (Android 16).

## Implemented Stages

| Stage | PR | Scope |
| --- | --- | --- |
| **D1** | #1353 | Removed synchronous `File.exists()`/`length()`/`lastModified()` from `SharedTgsEmojiRenderer.attach()`; added memory-only `peekFirstFrame()`, moved scheduler loop to `Dispatchers.Default` with batched main-thread frame commits; moved `SharedTgsEmoji` lifecycle to `DisposableEffect`; replaced `AndroidView(ImageView)` static bitmaps with Compose `Image`; bounded `LocalMediaImage` decodes (`MediaDecodeDispatcher`); warmed `PackOrderPreferences` & `RecentEmojiStore` off main thread. |
| **D2** | #1354 | Added account-scoped, version-validated `MediaPickerSessionCache` (`WeakReference<ConversationRepository>` identity check, bounded LRU 24 sticker packs / 64 custom emoji sets, `clearAccount` on logout); eliminated warm-reopen TDLib bursts and loading flashes; gated sticker shelf fetches on `StickerPanelTab.Stickers`; paused animations and froze `belowPanelPx` updates during `AnimatedVisibility` open/close transitions. |
| **D3** | #1356 | Added `MediaPerformanceTier` (`Low`/`Mid`/`High` using `isLowRamDevice`, `memoryClass`, `availableProcessors()`, `isPowerSaveMode`) and weighted TGS budget (`animationCostUnits`); routed picker TGS stickers through `SharedTgsMedia`; added `GifAnimationBudget` concurrency cap with preview preemption; paused animations during `StickerUnifiedFeed`, `StickerGrid`, and `GifGrid` scroll/fling. |
| **D4** | Current | Added `MediaPickerPerformanceRegressionTest` (tab gating, warm reopen zero-TDLib-burst, surgical version invalidation, logout cleanup, 50 open/close cycles with zero leaked renderer contexts/schedulers/GIF slots) and this profiling runbook. |

## Automated Deterministic Verification

Run locally on JVM / Robolectric:

```powershell
$env:ANDROID_HOME = 'C:\Users\Beykus\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDevDebugUnitTest `
  --tests '*MediaPickerPerformanceRegressionTest*' `
  --tests '*MediaPickerSessionCacheTest*' `
  --tests '*UnifiedAnimationBudgetTest*' `
  --tests '*SharedTgsEmojiRendererTest*' `
  --tests '*EmojiPanelStressTest*' `
  --tests '*StickerPanel*'
```

> **Note:** JVM/Robolectric tests and green CI (`Build APKs`) verify logic, caching, gating, budget math, and resource cleanup. They **do not** replace physical frame-time / jank measurements on Samsung M21.

## Physical Device Profiling Protocol (Samsung M21, Android 16)

Use `scripts/perf-capture.ps1` against `com.beykus.beygram.dev` (do **not** clear app data or uninstall the existing session per `AGENTS.md`). Run each scenario in both **Material** and **Liquid Glass** modes:

```powershell
.\scripts\perf-capture.ps1 -Label "picker-cold-open" -Seconds 15
.\scripts\perf-capture.ps1 -Label "picker-warm-reopen" -Seconds 15
.\scripts\perf-capture.ps1 -Label "picker-tab-switch" -Seconds 20
.\scripts\perf-capture.ps1 -Label "picker-emoji-scroll" -Seconds 25
.\scripts\perf-capture.ps1 -Label "picker-stickers-scroll" -Seconds 25
.\scripts\perf-capture.ps1 -Label "picker-gifs-scroll" -Seconds 25
.\scripts\perf-capture.ps1 -Label "picker-preview-open-close" -Seconds 20
.\scripts\perf-capture.ps1 -Label "picker-20-cycles" -Seconds 30
```

## Comparison vs DEV Build 824 (`697302d21`)

| Scenario | Build 824 (`697302d21`) | Post D1–D4 (Pending Samsung M21 capture) |
| --- | --- | --- |
| Cold open (Emoji / Stickers / GIFs) | Noticeable freeze reported | Pending device capture |
| Warm reopen | Re-created state + TDLib burst | Pending device capture |
| Dense grid scroll (Emoji / Stickers / GIFs) | Scroll jank reported | Pending device capture |
| 20x open/close cycles | Unmeasured | Pending device capture |
