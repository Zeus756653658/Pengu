package com.devrayan.pengu

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmapOrNull
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.devrayan.pengu.util.NotificationUtil
import com.devrayan.pengu.util.PENGU_ISLAND_OFFSET_DP
import com.devrayan.pengu.util.PENGU_OVERLAY_ISLAND_ENABLED
import com.devrayan.pengu.util.PenguAudioManager
import com.devrayan.pengu.util.PreferenceUtil.getBoolean
import com.devrayan.pengu.util.PreferenceUtil.getInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Optional System-Wide Dynamic Island Floating Overlay Service (`SYSTEM_ALERT_WINDOW`).
 * Renders the punch-hole black capsule -> expanded media card over all apps when user enables
 * "System-Wide Floating Island" in Settings -> Android Dynamic Island.
 */
class PenguIslandOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var rootContainer: FrameLayout? = null
    private var collapsedPill: LinearLayout? = null
    private var expandedCard: LinearLayout? = null
    private var isExpanded = false

    private var collapsedArt: ImageView? = null
    private var eqBadge: TextView? = null
    private var expandedArt: ImageView? = null
    private var expandedTitle: TextView? = null
    private var expandedArtist: TextView? = null
    private var expandedSeekBar: SeekBar? = null
    private var expandedPlayPause: ImageView? = null

    private var lastArtKey = ""
    private var loadedArtBitmap: Bitmap? = null
    private var artJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationUtil.createNotificationChannel()
        }
        startForeground(NotificationUtil.MEDIA_NOTIFICATION_ID + 10, buildOverlayForegroundNotification())
        attachOverlayWindow()
        serviceScope.launch {
            PenguAudioManager.state.collectLatest { st ->
                val info = st.mediaInfo
                if (info == null || !st.isAudioOnly || !canShowOverlay(this@PenguIslandOverlayService)) {
                    stopSelf()
                    return@collectLatest
                }
                bindState(st)
            }
        }
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun attachOverlayWindow() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val root = FrameLayout(this)
        val offsetPx = dp(PENGU_ISLAND_OFFSET_DP.getInt() + 4)

        val typeFlag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                typeFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = offsetPx
            }

        // 1. Collapsed Capsule Pill (Image 3 style)
        val pillBg =
            GradientDrawable().apply {
                setColor(0xFF07070C.toInt())
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), 0x30FFFFFF)
            }
        val pill =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = pillBg
                setPadding(dp(8), dp(5), dp(10), dp(5))
                layoutParams = FrameLayout.LayoutParams(dp(176), dp(34))
                setOnClickListener {
                    isExpanded = true
                    toggleViews()
                }
            }
        val cArt =
            ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFF7C3AED.toInt())
                }
                clipToOutline = true
            }
        val cSpacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }
        val cEq =
            TextView(this).apply {
                text = "ı||ı"
                setTextColor(Color.WHITE)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
            }
        pill.addView(cArt)
        pill.addView(cSpacer)
        pill.addView(cEq)

        // 2. Expanded Apple Card (Images 1 & 2 style)
        val cardBg =
            GradientDrawable().apply {
                setColor(0xF0151220.toInt())
                cornerRadius = dp(26).toFloat()
                setStroke(dp(1), 0x26FFFFFF)
            }
        val card =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = cardBg
                setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams = FrameLayout.LayoutParams(dp(336), FrameLayout.LayoutParams.WRAP_CONTENT)
                visibility = View.GONE
            }

        val headerRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        val eArt =
            ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(0xFF7C3AED.toInt())
                }
                clipToOutline = true
                setOnClickListener {
                    startActivity(
                        Intent(this@PenguIslandOverlayService, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                    isExpanded = false
                    toggleViews()
                }
            }
        val titleCol =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(8), 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        val eTitle =
            TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
        val eArtist =
            TextView(this).apply {
                setTextColor(0xB3FFFFFF.toInt())
                textSize = 12f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
        titleCol.addView(eTitle)
        titleCol.addView(eArtist)

        val collapseBtn =
            ImageView(this).apply {
                setImageResource(R.drawable.outline_cancel_24)
                setColorFilter(Color.WHITE)
                setPadding(dp(6), dp(6), dp(6), dp(6))
                setOnClickListener {
                    isExpanded = false
                    toggleViews()
                }
            }
        headerRow.addView(eArt)
        headerRow.addView(titleCol)
        headerRow.addView(collapseBtn)

        val seekBar =
            SeekBar(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(10)
                        bottomMargin = dp(8)
                    }
                progressTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x38FFFFFF)
                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                            if (fromUser) PenguAudioManager.seekTo(progress)
                        }
                        override fun onStartTrackingTouch(sb: SeekBar?) {}
                        override fun onStopTrackingTouch(sb: SeekBar?) {}
                    }
                )
            }

        val ctrlRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
        val prevBtn =
            ImageView(this).apply {
                setImageResource(R.drawable.outline_skip_previous_24)
                setColorFilter(Color.WHITE)
                setPadding(dp(16), dp(8), dp(16), dp(8))
                setOnClickListener { PenguAudioManager.skipToPrevious() }
            }
        val playBtn =
            ImageView(this).apply {
                setImageResource(R.drawable.outline_pause_24)
                setColorFilter(Color.WHITE)
                background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(0x33FFFFFF)
                    }
                setPadding(dp(14), dp(14), dp(14), dp(14))
                setOnClickListener { PenguAudioManager.togglePlayPause() }
            }
        val nextBtn =
            ImageView(this).apply {
                setImageResource(R.drawable.outline_skip_next_24)
                setColorFilter(Color.WHITE)
                setPadding(dp(16), dp(8), dp(16), dp(8))
                setOnClickListener { PenguAudioManager.skipToNext() }
            }
        ctrlRow.addView(prevBtn)
        ctrlRow.addView(playBtn)
        ctrlRow.addView(nextBtn)

        card.addView(headerRow)
        card.addView(seekBar)
        card.addView(ctrlRow)

        root.addView(pill)
        root.addView(card)

        this.rootContainer = root
        this.collapsedPill = pill
        this.expandedCard = card
        this.collapsedArt = cArt
        this.eqBadge = cEq
        this.expandedArt = eArt
        this.expandedTitle = eTitle
        this.expandedArtist = eArtist
        this.expandedSeekBar = seekBar
        this.expandedPlayPause = playBtn

        runCatching { wm.addView(root, params) }
    }

    private fun toggleViews() {
        collapsedPill?.visibility = if (isExpanded) View.GONE else View.VISIBLE
        expandedCard?.visibility = if (isExpanded) View.VISIBLE else View.GONE
    }

    private fun bindState(st: PenguAudioManager.PlaybackState) {
        val info = st.mediaInfo ?: return
        expandedTitle?.text = info.videoTitle
        expandedArtist?.text = st.artistTag.ifBlank { info.extractor.ifBlank { "Pengu Music" } }
        eqBadge?.alpha = if (st.isPlaying) 1.0f else 0.45f
        expandedPlayPause?.setImageResource(
            if (st.isPlaying) R.drawable.outline_pause_24 else R.drawable.outline_play_arrow_24
        )
        expandedSeekBar?.apply {
            max = st.durationMs.coerceAtLeast(1)
            progress = st.positionMs.coerceIn(0, max)
        }

        val key = "${info.id}|${info.videoPath}|${st.embeddedArtBytes?.size ?: 0}"
        if (key != lastArtKey) {
            lastArtKey = key
            val embedded =
                st.embeddedArtBytes?.let { b ->
                    runCatching { BitmapFactory.decodeByteArray(b, 0, b.size) }.getOrNull()
                }
            if (embedded != null) {
                loadedArtBitmap = embedded
                collapsedArt?.setImageBitmap(embedded)
                expandedArt?.setImageBitmap(embedded)
            } else {
                collapsedArt?.setImageResource(R.drawable.ic_stat_pengu)
                expandedArt?.setImageResource(R.drawable.ic_stat_pengu)
                if (info.thumbnailUrl.isNotBlank()) {
                    artJob?.cancel()
                    artJob =
                        serviceScope.launch(Dispatchers.IO) {
                            val req =
                                ImageRequest.Builder(applicationContext)
                                    .data(info.thumbnailUrl)
                                    .size(160)
                                    .allowHardware(false)
                                    .build()
                            val bmp =
                                (applicationContext.imageLoader.execute(req) as? SuccessResult)
                                    ?.drawable
                                    ?.toBitmapOrNull()
                            if (bmp != null && lastArtKey == key) {
                                loadedArtBitmap = bmp
                                collapsedArt?.post { collapsedArt?.setImageBitmap(bmp) }
                                expandedArt?.post { expandedArt?.setImageBitmap(bmp) }
                            }
                        }
                }
            }
        }
    }

    private fun buildOverlayForegroundNotification(): Notification {
        val openIntent =
            PendingIntent.getActivity(
                this,
                NotificationUtil.MEDIA_NOTIFICATION_ID + 11,
                Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat.Builder(this, NotificationUtil.MEDIA_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pengu)
            .setContentTitle("Pengu Dynamic Island Active")
            .setContentText("Tap to return to Pengu")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setContentIntent(openIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        artJob?.cancel()
        serviceScope.cancel()
        rootContainer?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        rootContainer = null
        super.onDestroy()
    }

    companion object {
        fun canShowOverlay(context: Context): Boolean =
            PENGU_OVERLAY_ISLAND_ENABLED.getBoolean() && Settings.canDrawOverlays(context)

        fun syncOverlay(context: Context) {
            val st = PenguAudioManager.state.value
            val active = st.mediaInfo != null && st.isAudioOnly && canShowOverlay(context)
            val intent = Intent(context, PenguIslandOverlayService::class.java)
            if (active) {
                runCatching { ContextCompat.startForegroundService(context, intent) }
            } else {
                runCatching { context.stopService(intent) }
            }
        }
    }
}
