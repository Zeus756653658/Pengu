package com.devrayan.pengu.ui.page

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devrayan.pengu.R
import com.devrayan.pengu.ui.common.LocalDarkTheme
import com.devrayan.pengu.ui.component.ReleaseNoteBlocksColumn
import com.devrayan.pengu.ui.component.parseReleaseMarkdownBlocks
import com.devrayan.pengu.util.ToastUtil
import com.devrayan.pengu.util.UpdateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog(onDismissRequest: () -> Unit, release: UpdateUtil.Release) {
    var currentDownloadStatus by remember {
        mutableStateOf(UpdateUtil.DownloadStatus.NotYet as UpdateUtil.DownloadStatus)
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            UpdateUtil.installLatestApk()
        }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                UpdateUtil.installLatestApk()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    settingsLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}"),
                        )
                    )
                } else {
                    UpdateUtil.installLatestApk()
                }
            }
        }

    val launchInstaller: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (context.packageManager.canRequestPackageInstalls()) {
                UpdateUtil.installLatestApk()
            } else {
                permissionLauncher.launch(Manifest.permission.REQUEST_INSTALL_PACKAGES)
            }
        } else {
            UpdateUtil.installLatestApk()
        }
    }

    UpdateDialogImpl(
        onDismissRequest = onDismissRequest,
        title = release.name?.takeIf { it.isNotBlank() } ?: release.tagName.orEmpty(),
        onConfirmUpdate = {
            scope.launch(Dispatchers.IO) {
                runCatching {
                        UpdateUtil.downloadApk(release = release).collect { downloadStatus ->
                            currentDownloadStatus = downloadStatus
                        }
                    }
                    .onFailure {
                        it.printStackTrace()
                        currentDownloadStatus = UpdateUtil.DownloadStatus.NotYet
                        ToastUtil.makeToastSuspend(
                            it.message?.takeIf { m -> m.isNotBlank() }
                                ?: context.getString(R.string.app_update_failed)
                        )
                        return@launch
                    }
            }
        },
        releaseNote = release.body.orEmpty(),
        downloadStatus = currentDownloadStatus,
        onConfirmInstall = launchInstaller,
    )
}

@Composable
fun UpdateDialogImpl(
    onDismissRequest: () -> Unit,
    title: String,
    onConfirmUpdate: () -> Unit,
    releaseNote: String,
    downloadStatus: UpdateUtil.DownloadStatus,
    onConfirmInstall: (() -> Unit)? = null,
) {
    val isDark = LocalDarkTheme.current.isDarkTheme()
    val blocks = remember(releaseNote) { parseReleaseMarkdownBlocks(releaseNote) }
    val versionPill =
        remember(title) {
            Regex("""v?\d+\.\d+\.\d+(?:[-.\w]+)?""").find(title)?.value?.let {
                if (it.startsWith("v", ignoreCase = true)) it else "v$it"
            } ?: title.ifBlank { "Latest" }
        }
    val isDownloading = downloadStatus is UpdateUtil.DownloadStatus.Progress
    val isVerifying = downloadStatus is UpdateUtil.DownloadStatus.Verifying
    val finishedStatus = downloadStatus as? UpdateUtil.DownloadStatus.Finished
    val percent = (downloadStatus as? UpdateUtil.DownloadStatus.Progress)?.percent ?: 0

    Dialog(
        onDismissRequest = { if (!isDownloading && !isVerifying) onDismissRequest() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier.fillMaxWidth(0.92f)
                    .heightIn(max = 650.dp)
                    .border(
                        width = 1.dp,
                        color =
                            if (isDark) Color.White.copy(alpha = 0.09f)
                            else Color.Black.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(28.dp),
                    ),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 8.dp,
            shadowElevation = 18.dp,
            color = if (isDark) Color(0xFF17171F) else Color(0xFFFFFFFF),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF5B5CE8),
                                        Color(0xFF8B5CF6),
                                        Color(0xFFD946EF),
                                    )
                                )
                            )
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier.size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White)
                                    .padding(5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.pengu_logo),
                                contentDescription = null,
                                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "UPDATE AVAILABLE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = title.ifBlank { "Pengu Update" },
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.22f),
                        ) {
                            Text(
                                text = versionPill,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }

                Column(
                    modifier =
                        Modifier.weight(1f, fill = false)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    if (blocks.isEmpty()) {
                        Text(
                            text = "A new update for Pengu is ready to install.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        ReleaseNoteBlocksColumn(blocks = blocks, isDark = isDark)
                    }
                }

                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .background(if (isDark) Color(0xFF1D1D27) else Color(0xFFF8FAFC))
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    if (isDownloading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Downloading APK over HTTPS...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "$percent%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6366F1),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (percent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = Color(0xFF7C3AED),
                        )
                        Spacer(Modifier.height(12.dp))
                    } else if (isVerifying) {
                        Text(
                            text = "Verifying SHA-256 & signing certificate...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = Color(0xFF7C3AED),
                        )
                        Spacer(Modifier.height(12.dp))
                    } else if (finishedStatus != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.14f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(
                                    text =
                                        if (finishedStatus.certVerified)
                                            "Verified: SHA-256 & Signing Certificate OK"
                                        else "Verified: SHA-256 Checksum OK",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981),
                                )
                                if (finishedStatus.sha256Hex.isNotBlank()) {
                                    Text(
                                        text = "SHA-256: ${finishedStatus.sha256Hex.take(24)}...",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(
                                text = stringResource(id = R.string.dismiss),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        Button(
                            onClick = {
                                when {
                                    isDownloading || isVerifying -> Unit
                                    finishedStatus != null ->
                                        onConfirmInstall?.invoke() ?: onConfirmUpdate()
                                    else -> onConfirmUpdate()
                                }
                            },
                            enabled = !isDownloading && !isVerifying,
                            modifier = Modifier.weight(1.25f).height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor =
                                        if (finishedStatus != null) Color(0xFF10B981)
                                        else Color(0xFF6366F1),
                                    contentColor = Color.White,
                                ),
                        ) {
                            Text(
                                text =
                                    when {
                                        isDownloading -> "$percent %"
                                        isVerifying -> "Verifying..."
                                        finishedStatus != null -> "Install Update"
                                        else -> stringResource(R.string.update)
                                    },
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.animateContentSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun Preview() {
    var b by remember { mutableStateOf(false) }
    val flow: MutableStateFlow<UpdateUtil.DownloadStatus> = remember {
        MutableStateFlow(UpdateUtil.DownloadStatus.NotYet)
    }

    LaunchedEffect(b) {
        if (b) {
            repeat(100) { i ->
                flow.update { UpdateUtil.DownloadStatus.Progress(percent = i) }
                delay(50)
            }
        } else {
            flow.update { UpdateUtil.DownloadStatus.NotYet }
        }
    }

    val status by flow.collectAsStateWithLifecycle()

    UpdateDialogImpl(
        onDismissRequest = { b = false },
        title = "Pengu - 1.2.0",
        onConfirmUpdate = { b = true },
        releaseNote = "## 1. About\n**Pengu** built with `yt-dlp`.\n\n## 2. Add\n- **Pengu Music Player**: Synced lyrics.",
        downloadStatus = status,
    )
}
