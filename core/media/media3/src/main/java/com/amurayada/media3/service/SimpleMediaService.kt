package com.amurayada.media3.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.content.getSystemService
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import androidx.media3.ui.DefaultMediaDescriptionAdapter
import androidx.media3.ui.PlayerNotificationManager
import androidx.media3.session.MediaNotification
import androidx.media3.session.CommandButton
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.MoreExecutors
import com.amurayada.common.MEDIA_NOTIFICATION
import com.amurayada.domain.manager.DataStoreManager
import com.amurayada.domain.mediaservice.handler.MediaPlayerHandler
import com.amurayada.domain.mediaservice.player.MediaPlayerInterface
import com.amurayada.logger.Logger
import com.amurayada.media3.exoplayer.CrossfadeExoPlayerAdapter
import com.amurayada.media3.R
import com.amurayada.media3.extension.toCommandButton
import com.amurayada.media3.utils.CoilBitmapLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

@UnstableApi
internal class SimpleMediaService :
    MediaLibraryService(),
    KoinComponent {
    private val coroutineScope by inject<CoroutineScope>(named(com.amurayada.common.Config.SERVICE_SCOPE))
    private val mediaPlayerAdapter: MediaPlayerInterface by inject<MediaPlayerInterface>()
    private val player: Player by lazy {
        (mediaPlayerAdapter as CrossfadeExoPlayerAdapter).forwardingPlayer
    }
    private val coilBitmapLoader: CoilBitmapLoader by inject<CoilBitmapLoader>()

    private val simpleMediaSessionCallback: MediaLibrarySession.Callback by inject<MediaLibrarySession.Callback>()
    private val simpleMediaServiceHandler: MediaPlayerHandler by inject<MediaPlayerHandler>()
    private val dataStoreManager: DataStoreManager by inject<DataStoreManager>()

    private val binder = MusicBinder()

    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var mediaSession: MediaLibrarySession? = null


    inner class MusicBinder : Binder() {
        val service: SimpleMediaService
            get() = this@SimpleMediaService

        fun setActivitySession(
            context: Context,
            activity: Class<out Activity>,
        ) {
            mediaSession?.setSessionActivity(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, activity),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        Logger.w("Service", "Simple Media Service Bound")
        return super.onBind(intent) ?: binder
    }

    override fun startForegroundService(service: Intent): ComponentName? {
        return try {
            // Only attempt to start as foreground if specifically requested and permitted
            super.startForegroundService(service)
        } catch (e: Exception) {
            // On Android 12+, this catch is crucial to prevent crash from background
            Logger.e("Service", "startForegroundService call failed: ${e.message}")
            // Return null, falling back to background service state if possible
            null
        }
    }

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        Logger.w("Service", "Simple Media Service Created")

        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Harmoni:PlaybackWakeLock")
        wakeLock?.setReferenceCounted(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                MEDIA_NOTIFICATION.NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(null, null)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                this,
                { MEDIA_NOTIFICATION.NOTIFICATION_ID },
                MEDIA_NOTIFICATION.NOTIFICATION_CHANNEL_ID,
                R.string.notification_channel_name,
            ).apply {
                setSmallIcon(R.drawable.ic_notifi)
            },
        )

        if (mediaSession == null) {
            mediaSession =
                provideMediaLibrarySession(
                    this,
                    player,
                    simpleMediaSessionCallback,
                )
        }

        simpleMediaServiceHandler.onUpdateNotification = { list ->
            val commandButtonList = list.map { it.toCommandButton(this) }
            mediaSession?.setMediaButtonPreferences(
                commandButtonList,
            )
        }

        val sessionToken = SessionToken(this, ComponentName(this, SimpleMediaService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({ controllerFuture.get() }, MoreExecutors.directExecutor())

        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                    if (player.isPlaying) {
                        if (wakeLock?.isHeld == false) {
                            wakeLock?.acquire()
                            Logger.d("Service", "WakeLock acquired")
                        }
                    } else {
                        if (wakeLock?.isHeld == true) {
                            wakeLock?.release()
                            Logger.d("Service", "WakeLock released")
                        }
                    }
                }
            }
        })

        // The service now relies on MediaLibraryService's default notification provider.
    }

    @UnstableApi
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        Logger.w("Service", "Simple Media Service Received Action: ${intent?.action}")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession



    @UnstableApi
    fun release() {
        Logger.w("Service", "Starting release process")
        runBlocking {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
                // Release MediaSession (don't release player - CrossfadeExoPlayerAdapter manages it)
                mediaSession?.run {
                    this.player.pause()
                    this.player.playWhenReady = false
                    // Don't call this.player.release() - CrossfadeExoPlayerAdapter manages player lifecycle
                    this.release()
                }
                // Release handler (contains coroutines and jobs, which also releases the adapter)
                simpleMediaServiceHandler.release()
                mediaSession = null
                Logger.w("Service", "Simple Media Service Released")
            } catch (e: Exception) {
                Logger.e("Service", "Error during release")
            }
        }
    }

    @UnstableApi
    override fun onDestroy() {
        super.onDestroy()
        Logger.w("Service", "Simple Media Service Destroyed")
        release()
    }

    override fun onTrimMemory(level: Int) {
        Logger.w("Service", "Simple Media Service Trim Memory Level: $level")
        simpleMediaServiceHandler.mayBeSaveRecentSong()
    }

    @UnstableApi
    override fun onTaskRemoved(rootIntent: Intent?) {
        Logger.w("Service", "Simple Media Service Task Removed")
        if (simpleMediaServiceHandler.shouldReleaseOnTaskRemoved()) {
            release()
            super.onTaskRemoved(rootIntent)
            exitProcess(0)
        }
    }

    // Can't inject by Koin because it depend on service
    @UnstableApi
    private fun provideMediaLibrarySession(
        service: MediaLibraryService,
        player: Player,
        callback: MediaLibrarySession.Callback,
    ): MediaLibrarySession =
        MediaLibrarySession
            .Builder(
                service,
                player,
                callback,
            ).setId(this.javaClass.name)
            .setBitmapLoader(coilBitmapLoader)
            .build()

    override fun onUpdateNotification(session: MediaSession, startInForeground: Boolean) {
        // Media3 logic: only promote to foreground if it's actually playing or preparing to play.
        // Also check if we are already in foreground to avoid redundant/illegal calls.
        val isPlayingOrPreparing = player.isPlaying || player.playWhenReady || player.playbackState == Player.STATE_BUFFERING
        
        // If we are supposed to start in foreground, ensure we have a valid reason (playing)
        // to minimize ForegroundServiceStartNotAllowedException risks.
        val shouldStartInForeground = startInForeground && isPlayingOrPreparing
        
        try {
            super.onUpdateNotification(session, shouldStartInForeground)
        } catch (e: Exception) {
            Logger.e("Service", "onUpdateNotification failed: ${e.message}")
            // Fallback: try updating without promoting to foreground if it failed
            if (shouldStartInForeground) {
                try {
                    super.onUpdateNotification(session, false)
                } catch (e2: Exception) {
                    Logger.e("Service", "onUpdateNotification fallback also failed: ${e2.message}")
                }
            }
        }
    }
}