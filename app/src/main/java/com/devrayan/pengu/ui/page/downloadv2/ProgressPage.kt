package com.devrayan.pengu.ui.page.downloadv2

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devrayan.pengu.R
import com.devrayan.pengu.download.DownloaderV2
import com.devrayan.pengu.download.Task
import com.devrayan.pengu.download.Task.DownloadState.Canceled
import com.devrayan.pengu.download.Task.DownloadState.Completed
import com.devrayan.pengu.download.Task.DownloadState.Error
import com.devrayan.pengu.download.Task.DownloadState.FetchingInfo
import com.devrayan.pengu.download.Task.DownloadState.Idle
import com.devrayan.pengu.download.Task.DownloadState.ReadyWithInfo
import com.devrayan.pengu.download.Task.DownloadState.Running
import com.devrayan.pengu.ui.common.AsyncImageImpl
import com.devrayan.pengu.ui.common.HapticFeedback.slightHapticFeedback
import com.devrayan.pengu.ui.common.LocalDarkTheme
import com.devrayan.pengu.util.FileUtil
import com.devrayan.pengu.util.makeToast
import kotlin.math.roundToInt
import org.koin.compose.koinInject

private val speedRegex = Regex("""(\d+(?:\.\d+)?)\s*(Ki?B/s|Mi?B/s|Gi?B/s|B/s)""", RegexOption.IGNORE_CASE)

/**
 * VidSnap Screen-3 Download Progress screen with Active/Completed segmented tabs,
 * live circular progress hero summary, speed extraction, and per-task actions.
 */
