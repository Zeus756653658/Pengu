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
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devrayan.pengu.R
import com.devrayan.pengu.ui.common.LocalDarkTheme

data class PlatformChip(val name: String, val iconColor: Color, val emoji: String)

val SupportedPlatformsList =
    listOf(
        PlatformChip("YouTube", Color(0xFFFF0000), "🔴"),
        PlatformChip("Instagram", Color(0xFFE1306C), "📸"),
        PlatformChip("TikTok", Color(0xFF000000), "🎵"),
        PlatformChip("Facebook", Color(0xFF1877F2), "📘"),
        PlatformChip("Twitter / X", Color(0xFF1DA1F2), "🐦"),
        PlatformChip("Reddit", Color(0xFFFF4500), "👽"),
        PlatformChip("Vimeo", Color(0xFF1AB7EA), "🎬"),
        PlatformChip("SoundCloud", Color(0xFFFF5500), "🎧"),
        PlatformChip("1,000+ Sites", Color(0xFF7C3AED), "🌐"),
    )

/**
 * VidSnap-inspired modern media downloader home hub with vibrant gradient hero banner,
 * quick action video/audio downloader cards, supported platforms, and recent tasks.
 */
@Composable
fun VidSnapHomeHub(
    modifier: Modifier = Modifier,
    onDownloadUrl: (String) -> Unit,
    onVideoDownloadClick: () -> Unit,
    onAudioDownloadClick: () -> Unit,
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier.size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFF3F4F6))
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
                Text(
                    text = "Pengu",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
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
                                    Toast.makeText(context, "Copy a ${platform.name} link to download", Toast.LENGTH_SHORT).show()
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

        // 5. "Recent downloads" Section Header
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
    }
}
