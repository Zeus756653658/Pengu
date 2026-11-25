package com.devrayan.pengu.ui.page.downloadv2

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devrayan.pengu.ui.common.HapticFeedback.slightHapticFeedback
import com.devrayan.pengu.ui.common.LocalDarkTheme
import com.devrayan.pengu.util.AUDIO_CONVERSION_FORMAT
import com.devrayan.pengu.util.AUDIO_CONVERT
import com.devrayan.pengu.util.AUDIO_QUALITY
import com.devrayan.pengu.util.CONVERT_M4A
import com.devrayan.pengu.util.CONVERT_MP3
import com.devrayan.pengu.util.DownloadUtil
import com.devrayan.pengu.util.EXTRACT_AUDIO
import com.devrayan.pengu.util.HIGH
import com.devrayan.pengu.util.LOW
import com.devrayan.pengu.util.MEDIUM
import com.devrayan.pengu.util.PreferenceUtil.getInt
import com.devrayan.pengu.util.PreferenceUtil.updateBoolean
import com.devrayan.pengu.util.PreferenceUtil.updateInt
import com.devrayan.pengu.util.RES_1080P
import com.devrayan.pengu.util.RES_2160P
import com.devrayan.pengu.util.RES_360P
import com.devrayan.pengu.util.RES_480P
import com.devrayan.pengu.util.RES_720P
import com.devrayan.pengu.util.RES_HIGHEST
import com.devrayan.pengu.util.VIDEO_QUALITY
import com.devrayan.pengu.util.findURLsFromString

private data class QualityPresetOption(
    val badge: String,
    val title: String,
    val subtitle: String,
    val isBest: Boolean = false,
    val value: Int,
    val secondaryValue: Int = 0,
)

private val VideoQualityPresets =
    listOf(
        QualityPresetOption("4K", "Ultra HD / Best", "MP4 • Up to 2160p", isBest = true, value = RES_HIGHEST),
        QualityPresetOption("1080p", "Full HD", "MP4 • Crisp high quality", value = RES_1080P),
        QualityPresetOption("720p", "HD", "MP4 • Balanced size & clarity", value = RES_720P),
        QualityPresetOption("480p", "SD", "MP4 • Data saver", value = RES_480P),
        QualityPresetOption("360p", "Low", "MP4 • Fastest download", value = RES_360P),
    )

private val AudioQualityPresets =
    listOf(
        QualityPresetOption("320k", "High Quality MP3", "MP3 • Best audio fidelity", isBest = true, value = HIGH, secondaryValue = CONVERT_MP3),
        QualityPresetOption("192k", "Standard MP3", "MP3 • Balanced file size", value = MEDIUM, secondaryValue = CONVERT_MP3),
        QualityPresetOption("128k", "Compact MP3", "MP3 • Lightweight audio", value = LOW, secondaryValue = CONVERT_MP3),
        QualityPresetOption("M4A", "M4A Audio", "M4A • Native fast container", value = HIGH, secondaryValue = CONVERT_M4A),
    )

