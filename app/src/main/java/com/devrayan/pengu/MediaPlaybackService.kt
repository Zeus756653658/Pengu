package com.devrayan.pengu

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmapOrNull
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.devrayan.pengu.util.NotificationUtil
import com.devrayan.pengu.util.PenguAudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground MediaPlaybackService exposing a true Android MediaSession + Notification.MediaStyle
 * so Lockscreen controls, Control Center cards, Bluetooth AVRCP, OEM Dynamic Islands (HyperOS /
 * OxygenOS / OneUI), and Android 16 Live Updates show artwork, scrubbing seekbar, timestamps, and
 * transport actions cleanly.
 */
class MediaPlaybackService : Service() {

    private var mediaSession: MediaSession? = null
    private var noisyRegistered = false
    private var lastTrackKey: String = ""
    private var cachedArtBitmap: Bitmap? = null
    private var artLoadJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val noisyReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    PenguAudioManager.pause()
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationUtil.createNotificationChannel()
        }
        val session =
            MediaSession(this, "PenguMusicSession").apply {
                setCallback(
                    object : MediaSession.Callback() {
                        override fun onPlay() {
                            PenguAudioManager.play()
                        }

                        override fun onPause() {
                            PenguAudioManager.pause()
                        }

                        override fun onSkipToNext() {
                            PenguAudioManager.skipToNext()
                        }

                        override fun onSkipToPrevious() {
                            PenguAudioManager.skipToPrevious()
                        }

                        override fun onSeekTo(pos: Long) {
                            PenguAudioManager.seekTo(pos.toInt())
                        }

                        override fun onStop() {
                            PenguAudioManager.stopAndClear()
                        }
                    }
                )
                isActive = true
            }
        mediaSession = session

        if (!noisyRegistered) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(noisyReceiver, filter)
            }
            noisyRegistered = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopServiceCleanly()
            return START_NOT_STICKY
        }

        val st = PenguAudioManager.state.value
        val info = st.mediaInfo
        if (info == null || !st.isAudioOnly) {
            stopServiceCleanly()
            return START_NOT_STICKY
        }

        requestFocusIfNeeded(st.isPlaying)

        val trackKey = "${info.id}|${info.videoPath}|${st.embeddedArtBytes?.size ?: 0}"
        if (trackKey != lastTrackKey || cachedArtBitmap == null) {
            lastTrackKey = trackKey
            val embedded =
                st.embeddedArtBytes?.let { bytes ->
                    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
                }
            cachedArtBitmap = embedded ?: createFallbackArtworkBitmap(info.videoTitle)
            if (embedded == null && info.thumbnailUrl.isNotBlank()) {
                artLoadJob?.cancel()
                artLoadJob =
                    serviceScope.launch(Dispatchers.IO) {
                        val req =
                            ImageRequest.Builder(applicationContext)
                                .data(info.thumbnailUrl)
                                .size(512)
                                .allowHardware(false)
                                .build()
                        val bmp =
                            (applicationContext.imageLoader.execute(req) as? SuccessResult)
                                ?.drawable
                                ?.toBitmapOrNull()
                        if (bmp != null && lastTrackKey == trackKey) {
                            cachedArtBitmap = bmp
                            updateSessionAndForeground(PenguAudioManager.state.value)
                        }
                    }
            }
        }

        updateSessionAndForeground(st)
        return START_STICKY
    }

    private fun updateSessionAndForeground(st: PenguAudioManager.PlaybackState) {
        val session = mediaSession ?: return
        val info = st.mediaInfo ?: return
        val artBmp = cachedArtBitmap ?: createFallbackArtworkBitmap(info.videoTitle)

        val artistText =
            when {
                st.sleepRemainingMs > 0L -> {
                    val mins = (st.sleepRemainingMs / 60_000L) + 1
                    "${st.artistTag.ifBlank { info.extractor }} • Sleep in ${mins}m"
                }
                st.artistTag.isNotBlank() -> st.artistTag
                else -> info.extractor.ifBlank { "Pengu Music" }
            }

        val metadata =
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, info.videoTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artistText)
                .putString(
                    MediaMetadata.METADATA_KEY_ALBUM,
                    st.albumTag.ifBlank { "Pengu Library" },
                )
                .putLong(
                    MediaMetadata.METADATA_KEY_DURATION,
                    st.durationMs.toLong().coerceAtLeast(1L),
                )
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artBmp)
                .putBitmap(MediaMetadata.METADATA_KEY_ART, artBmp)
                .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, artBmp)
                .build()
        session.setMetadata(metadata)

        val actions =
            PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_STOP

        val stateCode =
            if (st.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val pbState =
            PlaybackState.Builder()
                .setActions(actions)
                .setState(
                    stateCode,
                    st.positionMs.toLong().coerceAtLeast(0L),
                    if (st.isPlaying) st.playbackSpeed else 0f,
                    SystemClock.elapsedRealtime(),
                )
                .build()
        session.setPlaybackState(pbState)
        session.isActive = true

        val notification = buildMediaStyleNotification(session, info.videoTitle, artistText, st.isPlaying, artBmp)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                startForeground(
                    NotificationUtil.MEDIA_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            }.onFailure {
                startForeground(NotificationUtil.MEDIA_NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NotificationUtil.MEDIA_NOTIFICATION_ID, notification)
        }
    }

    @Suppress("DEPRECATION")
    private fun buildMediaStyleNotification(
        session: MediaSession,
        title: String,
        subtitle: String,
        isPlaying: Boolean,
        artBitmap: Bitmap,
    ): Notification {
        val openIntent =
            PendingIntent.getActivity(
                this,
                NotificationUtil.MEDIA_NOTIFICATION_ID,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val prevAction =
            Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.outline_skip_previous_24),
                "Previous",
                broadcastAction(NotificationUtil.MEDIA_NOTIFICATION_ID + 3, NotificationActionReceiver.ACTION_MEDIA_PREV),
            ).build()

        val playPauseAction =
            Notification.Action.Builder(
                Icon.createWithResource(
                    this,
                    if (isPlaying) R.drawable.outline_pause_24 else R.drawable.outline_play_arrow_24,
                ),
                if (isPlaying) "Pause" else "Play",
                broadcastAction(NotificationUtil.MEDIA_NOTIFICATION_ID + 1, NotificationActionReceiver.ACTION_MEDIA_TOGGLE),
            ).build()

        val nextAction =
            Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.outline_skip_next_24),
                "Next",
                broadcastAction(NotificationUtil.MEDIA_NOTIFICATION_ID + 4, NotificationActionReceiver.ACTION_MEDIA_NEXT),
            ).build()

        val stopAction =
            Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.outline_cancel_24),
                getString(R.string.cancel),
                broadcastAction(NotificationUtil.MEDIA_NOTIFICATION_ID + 2, NotificationActionReceiver.ACTION_MEDIA_STOP),
            ).build()

        val mediaStyle =
            Notification.MediaStyle()
                .setMediaSession(session.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)

        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, NotificationUtil.MEDIA_CHANNEL_ID)
            } else {
                Notification.Builder(this)
            }

        builder
            .setSmallIcon(R.drawable.ic_stat_pengu)
            .setLargeIcon(artBitmap)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSubText("Pengu Music")
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setColor(0xFF7C3AED.toInt())
            .setColorized(true)
            .setContentIntent(openIntent)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .addAction(stopAction)
            .setStyle(mediaStyle)

        NotificationUtil.applyPromotedLiveUpdateCompat(builder, "Playing")
        return builder.build()
    }

    private fun broadcastAction(requestCode: Int, action: Int): PendingIntent =
        PendingIntent.getBroadcast(
            this,
            requestCode,
            Intent(this, NotificationActionReceiver::class.java)
                .putExtra(NotificationActionReceiver.ACTION_KEY, action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun requestFocusIfNeeded(isPlaying: Boolean) {
        if (!isPlaying) return
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req =
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .build()
            am.requestAudioFocus(req)
        }
    }

    private fun createFallbackArtworkBitmap(seedTitle: String): Bitmap {
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val palettePairs =
            listOf(
                0xFF6366F1.toInt() to 0xFFD946EF.toInt(),
                0xFFF43F5E.toInt() to 0xFF7C3AED.toInt(),
                0xFF0EA5E9.toInt() to 0xFF6366F1.toInt(),
                0xFF10B981.toInt() to 0xFF0F766E.toInt(),
            )
        val pair = palettePairs[kotlin.math.abs(seedTitle.hashCode()) % palettePairs.size]
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    LinearGradient(
                        0f,
                        0f,
                        size.toFloat(),
                        size.toFloat(),
                        pair.first,
                        pair.second,
                        Shader.TileMode.CLAMP,
                    )
            }
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 48f, 48f, paint)

        val ringPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.argb(48, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = 22f
            }
        canvas.drawCircle(size / 2f, size / 2f, 150f, ringPaint)

        val corePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.WHITE
                textSize = 180f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
        val initial = seedTitle.trim().firstOrNull()?.uppercase() ?: "♫"
        canvas.drawText(initial, size / 2f, size / 2f + 62f, corePaint)
        return bmp
    }

    private fun stopServiceCleanly() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
        NotificationUtil.cancelMediaNotification()
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        artLoadJob?.cancel()
        serviceScope.cancel()
        if (noisyRegistered) {
            runCatching { unregisterReceiver(noisyReceiver) }
            noisyRegistered = false
        }
        runCatching {
            mediaSession?.isActive = false
            mediaSession?.release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        private const val ACTION_SYNC = "com.devrayan.pengu.action.MEDIA_SYNC"
        private const val ACTION_STOP_SERVICE = "com.devrayan.pengu.action.MEDIA_STOP"

        fun syncPlayback(context: Context, st: PenguAudioManager.PlaybackState) {
            if (st.mediaInfo == null || !st.isAudioOnly) {
                stop(context)
                return
            }
            val intent = Intent(context, MediaPlaybackService::class.java).setAction(ACTION_SYNC)
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }.onFailure {
                runCatching { context.startService(intent) }
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, MediaPlaybackService::class.java))
            }
            NotificationUtil.cancelMediaNotification()
        }
    }
}
