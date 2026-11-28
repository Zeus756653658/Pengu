package com.devrayan.pengu.ui.page.settings.bugreport

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devrayan.pengu.bugreport.BugReportViewModel
import com.devrayan.pengu.bugreport.LocalSubmissionState
import com.devrayan.pengu.ui.component.BackButton
import com.devrayan.pengu.util.ToastUtil
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BugReportDetailPage(
    reportLocalId: String,
    onNavigateBack: () -> Unit,
    viewModel: BugReportViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val palette = rememberBugReportPalette()
    val clipboard = LocalClipboardManager.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val record = remember(state.reports, reportLocalId) {
        state.reports.firstOrNull { it.localId == reportLocalId }
    }

    Scaffold(
        containerColor = palette.canvas,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.canvas),
                title = { Text("Bug Report Details", fontWeight = FontWeight.Bold) },
                navigationIcon = { BackButton { onNavigateBack() } },
                actions = {
                    if (record != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Delete local record",
                                tint = palette.coral,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (record == null) {
            Box(
                modifier = Modifier.fillMaxSize().background(palette.canvas).padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Bug report record not found.", color = palette.textSecondary)
            }
            return@Scaffold
        }

        val needsRetry = record.resolvedSubmissionState() != LocalSubmissionState.SUBMITTED

        LazyColumn(
            modifier =
                Modifier.fillMaxSize()
                    .background(palette.canvas)
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                BugSectionCard(palette) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = record.remoteId ?: "Pending Sync",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = palette.violet,
                                )
                                record.remoteId?.let { rid ->
                                    IconButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(rid))
                                            ToastUtil.makeToast("Report ID copied")
                                        },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            Icons.Rounded.ContentCopy,
                                            contentDescription = "Copy ID",
                                            tint = palette.violet,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                }
                            }
                            BugReportStatusPill(record, palette)
                        }

                        Text(
                            text = record.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.textPrimary,
                        )
                        Text(
                            text =
                                "Category: ${record.category}  ·  Created: ${record.createdAt.substringBefore("T")}\nLast updated: ${record.updatedAt}",
                            fontSize = 12.sp,
                            color = palette.textSecondary,
                        )

                        if (needsRetry) {
                            Button(
                                onClick = { viewModel.retryReport(record.localId) },
                                enabled = !state.isSubmitting,
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = palette.violet,
                                        contentColor = Color.White,
                                    ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Retry Submission Now", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.refreshStatus(record.localId) },
                                enabled = !state.isRefreshingStatus,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (state.isRefreshingStatus) "Refreshing..."
                                    else "Refresh Status from Server",
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            if (record.developerResponse.isNotBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = palette.emerald.copy(alpha = 0.13f),
                        border = BorderStroke(1.dp, palette.emerald.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.VerifiedUser,
                                    contentDescription = null,
                                    tint = palette.emerald,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    "DEVELOPER RESPONSE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = palette.emerald,
                                )
                            }
                            Text(
                                text = record.developerResponse,
                                fontSize = 14.sp,
                                color = palette.textPrimary,
                            )
                        }
                    }
                }
            }

            item {
                BugSectionCard(palette) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DetailSection("Description", record.description, palette)
                        if (record.stepsToReproduce.isNotBlank()) {
                            DetailSection("Steps to Reproduce", record.stepsToReproduce, palette)
                        }
                        if (record.expectedBehavior.isNotBlank()) {
                            DetailSection("Expected Behavior", record.expectedBehavior, palette)
                        }
                        if (record.actualBehavior.isNotBlank()) {
                            DetailSection("Actual Behavior", record.actualBehavior, palette)
                        }
                    }
                }
            }

            if (record.attachmentPaths.isNotEmpty()) {
                item {
                    BugSectionCard(palette) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Attached Screenshots",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = palette.textPrimary,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                record.attachmentPaths.forEach { path ->
                                    AttachmentThumbnailBox(
                                        path = path,
                                        borderColor = palette.cardBorder,
                                        onRemove = null,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            record.diagnostics?.let { diag ->
                item {
                    BugSectionCard(palette, modifier = Modifier.padding(bottom = 28.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Shared Redacted Diagnostics",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = palette.textPrimary,
                            )
                            Text(
                                text =
                                    "App: Pengu ${diag.appVersion} (${diag.versionCode})\n" +
                                        "Android: ${diag.androidVersion} (API ${diag.apiLevel})\n" +
                                        "Device: ${diag.deviceManufacturer} ${diag.deviceModel}\n" +
                                        "ABIs: ${diag.supportedAbis.joinToString()}\n" +
                                        "yt-dlp: ${diag.ytDlpVersion}" +
                                        if (diag.crashStackTrace.isNotBlank())
                                            "\n\nStack Trace (Redacted):\n${diag.crashStackTrace}"
                                        else "",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = palette.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm && record != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Local Report Record?") },
            text = { Text("This deletes the local copy and cached screenshots from your device.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLocalRecord(record.localId)
                        showDeleteConfirm = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.coral),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DetailSection(title: String, body: String, palette: BugReportPalette) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title.uppercase(),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp,
            color = palette.violet,
        )
        Text(text = body, fontSize = 14.sp, lineHeight = 20.sp, color = palette.textPrimary)
    }
}
