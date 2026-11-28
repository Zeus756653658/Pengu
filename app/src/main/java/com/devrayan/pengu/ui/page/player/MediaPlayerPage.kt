package com.devrayan.pengu.ui.page.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devrayan.pengu.R
import com.devrayan.pengu.ui.common.HapticFeedback.slightHapticFeedback
import com.devrayan.pengu.ui.common.LocalDarkTheme
import com.devrayan.pengu.util.DatabaseUtil
import com.devrayan.pengu.util.FileUtil
import com.devrayan.pengu.util.FileUtil.getFileSize
import com.devrayan.pengu.util.PenguAudioManager
import com.devrayan.pengu.util.PenguPipState
import com.devrayan.pengu.util.RingtoneToolsUtil
import com.devrayan.pengu.util.toFileSizeText
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/**
 * Pengu "Now Playing" Video & Background Audio Player with Picture-in-Picture (PiP),
 * Sleep Timer, A-B Loop, and Ringtone Maker shortcut backed by PenguAudioManager.
 */
@Composable
fun MediaPlayerPage(
    mediaId: Int,
    onNavigateBack: () -> Unit,
    onOpenTools: (String) -> Unit = {},
) {
    val isDark = LocalDarkTheme.current.isDarkTheme()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val playback by PenguAudioManager.state.collectAsStateWithLifecycle()
    val isInPipMode by PenguPipState.isInPipMode.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSleepMenu by remember { mutableStateOf(false) }
    var surfaceReady by remember { mutableStateOf(false) }

    LaunchedEffect(mediaId, surfaceReady) {
        val loaded =
            withContext(Dispatchers.IO) {
                runCatching { DatabaseUtil.getInfoById(mediaId) }.getOrNull()
            }
        if (loaded != null) {
            PenguAudioManager.loadAndPlay(loaded)
        }
    }

    LaunchedEffect(playback.errorMessage) {
        playback.errorMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(playback.isAudioOnly, playback.isPlaying, playback.videoRatio, playback.fileNotFound) {
        val videoActive = !playback.isAudioOnly && !playback.fileNotFound && playback.mediaInfo != null
        PenguPipState.updateVideoSession(
            visible = videoActive,
            playing = playback.isPlaying,
            ratio = playback.videoRatio,
        )
        activity?.let { act ->
            PenguPipState.syncAutoEnterParams(
                activity = act,
                enableAutoEnter = videoActive && playback.isPlaying,
                ratio = playback.videoRatio,
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !PenguPipState.isInPipMode.value && !playback.isAudioOnly) {
                PenguAudioManager.pauseIfVideoLeaving()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            PenguPipState.updateVideoSession(visible = false, playing = false, ratio = 16f / 9f)
            activity?.let { PenguPipState.syncAutoEnterParams(it, enableAutoEnter = false) }
            PenguAudioManager.pauseIfVideoLeaving()
        }
    }

    val info = playback.mediaInfo
    val isAudio = playback.isAudioOnly
    val fileSize = remember(info?.videoPath) { info?.videoPath?.getFileSize() ?: 0L }
    val ext = remember(info?.videoPath) { info?.videoPath?.substringAfterLast('.', "")?.uppercase().orEmpty() }

    if (isInPipMode && !isAudio && !playback.fileNotFound && info != null) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().aspectRatio(playback.videoRatio),
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        holder.addCallback(
                            object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    PenguAudioManager.attachVideoSurface(holder)
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
                                    PenguAudioManager.detachVideoSurface(holder)
                                }
                            }
                        )
                    }
                },
            )
        }
    } else if (isAudio && !playback.fileNotFound && info != null) {
        PenguMusicPlayerView(
            playback = playback,
            onNavigateBack = onNavigateBack,
            onOpenTools = onOpenTools,
            onRequestDelete = { showDeleteDialog = true },
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
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

                Row {
                    if (!isAudio && !playback.fileNotFound && PenguPipState.isPipSupported(context)) {
                        IconButton(
                            onClick = {
                                view.slightHapticFeedback()
                                activity?.let { PenguPipState.enterPipMode(it, playback.videoRatio) }
                            }
                        ) {
                            Icon(
                                Icons.Filled.PictureInPictureAlt,
                                contentDescription = "Picture-in-picture",
                            )
                        }
                    }
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
            }

            // Status green pill
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
                            if (isAudio) "$ext saved • Background audio enabled"
                            else "Download complete • Saved to Downloads",
                        color = Color(0xFF10B981),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            if (playback.fileNotFound) {
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
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .aspectRatio(playback.videoRatio)
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
                                            PenguAudioManager.attachVideoSurface(holder)
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
                                            PenguAudioManager.detachVideoSurface(holder)
                                        }
                                    }
                                )
                            }
                        },
                    )
                }
            } else {
                val artGradient =
                    Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFFD946EF)))
                Box(
                    modifier =
                        Modifier.size(190.dp)
                            .shadow(16.dp, RoundedCornerShape(32.dp), spotColor = Color(0xFF7C3AED))
                            .clip(RoundedCornerShape(32.dp))
                            .background(artGradient),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(84.dp),
                    )
                }

                Spacer(Modifier.height(20.dp))

                AnimatedAudioWaveform(isPlaying = playback.isPlaying)
            }

            Spacer(Modifier.height(18.dp))

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

            Spacer(Modifier.height(14.dp))

            val safeDuration = playback.durationMs.coerceAtLeast(1)
            Slider(
                value = playback.positionMs.coerceIn(0, safeDuration).toFloat(),
                onValueChange = { newPos -> PenguAudioManager.seekTo(newPos.toInt()) },
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
                    text = RingtoneToolsUtil.formatClock((playback.positionMs / 1000L).coerceAtLeast(0L)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = RingtoneToolsUtil.formatClock((playback.durationMs / 1000L).coerceAtLeast(0L)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(10.dp))

            // Transport controls: Speed, -10s, Play/Pause, +10s, Sleep Timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) Color(0xFF1E1E24) else Color(0xFFF3F4F6),
                    modifier =
                        Modifier.clickable {
                            view.slightHapticFeedback()
                            PenguAudioManager.cycleSpeed()
                        },
                ) {
                    Text(
                        text = "${playback.playbackSpeed}x",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7C3AED),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }

                IconButton(
                    onClick = {
                        view.slightHapticFeedback()
                        PenguAudioManager.skipBy(-10_000)
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
                                PenguAudioManager.togglePlayPause()
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playback.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }

                IconButton(
                    onClick = {
                        view.slightHapticFeedback()
                        PenguAudioManager.skipBy(10_000)
                    }
                ) {
                    Icon(Icons.Filled.Forward10, contentDescription = "Forward 10s", modifier = Modifier.size(30.dp))
                }

                // Sleep timer pill
                Box {
                    val timerActive = playback.sleepRemainingMs > 0L
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color =
                            if (timerActive) Color(0xFF7C3AED).copy(alpha = 0.16f)
                            else if (isDark) Color(0xFF1E1E24) else Color(0xFFF3F4F6),
                        modifier = Modifier.clickable { showSleepMenu = true },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bedtime,
                                contentDescription = "Sleep Timer",
                                tint = if (timerActive) Color(0xFF7C3AED) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            if (timerActive) {
                                Spacer(Modifier.width(4.dp))
                                val m = (playback.sleepRemainingMs / 60_000L) + 1
                                Text(
                                    text = "${m}m",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF7C3AED),
                                )
                            }
                        }
                    }
                    DropdownMenu(
                        expanded = showSleepMenu,
                        onDismissRequest = { showSleepMenu = false },
                    ) {
                        listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 45 to "45 min", 60 to "60 min").forEach { (mins, label) ->
                            DropdownMenuItem(
                                text = { Text("Sleep Timer: $label") },
                                onClick = {
                                    showSleepMenu = false
                                    PenguAudioManager.setSleepTimer(mins)
                                    val toastMsg = if (mins > 0) "Sleep timer set for $mins min" else "Sleep timer turned off"
                                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // A-B Loop pill
            val abLabel =
                when {
                    playback.abLoopStartMs != null && playback.abLoopEndMs != null ->
                        "A-B Loop: ${RingtoneToolsUtil.formatClock(playback.abLoopStartMs!! / 1000L)} - ${RingtoneToolsUtil.formatClock(playback.abLoopEndMs!! / 1000L)} (Tap to clear)"
                    playback.abLoopStartMs != null ->
                        "Point A set (${RingtoneToolsUtil.formatClock(playback.abLoopStartMs!! / 1000L)}) • Tap to set Point B"
                    else -> "Tap to set A-B Loop"
                }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color =
                    if (playback.abLoopStartMs != null) Color(0xFF7C3AED).copy(alpha = 0.14f)
                    else if (isDark) Color(0xFF1C1C22) else Color(0xFFF3F4F6),
                modifier =
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                        view.slightHapticFeedback()
                        PenguAudioManager.setAbLoopPoint()
                    },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Repeat,
                        contentDescription = null,
                        tint = if (playback.abLoopStartMs != null) Color(0xFF7C3AED) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = abLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (playback.abLoopStartMs != null) Color(0xFF7C3AED) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // Bottom quick action bar: Share, Ringtone Maker, Delete
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isDark) Color(0xFF1C1C22) else Color(0xFFF3F4F6))
                        .padding(vertical = 10.dp, horizontal = 6.dp),
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
                PlayerActionTile(icon = Icons.Filled.NotificationsActive, label = "Ringtone") {
                    PenguAudioManager.pauseIfVideoLeaving()
                    onOpenTools(info?.videoPath.orEmpty())
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
                        PenguAudioManager.stopAndClear()
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
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = tint)
    }
}
