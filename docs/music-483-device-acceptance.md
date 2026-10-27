# BeyGram Music 2.0 — follow-up acceptance / device matrix (#483)

This checklist is **not evidence of completed device testing**. Execute on at least two Android devices (including Samsung M21 where possible) and attach logs/results before approving the PR.

## Process death and privacy
- Start music in a normal chat, pause at a known position, verify persisted account-scoped message origins and repeat/shuffle. Kill the **app process**, reopen with the same account and confirm a paused playlist at roughly the previous timestamp, **no automatic audio, background service or speculative download**, then press Play.
- Change the selected account, kill/relaunch: no cross-account metadata, artwork, playback or resurrection. Return to the first account and restore only its own queue.
- Explicit Stop must clear persisted state; force-stop and relaunch must not resurrect music. Check process death immediately after Stop and while app is foreground/background.
- Delete a source message, remove access, log out, and retry restoration: stale items must be discarded. Secret/view-once/protected chat files must never appear in preferences, MediaSession, notification, or restored queue.
- With 100+ queued messages verify bounded storage (80 identities maximum) and that the selected track retains its location.

## Chat and channel playlists
- Play music from an ordinary chat, channel, Saved Messages, and a forum topic. Verify neighboring **Audio** messages form an ordered queue (newest -> oldest) including files not yet downloaded, without crossing chat/topic boundaries.
- Next/Previous, user pause, end-of-track, shuffle, repeat Off/One/All, reorder and Play Next should never duplicate or skip messages while a remote older page arrives.
- Start from a chat/channel with 20+ songs, scroll through pages, and play to an older page. Verify cursor, dedupe, no page reordered and no unexpected mass download (Data Saver/roaming respected).
- Switch chat/account or Stop while a page is in flight; the old response must not mutate the active playlist.
- Simulate zero-results page, deleted/unavailable media, no connection and retry. Verify buffering/error semantics and no infinite pagination requests.
- Test fileId collisions between Telegram accounts, unchanged track position on append, source navigation and voice/video-note interruption/resume.

## System integration — mandatory physical checks
- Android notification, lock screen and Bluetooth media keys: Play, Pause, Next, Previous, Seek, Stop, metadata, artwork and queue follow MediaSession exactly.
- Wired headset disconnect/reconnect, Bluetooth connect/disconnect, car/headset media buttons, incoming phone call, audio focus loss/gain, changing output route. No parallel audio or unsolicited resume after manual pause.
- Equalizer: supported and unsupported Android audio session, wired/Bluetooth/speaker, toggle, Flat, built-in and custom presets; confirm audible effect **only on music**, stable audioSessionId lifecycle, release on Stop/switch.
- 2–3 hours background playback: no phantom notifications, excessive traffic/battery/CPU or leaked audio effects.
- Accessibility (TalkBack, 200% font, landscape), small screen, AMOLED/light/dark theme, animation scale changes.

## PR gate
- CI: assemble debug + JVM tests + lint as configured by this repository.
- Codex/manual review for concurrency and thread-affinity; no merge until owner explicitly approves.
- Record model, Android version, screenshots/video of lock screen/media controls, pass/fail and failure logs for each case.
