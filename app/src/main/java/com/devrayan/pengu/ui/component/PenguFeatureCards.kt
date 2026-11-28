package com.devrayan.pengu.ui.component

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.devrayan.pengu.database.objects.DownloadedVideoInfo
import com.devrayan.pengu.ui.common.HapticFeedback.slightHapticFeedback
import com.devrayan.pengu.ui.common.LocalDarkTheme
import com.devrayan.pengu.util.DatabaseUtil
import com.devrayan.pengu.util.PENGU_BADGE_STYLE
import com.devrayan.pengu.util.PENGU_ISLAND_OFFSET_DP
import com.devrayan.pengu.util.PENGU_OVERLAY_ISLAND_ENABLED
import com.devrayan.pengu.util.PenguAudioManager
import com.devrayan.pengu.util.PreferenceUtil
import com.devrayan.pengu.util.PreferenceUtil.getBoolean
import com.devrayan.pengu.util.PreferenceUtil.getInt
import com.devrayan.pengu.util.PreferenceUtil.updateBoolean
import com.devrayan.pengu.util.PreferenceUtil.updateInt
import com.devrayan.pengu.util.RingtoneToolsUtil
import com.devrayan.pengu.util.STATS_TOOL_OPS
import com.devrayan.pengu.util.toFileSizeText
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Compact Activity Stats dashboard card displayed inside Settings.
 */
@Composable
fun PenguActivityStatsCard(modifier: Modifier = Modifier) {
    val isDark = LocalDarkTheme.current.isDarkTheme()
    val history by DatabaseUtil.getDownloadHistoryFlow().collectAsStateWithLifecycle(emptyList())

    var totalBytes by remember { mutableLongStateOf(0L) }
    var existingCount by remember { mutableIntStateOf(0) }
    var topSource by remember { mutableStateOf("Ready") }
    var toolOps by remember { mutableIntStateOf(STATS_TOOL_OPS.getInt()) }

    LaunchedEffect(history) {
        val (count, bytes, source) =
            withContext(Dispatchers.IO) {
                val present = history.filter { File(it.videoPath).exists() }
                val sum = present.sumOf { File(it.videoPath).length() }
                val top =
                    present
                        .groupingBy { it.extractor.ifBlank { "Pengu" } }
                        .eachCount()
                        .maxByOrNull { it.value }
                        ?.key ?: "Ready"
                Triple(present.size, sum, top)
            }
        existingCount = count
        totalBytes = bytes
        topSource = source
        toolOps = STATS_TOOL_OPS.getInt()
    }

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(22.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF1A1B24) else Color(0xFFF3F0FF)
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier.size(30.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF6366F1), Color(0xFFD946EF))
                                    )
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Pengu Activity Hub",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Top source: $topSource",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF7C3AED).copy(alpha = 0.14f),
                ) {
                    Text(
                        text = "v1.2.0",
                        color = Color(0xFF7C3AED),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatMetricTile(
                    value = "$existingCount",
                    label = "Saved Files",
                    isDark = isDark,
                    modifier = Modifier.weight(1f),
                )
                StatMetricTile(
                    value = totalBytes.toFileSizeText(),
                    label = "Library Size",
                    isDark = isDark,
                    modifier = Modifier.weight(1f),
                )
                StatMetricTile(
                    value = "$toolOps",
                    label = "Ringtones Made",
                    isDark = isDark,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatMetricTile(
    value: String,
    label: String,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) Color(0xFF252632) else Color.White,
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(
                text = value,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * Curated One-Tap Theme Presets & Pengu Mascot Badge style picker for Appearance settings.
 */
@Composable
fun PenguThemePresetsCard() {
    val view = LocalView.current
    val isDark = LocalDarkTheme.current.isDarkTheme()
    val appSettings by PreferenceUtil.AppSettingsStateFlow.collectAsStateWithLifecycle()
    var badgeStyle by remember { mutableIntStateOf(PENGU_BADGE_STYLE.getInt()) }

    val presets =
        remember {
            listOf(
                "Violet" to 0xFF7C3AED.toInt(),
                "Apple Blue" to 0xFF007AFF.toInt(),
                "Emerald" to 0xFF10B981.toInt(),
                "Rose" to 0xFFF43F5E.toInt(),
                "Obsidian" to 0xFF334155.toInt(),
            )
        }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF1C1C22) else Color(0xFFF5F6FA)
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                text = "Curated Theme Presets",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEach { (label, argb) ->
                    val selected = !appSettings.isDynamicColorEnabled && appSettings.seedColor == argb
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color =
                            if (selected) Color(argb).copy(alpha = 0.16f)
                            else if (isDark) Color(0xFF282830) else Color.White,
                        modifier =
                            Modifier.clip(RoundedCornerShape(12.dp))
                                .then(
                                    if (selected) {
                                        Modifier.border(1.5.dp, Color(argb), RoundedCornerShape(12.dp))
                                    } else Modifier
                                )
                                .clickable {
                                    view.slightHapticFeedback()
                                    PreferenceUtil.switchDynamicColor(false)
                                    PreferenceUtil.modifyThemeSeedColor(
                                        argb,
                                        appSettings.paletteStyleIndex,
                                    )
                                },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier.size(14.dp).clip(CircleShape).background(Color(argb))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "Pengu Header Badge Style",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Classic", "Violet Aura", "Minimal").forEachIndexed { idx, label ->
                    val active = badgeStyle == idx
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color =
                            if (active) Color(0xFF7C3AED)
                            else if (isDark) Color(0xFF282830) else Color.White,
                        modifier =
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable {
                                view.slightHapticFeedback()
                                badgeStyle = idx
                                PENGU_BADGE_STYLE.updateInt(idx)
                            },
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}


/**
 * Animated mascot mood badge displayed on Pengu Home Hub header.
 */
@Composable
fun PenguMascotStatusBadge(
    activeDownloadCount: Int,
    modifier: Modifier = Modifier,
) {
    val active = activeDownloadCount > 0
    val pulse by
        rememberInfiniteTransition(label = "pengu_pulse")
            .animateFloat(
                initialValue = 0.72f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(if (active) 550 else 1500),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "pulse_alpha",
            )
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color =
            if (active) Color(0xFF7C3AED).copy(alpha = 0.16f)
            else Color(0xFF10B981).copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier.size(7.dp)
                        .graphicsLayer { alpha = pulse }
                        .clip(CircleShape)
                        .background(if (active) Color(0xFF7C3AED) else Color(0xFF10B981))
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = if (active) "Surfing x$activeDownloadCount" else "Ready",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) Color(0xFF7C3AED) else Color(0xFF10B981),
            )
        }
    }
}

