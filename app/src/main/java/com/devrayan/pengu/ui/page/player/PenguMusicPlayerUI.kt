package com.devrayan.pengu.ui.page.player

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmapOrNull
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.devrayan.pengu.R
import com.devrayan.pengu.database.objects.DownloadedVideoInfo
import com.devrayan.pengu.ui.common.HapticFeedback.slightHapticFeedback
import com.devrayan.pengu.util.FileUtil
import com.devrayan.pengu.util.FileUtil.getFileSize
import com.devrayan.pengu.util.PENGU_PLAYER_STYLE
import com.devrayan.pengu.util.PENGU_SLIDER_STYLE
import com.devrayan.pengu.util.PenguAudioManager
import com.devrayan.pengu.util.PenguAudioManager.RepeatMode as PenguRepeatMode
import com.devrayan.pengu.util.PenguColorExtractor
import com.devrayan.pengu.util.PenguLyricsUtil
import com.devrayan.pengu.util.PreferenceUtil.getInt
import com.devrayan.pengu.util.PreferenceUtil.updateInt
import com.devrayan.pengu.util.RingtoneToolsUtil
import com.devrayan.pengu.util.toFileSizeText
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-screen Pengu Music Player Experience (Apple Music Blurred + Material You Expressive +
 * Synced Lyrics + Queue + Hardware Equalizer + Ringtone Maker shortcut).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PenguMusicPlayerView(
    playback: PenguAudioManager.PlaybackState,
    onNavigateBack: () -> Unit,
    onOpenTools: (String) -> Unit,
    onRequestDelete: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val info = playback.mediaInfo ?: return

    var showLyricsMode by remember { mutableStateOf(false) }
    var playerStyle by remember { mutableIntStateOf(PENGU_PLAYER_STYLE.getInt()) }
    var sliderStyle by remember { mutableIntStateOf(PENGU_SLIDER_STYLE.getInt()) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showEqSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val embeddedBitmap: Bitmap? =
        remember(playback.embeddedArtBytes) {
            playback.embeddedArtBytes?.let { bytes ->
                runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            }
        }

    val defaultColors = remember {
        listOf(Color(0xFF4C1D95), Color(0xFF831843), Color(0xFF1F1235), Color(0xFF09090F))
    }
    var paletteColors by remember { mutableStateOf(defaultColors) }
    val fallbackArgb = Color(0xFF5B21B6).toArgb()

    LaunchedEffect(info.id, embeddedBitmap, info.thumbnailUrl) {
        val extracted =
            withContext(Dispatchers.IO) {
                val bmp =
                    embeddedBitmap
                        ?: info.thumbnailUrl.takeIf { it.isNotBlank() }?.let { url ->
                            val req =
                                ImageRequest.Builder(context)
                                    .data(url)
                                    .size(96)
                                    .allowHardware(false)
                                    .build()
                            (context.imageLoader.execute(req) as? SuccessResult)
                                ?.drawable
                                ?.toBitmapOrNull()
                        }
                if (bmp != null) PenguColorExtractor.extractGradientColors(bmp, fallbackArgb)
                else defaultColors
            }
        paletteColors = extracted
    }

    val topGlow by animateColorAsState(paletteColors.getOrElse(0) { Color(0xFF4C1D95) }, tween(900), label = "glow0")
    val midGlow by animateColorAsState(paletteColors.getOrElse(1) { Color(0xFF831843) }, tween(900), label = "glow1")
    val deepBase by animateColorAsState(paletteColors.getOrElse(2) { Color(0xFF18112B) }, tween(900), label = "glow2")

    val meshTransition = rememberInfiniteTransition(label = "penguMesh")
    val meshRotation by
        meshTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(45_000, easing = LinearEasing), RepeatMode.Restart),
            label = "meshRot",
        )

    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(topGlow, midGlow, deepBase, Color(0xFF09090F)))
                )
    ) {
        if (embeddedBitmap != null) {
            Image(
                bitmap = embeddedBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier.fillMaxSize()
                        .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(95.dp) else Modifier)
                        .graphicsLayer {
                            alpha = 0.42f
                            scaleX = 1.45f
                            scaleY = 1.45f
                            rotationZ = meshRotation
                        },
            )
        } else if (info.thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = info.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier.fillMaxSize()
                        .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(95.dp) else Modifier)
                        .graphicsLayer {
                            alpha = 0.40f
                            scaleX = 1.45f
                            scaleY = 1.45f
                            rotationZ = meshRotation
                        },
            )
        }

        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops =
                                arrayOf(
                                    0.0f to Color.Black.copy(alpha = 0.34f),
                                    0.45f to Color.Black.copy(alpha = 0.24f),
                                    0.85f to Color.Black.copy(alpha = 0.62f),
                                    1.0f to Color.Black.copy(alpha = 0.80f),
                                )
                        )
                    )
        )

        Column(
            modifier =
                Modifier.fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = {
                        view.slightHapticFeedback()
                        onNavigateBack()
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.14f),
                ) {
                    Row(modifier = Modifier.padding(3.dp)) {
                        listOf("Song" to false, "Lyrics" to true).forEach { (label, lyricsTab) ->
                            val selected = showLyricsMode == lyricsTab
                            Box(
                                modifier =
                                    Modifier.clip(CircleShape)
                                        .background(
                                            if (selected) Color.White.copy(alpha = 0.24f) else Color.Transparent
                                        )
                                        .clickable {
                                            view.slightHapticFeedback()
                                            showLyricsMode = lyricsTab
                                        }
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            view.slightHapticFeedback()
                            val nextStyle = if (playerStyle == 0) 1 else 0
                            playerStyle = nextStyle
                            PENGU_PLAYER_STYLE.updateInt(nextStyle)
                            val nextSlider = if (nextStyle == 1) 1 else 0
                            sliderStyle = nextSlider
                            PENGU_SLIDER_STYLE.updateInt(nextSlider)
                            Toast.makeText(
                                context,
                                if (nextStyle == 0) "Apple Blur Style" else "Material Expressive Style",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Switch Player Style",
                            tint = Color.White,
                        )
                    }

                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreHoriz,
                                contentDescription = "More Options",
                                tint = Color.White,
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Ringtone Maker") },
                                leadingIcon = { Icon(Icons.Rounded.NotificationsActive, null) },
                                onClick = {
                                    showMoreMenu = false
                                    onOpenTools(info.videoPath)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Share Track") },
                                leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                onClick = {
                                    showMoreMenu = false
                                    FileUtil.createIntentForSharingFile(info.videoPath)?.let { intent ->
                                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Open Externally") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                onClick = {
                                    showMoreMenu = false
                                    FileUtil.openFile(info.videoPath) {
                                        Toast.makeText(context, R.string.file_unavailable, Toast.LENGTH_SHORT).show()
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Track", color = Color(0xFFEF4444)) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Color(0xFFEF4444)) },
                                onClick = {
                                    showMoreMenu = false
                                    onRequestDelete()
                                },
                            )
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = showLyricsMode,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(240)) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "penguSongLyricsSwitch",
            ) { lyricsActive ->
                if (lyricsActive) {
                    PenguLyricsFullView(
                        playback = playback,
                        embeddedBitmap = embeddedBitmap,
                        info = info,
                        sliderStyle = sliderStyle,
                        playerStyle = playerStyle,
                        onSeek = { PenguAudioManager.seekTo(it) },
                        onTogglePlay = { PenguAudioManager.togglePlayPause() },
                        onPrev = { PenguAudioManager.skipToPrevious() },
                        onNext = { PenguAudioManager.skipToNext() },
                        onExitLyrics = { showLyricsMode = false },
                    )
                } else {
                    PenguSongMainView(
                        playback = playback,
                        embeddedBitmap = embeddedBitmap,
                        info = info,
                        playerStyle = playerStyle,
                        sliderStyle = sliderStyle,
                        topGlow = topGlow,
                        onOpenLyrics = { showLyricsMode = true },
                        onOpenQueue = { showQueueSheet = true },
                        onOpenEq = { showEqSheet = true },
                        onCycleSpeed = { PenguAudioManager.cycleSpeed() },
                        onSeek = { PenguAudioManager.seekTo(it) },
                        onTogglePlay = { PenguAudioManager.togglePlayPause() },
                        onPrev = { PenguAudioManager.skipToPrevious() },
                        onNext = { PenguAudioManager.skipToNext() },
                        onToggleShuffle = { PenguAudioManager.toggleShuffle() },
                        onCycleRepeat = { PenguAudioManager.cycleRepeatMode() },
                        onCycleSliderStyle = {
                            val next = (sliderStyle + 1) % 3
                            sliderStyle = next
                            PENGU_SLIDER_STYLE.updateInt(next)
                        },
                    )
                }
            }
        }
    }

    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            PenguQueueBottomSheet(
                playback = playback,
                onSelectTrack = { idx -> PenguAudioManager.playQueueIndex(idx) },
                onToggleShuffle = { PenguAudioManager.toggleShuffle() },
                onCycleRepeat = { PenguAudioManager.cycleRepeatMode() },
            )
        }
    }

    if (showEqSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEqSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            PenguFxBottomSheet(
                playback = playback,
                playerStyle = playerStyle,
                sliderStyle = sliderStyle,
                onSelectPlayerStyle = { st ->
                    playerStyle = st
                    PENGU_PLAYER_STYLE.updateInt(st)
                },
                onSelectSliderStyle = { sl ->
                    sliderStyle = sl
                    PENGU_SLIDER_STYLE.updateInt(sl)
                },
            )
        }
    }
}

