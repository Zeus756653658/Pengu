package com.devrayan.pengu.ui.page.downloadv2

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devrayan.pengu.R
import com.devrayan.pengu.database.objects.DownloadedVideoInfo
import com.devrayan.pengu.ui.common.LocalDarkTheme
import com.devrayan.pengu.util.AUDIO_REGEX
import com.devrayan.pengu.util.FileUtil.getFileSize
import com.devrayan.pengu.util.toFileSizeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PlatformChip(
    val name: String,
    val iconColor: Color,
    val emoji: String,
    val browseUrl: String = "https://m.youtube.com",
)

val SupportedPlatformsList =
    listOf(
        PlatformChip("YouTube", Color(0xFFFF0000), "🔴", "https://m.youtube.com"),
        PlatformChip("Instagram", Color(0xFFE1306C), "📸", "https://www.instagram.com"),
        PlatformChip("TikTok", Color(0xFF000000), "🎵", "https://www.tiktok.com"),
        PlatformChip("Facebook", Color(0xFF1877F2), "📘", "https://m.facebook.com"),
        PlatformChip("Twitter / X", Color(0xFF1DA1F2), "🐦", "https://x.com"),
        PlatformChip("Reddit", Color(0xFFFF4500), "👽", "https://www.reddit.com"),
        PlatformChip("Vimeo", Color(0xFF1AB7EA), "🎬", "https://vimeo.com"),
        PlatformChip("SoundCloud", Color(0xFFFF5500), "🎧", "https://m.soundcloud.com"),
        PlatformChip("1,000+ Sites", Color(0xFF7C3AED), "🌐", "https://www.google.com"),
    )

/**
 * VidSnap-inspired modern media downloader home hub with vibrant gradient hero banner,
 * quick action video/audio downloader cards, browser & FFmpeg tools, supported platforms, and recent downloads.
 */
