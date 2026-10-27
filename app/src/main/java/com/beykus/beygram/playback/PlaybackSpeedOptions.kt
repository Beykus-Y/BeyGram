package com.beykus.beygram.playback

/**
 * Shared choices for music, voice messages and circular videos. The chosen value is stored separately per
 * category: changing a voice speed never changes music (which defaults to 1x), and vice versa.
 */
internal val PLAYBACK_SPEED_OPTIONS = listOf(0.5f, 1f, 1.5f, 2f, 5f)