@Composable
fun ProgressPage(
    downloader: DownloaderV2 = koinInject(),
    onOpenCompletedFile: (String) -> Unit = {},
    onNavigateToCustomTasks: () -> Unit = {},
    onNavigateHome: () -> Unit = {},
) {
    val isDark = LocalDarkTheme.current.isDarkTheme()
    val context = LocalContext.current
    val view = LocalView.current
    val taskMap = downloader.getTaskStateMap()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Active, 1 = Completed

    val activeTasks by remember {
        derivedStateOf {
            taskMap.entries
                .filter { it.value.downloadState !is Completed }
                .sortedBy { it.value.downloadState }
                .map { it.key to it.value }
        }
    }

    val completedTasks by remember {
        derivedStateOf {
            taskMap.entries
                .filter { it.value.downloadState is Completed }
                .sortedByDescending { it.key.timeCreated }
                .map { it.key to it.value }
        }
    }

    val runningTasks = remember(activeTasks) {
        activeTasks.filter { it.second.downloadState is Running || it.second.downloadState is FetchingInfo }
    }

    val avgProgress by remember(runningTasks) {
        derivedStateOf {
            val withPct = runningTasks.mapNotNull { (_, state) ->
                (state.downloadState as? Running)?.progress?.takeIf { it >= 0f }
            }
            if (withPct.isEmpty()) 0f else (withPct.sum() / withPct.size).coerceIn(0f, 1f)
        }
    }

    val combinedSpeedText = remember(runningTasks) {
        val speeds =
            runningTasks.mapNotNull { (_, state) ->
                val text = (state.downloadState as? Running)?.progressText.orEmpty()
                speedRegex.find(text)?.value
            }
        when {
            speeds.isNotEmpty() -> speeds.first()
            runningTasks.isNotEmpty() -> "Downloading..."
            activeTasks.isNotEmpty() -> "${activeTasks.size} queued / paused"
            else -> "Ready"
        }
    }

    val animatedRing by animateFloatAsState(targetValue = avgProgress, label = "ringProgress")

    Scaffold(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = paddingValues.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Top bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Progress",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF7C3AED).copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = "${runningTasks.size} active",
                            color = Color(0xFF7C3AED),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onNavigateToCustomTasks) {
                        Icon(
                            imageVector = Icons.Filled.Terminal,
                            contentDescription = "Command Tasks",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Active / Completed segmented tabs
            item {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDark) Color(0xFF1C1C22) else Color(0xFFF3F4F6))
                            .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(
                        "Active (${activeTasks.size})",
                        "Completed (${completedTasks.size})",
                    ).forEachIndexed { idx, label ->
                        val active = selectedTab == idx
                        Box(
                            modifier =
                                Modifier.weight(1f)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(if (active) Color.White else Color.Transparent)
                                    .clickable {
                                        view.slightHapticFeedback()
                                        selectedTab = idx
                                    }
                                    .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color =
                                    if (active) Color(0xFF111827)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Hero gradient progress summary card (VidSnap Screen 3)
            item {
                val heroGradient =
                    Brush.linearGradient(
                        listOf(Color(0xFF6366F1), Color(0xFFD946EF), Color(0xFFF43F5E))
                    )
                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .shadow(8.dp, RoundedCornerShape(22.dp), spotColor = Color(0xFF7C3AED)),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(heroGradient).padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text =
                                    if (runningTasks.isNotEmpty())
                                        "Downloading ${runningTasks.size} ${if (runningTasks.size == 1) "file" else "files"}"
                                    else if (activeTasks.isNotEmpty())
                                        "${activeTasks.size} task(s) in queue"
                                    else "No active downloads",
                                color = Color.White.copy(alpha = 0.88f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = combinedSpeedText,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text =
                                    if (completedTasks.isNotEmpty())
                                        "${completedTasks.size} completed in this session"
                                    else "Total speed & live queue",
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 12.sp,
                            )
                        }

                        // Circular progress ring
                        Box(modifier = Modifier.size(68.dp), contentAlignment = Alignment.Center) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeWidth = 7.dp.toPx()
                                drawArc(
                                    color = Color.White.copy(alpha = 0.28f),
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                )
                                drawArc(
                                    color = Color.White,
                                    startAngle = -90f,
                                    sweepAngle = 360f * animatedRing,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                )
                            }
                            Text(
                                text = "${(animatedRing * 100).roundToInt()}%",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }

            val visibleList = if (selectedTab == 0) activeTasks else completedTasks

            if (visibleList.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = if (isDark) Color(0xFF1C1C22) else Color(0xFFF9FAFB)
                            ),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier =
                                    Modifier.size(52.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF7C3AED).copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.HourglassEmpty,
                                    contentDescription = null,
                                    tint = Color(0xFF7C3AED),
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (selectedTab == 0) "Nothing downloading right now" else "No completed tasks yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Paste any video or audio link on Home to get started",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(14.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF7C3AED),
                                modifier = Modifier.clickable(onClick = onNavigateHome),
                            ) {
                                Text(
                                    text = "Go to Home",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                items(items = visibleList, key = { it.first.id }) { (task, state) ->
                    ProgressTaskCard(
                        task = task,
                        state = state,
                        isDark = isDark,
                        onCancel = { downloader.cancel(task) },
                        onResume = { downloader.restart(task) },
                        onRemove = { downloader.remove(task) },
                        onOpen = { path ->
                            if (!path.isNullOrBlank()) {
                                onOpenCompletedFile(path)
                            } else {
                                context.makeToast(R.string.file_unavailable)
                            }
                        },
                        onShare = { path ->
                            FileUtil.createIntentForSharingFile(path)?.let {
                                context.startActivity(Intent.createChooser(it, context.getString(R.string.share)))
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressTaskCard(
    task: Task,
    state: Task.State,
    isDark: Boolean,
    onCancel: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    onOpen: (String?) -> Unit,
    onShare: (String?) -> Unit,
) {
    val isAudio = task.preferences.extractAudio
    val ds = state.downloadState
    val title = state.viewState.title.ifBlank { task.url }
    val subtitle =
        listOfNotNull(
                state.viewState.extractorKey.takeIf { it.isNotBlank() },
                if (isAudio) "MP3 Audio" else "Video",
            )
            .joinToString(" • ")

    val iconGradient =
        if (isAudio) Brush.linearGradient(listOf(Color(0xFFF43F5E), Color(0xFFFB923C)))
        else Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF7C3AED)))

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(18.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF1C1C22) else Color.White
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(iconGradient),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!state.viewState.thumbnailUrl.isNullOrBlank()) {
                        AsyncImageImpl(
                            model = state.viewState.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            imageVector = if (isAudio) Icons.Filled.MusicNote else Icons.Filled.Videocam,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                when (ds) {
                    is Running,
                    is FetchingInfo -> {
                        IconButton(onClick = onCancel, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel", modifier = Modifier.size(18.dp))
                        }
                    }
                    is Canceled,
                    is Error -> {
                        IconButton(onClick = onResume, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Retry", tint = Color(0xFF7C3AED), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                        }
                    }
                    is Completed -> {
                        IconButton(onClick = { onShare(ds.filePath) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { onOpen(ds.filePath) },
                            modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF7C3AED).copy(alpha = 0.14f)),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color(0xFF7C3AED), modifier = Modifier.size(18.dp))
                        }
                    }
                    else -> {
                        IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            when (ds) {
                is Running -> {
                    if (ds.progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { ds.progress.coerceIn(0f, 1f) },
                            color = if (isAudio) Color(0xFFF43F5E) else Color(0xFF7C3AED),
                            trackColor = if (isDark) Color(0xFF2C2C34) else Color(0xFFE5E7EB),
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        )
                    } else {
                        LinearProgressIndicator(
                            color = if (isAudio) Color(0xFFF43F5E) else Color(0xFF7C3AED),
                            trackColor = if (isDark) Color(0xFF2C2C34) else Color(0xFFE5E7EB),
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        )
                    }
                    if (ds.progressText.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = ds.progressText.trim(),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is FetchingInfo,
                Idle,
                ReadyWithInfo -> {
                    LinearProgressIndicator(
                        color = Color(0xFF6366F1),
                        trackColor = if (isDark) Color(0xFF2C2C34) else Color(0xFFE5E7EB),
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (ds is FetchingInfo) "Fetching media info..." else "Waiting in queue...",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is Canceled -> {
                    Text(
                        text = "Paused / Canceled",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is Error -> {
                    Text(
                        text = ds.throwable.message ?: "Download failed",
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                is Completed -> {
                    Text(
                        text = "Completed • Saved to Downloads",
                        fontSize = 12.sp,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
