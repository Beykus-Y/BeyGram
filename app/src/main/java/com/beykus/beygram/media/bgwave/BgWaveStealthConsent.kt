package com.beykus.beygram.media.bgwave

/**
 * Wave signals must reach the peer at once, so stealth mode cannot delay them. They need the user's explicit consent
 * whenever stealth would hold an ordinary send or warns before one.
 */
internal fun waveNeedsStealthConsent(holdsSends: Boolean, warnsBeforeSending: Boolean): Boolean =
    holdsSends || warnsBeforeSending

/** Fail closed: a wave signal goes out only when stealth does not apply or the user consented to this wave session. */
internal fun waveSignalAllowed(needsConsent: Boolean, consented: Boolean): Boolean = !needsConsent || consented
