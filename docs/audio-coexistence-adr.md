# Audio Coexistence 1.0

Status: proposed for #1323. Baseline: DEV aed8413887ab7c2917263eec6dfc6758d9e2354a, Media3 1.11.1, target SDK 36.

## Current ownership

- PlaybackEngine owns the active snapshot, queue source and persistence, resolver, prefetch and paging, ExoPlayer and BG Mix. Speech currently enters the same transport; restoring an interrupted music queue does not preserve its live renderer.
- VoiceBubble and VideoNote observe the shared snapshot and use its seek and toggle methods. VideoNote also binds the engine's TextureView and stops secret content on disposal. Both pass an explicit-start callback for marking playback.
- ChatScreen owns the recording interruption token and also calls the separate recording focus API. Audit the recorder's actual start/stop callbacks before moving ownership to capture lifecycle.
- TrackCrossfader writes primary and tail player volume. The engine also writes volume for sleep fade, interruption recovery and Exo/BG handover. BG focus writes native gain directly. Capturing current renderer volume as a new base would compound attenuation on subsequent transitions.
- PlaybackService owns the Android MediaSession. The engine's ForwardingPlayer mediates system transport. ProfileMusic and MusicPlayerSheet currently use the shared state and transport. PlaybackSelectors projects the shared snapshot into NowPlayingBar and full-player UI.

## Proposed contracts

Keep PlaybackEngine.state and the existing music transport for music callers once speech consumers migrate together. ProfileMusic continues to observe PlaybackEngine.state and call togglePlayPause or playQueue. During migration, audit all mixed-media callers and make them explicitly dispatch by item type. Add a read-only speech snapshot and addressed speech operations for toggle, pause, seek, surface attachment and teardown. Every operation that targets an existing speech item checks the account, occurrence and generation. Use main-thread ownership, and publish a combined snapshot for surfaces that need a consistent pair.

Speech owns one lazily created ExoPlayer. Reuse the growing Ogg data source and deferred seek contract. Track player identity for callbacks, bind the surface only for the current occurrence and clear it on detach. Mark playback from the current occurrence's confirmed explicit start only. Release speech on logout/account switch and protected-scene disposal. Keep speech out of persisted queue and system metadata. Music remains the sole system transport. Speech-only initially has in-app controls and no system notification. Verify background teardown before shipping this behavior.

## Policy and gain

Store a local policy per action (record voice, record circle, play voice, play circle), with separate internal and external selections. Proposed first-run defaults for review are continue for recording, 20% duck for message playback and unity speech gain. External defaults require review. Store bounded gains independently of Android stream volume, with reset and a migration from absent keys.

Give each recording or speech occurrence an identity-bound lease. Acquire only after confirmed start eligibility, hold through buffering while play is requested and release on pause, stop, completion, failure or disposal. Transfer leases atomically when replacing speech. Release all on account change. Recording can stop speech without granting any stale callback authority over the new capture. Preview acquires playback policy only when explicitly played.

An active pause lease takes priority. Resume only if music intended to play before the first pause lease and the user, account, queue and system focus still permit it. Compose overlapping duck leases by minimum so a configured level remains the upper bound of each owner. Multiply this factor by user gain, sleep envelope and each renderer's transition envelope. Preserve crossfade ratios for primary, tail and BG Mix. Route all existing renderer writes through one owner and treat native handover silence as an envelope. Cancel superseded ramps from their current value. Measure headroom and clipping on device before finalizing gain curves.

## Audio Focus

Migrate Exo automatic focus and BG manual focus in one integration change. A main-thread coordinator owns the Android request and callback generation. Music normally uses GAIN, and an external preference maps to no additional request, TRANSIENT_MAY_DUCK or TRANSIENT. Retain owned music focus during local mixing. When no lane owns focus, continue-mode speech is a deliberate no-request policy and requires testing during calls, background and device routing changes. Decide the replacement strategy for owners with different focus strengths through platform integration tests before enabling it. Reject delayed grants initially and handle denial without starting focus-dependent output.

Use system automatic ducking where applicable and avoid multiplying a second attenuation for the same event. For delivered loss callbacks, suspend affected lanes with a separate focus reason. A permanent loss clears auto-resume intent. A transient gain resumes only current, still-requested playback. Explicit pause, logout and becoming noisy cancel resume intent. Validate focus acquisition from top app or eligible foreground service on SDK 36.

## Release and rollback

Merge policy data only when behavior remains compatible with DEV. Ship renderer, gain and focus migration together if intermediate ownership would overlap. Then wire the dual NowPlayingBar after owner review and #1363 coordination. Retain the existing music queue format and application identity. Revert integration as a unit if playback or session regressions appear. Settings keys can remain dormant on rollback.

Before enabling the integration, verify lease overlap, user pause during interruption, focus denial and stale callbacks, crossfade and both BG handover directions, progressive seek, secret/view-once teardown, system transport and three chat top-panel styles. Use synthetic states and Roborazzi for the visual suite. Samsung M21 acceptance must measure recording continuity, gains and clipping on speaker, wired and Bluetooth routes, then verify external-player focus behavior, calls, lockscreen and disconnection.

References: [Issue #1323](https://github.com/Beykus-Y/BeyGram/issues/1323), [Android Audio Focus](https://developer.android.com/media/optimize/audio-focus).
