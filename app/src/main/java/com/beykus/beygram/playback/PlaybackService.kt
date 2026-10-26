package com.beykus.beygram.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.beykus.beygram.MainActivity
import com.beykus.beygram.R

/**
 * Держит общий плеер на переднем плане, пока он играет: уведомление с управлением, экран блокировки и гарнитура.
 * Сам плеер принадлежит [PlaybackEngine] и не пропадает, когда сервис останавливается на паузе.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.player_notification_channel)
                .build()
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, PlaybackEngine.player(this)).setSessionActivity(openApp).build()
        // Equalizer failures must never prevent music from playing; the runtime reports them to the UI instead.
        runCatching { EqualizerRuntime.start(this) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        runCatching { EqualizerRuntime.stop() }
        session?.release()
        session = null
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "playback"
    }
}
