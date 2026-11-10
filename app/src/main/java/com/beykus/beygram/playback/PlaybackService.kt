package com.beykus.beygram.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.beykus.beygram.MainActivity
import com.beykus.beygram.R
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Держит общий плеер на переднем плане, пока он играет: уведомление с управлением, экран блокировки и гарнитура.
 * Сам плеер принадлежит [PlaybackEngine] и не пропадает, когда сервис останавливается на паузе.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val shuffleCommand = SessionCommand(COMMAND_SHUFFLE, Bundle.EMPTY)
    private val closeCommand = SessionCommand(COMMAND_CLOSE, Bundle.EMPTY)

    /** Shuffle and close next to the transport buttons: the two actions people reach for from the lock screen. */
    private fun buttons(shuffleOn: Boolean): ImmutableList<CommandButton> = ImmutableList.of(
        CommandButton.Builder(if (shuffleOn) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
            .setDisplayName(getString(R.string.player_notification_shuffle))
            .setSessionCommand(shuffleCommand)
            .build(),
        CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setCustomIconResId(R.drawable.ic_notification_close)
            .setDisplayName(getString(R.string.player_notification_close))
            .setSessionCommand(closeCommand)
            .build()
    )

    private val callback = object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(shuffleCommand)
                .add(closeCommand)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                COMMAND_SHUFFLE -> PlaybackEngine.setShuffle(!PlaybackEngine.state.value.shuffleEnabled)
                COMMAND_CLOSE -> PlaybackEngine.close()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

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
            // The marker makes a tap on the notification open the full player, not just the app.
            Intent(this, MainActivity::class.java).putExtra(EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val created = MediaSession.Builder(this, PlaybackEngine.player(this))
            .setSessionActivity(openApp)
            .setCallback(callback)
            .setMediaButtonPreferences(buttons(PlaybackEngine.state.value.shuffleEnabled))
            .build()
        session = created
        scope.launch {
            PlaybackEngine.state.map { it.shuffleEnabled }.distinctUntilChanged().collect { shuffleOn ->
                created.setMediaButtonPreferences(buttons(shuffleOn))
            }
        }
        // Equalizer failures must never prevent music from playing; the runtime reports them to the UI instead.
        runCatching { EqualizerRuntime.start(this) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        scope.cancel()
        runCatching { EqualizerRuntime.stop() }
        session?.release()
        session = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "playback"

        /** Set on the notification's tap intent; [MainActivity] then opens the full player. */
        const val EXTRA_OPEN_PLAYER = "beygram.open_player"

        private const val COMMAND_SHUFFLE = "beygram.shuffle"
        private const val COMMAND_CLOSE = "beygram.close"
    }
}
