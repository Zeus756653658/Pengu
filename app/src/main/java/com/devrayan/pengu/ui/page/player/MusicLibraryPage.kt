package com.devrayan.pengu.ui.page.player

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.devrayan.pengu.ui.common.HapticFeedback.slightHapticFeedback
import com.devrayan.pengu.ui.common.LocalDarkTheme
import com.devrayan.pengu.util.AUDIO_REGEX
import com.devrayan.pengu.util.DatabaseUtil
import com.devrayan.pengu.util.FileUtil.getFileSize
import com.devrayan.pengu.util.PenguAudioManager
import com.devrayan.pengu.util.RingtoneToolsUtil
import com.devrayan.pengu.util.toFileSizeText
import java.io.File

/**
 * Top-level Music Player hub accessible directly from the main bottom pill bar between
 * Downloads and Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicLibraryPage(
    onOpenPlayer: (Int) -> Unit,
    onOpenRingtoneMaker: (String) -> Unit,
    onNavigateToDownloads: () -> Unit,
) {
    val isDark = LocalDarkTheme.current.isDarkTheme()
    val view = LocalView.current
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val history by DatabaseUtil.getDownloadHistoryFlow().collectAsStateWithLifecycle(emptyList())
    val audioTracks =
        remember(history) {
            history.reversed().filter { item ->
                val f = File(item.videoPath)
                f.exists() && item.videoPath.lowercase().contains(Regex(AUDIO_REGEX))
            }
        }
    val playback by PenguAudioManager.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Music",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            view.slightHapticFeedback()
                            onOpenRingtoneMaker(playback.mediaInfo?.videoPath.orEmpty())
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = "Ringtone Maker",
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                    ),
                windowInsets = WindowInsets.statusBars,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding() + 6.dp,
                    bottom = 112.dp + navBottomInset,
                    start = 16.dp,
                    end = 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Active Now Playing Card OR Hero Launch Card
            item {
                val activeInfo = playback.mediaInfo
                val artBitmap =
                    remember(playback.embeddedArtBytes) {
                        playback.embeddedArtBytes?.let { bytes ->
                            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                                .getOrNull()
                        }
                    }

                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF4F46E5),
                                        Color(0xFF7C3AED),
                                        Color(0xFFEC4899),
                                    )
                                )
                            )
                            .clickable {
                                val targetId = activeInfo?.id ?: audioTracks.firstOrNull()?.id
                                if (targetId != null) {
                                    view.slightHapticFeedback()
                                    onOpenPlayer(targetId)
                                }
                            }
                            .padding(18.dp)
                ) {
                    if (activeInfo != null && playback.isAudioOnly) {
                        val progress =
                            if (playback.durationMs > 0) {
                                (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier =
                                        Modifier.size(58.dp)
                                            .clip(RoundedCornerShape(15.dp))
                                            .background(Color.White.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    when {
                                        artBitmap != null ->
                                            Image(
                                                bitmap = artBitmap.asImageBitmap(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        activeInfo.thumbnailUrl.isNotBlank() ->
                                            AsyncImage(
                                                model = activeInfo.thumbnailUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        else ->
                                            Icon(
                                                Icons.Rounded.MusicNote,
                                                null,
                                                tint = Color.White,
                                                modifier = Modifier.size(28.dp),
                                            )
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "NOW PLAYING",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = Color.White.copy(alpha = 0.8f),
                                    )
                                    Text(
                                        text = activeInfo.videoTitle,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text =
                                            listOfNotNull(
                                                    playback.artistTag.takeIf { it.isNotBlank() },
                                                    "${RingtoneToolsUtil.formatClockMs(playback.positionMs.toLong())} / ${RingtoneToolsUtil.formatClockMs(playback.durationMs.toLong())}",
                                                )
                                                .joinToString(" • "),
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.82f),
                                        maxLines = 1,
                                    )
                                }
                            }

                            LinearProgressIndicator(
                                progress = { progress },
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.25f),
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(onClick = { PenguAudioManager.skipToPrevious() }) {
                                        Icon(Icons.Rounded.SkipPrevious, "Previous", tint = Color.White)
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White,
                                        modifier =
                                            Modifier.size(42.dp)
                                                .clip(CircleShape)
                                                .clickable { PenguAudioManager.togglePlayPause() },
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector =
                                                    if (playback.isPlaying) Icons.Rounded.Pause
                                                    else Icons.Rounded.PlayArrow,
                                                contentDescription = null,
                                                tint = Color(0xFF4F46E5),
                                            )
                                        }
                                    }
                                    IconButton(onClick = { PenguAudioManager.skipToNext() }) {
                                        Icon(Icons.Rounded.SkipNext, "Next", tint = Color.White)
                                    }
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.22f),
                                    modifier =
                                        Modifier.clip(CircleShape).clickable {
                                            onOpenPlayer(activeInfo.id)
                                        },
                                ) {
                                    Text(
                                        text = "Full Player",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Pengu Music Player",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                            )
                            Text(
                                text =
                                    if (audioTracks.isEmpty())
                                        "Download any MP3 or audio track to listen with Lyrics, Equalizer & Live Controls."
                                    else "${audioTracks.size} tracks ready • Apple Blur & Material Expressive UI",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.88f),
                            )
                        }
                    }
                }
            }

            // Quick Actions Row
            if (audioTracks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {
                                view.slightHapticFeedback()
                                val first = audioTracks.first()
                                if (!PenguAudioManager.state.value.shuffleEnabled) {
                                    PenguAudioManager.toggleShuffle()
                                }
                                PenguAudioManager.loadAndPlay(first, preserveQueue = false)
                                onOpenPlayer(first.id)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF6366F1),
                                    contentColor = Color.White,
                                ),
                        ) {
                            Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Shuffle All", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                view.slightHapticFeedback()
                                onOpenRingtoneMaker(
                                    playback.mediaInfo?.videoPath ?: audioTracks.first().videoPath
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Rounded.NotificationsActive, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Ringtone Maker", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (audioTracks.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isDark) Color(0xFF181822) else Color(0xFFF8FAFC),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Rounded.MusicNote,
                                null,
                                tint = Color(0xFF7C3AED),
                                modifier = Modifier.size(44.dp),
                            )
                            Text(
                                text = "No audio tracks downloaded yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Use MP3 Downloader on Home or check your Downloads library.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = onNavigateToDownloads) {
                                    Text("Downloads")
                                }
                                Button(
                                    onClick = { onOpenRingtoneMaker("") },
                                    colors =
                                        ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                ) {
                                    Text("Ringtone Maker")
                                }
                            }
                        }
                    }
                }
            } else {
                items(audioTracks, key = { it.id }) { track ->
                    val isCurrent = playback.mediaInfo?.id == track.id
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color =
                            when {
                                isCurrent && isDark -> Color(0xFF251E38)
                                isCurrent -> Color(0xFFF3E8FF)
                                isDark -> Color(0xFF181822)
                                else -> Color(0xFFF8FAFC)
                            },
                        modifier =
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                                view.slightHapticFeedback()
                                onOpenPlayer(track.id)
                            },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier.size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF6366F1), Color(0xFFD946EF))
                                            )
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (track.thumbnailUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = track.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Icon(
                                        imageVector =
                                            if (isCurrent) Icons.Rounded.Equalizer
                                            else Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White,
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.videoTitle,
                                    fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color =
                                        if (isCurrent) Color(0xFF7C3AED)
                                        else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text =
                                        listOfNotNull(
                                                track.videoAuthor.takeIf { it.isNotBlank() && it != "Author" },
                                                track.videoPath.getFileSize().toFileSizeText(),
                                            )
                                            .joinToString(" • "),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = {
                                    view.slightHapticFeedback()
                                    if (isCurrent) {
                                        PenguAudioManager.togglePlayPause()
                                    } else {
                                        PenguAudioManager.loadAndPlay(track, preserveQueue = false)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector =
                                        if (isCurrent && playback.isPlaying) Icons.Rounded.Pause
                                        else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color(0xFF7C3AED),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
