package com.devrayan.pengu.ui.page.player

import android.content.Intent
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.Button
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.devrayan.pengu.R
import com.devrayan.pengu.database.objects.DownloadedVideoInfo
import com.devrayan.pengu.ui.common.HapticFeedback.slightHapticFeedback
import com.devrayan.pengu.ui.common.LocalDarkTheme
import com.devrayan.pengu.util.AUDIO_REGEX
import com.devrayan.pengu.util.DatabaseUtil
import com.devrayan.pengu.util.FfmpegToolsUtil
import com.devrayan.pengu.util.FileUtil
import com.devrayan.pengu.util.FileUtil.getFileSize
import com.devrayan.pengu.util.toFileSizeText
import java.io.File
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * VidSnap Screens 5 & 6 "Now Playing" in-app Video & Audio Player.
 */
@Composable
fun MediaPlayerPage(
    mediaId: Int,
    onNavigateBack: () -> Unit,
    onOpenTools: () -> Unit = {},
) {
    val isDark = LocalDarkTheme.current.isDarkTheme()
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var mediaInfo by remember { mutableStateOf<DownloadedVideoInfo?>(null) }
    var fileNotFound by remember { mutableStateOf(false) }

    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var durationMs by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableIntStateOf(0) }
    var videoRatio by remember { mutableFloatStateOf(16f / 9f) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val mediaPlayer = remember { MediaPlayer() }
    var surfaceReady by remember { mutableStateOf(false) }

    LaunchedEffect(mediaId) {
        val loaded = withContext(Dispatchers.IO) { runCatching { DatabaseUtil.getInfoById(mediaId) }.getOrNull() }
        mediaInfo = loaded
        if (loaded == null || !File(loaded.videoPath).exists()) {
            fileNotFound = true
        }
    }

    val isAudio = remember(mediaInfo) {
        val path = mediaInfo?.videoPath.orEmpty().lowercase()
        path.contains(Regex(AUDIO_REGEX))
    }

    // Prepare audio immediately or video once SurfaceView is ready
    LaunchedEffect(mediaInfo, isAudio, surfaceReady) {
        val info = mediaInfo ?: return@LaunchedEffect
        val file = File(info.videoPath)
        if (!file.exists()) return@LaunchedEffect
        if (!isAudio && !surfaceReady) return@LaunchedEffect

        runCatching {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.setOnPreparedListener { mp ->
                durationMs = mp.duration.coerceAtLeast(1)
                isPrepared = true
                mp.start()
                isPlaying = true
            }
            mediaPlayer.setOnVideoSizeChangedListener { _, width, height ->
                if (width > 0 && height > 0) {
                    videoRatio = (width.toFloat() / height.toFloat()).coerceIn(0.5f, 2.4f)
                }
            }
            mediaPlayer.setOnCompletionListener {
                isPlaying = false
                positionMs = durationMs
            }
            mediaPlayer.prepareAsync()
        }.onFailure {
            it.printStackTrace()
            Toast.makeText(context, "Cannot play media", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isPlaying, isPrepared) {
        while (isPlaying && isPrepared) {
            runCatching {
                if (mediaPlayer.isPlaying) {
                    positionMs = mediaPlayer.currentPosition
                }
            }
            delay(250)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                runCatching {
                    if (mediaPlayer.isPlaying) {
                        mediaPlayer.pause()
                        isPlaying = false
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching {
                mediaPlayer.stop()
                mediaPlayer.release()
            }
        }
    }

    val info = mediaInfo
    val fileSize = remember(info) { info?.videoPath?.getFileSize() ?: 0L }
    val ext = remember(info) { info?.videoPath?.substringAfterLast('.', "")?.uppercase().orEmpty() }

    Scaffold(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = {
                        view.slightHapticFeedback()
                        onNavigateBack()
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }

                Text(
                    text = "Now Playing",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                IconButton(
                    onClick = {
                        info?.let {
                            FileUtil.openFile(it.videoPath) {
                                Toast.makeText(context, R.string.file_unavailable, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open externally")
                }
            }

            // Status green pill (VidSnap Screens 5 & 6)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF10B981).copy(alpha = 0.14f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text =
                            if (isAudio) "$ext saved • Ready in Downloads"
                            else "Download complete • Saved to Downloads",
                        color = Color(0xFF10B981),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            if (fileNotFound) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                ) {
                    Text(
                        text = "Media file is unavailable or was moved from storage.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else if (!isAudio) {
                // Video Surface Player
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .aspectRatio(videoRatio)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                holder.addCallback(
                                    object : SurfaceHolder.Callback {
                                        override fun surfaceCreated(holder: SurfaceHolder) {
                                            mediaPlayer.setDisplay(holder)
                                            surfaceReady = true
                                        }

                                        override fun surfaceChanged(
                                            holder: SurfaceHolder,
                                            format: Int,
                                            width: Int,
                                            height: Int,
                                        ) {}

                                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                                            surfaceReady = false
                                            runCatching { mediaPlayer.setDisplay(null) }
                                        }
                                    }
                                )
                            }
                        },
                    )
                }
            } else {
                // VidSnap Screen 6 MP3 Gradient Artwork Card + Animated Waveform
                val artGradient =
                    Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFFD946EF)))
                Box(
                    modifier =
                        Modifier.size(196.dp)
                            .shadow(16.dp, RoundedCornerShape(32.dp), spotColor = Color(0xFF7C3AED))
                            .clip(RoundedCornerShape(32.dp))
                            .background(artGradient),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(86.dp),
                    )
                }

                Spacer(Modifier.height(22.dp))

                AnimatedAudioWaveform(isPlaying = isPlaying)
            }

            Spacer(Modifier.height(22.dp))

            // Title & Badges
            Text(
                text = info?.videoTitle ?: "Loading...",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (isAudio) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isAudio) Arrangement.Center else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF7C3AED).copy(alpha = 0.14f),
                ) {
                    Text(
                        text = ext.ifBlank { "MEDIA" },
                        color = Color(0xFF7C3AED),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text =
                        listOfNotNull(
                                info?.extractor?.takeIf { it.isNotBlank() && it != "Unknown" },
                                info?.videoAuthor?.takeIf { it.isNotBlank() && it != "Author" },
                                fileSize.toFileSizeText(),
                            )
                            .joinToString(" • "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Seekbar + time labels
            val safeDuration = durationMs.coerceAtLeast(1)
            Slider(
                value = positionMs.coerceIn(0, safeDuration).toFloat(),
                onValueChange = { newPos ->
                    positionMs = newPos.toInt()
                    if (isPrepared) {
                        runCatching { mediaPlayer.seekTo(positionMs) }
                    }
                },
                valueRange = 0f..safeDuration.toFloat(),
                colors =
                    SliderDefaults.colors(
                        thumbColor = Color(0xFF7C3AED),
                        activeTrackColor = Color(0xFF7C3AED),
                    ),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = FfmpegToolsUtil.formatClock((positionMs / 1000L).coerceAtLeast(0L)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = FfmpegToolsUtil.formatClock((durationMs / 1000L).coerceAtLeast(0L)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Playback controls: -10s, Play/Pause, +10s, Speed toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Speed toggle
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) Color(0xFF1E1E24) else Color(0xFFF3F4F6),
                    modifier =
                        Modifier.clickable {
                            view.slightHapticFeedback()
                            val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f)
                            val nextIdx = (speeds.indexOf(playbackSpeed) + 1) % speeds.size
                            playbackSpeed = speeds[nextIdx]
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isPrepared) {
                                runCatching {
                                    mediaPlayer.playbackParams =
                                        PlaybackParams().setSpeed(playbackSpeed)
                                }
                            }
                        },
                ) {
                    Text(
                        text = "${playbackSpeed}x",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7C3AED),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }

                IconButton(
                    onClick = {
                        view.slightHapticFeedback()
                        if (isPrepared) {
                            val target = (mediaPlayer.currentPosition - 10_000).coerceAtLeast(0)
                            mediaPlayer.seekTo(target)
                            positionMs = target
                        }
                    }
                ) {
                    Icon(Icons.Filled.Replay10, contentDescription = "Rewind 10s", modifier = Modifier.size(30.dp))
                }

                val btnGradient =
                    if (isAudio) Brush.linearGradient(listOf(Color(0xFFF43F5E), Color(0xFFD946EF)))
                    else Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF7C3AED)))

                Box(
                    modifier =
                        Modifier.size(64.dp)
                            .shadow(10.dp, CircleShape, spotColor = Color(0xFF7C3AED))
                            .clip(CircleShape)
                            .background(btnGradient)
                            .clickable {
                                view.slightHapticFeedback()
                                if (!isPrepared) return@clickable
                                runCatching {
                                    if (mediaPlayer.isPlaying) {
                                        mediaPlayer.pause()
                                        isPlaying = false
                                    } else {
                                        mediaPlayer.start()
                                        isPlaying = true
                                    }
                                }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }

                IconButton(
                    onClick = {
                        view.slightHapticFeedback()
                        if (isPrepared) {
                            val target = (mediaPlayer.currentPosition + 10_000).coerceAtMost(safeDuration)
                            mediaPlayer.seekTo(target)
                            positionMs = target
                        }
                    }
                ) {
                    Icon(Icons.Filled.Forward10, contentDescription = "Forward 10s", modifier = Modifier.size(30.dp))
                }

                // Spacer balance for speed pill
                Spacer(Modifier.width(42.dp))
            }

            Spacer(Modifier.height(24.dp))

            // Bottom quick action bar: Share, Tools, Delete
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isDark) Color(0xFF1C1C22) else Color(0xFFF3F4F6))
                        .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                PlayerActionTile(icon = Icons.Filled.Share, label = "Share") {
                    info?.let {
                        FileUtil.createIntentForSharingFile(it.videoPath)?.let { intent ->
                            context.startActivity(
                                Intent.createChooser(intent, context.getString(R.string.share))
                            )
                        }
                    }
                }
                PlayerActionTile(icon = Icons.Filled.ContentCut, label = "Trim / MP3") {
                    runCatching {
                        if (mediaPlayer.isPlaying) {
                            mediaPlayer.pause()
                            isPlaying = false
                        }
                    }
                    onOpenTools()
                }
                PlayerActionTile(
                    icon = Icons.Filled.Delete,
                    label = "Delete",
                    tint = Color(0xFFEF4444),
                ) {
                    showDeleteDialog = true
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    if (showDeleteDialog && info != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete media?") },
            text = { Text("Remove \"${info.videoTitle}\" from downloads and delete file from storage?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch(Dispatchers.IO) {
                            DatabaseUtil.deleteInfoList(listOf(info), deleteFile = true)
                            withContext(Dispatchers.Main) { onNavigateBack() }
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AnimatedAudioWaveform(isPlaying: Boolean) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * Math.PI).toFloat(),
            animationSpec =
                infiniteRepeatable(
                    animation = tween(1400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "phase",
        )

    Row(
        modifier = Modifier.fillMaxWidth().height(42.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val barCount = 24
        for (i in 0 until barCount) {
            val baseRatio = 0.28f + 0.55f * abs(sin((i * 0.55f) + if (isPlaying) phase else 0f))
            val barColor = if (i % 2 == 0) Color(0xFFF43F5E) else Color(0xFF7C3AED)
            Box(
                modifier =
                    Modifier.width(4.dp)
                        .height((36 * baseRatio).dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(barColor.copy(alpha = if (isPlaying) 0.9f else 0.4f))
            )
        }
    }
}

@Composable
private fun PlayerActionTile(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = tint)
    }
}
