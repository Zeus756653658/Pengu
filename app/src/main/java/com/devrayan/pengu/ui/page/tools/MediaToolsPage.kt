package com.devrayan.pengu.ui.page.tools

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devrayan.pengu.R
import com.devrayan.pengu.database.objects.DownloadedVideoInfo
import com.devrayan.pengu.ui.common.HapticFeedback.slightHapticFeedback
import com.devrayan.pengu.ui.common.LocalDarkTheme
import com.devrayan.pengu.util.DatabaseUtil
import com.devrayan.pengu.util.FfmpegToolsUtil
import com.devrayan.pengu.util.FileUtil
import java.io.File
import kotlin.math.roundToLong
import kotlinx.coroutines.launch

/**
 * Offline FFmpeg Media Tools: Video-to-MP3 converter and Video/Audio Trimmer.
 */
@Composable
fun MediaToolsPage(
    onNavigateBack: () -> Unit,
    onOpenDownloadedFile: (String) -> Unit = {},
) {
    val isDark = LocalDarkTheme.current.isDarkTheme()
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val history by DatabaseUtil.getDownloadHistoryFlow().collectAsStateWithLifecycle(emptyList())
    val recentFiles = remember(history) { history.reversed().filter { File(it.videoPath).exists() } }

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Video to MP3, 1 = Trim/Cut
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFilePath by remember { mutableStateOf<String?>(null) }
    var mediaInfo by remember { mutableStateOf<FfmpegToolsUtil.MediaProbeInfo?>(null) }
    var outputName by remember { mutableStateOf("") }
    var bitrateKbps by remember { mutableIntStateOf(320) }
    var trimRange by remember { mutableStateOf(0f..10f) }

    var isProcessing by remember { mutableStateOf(false) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun applyMediaProbe(uri: Uri?, path: String?, probe: FfmpegToolsUtil.MediaProbeInfo) {
        selectedUri = uri
        selectedFilePath = path
        mediaInfo = probe
        outputName = probe.displayName.substringBeforeLast('.')
        val durSec = (probe.durationMs / 1000L).coerceAtLeast(1L).toFloat()
        trimRange = 0f..durSec
        outputFile = null
        errorMessage = null
    }

    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                val probe = FfmpegToolsUtil.probeMedia(context, it)
                applyMediaProbe(it, null, probe)
            }
        }

    Scaffold(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Top bar
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
                        text = "Media Tools",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Offline FFmpeg Converter & Trimmer",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Segmented tabs (Video to MP3 | Trim & Cut)
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isDark) Color(0xFF1C1C22) else Color(0xFFF3F4F6))
                        .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf("Video to MP3" to Icons.Filled.MusicNote, "Trim / Cut" to Icons.Filled.ContentCut)
                    .forEachIndexed { idx, (label, icon) ->
                        val active = activeTab == idx
                        Box(
                            modifier =
                                Modifier.weight(1f)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(if (active) Color(0xFF7C3AED) else Color.Transparent)
                                    .clickable {
                                        view.slightHapticFeedback()
                                        activeTab = idx
                                        outputFile = null
                                        errorMessage = null
                                    }
                                    .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = label,
                                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
            }

            Column(
                modifier =
                    Modifier.weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // File chooser card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF1C1C22) else Color(0xFFF9FAFB)
                        ),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = if (mediaInfo == null) "1. Select media file" else "Selected media",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(10.dp))

                        mediaInfo?.let { info ->
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF7C3AED).copy(alpha = 0.1f))
                                        .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (info.isAudioOnly) Icons.Filled.AudioFile else Icons.Filled.VideoFile,
                                    contentDescription = null,
                                    tint = Color(0xFF7C3AED),
                                    modifier = Modifier.size(28.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = info.displayName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text =
                                            "Duration: ${FfmpegToolsUtil.formatClock((info.durationMs / 1000L).coerceAtLeast(0L))} • ${info.extension.uppercase()}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }

                        OutlinedButton(
                            onClick = {
                                val mimes =
                                    if (activeTab == 0) arrayOf("video/*")
                                    else arrayOf("video/*", "audio/*")
                                filePickerLauncher.launch(mimes)
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (mediaInfo == null) "Pick from Device Storage" else "Change File")
                        }
                    }
                }

                // Quick pick from Pengu Downloads
                if (mediaInfo == null && recentFiles.isNotEmpty()) {
                    Text(
                        text = "Or pick from your Pengu downloads",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    recentFiles.take(5).forEach { item ->
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isDark) Color(0xFF1C1C22) else Color(0xFFF3F4F6))
                                    .clickable {
                                        val probe = FfmpegToolsUtil.probeLocalFile(item.videoPath)
                                        applyMediaProbe(null, item.videoPath, probe)
                                    }
                                    .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (item.videoPath.endsWith(".mp3", true)) Icons.Filled.AudioFile else Icons.Filled.VideoFile,
                                contentDescription = null,
                                tint = Color(0xFF7C3AED),
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.videoTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                )
                                Text(
                                    text = item.extractor,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Tool options when a file is selected
                mediaInfo?.let { info ->
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = if (isDark) Color(0xFF1C1C22) else Color(0xFFF9FAFB)
                            ),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = if (activeTab == 0) "2. MP3 Export Settings" else "2. Select Trim Range",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.height(12.dp))

                            if (activeTab == 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    listOf(320, 192, 128).forEach { br ->
                                        val selected = bitrateKbps == br
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color =
                                                if (selected) Color(0xFF7C3AED)
                                                else if (isDark) Color(0xFF282830) else Color(0xFFE5E7EB),
                                            modifier =
                                                Modifier.weight(1f).clickable {
                                                    view.slightHapticFeedback()
                                                    bitrateKbps = br
                                                },
                                        ) {
                                            Text(
                                                text = "$br kbps",
                                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                            )
                                        }
                                    }
                                }
                            } else {
                                val maxSec = (info.durationMs / 1000L).coerceAtLeast(2L).toFloat()
                                val startSec = trimRange.start.roundToLong().coerceAtLeast(0L)
                                val endSec = trimRange.endInclusive.roundToLong().coerceAtLeast(startSec + 1L)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "Start: ${FfmpegToolsUtil.formatClock(startSec)}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF7C3AED),
                                    )
                                    Text(
                                        text = "End: ${FfmpegToolsUtil.formatClock(endSec)}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF7C3AED),
                                    )
                                }
                                RangeSlider(
                                    value = trimRange.start.coerceIn(0f, maxSec - 1f)..trimRange.endInclusive.coerceIn(1f, maxSec),
                                    onValueChange = { newRange -> trimRange = newRange },
                                    valueRange = 0f..maxSec,
                                    colors =
                                        SliderDefaults.colors(
                                            thumbColor = Color(0xFF7C3AED),
                                            activeTrackColor = Color(0xFF7C3AED),
                                        ),
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            OutlinedTextField(
                                value = outputName,
                                onValueChange = { outputName = it },
                                label = { Text("Output file name") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Spacer(Modifier.height(16.dp))

                            val gradient =
                                Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFFD946EF)))
                            Button(
                                onClick = {
                                    if (isProcessing) return@Button
                                    view.slightHapticFeedback()
                                    isProcessing = true
                                    outputFile = null
                                    errorMessage = null
                                    scope.launch {
                                        val res =
                                            if (activeTab == 0) {
                                                FfmpegToolsUtil.convertToMp3(
                                                    context = context,
                                                    inputUri = selectedUri,
                                                    inputFilePath = selectedFilePath,
                                                    bitrateKbps = bitrateKbps,
                                                    outputBaseName = outputName,
                                                )
                                            } else {
                                                val s = trimRange.start.roundToLong().coerceAtLeast(0L)
                                                val e = trimRange.endInclusive.roundToLong().coerceAtLeast(s + 1L)
                                                FfmpegToolsUtil.trimMedia(
                                                    context = context,
                                                    inputUri = selectedUri,
                                                    inputFilePath = selectedFilePath,
                                                    startSeconds = s,
                                                    endSeconds = e,
                                                    isAudioOnly = info.isAudioOnly,
                                                    outputExt = info.extension,
                                                    outputBaseName = outputName,
                                                )
                                            }
                                        isProcessing = false
                                        res.onSuccess { file ->
                                            outputFile = file
                                            Toast.makeText(context, "Saved: ${file.name}", Toast.LENGTH_SHORT).show()
                                        }.onFailure { th ->
                                            errorMessage = th.message ?: "Processing failed"
                                        }
                                    }
                                },
                                enabled = !isProcessing,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues(),
                                shape = RoundedCornerShape(16.dp),
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .height(50.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(gradient),
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text("Processing with FFmpeg...", color = Color.White, fontWeight = FontWeight.Bold)
                                } else {
                                    Text(
                                        text = if (activeTab == 0) "Convert to MP3" else "Save Trimmed Clip",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                errorMessage?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }

                outputFile?.let { file ->
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.14f),
                        shape = RoundedCornerShape(16.dp),
                        modifier =
                            Modifier.fillMaxWidth().border(1.dp, Color(0xFF10B981).copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Saved to Downloads!",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = file.absolutePath,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { onOpenDownloadedFile(file.absolutePath) },
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Play")
                                }
                                OutlinedButton(
                                    onClick = {
                                        FileUtil.createIntentForSharingFile(file.absolutePath)?.let {
                                            context.startActivity(Intent.createChooser(it, context.getString(R.string.share)))
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Share")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
