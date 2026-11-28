package com.devrayan.pengu.ui.component

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.devrayan.pengu.R
import com.devrayan.pengu.ui.common.HapticFeedback.slightHapticFeedback
import com.devrayan.pengu.ui.common.LocalDarkTheme
import com.devrayan.pengu.util.PreferenceUtil.getBoolean
import com.devrayan.pengu.util.PreferenceUtil.getString
import com.devrayan.pengu.util.PreferenceUtil.updateBoolean
import com.devrayan.pengu.util.PreferenceUtil.updateString
import com.devrayan.pengu.util.RATE_PROMPT_HISTORY
import com.devrayan.pengu.util.RATE_STARRED_OR_DISMISSED

/**
 * Manages the weekly Rate / GitHub Star prompt frequency:
 * strictly at most 2 prompts per rolling 7-day window, spaced by at least 72 hours.
 */
object PenguRateManager {

    const val GITHUB_REPO_URL = "https://github.com/Zeus756653658/Pengu"
    private const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
    private const val MIN_GAP_MS = 3L * 24L * 60L * 60L * 1000L
    private const val MAX_PROMPTS_PER_WEEK = 2

    internal fun parseRecentTimestamps(historyCsv: String, nowMs: Long): List<Long> {
        val cutoff = nowMs - SEVEN_DAYS_MS
        return historyCsv
            .split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { it in cutoff..nowMs }
            .sorted()
    }

    internal fun shouldShowRatePrompt(
        nowMs: Long,
        historyCsv: String,
        starredOrDismissed: Boolean,
        hasCompletedActivity: Boolean,
    ): Boolean {
        if (starredOrDismissed || !hasCompletedActivity) return false
        val recent = parseRecentTimestamps(historyCsv, nowMs)
        if (recent.size >= MAX_PROMPTS_PER_WEEK) return false
        val lastShown = recent.lastOrNull() ?: return true
        return (nowMs - lastShown) >= MIN_GAP_MS
    }

    fun shouldShowNow(hasCompletedActivity: Boolean, nowMs: Long = System.currentTimeMillis()): Boolean =
        shouldShowRatePrompt(
            nowMs = nowMs,
            historyCsv = RATE_PROMPT_HISTORY.getString(),
            starredOrDismissed = RATE_STARRED_OR_DISMISSED.getBoolean(),
            hasCompletedActivity = hasCompletedActivity,
        )

    fun recordPromptShown(nowMs: Long = System.currentTimeMillis()) {
        val recent = parseRecentTimestamps(RATE_PROMPT_HISTORY.getString(), nowMs)
        val updated = (recent + nowMs).takeLast(MAX_PROMPTS_PER_WEEK)
        RATE_PROMPT_HISTORY.updateString(updated.joinToString(","))
    }

    fun markStarredOrDismissed() {
        RATE_STARRED_OR_DISMISSED.updateBoolean(true)
    }

    fun openGitHubForStar(context: Context) {
        markStarredOrDismissed()
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        runCatching { context.startActivity(intent) }
    }
}

@Composable
fun RatePenguDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isDark = LocalDarkTheme.current.isDarkTheme()
    var selectedStars by remember { mutableIntStateOf(5) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = if (isDark) Color(0xFF171720) else Color.White,
            tonalElevation = 8.dp,
            shadowElevation = 18.dp,
            modifier =
                Modifier.fillMaxWidth().border(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                    RoundedCornerShape(26.dp),
                ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier.size(58.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFFD946EF)))
                            )
                            .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.pengu_logo),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Enjoying Pengu?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Tap to open Pengu on GitHub and leave a Star to support future updates!",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (star in 1..5) {
                        val filled = star <= selectedStars
                        Icon(
                            imageVector = if (filled) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "$star star",
                            tint = if (filled) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier =
                                Modifier.size(36.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        view.slightHapticFeedback()
                                        selectedStars = star
                                    }
                                    .padding(2.dp),
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = {
                        view.slightHapticFeedback()
                        PenguRateManager.openGitHubForStar(context)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1),
                            contentColor = Color.White,
                        ),
                ) {
                    Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Star on GitHub", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = {
                            PenguRateManager.markStarredOrDismissed()
                            onDismiss()
                        }
                    ) {
                        Text("Already Starred", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Later", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
