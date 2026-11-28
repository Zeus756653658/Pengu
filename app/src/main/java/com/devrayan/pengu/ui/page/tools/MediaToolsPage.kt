package com.devrayan.pengu.ui.page.tools

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devrayan.pengu.ui.common.HapticFeedback.slightHapticFeedback
import com.devrayan.pengu.ui.common.LocalDarkTheme
import com.devrayan.pengu.util.DatabaseUtil
import com.devrayan.pengu.util.RingtoneToolsUtil
import com.devrayan.pengu.util.RingtoneToolsUtil.RingtoneResult
import com.devrayan.pengu.util.RingtoneToolsUtil.RingtoneTarget
import com.devrayan.pengu.util.toFileSizeText
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dedicated offline Ringtone Maker supporting audio & video extraction, waveform range preview,
 * and 5 save/apply actions: Save, Set Ringtone, Set Contact Ringtone, Set Alarm Sound, and
 * Set Notification.
 */
@Composable
fun MediaToolsPage(
    initialTab: Int = 0,
    initialFilePath: String = "",
    onNavigateBack: () -> Unit,
    onOpenDownloadedFile: (String) -> Unit = {},
) {
    val isDark = LocalDarkTheme.current.isDarkTheme()
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val history by DatabaseUtil.getDownloadHistoryFlow().collectAsStateWithLifecycle(emptyList())
    val recentFiles =
        remember(history) {
            history.reversed().filter { File(it.videoPath).exists() }
        }

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFilePath by remember { mutableStateOf<String?>(null) }
    var mediaInfo by remember { mutableStateOf<RingtoneToolsUtil.MediaProbeInfo?>(null) }
    var ringtoneTitle by remember { mutableStateOf("") }
    var bitrateKbps by remember { mutableIntStateOf(192) }
    var fadeInOut by remember { mutableStateOf(true) }
    var clipRangeMs by remember { mutableStateOf(0f..30_000f) }

    var isProcessing by remember { mutableStateOf(false) }
    var processProgress by remember { mutableFloatStateOf(0f) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var lastResult by remember { mutableStateOf<RingtoneResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showRecentPicker by remember { mutableStateOf(false) }
    var pendingSystemTarget by remember { mutableStateOf<RingtoneTarget?>(null) }

    val previewPlayer = remember { MediaPlayer() }
    var isPreviewPrepared by remember { mutableStateOf(false) }
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var previewPositionMs by remember { mutableLongStateOf(0L) }
    var loopClip by remember { mutableStateOf(true) }

    fun applyMediaProbe(uri: Uri?, path: String?, probe: RingtoneToolsUtil.MediaProbeInfo) {
        runCatching { if (previewPlayer.isPlaying) previewPlayer.pause() }
        isPreviewPlaying = false
        isPreviewPrepared = false
        previewPositionMs = 0L
        selectedUri = uri
        selectedFilePath = path
        mediaInfo = probe
        ringtoneTitle = "${probe.displayName.substringBeforeLast('.')}_ringtone"
        val totalMs = probe.durationMs.coerceAtLeast(1_000L)
        clipRangeMs = 0f..minOf(totalMs, 30_000L).toFloat()
        lastResult = null
        errorMessage = null
        showRecentPicker = false
    }

    LaunchedEffect(initialFilePath) {
        if (initialFilePath.isNotBlank() && selectedFilePath == null && selectedUri == null) {
            val file = File(initialFilePath)
            if (file.exists()) {
                val probe = withContext(Dispatchers.IO) { RingtoneToolsUtil.probeLocalFile(initialFilePath) }
                applyMediaProbe(null, initialFilePath, probe)
            }
        }
    }

    LaunchedEffect(selectedUri, selectedFilePath, mediaInfo) {
        mediaInfo ?: return@LaunchedEffect
        isPreviewPrepared = false
        isPreviewPlaying = false
        runCatching {
            previewPlayer.reset()
            when {
                !selectedFilePath.isNullOrBlank() -> previewPlayer.setDataSource(selectedFilePath)
                selectedUri != null -> previewPlayer.setDataSource(context, selectedUri!!)
                else -> return@LaunchedEffect
            }
            previewPlayer.setOnPreparedListener { mp ->
                isPreviewPrepared = true
                previewPositionMs = clipRangeMs.start.roundToLong().coerceAtLeast(0L)
                runCatching { mp.seekTo(previewPositionMs.toInt()) }
            }
            previewPlayer.setOnCompletionListener {
                isPreviewPlaying = false
                val startTarget = clipRangeMs.start.roundToLong().coerceAtLeast(0L)
                previewPositionMs = startTarget
                runCatching { previewPlayer.seekTo(startTarget.toInt()) }
            }
            previewPlayer.setOnErrorListener { _, _, _ ->
                isPreviewPlaying = false
                isPreviewPrepared = false
                true
            }
            previewPlayer.prepareAsync()
        }
    }

    LaunchedEffect(isPreviewPlaying, isPreviewPrepared, clipRangeMs, loopClip) {
        val startBound = clipRangeMs.start.roundToLong().coerceAtLeast(0L)
        val endBound = clipRangeMs.endInclusive.roundToLong().coerceAtLeast(startBound + 500L)
        while (isPreviewPlaying && isPreviewPrepared) {
            runCatching {
                if (previewPlayer.isPlaying) {
                    val pos = previewPlayer.currentPosition.toLong()
                    previewPositionMs = pos
                    if (pos >= endBound) {
                        previewPlayer.seekTo(startBound.toInt())
                        previewPositionMs = startBound
                        if (!loopClip) {
                            previewPlayer.pause()
                            isPreviewPlaying = false
                        }
                    }
                } else {
                    isPreviewPlaying = false
                }
            }
            delay(80)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                runCatching {
                    if (previewPlayer.isPlaying) {
                        previewPlayer.pause()
                        isPreviewPlaying = false
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activeJob?.cancel()
            RingtoneToolsUtil.cancelActiveProcess()
            runCatching {
                previewPlayer.stop()
                previewPlayer.release()
            }
        }
    }

    fun executeRingtoneAction(target: RingtoneTarget, contactUri: Uri? = null) {
        if (mediaInfo == null || isProcessing) return
        runCatching {
            if (previewPlayer.isPlaying) {
                previewPlayer.pause()
                isPreviewPlaying = false
            }
        }
        isProcessing = true
        processProgress = 0f
        lastResult = null
        errorMessage = null

        val startMs = clipRangeMs.start.roundToLong()
        val endMs = clipRangeMs.endInclusive.roundToLong()

        activeJob =
            scope.launch {
                val res =
                    RingtoneToolsUtil.createAndApplyRingtone(
                        context = context,
                        inputUri = selectedUri,
                        inputFilePath = selectedFilePath,
                        startMs = startMs,
                        endMs = endMs,
                        outputBaseName = ringtoneTitle,
                        bitrateKbps = bitrateKbps,
                        fadeInOut = fadeInOut,
                        target = target,
                        contactUri = contactUri,
                        onProgress = { frac ->
                            scope.launch(Dispatchers.Main) { processProgress = frac }
                        },
                    )
                isProcessing = false
                res
                    .onSuccess { result ->
                        lastResult = result
                        val msg =
                            when (result.target) {
                                RingtoneTarget.SAVE -> "Saved: ${result.fileName}"
                                RingtoneTarget.SET_RINGTONE -> "Default phone ringtone updated!"
                                RingtoneTarget.SET_CONTACT_RINGTONE ->
                                    "Ringtone set for ${result.assignedContactName ?: "contact"}!"
                                RingtoneTarget.SET_ALARM -> "Default alarm sound updated!"
                                RingtoneTarget.SET_NOTIFICATION -> "Default notification sound updated!"
                            }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { th ->
                        if (th !is CancellationException) {
                            errorMessage = th.message ?: "Unable to create ringtone"
                        }
                    }
            }
    }

    val writeSettingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val target = pendingSystemTarget
            pendingSystemTarget = null
            if (target != null && RingtoneToolsUtil.canWriteSystemSettings(context)) {
                executeRingtoneAction(target)
            } else if (target != null) {
                Toast.makeText(
                    context,
                    "Allow 'Modify system settings' to apply ringtones",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

    val contactPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { contactUri ->
            if (contactUri != null) {
                executeRingtoneAction(RingtoneTarget.SET_CONTACT_RINGTONE, contactUri = contactUri)
            }
        }

    val contactsPermLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val granted = grants.values.all { it }
            if (granted) {
                contactPickerLauncher.launch(null)
            } else {
                Toast.makeText(
                    context,
                    "Contacts permission is required to assign a contact ringtone",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

    fun triggerAction(target: RingtoneTarget) {
        view.slightHapticFeedback()
        if (mediaInfo == null) {
            Toast.makeText(context, "Select an audio or video file first", Toast.LENGTH_SHORT).show()
            return
        }
        when (target) {
            RingtoneTarget.SAVE -> executeRingtoneAction(RingtoneTarget.SAVE)
            RingtoneTarget.SET_RINGTONE,
            RingtoneTarget.SET_ALARM,
            RingtoneTarget.SET_NOTIFICATION -> {
                if (RingtoneToolsUtil.canWriteSystemSettings(context)) {
                    executeRingtoneAction(target)
                } else {
                    pendingSystemTarget = target
                    Toast.makeText(
                        context,
                        "Allow 'Modify system settings' for Pengu to apply",
                        Toast.LENGTH_LONG,
                    ).show()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        writeSettingsLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                Uri.parse("package:${context.packageName}"),
                            )
                        )
                    }
                }
            }
            RingtoneTarget.SET_CONTACT_RINGTONE -> {
                val hasRead =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                        PackageManager.PERMISSION_GRANTED
                val hasWrite =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) ==
                        PackageManager.PERMISSION_GRANTED
                if (hasRead && hasWrite) {
                    contactPickerLauncher.launch(null)
                } else {
                    contactsPermLauncher.launch(
                        arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
                    )
                }
            }
        }
    }

    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                scope.launch {
                    val probe = withContext(Dispatchers.IO) { RingtoneToolsUtil.probeMedia(context, it) }
                    applyMediaProbe(it, null, probe)
                }
            }
        }

    fun seekPreviewQuietly(targetMs: Long) {
        previewPositionMs = targetMs
        if (isPreviewPrepared) {
            runCatching { previewPlayer.seekTo(targetMs.toInt()) }
        }
    }

    fun togglePreviewPlay() {
        view.slightHapticFeedback()
        if (!isPreviewPrepared) return
        val startBound = clipRangeMs.start.roundToLong().coerceAtLeast(0L)
        val endBound = clipRangeMs.endInclusive.roundToLong().coerceAtLeast(startBound + 500L)
        runCatching {
            if (previewPlayer.isPlaying) {
                previewPlayer.pause()
                isPreviewPlaying = false
            } else {
                val cur = previewPlayer.currentPosition.toLong()
                if (cur < startBound || cur >= endBound - 150L) {
                    previewPlayer.seekTo(startBound.toInt())
                    previewPositionMs = startBound
                }
                previewPlayer.start()
                isPreviewPlaying = true
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        view.slightHapticFeedback()
                        onNavigateBack()
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ringtone Maker",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Cut any song or video & set as Ringtone, Contact, Alarm, or Notification",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(
                modifier =
                    Modifier.weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Step 1: Media Source Picker
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF1A1B23) else Color(0xFFF6F7FB)
                        ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier =
                                    Modifier.size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF6366F1), Color(0xFFEC4899))
                                            )
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector =
                                        if (mediaInfo?.isAudioOnly == true) Icons.Filled.AudioFile
                                        else Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mediaInfo?.displayName ?: "Choose Audio or Video",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text =
                                        mediaInfo?.let {
                                            "Length: ${RingtoneToolsUtil.formatClockMs(it.durationMs)} • ${it.extension.uppercase()} • ${it.fileSizeBytes.toFileSizeText()}"
                                        } ?: "Supports MP3, M4A, MP4, Reels & Shorts",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { filePickerLauncher.launch(arrayOf("audio/*", "video/*")) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            ) {
                                Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Device Files", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = { showRecentPicker = !showRecentPicker },
                                enabled = recentFiles.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Downloads (${recentFiles.size})", fontSize = 13.sp)
                            }
                        }

                        if (showRecentPicker && recentFiles.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                recentFiles.take(10).forEach { item ->
                                    val isAudioItem = item.videoPath.lowercase().endsWith(".mp3") || item.videoPath.lowercase().endsWith(".m4a")
                                    Row(
                                        modifier =
                                            Modifier.fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isDark) Color(0xFF252632) else Color.White)
                                                .clickable {
                                                    scope.launch {
                                                        val probe =
                                                            withContext(Dispatchers.IO) {
                                                                RingtoneToolsUtil.probeLocalFile(item.videoPath)
                                                            }
                                                        applyMediaProbe(null, item.videoPath, probe)
                                                    }
                                                }
                                                .padding(horizontal = 10.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector =
                                                if (isAudioItem) Icons.Filled.AudioFile
                                                else Icons.Filled.VideoFile,
                                            contentDescription = null,
                                            tint = Color(0xFF7C3AED),
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = item.videoTitle,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Step 2: Waveform Range Scrubber + Live Preview Controls
                if (mediaInfo != null) {
                    val maxDurMs = mediaInfo!!.durationMs.coerceAtLeast(1_000L)
                    val maxDurFloat = maxDurMs.toFloat()
                    val startMs = clipRangeMs.start.roundToLong().coerceIn(0L, maxDurMs)
                    val endMs = clipRangeMs.endInclusive.roundToLong().coerceIn(startMs, maxDurMs)
                    val clipDurationMs = (endMs - startMs).coerceAtLeast(0L)

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = if (isDark) Color(0xFF1A1B23) else Color(0xFFF6F7FB)
                            ),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Ringtone Clip Selector",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF7C3AED).copy(alpha = 0.16f),
                                ) {
                                    Text(
                                        text = RingtoneToolsUtil.formatClockWithTenths(clipDurationMs),
                                        color = Color(0xFF7C3AED),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                            }

                            // Visual Waveform Indicator showing selected window + active playhead
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isDark) Color(0xFF111118) else Color(0xFFEDE9FE))
                                        .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                val bars = 36
                                val startFrac = startMs.toFloat() / maxDurFloat
                                val endFrac = endMs.toFloat() / maxDurFloat
                                val curFrac = previewPositionMs.toFloat() / maxDurFloat
                                for (i in 0 until bars) {
                                    val barPos = i.toFloat() / (bars - 1).toFloat()
                                    val inClip = barPos in startFrac..endFrac
                                    val nearPlayhead = abs(barPos - curFrac) < 0.03f && isPreviewPlaying
                                    val hRatio = 0.30f + 0.60f * abs(sin(i * 0.72f + 0.6f))
                                    Box(
                                        modifier =
                                            Modifier.weight(1f)
                                                .height((38f * hRatio).dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(
                                                    when {
                                                        nearPlayhead -> Color(0xFFF43F5E)
                                                        inClip -> Color(0xFF7C3AED)
                                                        isDark -> Color.White.copy(alpha = 0.22f)
                                                        else -> Color(0xFF6366F1).copy(alpha = 0.25f)
                                                    }
                                                )
                                    )
                                }
                            }

                            RangeSlider(
                                value = clipRangeMs,
                                onValueChange = { next ->
                                    val clampedStart = next.start.coerceIn(0f, maxDurFloat - 500f)
                                    val clampedEnd = next.endInclusive.coerceIn(clampedStart + 500f, maxDurFloat)
                                    clipRangeMs = clampedStart..clampedEnd
                                    seekPreviewQuietly(clampedStart.roundToLong())
                                },
                                valueRange = 0f..maxDurFloat,
                                colors =
                                    SliderDefaults.colors(
                                        thumbColor = Color(0xFF7C3AED),
                                        activeTrackColor = Color(0xFF7C3AED),
                                    ),
                            )

                            // Preview Playback + Fine Nudge Bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF6366F1),
                                        modifier =
                                            Modifier.size(40.dp)
                                                .clip(CircleShape)
                                                .clickable(enabled = isPreviewPrepared) { togglePreviewPlay() },
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector =
                                                    if (isPreviewPlaying) Icons.Filled.Pause
                                                    else Icons.Filled.PlayArrow,
                                                contentDescription = "Preview Clip",
                                                tint = Color.White,
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = "Preview: ${RingtoneToolsUtil.formatClockWithTenths(previewPositionMs)}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text =
                                                "${RingtoneToolsUtil.formatClockWithTenths(startMs)} -> ${RingtoneToolsUtil.formatClockWithTenths(endMs)}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color =
                                        if (loopClip) Color(0xFF7C3AED).copy(alpha = 0.16f)
                                        else if (isDark) Color(0xFF282830)
                                        else Color.White,
                                    modifier =
                                        Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                                            view.slightHapticFeedback()
                                            loopClip = !loopClip
                                        },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Filled.Repeat,
                                            null,
                                            tint =
                                                if (loopClip) Color(0xFF7C3AED)
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "Loop",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }

                            // 0.1s Nudge & Android Ringtone Length Presets
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(15, 30, 40).forEach { secs ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isDark) Color(0xFF282830) else Color.White,
                                        modifier =
                                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable {
                                                view.slightHapticFeedback()
                                                val s = startMs.toFloat()
                                                val e = (s + secs * 1000f).coerceAtMost(maxDurFloat)
                                                clipRangeMs = s..e
                                                seekPreviewQuietly(startMs)
                                            },
                                    ) {
                                        Text(
                                            text = "${secs}s Clip",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = ringtoneTitle,
                                onValueChange = { ringtoneTitle = it },
                                label = { Text("Ringtone Title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text("Smooth Fade-In & Fade-Out", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Gentle start and end on ringtone playback", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = fadeInOut, onCheckedChange = { fadeInOut = it })
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(192 to "Standard 192 kbps", 320 to "High 320 kbps").forEach { (kbps, lbl) ->
                                    val active = bitrateKbps == kbps
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color =
                                            if (active) Color(0xFF7C3AED)
                                            else if (isDark) Color(0xFF282830)
                                            else Color.White,
                                        modifier =
                                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable {
                                                view.slightHapticFeedback()
                                                bitrateKbps = kbps
                                            },
                                    ) {
                                        Text(
                                            text = lbl,
                                            color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Step 3: 5 Save & Set Ringtone Actions
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = if (isDark) Color(0xFF1A1B23) else Color(0xFFF6F7FB)
                            ),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "Save & Apply Ringtone",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )

                            if (isProcessing) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "Creating ringtone... ${(processProgress * 100f).toInt()}%",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF7C3AED),
                                        )
                                        IconButton(
                                            onClick = {
                                                activeJob?.cancel()
                                                RingtoneToolsUtil.cancelActiveProcess()
                                                isProcessing = false
                                            },
                                            modifier = Modifier.size(26.dp),
                                        ) {
                                            Icon(Icons.Filled.Close, "Cancel", tint = Color(0xFFEF4444))
                                        }
                                    }
                                    LinearProgressIndicator(
                                        progress = { processProgress },
                                        color = Color(0xFF7C3AED),
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                    )
                                }
                            }

                            // 1. Primary Save button
                            Button(
                                onClick = { triggerAction(RingtoneTarget.SAVE) },
                                enabled = !isProcessing,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF6366F1),
                                        contentColor = Color.White,
                                    ),
                            ) {
                                Icon(Icons.Filled.Save, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Save", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            // 2 & 3: Set Ringtone | Set Contact Ringtone
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                RingtoneActionButton(
                                    icon = Icons.Filled.NotificationsActive,
                                    label = "Set Ringtone",
                                    accentColor = Color(0xFF7C3AED),
                                    enabled = !isProcessing,
                                    isDark = isDark,
                                    onClick = { triggerAction(RingtoneTarget.SET_RINGTONE) },
                                    modifier = Modifier.weight(1f),
                                )
                                RingtoneActionButton(
                                    icon = Icons.Filled.Contacts,
                                    label = "Set Contact Ringtone",
                                    accentColor = Color(0xFFEC4899),
                                    enabled = !isProcessing,
                                    isDark = isDark,
                                    onClick = { triggerAction(RingtoneTarget.SET_CONTACT_RINGTONE) },
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            // 4 & 5: Set Alarm Sound | Set Notification
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                RingtoneActionButton(
                                    icon = Icons.Filled.Alarm,
                                    label = "Set Alarm Sound",
                                    accentColor = Color(0xFFF59E0B),
                                    enabled = !isProcessing,
                                    isDark = isDark,
                                    onClick = { triggerAction(RingtoneTarget.SET_ALARM) },
                                    modifier = Modifier.weight(1f),
                                )
                                RingtoneActionButton(
                                    icon = Icons.Filled.Notifications,
                                    label = "Set Notification",
                                    accentColor = Color(0xFF10B981),
                                    enabled = !isProcessing,
                                    isDark = isDark,
                                    onClick = { triggerAction(RingtoneTarget.SET_NOTIFICATION) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                // Success Result Card
                lastResult?.let { res ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF10B981))
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text =
                                        when (res.target) {
                                            RingtoneTarget.SAVE -> "Ringtone Saved!"
                                            RingtoneTarget.SET_RINGTONE -> "Set as Default Ringtone!"
                                            RingtoneTarget.SET_CONTACT_RINGTONE ->
                                                "Assigned to ${res.assignedContactName ?: "Contact"}!"
                                            RingtoneTarget.SET_ALARM -> "Set as Default Alarm Sound!"
                                            RingtoneTarget.SET_NOTIFICATION ->
                                                "Set as Default Notification!"
                                        },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF10B981),
                                )
                                Text(
                                    text = res.displayPath,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                errorMessage?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun RingtoneActionButton(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    enabled: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color =
            if (isDark) Color(0xFF252632) else Color.White,
        modifier =
            modifier
                .border(
                    width = 1.dp,
                    color = accentColor.copy(alpha = 0.28f),
                    shape = RoundedCornerShape(14.dp),
                )
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = enabled, onClick = onClick),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier =
                    Modifier.size(34.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(18.dp))
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