@Composable
private fun PenguSongMainView(
    playback: PenguAudioManager.PlaybackState,
    embeddedBitmap: Bitmap?,
    info: DownloadedVideoInfo,
    playerStyle: Int,
    sliderStyle: Int,
    topGlow: Color,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenEq: () -> Unit,
    onCycleSpeed: () -> Unit,
    onSeek: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onCycleSliderStyle: () -> Unit,
) {
    val view = LocalView.current
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    val artScale by
        animateFloatAsState(
            targetValue = if (playback.isPlaying) 1f else 0.92f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "artScale",
        )

    val activeLyricIdx =
        remember(playback.lyrics.syncedLines, playback.positionMs) {
            PenguLyricsUtil.findActiveLyricIndex(
                playback.lyrics.syncedLines,
                playback.positionMs.toLong(),
            )
        }
    val activeLyricText = playback.lyrics.syncedLines.getOrNull(activeLyricIdx)?.text

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(6.dp))

        Box(
            modifier =
                Modifier.fillMaxWidth(0.86f)
                    .aspectRatio(1f)
                    .graphicsLayer {
                        scaleX = artScale
                        scaleY = artScale
                    }
                    .shadow(24.dp, RoundedCornerShape(26.dp), spotColor = Color.Black)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.linearGradient(listOf(topGlow, Color(0xFF7C3AED), Color(0xFFEC4899)))
                    )
                    .pointerInput(info.id) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragAccumulator = 0f },
                            onHorizontalDrag = { _, delta -> dragAccumulator += delta },
                            onDragEnd = {
                                if (dragAccumulator < -90f) {
                                    view.slightHapticFeedback()
                                    onNext()
                                } else if (dragAccumulator > 90f) {
                                    view.slightHapticFeedback()
                                    onPrev()
                                }
                                dragAccumulator = 0f
                            },
                        )
                    },
            contentAlignment = Alignment.Center,
        ) {
            when {
                embeddedBitmap != null ->
                    Image(
                        bitmap = embeddedBitmap.asImageBitmap(),
                        contentDescription = info.videoTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                info.thumbnailUrl.isNotBlank() ->
                    AsyncImage(
                        model = info.thumbnailUrl,
                        contentDescription = info.videoTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                else ->
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(92.dp),
                    )
            }
        }

        Spacer(Modifier.height(14.dp))

        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(26.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenLyrics),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text =
                    activeLyricText
                        ?: if (playback.isLoadingLyrics) "Syncing lyrics..."
                        else if (playback.lyrics.plainText.isNotBlank()) "Tap for lyrics"
                        else "",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.videoTitle,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
                Spacer(Modifier.height(2.dp))
                val sub =
                    listOfNotNull(
                            playback.artistTag.takeIf { it.isNotBlank() },
                            playback.albumTag.takeIf { it.isNotBlank() },
                            info.extractor.takeIf { playback.artistTag.isBlank() && it.isNotBlank() },
                        )
                        .joinToString(" • ")
                        .ifBlank { info.videoPath.getFileSize().toFileSizeText() }
                Text(
                    text = sub,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PenguCircleActionButton(
                    icon = Icons.Rounded.Lyrics,
                    contentDescription = "Lyrics",
                    highlighted = playback.lyrics.syncedLines.isNotEmpty() || playback.lyrics.plainText.isNotBlank(),
                    onClick = onOpenLyrics,
                )
                PenguCircleActionButton(
                    icon = Icons.Rounded.Equalizer,
                    contentDescription = "Audio Equalizer",
                    highlighted = playback.eqEnabled,
                    onClick = onOpenEq,
                )
                PenguCircleActionButton(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = "Queue",
                    highlighted = false,
                    onClick = onOpenQueue,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        val safeDuration = playback.durationMs.coerceAtLeast(1).toFloat()
        val safePos = playback.positionMs.coerceIn(0, playback.durationMs.coerceAtLeast(1)).toFloat()

        when (sliderStyle) {
            1 ->
                PenguSquigglySlider(
                    value = safePos,
                    onValueChange = { onSeek(it.roundToInt()) },
                    valueRange = 0f..safeDuration,
                    isPlaying = playback.isPlaying,
                    colors =
                        SliderDefaults.colors(
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                            thumbColor = Color.White,
                        ),
                )
            2 ->
                Slider(
                    value = safePos,
                    onValueChange = { onSeek(it.roundToInt()) },
                    valueRange = 0f..safeDuration,
                    colors =
                        SliderDefaults.colors(
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                            thumbColor = Color.White,
                        ),
                )
            else ->
                PenguAppleSlimSlider(
                    value = safePos,
                    onValueChange = { onSeek(it.roundToInt()) },
                    valueRange = 0f..safeDuration,
                    onValueChangeFinished = {},
                    activeColor = Color.White,
                    inactiveColor = Color.White.copy(alpha = 0.26f),
                )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = RingtoneToolsUtil.formatClock((playback.positionMs / 1000L).coerceAtLeast(0L)),
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.78f),
                fontWeight = FontWeight.Medium,
            )

            val codecStr =
                listOfNotNull(
                        playback.codecLabel.takeIf { it.isNotBlank() },
                        playback.bitrateKbps.takeIf { it > 0 }?.let { "$it kbps" },
                    )
                    .joinToString(" • ")
            if (codecStr.isNotBlank()) {
                Box(
                    modifier =
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                            .clickable(onClick = onCycleSliderStyle)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = codecStr,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = Color.White.copy(alpha = 0.88f),
                    )
                }
            }

            Text(
                text = RingtoneToolsUtil.formatClock((playback.durationMs / 1000L).coerceAtLeast(0L)),
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.78f),
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(Modifier.height(16.dp))

        val playInteraction = remember { MutableInteractionSource() }
        val playPressed by playInteraction.collectIsPressedAsState()
        val playScale by animateFloatAsState(if (playPressed) 0.90f else 1f, label = "playPress")

        val cookieIndent by
            animateFloatAsState(
                targetValue = if (playback.isPlaying && playerStyle == 1) 0.08f else 0f,
                animationSpec = tween(300, easing = LinearEasing),
                label = "cookieIndent",
            )
        val rot by
            rememberInfiniteTransition(label = "flowerSpin")
                .animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
                    label = "spinDeg",
                )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                view.slightHapticFeedback()
                onToggleShuffle()
            }) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (playback.shuffleEnabled) Color.White else Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.size(22.dp),
                )
            }

            FilledIconButton(
                onClick = {
                    view.slightHapticFeedback()
                    onPrev()
                },
                shape = RoundedCornerShape(20.dp),
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor =
                            if (playerStyle == 1) Color.White.copy(alpha = 0.16f) else Color.Transparent,
                        contentColor = Color.White,
                    ),
                modifier = Modifier.size(54.dp),
            ) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(32.dp))
            }

            FilledIconButton(
                onClick = {
                    view.slightHapticFeedback()
                    onTogglePlay()
                },
                shape =
                    if (playerStyle == 1 && cookieIndent > 0f) PenguWavyShape(9, cookieIndent, rot)
                    else if (playerStyle == 1) RoundedCornerShape(24.dp)
                    else CircleShape,
                interactionSource = playInteraction,
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF14141C),
                    ),
                modifier =
                    Modifier.size(78.dp).graphicsLayer {
                        scaleX = playScale
                        scaleY = playScale
                    },
            ) {
                Icon(
                    imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playback.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(36.dp),
                )
            }

            FilledIconButton(
                onClick = {
                    view.slightHapticFeedback()
                    onNext()
                },
                shape = RoundedCornerShape(20.dp),
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor =
                            if (playerStyle == 1) Color.White.copy(alpha = 0.16f) else Color.Transparent,
                        contentColor = Color.White,
                    ),
                modifier = Modifier.size(54.dp),
            ) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(32.dp))
            }

            IconButton(onClick = {
                view.slightHapticFeedback()
                onCycleRepeat()
            }) {
                Icon(
                    imageVector =
                        if (playback.repeatMode == PenguRepeatMode.ONE) Icons.Rounded.RepeatOne
                        else Icons.Rounded.Repeat,
                    contentDescription = "Repeat Mode",
                    tint =
                        if (playback.repeatMode == PenguRepeatMode.OFF) Color.White.copy(alpha = 0.45f)
                        else Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            PenguQuickPill(
                icon = Icons.Rounded.Speed,
                label = "${playback.playbackSpeed}x",
                active = playback.playbackSpeed != 1f,
                onClick = onCycleSpeed,
            )
            val abActive = playback.abLoopStartMs != null
            PenguQuickPill(
                icon = Icons.Rounded.Repeat,
                label =
                    when {
                        playback.abLoopStartMs != null && playback.abLoopEndMs != null ->
                            "A-B ${RingtoneToolsUtil.formatClock(playback.abLoopStartMs / 1000L)}-${RingtoneToolsUtil.formatClock(playback.abLoopEndMs / 1000L)}"
                        playback.abLoopStartMs != null -> "A set • Tap B"
                        else -> "A-B Loop"
                    },
                active = abActive,
                onClick = { PenguAudioManager.setAbLoopPoint() },
            )
            PenguQuickPill(
                icon = Icons.Rounded.Bedtime,
                label =
                    if (playback.sleepRemainingMs > 0L) "${(playback.sleepRemainingMs / 60_000L) + 1}m Sleep"
                    else "Sleep",
                active = playback.sleepRemainingMs > 0L,
                onClick = onOpenEq,
            )
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun PenguLyricsFullView(
    playback: PenguAudioManager.PlaybackState,
    embeddedBitmap: Bitmap?,
    info: DownloadedVideoInfo,
    sliderStyle: Int,
    playerStyle: Int,
    onSeek: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onExitLyrics: () -> Unit,
) {
    val synced = playback.lyrics.syncedLines
    val listState = rememberLazyListState()
    val activeIdx =
        remember(synced, playback.positionMs) {
            PenguLyricsUtil.findActiveLyricIndex(synced, playback.positionMs.toLong())
        }

    LaunchedEffect(activeIdx) {
        if (activeIdx >= 0 && synced.isNotEmpty()) {
            runCatching { listState.animateScrollToItem((activeIdx - 2).coerceAtLeast(0)) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                playback.isLoadingLyrics ->
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.height(10.dp))
                        Text("Fetching lyrics...", color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp)
                    }
                synced.isNotEmpty() ->
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(vertical = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(synced) { idx, line ->
                            val isCurrent = idx == activeIdx
                            val textAlpha by animateFloatAsState(if (isCurrent) 1f else 0.38f, label = "lyrAlpha")
                            Text(
                                text = line.text,
                                fontSize = if (isCurrent) 22.sp else 18.sp,
                                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
                                lineHeight = if (isCurrent) 28.sp else 24.sp,
                                color = Color.White.copy(alpha = textAlpha),
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSeek(line.timeMs.toInt()) }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                        }
                    }
                playback.lyrics.plainText.isNotBlank() ->
                    Text(
                        text = playback.lyrics.plainText,
                        fontSize = 17.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.88f),
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 18.dp),
                    )
                else ->
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "No lyrics found for this track yet",
                            color = Color.White.copy(alpha = 0.82f),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.16f),
                            modifier = Modifier.clickable { PenguAudioManager.refreshLyrics() },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Refresh, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Retry LRCLIB Search", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier.size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                        .clickable(onClick = onExitLyrics),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    embeddedBitmap != null ->
                        Image(
                            bitmap = embeddedBitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    info.thumbnailUrl.isNotBlank() ->
                        AsyncImage(
                            model = info.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    else -> Icon(Icons.Rounded.MusicNote, null, tint = Color.White)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.videoTitle,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = playback.artistTag.ifBlank { info.extractor },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (playback.lyrics.source != "None") {
                Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = 0.12f)) {
                    Text(
                        text = playback.lyrics.source,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        val safeDuration = playback.durationMs.coerceAtLeast(1).toFloat()
        val safePos = playback.positionMs.coerceIn(0, playback.durationMs.coerceAtLeast(1)).toFloat()
        if (sliderStyle == 1) {
            PenguSquigglySlider(
                value = safePos,
                onValueChange = { onSeek(it.roundToInt()) },
                valueRange = 0f..safeDuration,
                isPlaying = playback.isPlaying,
                colors =
                    SliderDefaults.colors(
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                        thumbColor = Color.White,
                    ),
            )
        } else {
            PenguAppleSlimSlider(
                value = safePos,
                onValueChange = { onSeek(it.roundToInt()) },
                valueRange = 0f..safeDuration,
                onValueChangeFinished = {},
                activeColor = Color.White,
                inactiveColor = Color.White.copy(alpha = 0.25f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            FilledIconButton(
                onClick = onTogglePlay,
                shape = if (playerStyle == 1) RoundedCornerShape(18.dp) else CircleShape,
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF14141C),
                    ),
                modifier = Modifier.size(62.dp),
            ) {
                Icon(
                    imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }
    }
}

@Composable
private fun PenguQueueBottomSheet(
    playback: PenguAudioManager.PlaybackState,
    onSelectTrack: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Up Next Queue", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${playback.queue.size} audio tracks in Pengu library",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = CircleShape,
                    color =
                        if (playback.shuffleEnabled) Color(0xFF7C3AED).copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable(onClick = onToggleShuffle),
                ) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playback.shuffleEnabled) Color(0xFF7C3AED) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(9.dp).size(18.dp),
                    )
                }
                Surface(
                    shape = CircleShape,
                    color =
                        if (playback.repeatMode != PenguRepeatMode.OFF) Color(0xFF7C3AED).copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable(onClick = onCycleRepeat),
                ) {
                    Icon(
                        if (playback.repeatMode == PenguRepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = "Repeat",
                        tint = if (playback.repeatMode != PenguRepeatMode.OFF) Color(0xFF7C3AED) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(9.dp).size(18.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(340.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(playback.queue) { idx, track ->
                val isCurrent = idx == playback.queueIndex
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isCurrent) Color(0xFF7C3AED).copy(alpha = 0.14f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                            .clickable { onSelectTrack(idx) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isCurrent) Icons.Rounded.Equalizer else Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = if (isCurrent) Color(0xFF7C3AED) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.videoTitle,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (isCurrent) Color(0xFF7C3AED) else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track.extractor.ifBlank { "Audio" },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PenguFxBottomSheet(
    playback: PenguAudioManager.PlaybackState,
    playerStyle: Int,
    sliderStyle: Int,
    onSelectPlayerStyle: (Int) -> Unit,
    onSelectSliderStyle: (Int) -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Pengu Sound FX & Equalizer", fontSize = 18.sp, fontWeight = FontWeight.Bold)

        Column {
            Text("Player Look", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("Apple Blur" to 0, "Material You" to 1).forEach { (label, styleVal) ->
                    val active = playerStyle == styleVal
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) Color(0xFF7C3AED) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onSelectPlayerStyle(styleVal) },
                    ) {
                        Text(
                            text = label,
                            color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        )
                    }
                }
            }
        }

        Column {
            Text("Seekbar Style", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("Apple Slim" to 0, "Squiggly Wave" to 1, "Classic" to 2).forEach { (label, valIdx) ->
                    val active = sliderStyle == valIdx
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) Color(0xFF7C3AED) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onSelectSliderStyle(valIdx) },
                    ) {
                        Text(
                            text = label,
                            color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Hardware Equalizer & Bass Boost", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Real-time Android AudioFX", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = playback.eqEnabled,
                onCheckedChange = { PenguAudioManager.setEqEnabled(it) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PenguAudioManager.eqPresetLabels.forEachIndexed { idx, name ->
                val active = playback.eqEnabled && playback.eqPresetIndex == idx
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (active) Color(0xFF7C3AED) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { PenguAudioManager.selectEqPreset(idx) },
                ) {
                    Text(
                        text = name,
                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }
        }

        Column {
            Text(
                text = "Bass Boost (${playback.bassStrength / 10}%)",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            Slider(
                value = playback.bassStrength.toFloat(),
                onValueChange = {
                    if (!playback.eqEnabled) PenguAudioManager.setEqEnabled(true)
                    PenguAudioManager.setBassStrength(it.roundToInt())
                },
                valueRange = 0f..1000f,
            )
        }

        Column {
            Text("Sleep Timer", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(0 to "Off", 15 to "15m", 30 to "30m", 45 to "45m", 60 to "60m").forEach { (mins, lbl) ->
                    val active =
                        (mins == 0 && playback.sleepRemainingMs <= 0L) ||
                            (mins > 0 && playback.sleepTimerMinutes == mins && playback.sleepRemainingMs > 0L)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) Color(0xFF7C3AED) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier =
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable {
                                PenguAudioManager.setSleepTimer(mins)
                                val msg = if (mins > 0) "Sleep timer set: ${mins}m" else "Sleep timer turned off"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                    ) {
                        Text(
                            text = lbl,
                            color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PenguCircleActionButton(
    icon: ImageVector,
    contentDescription: String,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = if (highlighted) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.14f),
        modifier = Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun PenguQuickPill(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = if (active) Color.White.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.12f),
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
