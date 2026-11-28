package com.devrayan.pengu.util

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import com.devrayan.pengu.R
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinates Android Picture-in-Picture (PiP) mode across MainActivity and MediaPlayerPage.
 */
object PenguPipState {

    const val ACTION_PIP_TOGGLE_PLAY = "com.devrayan.pengu.ACTION_PIP_TOGGLE_PLAY"

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode = _isInPipMode.asStateFlow()

    @Volatile var isVideoScreenVisible: Boolean = false
        private set

    @Volatile var isVideoPlayingForPip: Boolean = false
        private set

    @Volatile var aspectRatio: Float = 16f / 9f
        private set

    fun setInPipMode(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    fun updateVideoSession(visible: Boolean, playing: Boolean, ratio: Float) {
        isVideoScreenVisible = visible
        isVideoPlayingForPip = playing
        aspectRatio = ratio.coerceIn(0.45f, 2.35f)
    }

    fun shouldAutoEnterPip(): Boolean = isVideoScreenVisible && isVideoPlayingForPip

    fun isPipSupported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun enterPipMode(activity: Activity, ratio: Float = aspectRatio): Boolean {
        if (!isPipSupported(activity) || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return runCatching {
            val params = buildPipParams(activity, ratio, isAutoEnter = false)
            activity.enterPictureInPictureMode(params)
        }.getOrDefault(false)
    }

    fun syncAutoEnterParams(activity: Activity, enableAutoEnter: Boolean, ratio: Float = aspectRatio) {
        if (!isPipSupported(activity) || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val params = buildPipParams(activity, ratio, isAutoEnter = enableAutoEnter)
            activity.setPictureInPictureParams(params)
        }
    }

    private fun buildPipParams(
        context: Context,
        ratio: Float,
        isAutoEnter: Boolean,
    ): PictureInPictureParams {
        val clampedRatio = ratio.coerceIn(0.45f, 2.35f)
        val widthUnits = (clampedRatio * 100f).roundToInt().coerceIn(45, 235)
        val toggleIntent =
            PendingIntent.getBroadcast(
                context,
                9101,
                Intent(ACTION_PIP_TOGGLE_PLAY).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val isPlayingNow = PenguAudioManager.state.value.isPlaying
        val iconRes = if (isPlayingNow) R.drawable.outline_pause_24 else R.drawable.outline_play_arrow_24
        val label = if (isPlayingNow) "Pause" else "Play"
        val action =
            RemoteAction(
                Icon.createWithResource(context, iconRes),
                label,
                label,
                toggleIntent,
            )

        val builder =
            PictureInPictureParams.Builder()
                .setAspectRatio(Rational(widthUnits, 100))
                .setActions(listOf(action))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(isAutoEnter)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }
}