@Composable
fun VidSnapHomeHub(
    modifier: Modifier = Modifier,
    recentDownloads: List<DownloadedVideoInfo> = emptyList(),
    activeDownloadCount: Int = 0,
    onDownloadUrl: (String) -> Unit,
    onVideoDownloadClick: () -> Unit,
    onAudioDownloadClick: () -> Unit,
    onOpenBrowser: (String) -> Unit = {},
    onOpenTools: () -> Unit = {},
    onOpenPlayer: (Int) -> Unit = {},
    onNavigateToDownloads: () -> Unit,
    onMenuOpen: () -> Unit,
) {
    val isDark = LocalDarkTheme.current.isDarkTheme()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var inputUrl by remember { mutableStateOf("") }

    val heroGradient =
        Brush.linearGradient(
            colors =
                listOf(
                    Color(0xFF6366F1), // Vibrant Indigo
                    Color(0xFFD946EF), // Fuchsia / Purple
                    Color(0xFFF43F5E), // Rose / Coral
                )
        )

    val videoCardGradient =
        Brush.linearGradient(
            colors = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))
        )

    val audioCardGradient =
        Brush.linearGradient(
            colors = listOf(Color(0xFFF43F5E), Color(0xFFFB923C))
        )

    fun submitUrl() {
        val trimmed = inputUrl.trim()
        if (trimmed.isNotEmpty()) {
            onDownloadUrl(trimmed)
            inputUrl = ""
        } else {
            // Check clipboard
            val clipText = clipboardManager.getText()?.text?.trim()
            if (!clipText.isNullOrEmpty() && (clipText.startsWith("http://") || clipText.startsWith("https://"))) {
                onDownloadUrl(clipText)
                Toast.makeText(context, "Pasted link from clipboard", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Please enter or paste a valid video URL", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 1. Top Header Bar
        val badgeStyle =
            com.devrayan.pengu.util.PreferenceUtil.run {
                com.devrayan.pengu.util.PENGU_BADGE_STYLE.getInt()
            }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier.size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .then(
                            when (badgeStyle) {
                                1 -> Modifier.background(Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFFD946EF))))
                                2 -> Modifier.background(Color.Transparent)
                                else -> Modifier.background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFF3F4F6))
                            }
                        )
                        .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.pengu_logo),
                    contentDescription = "Pengu Logo",
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Welcome to",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6B7280),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Pengu",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    com.devrayan.pengu.ui.component.PenguMascotStatusBadge(
                        activeDownloadCount = activeDownloadCount
                    )
                }
            }

            IconButton(
                onClick = onMenuOpen,
                colors =
                    IconButtonDefaults.iconButtonColors(
                        containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF3F4F6)
                    ),
                modifier = Modifier.size(40.dp).clip(CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 2. Hero Gradient Card ("Download anything, anytime.")
        Card(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp), spotColor = Color(0xFF6366F1)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().background(heroGradient).padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "Download anything,\nanytime.",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        lineHeight = 28.sp,
                    )

                    Spacer(Modifier.height(18.dp))

                    // White URL input container
                    val inputBg = if (isDark) Color(0xFF1E1E22) else Color.White
                    val inputTextColor = if (isDark) Color.White else Color(0xFF111827)
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(inputBg)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = null,
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(20.dp),
                        )

                        Spacer(Modifier.width(10.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (inputUrl.isEmpty()) {
                                Text(
                                    text = "Paste video link here",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 14.sp,
                                )
                            }
                            BasicTextField(
                                value = inputUrl,
                                onValueChange = { inputUrl = it },
                                singleLine = true,
                                textStyle =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        color = inputTextColor,
                                        fontSize = 14.sp,
                                    ),
                                cursorBrush = SolidColor(Color(0xFF7C3AED)),
                                keyboardOptions =
                                    KeyboardOptions(
                                        keyboardType = KeyboardType.Uri,
                                        imeAction = ImeAction.Go,
                                    ),
                                keyboardActions = KeyboardActions(onGo = { submitUrl() }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        // Arrow submit button
                        IconButton(
                            onClick = { submitUrl() },
                            modifier =
                                Modifier.size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF6366F1), Color(0xFF7C3AED))
                                        )
                                    ),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Download",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 3. "Choose downloader" Section
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(
                text = "Choose downloader",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Video Downloader Card
                Card(
                    modifier =
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onVideoDownloadClick() }
                            .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp), spotColor = Color(0xFF6366F1)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().background(videoCardGradient).padding(16.dp)
                    ) {
                        Column {
                            Box(
                                modifier =
                                    Modifier.size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Videocam,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            Text(
                                text = "Video\nDownloader",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                lineHeight = 20.sp,
                            )

                            Spacer(Modifier.height(4.dp))

                            Text(
                                text = "MP4 • up to 4K",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                        }
                    }
                }

                // MP3 Downloader Card
                Card(
                    modifier =
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onAudioDownloadClick() }
                            .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp), spotColor = Color(0xFFF43F5E)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().background(audioCardGradient).padding(16.dp)
                    ) {
                        Column {
                            Box(
                                modifier =
                                    Modifier.size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            Text(
                                text = "MP3\nDownloader",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                lineHeight = 20.sp,
                            )

                            Spacer(Modifier.height(4.dp))

                            Text(
                                text = "Audio • 320 kbps",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Browser Downloader & FFmpeg Media Tools quick strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDark) Color(0xFF1C1C22) else Color(0xFFF3F4F6),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable {
                        onOpenBrowser("https://m.youtube.com")
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier.size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF6366F1).copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Public,
                                contentDescription = null,
                                tint = Color(0xFF6366F1),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Browser",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Reels & web",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDark) Color(0xFF1C1C22) else Color(0xFFF3F4F6),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable {
                        onOpenTools()
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier.size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFD946EF).copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = Color(0xFFD946EF),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Ringtone Maker",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Cut & set tones",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Quick Pengu Music Player Strip
            val activeMusicState by com.devrayan.pengu.util.PenguAudioManager.state.collectAsStateWithLifecycle()
            val activeMusic = activeMusicState.mediaInfo
            val latestAudio =
                remember(recentDownloads) {
                    recentDownloads.firstOrNull {
                        it.videoPath.lowercase().contains(Regex(com.devrayan.pengu.util.AUDIO_REGEX))
                    }
                }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) Color(0xFF1F162A) else Color(0xFFF3E8FF),
                modifier =
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                        val targetId = activeMusic?.id ?: latestAudio?.id
                        if (targetId != null) {
                            onOpenPlayer(targetId)
                        } else {
                            onNavigateToDownloads()
                            Toast.makeText(
                                context,
                                "Pick or download an MP3 track to launch Pengu Music Player",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier.size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFF43F5E), Color(0xFF7C3AED))
                                    )
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Audiotrack,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeMusic?.videoTitle ?: "Pengu Music Player & Dynamic Island",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        Text(
                            text =
                                if (activeMusic != null) "Tap to open Apple Blur / Expressive Player & Lyrics"
                                else if (latestAudio != null) "Play: ${latestAudio.videoTitle}"
                                else "Apple Blur • Squiggly Slider • Synced Lyrics • EQ",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFF7C3AED),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 4. "Supported platforms" Section
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Supported platforms",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier =
                    Modifier.horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SupportedPlatformsList.forEach { platform ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF3F4F6),
                        modifier =
                            Modifier.clickable {
                                val clipText = clipboardManager.getText()?.text?.trim()
                                if (!clipText.isNullOrEmpty() && (clipText.contains("http://") || clipText.contains("https://"))) {
                                    onDownloadUrl(clipText)
                                } else {
                                    onOpenBrowser(platform.browseUrl)
                                }
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = platform.emoji, fontSize = 14.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = platform.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 5. "Recent downloads" Section Header + Cards
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Recent downloads",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            TextButton(onClick = onNavigateToDownloads) {
                Text(
                    text = "See all",
                    color = Color(0xFF7C3AED),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        }

        if (recentDownloads.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                recentDownloads.forEach { item ->
                    val isAudio = item.videoPath.lowercase().contains(Regex(AUDIO_REGEX))
                    val size by
                        produceState(initialValue = 0L, item.videoPath) {
                            value = withContext(Dispatchers.IO) { item.videoPath.getFileSize() }
                        }
                    val ext = item.videoPath.substringAfterLast('.', "").uppercase()
                    val badgeGradient =
                        if (isAudio) audioCardGradient else videoCardGradient

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isDark) Color(0xFF1C1C22) else Color.White,
                        tonalElevation = if (isDark) 0.dp else 1.dp,
                        shadowElevation = if (isDark) 0.dp else 2.dp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                            onOpenPlayer(item.id)
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier.size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(badgeGradient),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector =
                                        if (isAudio) Icons.Outlined.MusicNote else Icons.Outlined.Videocam,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.videoTitle,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text =
                                        listOfNotNull(
                                                ext.takeIf { it.isNotBlank() },
                                                size.toFileSizeText(),
                                                item.extractor.takeIf { it.isNotBlank() && it != "Unknown" },
                                            )
                                            .joinToString(" • "),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier =
                                    Modifier.size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF7C3AED).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color(0xFF7C3AED),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