/**
 * VidSnap Screen-2 inspired quick Video/MP3 Downloader bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VidSnapQuickSheet(
    initialAudioMode: Boolean,
    initialUrl: String = "",
    onDismiss: () -> Unit,
    onStartPresetDownload: (url: String, preferences: DownloadUtil.DownloadPreferences) -> Unit,
    onOpenFullOptions: (url: String) -> Unit,
    onOpenBrowserForSite: (siteUrl: String) -> Unit,
) {
    val isDark = LocalDarkTheme.current.isDarkTheme()
    val context = LocalContext.current
    val view = LocalView.current
    val clipboardManager = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isAudioMode by remember(initialAudioMode) { mutableStateOf(initialAudioMode) }
    var urlInput by remember(initialUrl) {
        val clipboardUrl =
            if (initialUrl.isBlank()) {
                findURLsFromString(clipboardManager.getText()?.text.orEmpty(), true).firstOrNull().orEmpty()
            } else initialUrl
        mutableStateOf(clipboardUrl)
    }
    var selectedVideoQuality by remember {
        val current = VIDEO_QUALITY.getInt()
        mutableIntStateOf(if (current == RES_2160P) RES_HIGHEST else current)
    }
    var selectedAudioIdx by remember { mutableIntStateOf(0) }

    val quickPlatforms =
        remember {
            listOf(
                Triple("YouTube", "https://m.youtube.com", Color(0xFFFF0000)),
                Triple("Instagram", "https://www.instagram.com", Color(0xFFE1306C)),
                Triple("TikTok", "https://www.tiktok.com", Color(0xFF111827)),
                Triple("Facebook", "https://m.facebook.com", Color(0xFF1877F2)),
                Triple("Twitter / X", "https://x.com", Color(0xFF1DA1F2)),
            )
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (isDark) Color(0xFF151518) else Color(0xFFFFFFFF),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp)
        ) {
            // Sheet Title + Advanced tune button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (isAudioMode) "MP3 Downloader" else "Video Downloader",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(
                    onClick = {
                        view.slightHapticFeedback()
                        val valid = findURLsFromString(urlInput.trim(), true).firstOrNull().orEmpty()
                        onDismiss()
                        onOpenFullOptions(valid)
                    }
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Custom", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Mode switcher pill (Video MP4 | MP3 Audio)
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isDark) Color(0xFF232329) else Color(0xFFF3F4F6))
                        .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(false to "Video (MP4)", true to "Audio (MP3)").forEach { (audioTab, label) ->
                    val active = isAudioMode == audioTab
                    val bg by animateColorAsState(
                        if (active) Color(0xFF7C3AED) else Color.Transparent,
                        label = "modeBg",
                    )
                    Box(
                        modifier =
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(11.dp))
                                .background(bg)
                                .clickable {
                                    view.slightHapticFeedback()
                                    isAudioMode = audioTab
                                }
                                .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (audioTab) Icons.Filled.MusicNote else Icons.Filled.Videocam,
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

            Spacer(Modifier.height(14.dp))

            // Platform quick open row
            Text(
                text = "Browse platform or paste link",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                quickPlatforms.forEach { (name, siteUrl, accent) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) Color(0xFF232329) else Color(0xFFF9FAFB),
                        modifier =
                            Modifier.border(
                                1.dp,
                                accent.copy(alpha = 0.25f),
                                RoundedCornerShape(12.dp),
                            ).clickable {
                                view.slightHapticFeedback()
                                onDismiss()
                                onOpenBrowserForSite(siteUrl)
                            },
                    ) {
                        Text(
                            text = name,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // URL input + Paste button
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDark) Color(0xFF232329) else Color(0xFFF3F4F6))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = null,
                    tint = Color(0xFF7C3AED),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (urlInput.isEmpty()) {
                        Text(
                            text = "Paste video URL here...",
                            color = Color(0xFF9CA3AF),
                            fontSize = 14.sp,
                        )
                    }
                    BasicTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        singleLine = true,
                        textStyle =
                            MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                            ),
                        cursorBrush = SolidColor(Color(0xFF7C3AED)),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF7C3AED).copy(alpha = 0.14f),
                    modifier =
                        Modifier.clickable {
                            view.slightHapticFeedback()
                            val clip = clipboardManager.getText()?.text.orEmpty()
                            val found = findURLsFromString(clip, true).firstOrNull()
                            if (!found.isNullOrBlank()) {
                                urlInput = found
                            } else if (clip.isNotBlank()) {
                                urlInput = clip.trim()
                            } else {
                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                            }
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.ContentPaste,
                            contentDescription = null,
                            tint = Color(0xFF7C3AED),
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Paste",
                            color = Color(0xFF7C3AED),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = if (isAudioMode) "Select audio quality" else "Select video quality",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(10.dp))

            if (!isAudioMode) {
                VideoQualityPresets.forEach { preset ->
                    val selected = selectedVideoQuality == preset.value
                    QualityPresetCard(
                        preset = preset,
                        selected = selected,
                        isDark = isDark,
                        onClick = {
                            view.slightHapticFeedback()
                            selectedVideoQuality = preset.value
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            } else {
                AudioQualityPresets.forEachIndexed { index, preset ->
                    val selected = selectedAudioIdx == index
                    QualityPresetCard(
                        preset = preset,
                        selected = selected,
                        isDark = isDark,
                        onClick = {
                            view.slightHapticFeedback()
                            selectedAudioIdx = index
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // Primary Gradient CTA Button
            val ctaGradient =
                if (isAudioMode) {
                    Brush.linearGradient(listOf(Color(0xFFF43F5E), Color(0xFFFB923C)))
                } else {
                    Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFFD946EF)))
                }

            Button(
                onClick = {
                    view.slightHapticFeedback()
                    val resolved =
                        findURLsFromString(urlInput.trim(), true).firstOrNull()
                            ?: urlInput.trim().takeIf {
                                it.startsWith("http://") || it.startsWith("https://")
                            }
                    if (resolved.isNullOrBlank()) {
                        Toast.makeText(context, "Please enter or paste a valid URL", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    if (!isAudioMode) {
                        EXTRACT_AUDIO.updateBoolean(false)
                        VIDEO_QUALITY.updateInt(selectedVideoQuality)
                        val prefs =
                            DownloadUtil.DownloadPreferences.createFromPreferences()
                                .copy(extractAudio = false, videoResolution = selectedVideoQuality)
                        onDismiss()
                        onStartPresetDownload(resolved, prefs)
                    } else {
                        val audioPreset = AudioQualityPresets[selectedAudioIdx]
                        EXTRACT_AUDIO.updateBoolean(true)
                        AUDIO_CONVERT.updateBoolean(true)
                        AUDIO_QUALITY.updateInt(audioPreset.value)
                        AUDIO_CONVERSION_FORMAT.updateInt(audioPreset.secondaryValue)
                        val prefs =
                            DownloadUtil.DownloadPreferences.createFromPreferences()
                                .copy(
                                    extractAudio = true,
                                    convertAudio = true,
                                    audioQuality = audioPreset.value,
                                    audioConvertFormat = audioPreset.secondaryValue,
                                )
                        onDismiss()
                        onStartPresetDownload(resolved, prefs)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(ctaGradient),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isAudioMode) "Download MP3 Audio" else "Download Video",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityPresetCard(
    preset: QualityPresetOption,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    val borderColor =
        if (selected) Color(0xFF7C3AED)
        else if (isDark) Color(0xFF2C2C34) else Color(0xFFE5E7EB)
    val bgColor =
        if (selected) Color(0xFF7C3AED).copy(alpha = if (isDark) 0.18f else 0.08f)
        else if (isDark) Color(0xFF1E1E24) else Color(0xFFFAFAFC)

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(bgColor)
                .border(width = if (selected) 1.5.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) Color(0xFF7C3AED)
                        else if (isDark) Color(0xFF2C2C34) else Color(0xFFEDE9FE)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = preset.badge,
                color = if (selected) Color.White else Color(0xFF7C3AED),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = preset.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (preset.isBest) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = "BEST",
                            color = Color(0xFF10B981),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(
                text = preset.subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (selected) Color(0xFF7C3AED) else Color(0xFF9CA3AF),
            modifier = Modifier.size(22.dp),
        )
    }
}