/**
 * Floating Bottom Mini-Player supporting horizontal swipe skip (Prev / Next),
 * embedded cover artwork, and background progress across top tabs.
 */
@Composable
fun PenguMiniPlayerBar(
    state: PenguAudioManager.PlaybackState,
    isDark: Boolean,
    onOpenPlayer: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = state.mediaInfo ?: return
    val view = LocalView.current
    var dragAccum by remember { mutableFloatStateOf(0f) }
    val artBitmap =
        remember(state.embeddedArtBytes) {
            state.embeddedArtBytes?.let { bytes ->
                runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            }
        }
    val progress =
        if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
        else 0f

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .shadow(14.dp, RoundedCornerShape(28.dp))
                .border(
                    0.8.dp,
                    if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f),
                    RoundedCornerShape(28.dp),
                )
                .pointerInput(info.id) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccum = 0f },
                        onHorizontalDrag = { _, delta -> dragAccum += delta },
                        onDragEnd = {
                            if (dragAccum < -70f) {
                                view.slightHapticFeedback()
                                PenguAudioManager.skipToNext()
                            } else if (dragAccum > 70f) {
                                view.slightHapticFeedback()
                                PenguAudioManager.skipToPrevious()
                            }
                            dragAccum = 0f
                        },
                    )
                }
                .clickable { onOpenPlayer(info.id) },
        shape = RoundedCornerShape(28.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isDark) Color(0xFF1A1A22).copy(alpha = 0.94f)
                    else Color(0xFFFFFFFF).copy(alpha = 0.95f)
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier.size(40.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFFD946EF)))
                            ),
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
                        info.thumbnailUrl.isNotBlank() ->
                            AsyncImage(
                                model = info.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        else ->
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = info.videoTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            listOfNotNull(
                                    state.artistTag.takeIf { it.isNotBlank() },
                                    "${RingtoneToolsUtil.formatClockMs(state.positionMs.toLong())} / ${RingtoneToolsUtil.formatClockMs(state.durationMs.toLong())}",
                                )
                                .joinToString(" • "),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = Color(0xFF7C3AED),
                    )
                }
                IconButton(onClick = {
                    view.slightHapticFeedback()
                    PenguAudioManager.skipToNext()
                }) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next Track",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Mini Player",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                color = Color(0xFF7C3AED),
                trackColor = if (isDark) Color(0xFF2D2D38) else Color(0xFFE5E7EB),
                modifier = Modifier.fillMaxWidth().height(3.dp),
            )
        }
    }
}

/**
 * Dialog shown when user submits a normalized URL already saved locally.
 */
@Composable
fun DuplicateLinkAlertDialog(
    existingInfo: DownloadedVideoInfo,
    onPlayExisting: () -> Unit,
    onDownloadAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Already in Downloads") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = existingInfo.videoTitle,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        "Saved via ${existingInfo.extractor.ifBlank { "Pengu" }}. Would you like to open it now or download a fresh copy?",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onPlayExisting) { Text("Play Now") }
        },
        dismissButton = {
            TextButton(onClick = onDownloadAgain) { Text("Download Again") }
        },
    )
}


/**
 * Settings item + configuration dialog for Android Dynamic Island & System-Wide Floating Overlay.
 */
@Composable
fun DynamicIslandSettingEntry() {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var overlayEnabled by remember { mutableStateOf(PENGU_OVERLAY_ISLAND_ENABLED.getBoolean()) }
    var offsetDp by remember { mutableIntStateOf(PENGU_ISLAND_OFFSET_DP.getInt()) }

    SettingItem(
        title = "System Dynamic Island & Notification",
        description =
            "System MediaStyle active • Screen Overlay ${if (overlayEnabled) "ON" else "OFF"}",
        icon = Icons.Rounded.AutoAwesome,
    ) {
        overlayEnabled = PENGU_OVERLAY_ISLAND_ENABLED.getBoolean()
        offsetDp = PENGU_ISLAND_OFFSET_DP.getInt()
        showDialog = true
    }

    if (showDialog) {
        val hasOverlayPerm = Settings.canDrawOverlays(context)
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("System Dynamic Island") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Android Lockscreen, Control Center & System Island notifications are automatically active via MediaSession & Live Updates.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Floating Island Over Other Apps", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                if (hasOverlayPerm) "Overlay permission granted"
                                else "Tap switch to grant 'Display over other apps'",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = overlayEnabled && hasOverlayPerm,
                            onCheckedChange = { checked ->
                                if (checked && !Settings.canDrawOverlays(context)) {
                                    PENGU_OVERLAY_ISLAND_ENABLED.updateBoolean(true)
                                    overlayEnabled = true
                                    Toast.makeText(
                                        context,
                                        "Enable 'Display over other apps' for Pengu Island",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                    val permIntent =
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}"),
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    runCatching { context.startActivity(permIntent) }
                                } else {
                                    overlayEnabled = checked
                                    PENGU_OVERLAY_ISLAND_ENABLED.updateBoolean(checked)
                                    com.devrayan.pengu.PenguIslandOverlayService.syncOverlay(context)
                                }
                            },
                        )
                    }

                    Column {
                        Text(
                            "Overlay Notch Offset (${if (offsetDp >= 0) "+$offsetDp" else "$offsetDp"} dp)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        )
                        androidx.compose.material3.Slider(
                            value = offsetDp.toFloat(),
                            onValueChange = {
                                val next = it.toInt().coerceIn(-16, 16)
                                offsetDp = next
                                PENGU_ISLAND_OFFSET_DP.updateInt(next)
                            },
                            valueRange = -16f..16f,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        com.devrayan.pengu.PenguIslandOverlayService.syncOverlay(context)
                        showDialog = false
                    }
                ) {
                    Text("Done")
                }
            },
        )
    }
}
