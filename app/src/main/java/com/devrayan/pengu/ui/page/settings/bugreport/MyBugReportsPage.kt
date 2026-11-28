package com.devrayan.pengu.ui.page.settings.bugreport

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SyncProblem
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devrayan.pengu.bugreport.BugReportStatus
import com.devrayan.pengu.bugreport.BugReportViewModel
import com.devrayan.pengu.bugreport.LocalBugReportRecord
import com.devrayan.pengu.bugreport.LocalSubmissionState
import com.devrayan.pengu.ui.component.BackButton
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBugReportsPage(
    onNavigateBack: () -> Unit,
    onOpenNewReport: () -> Unit,
    onOpenDetail: (String) -> Unit,
    viewModel: BugReportViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val palette = rememberBugReportPalette()
    var filterTab by rememberSaveable { mutableIntStateOf(0) }
    var pendingDeleteTarget by remember { mutableStateOf<LocalBugReportRecord?>(null) }

    val filters = listOf("All", "Pending Retry", "Open", "In Progress", "Fixed")
    val filtered =
        remember(state.reports, filterTab) {
            when (filterTab) {
                1 ->
                    state.reports.filter {
                        it.resolvedSubmissionState() != LocalSubmissionState.SUBMITTED
                    }
                2 -> state.reports.filter { it.resolvedStatus() == BugReportStatus.OPEN }
                3 ->
                    state.reports.filter {
                        it.resolvedStatus() == BugReportStatus.IN_PROGRESS ||
                            it.resolvedStatus() == BugReportStatus.REVIEWING
                    }
                4 -> state.reports.filter { it.resolvedStatus() == BugReportStatus.FIXED }
                else -> state.reports
            }
        }

    Scaffold(
        containerColor = palette.canvas,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.canvas),
                title = { Text("My Bug Reports", fontWeight = FontWeight.Bold) },
                navigationIcon = { BackButton { onNavigateBack() } },
                actions = {
                    IconButton(onClick = onOpenNewReport) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = "New Bug Report",
                            tint = palette.violet,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().background(palette.canvas).padding(padding)
        ) {
            state.errorMessage?.let { err ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = palette.coral.copy(alpha = 0.14f),
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { viewModel.clearBanners() },
                ) {
                    Text(
                        err,
                        color = palette.coral,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            state.infoMessage?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = palette.emerald.copy(alpha = 0.14f),
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { viewModel.clearBanners() },
                ) {
                    Text(
                        msg,
                        color = palette.emerald,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filters.forEachIndexed { idx, label ->
                    val active = filterTab == idx
                    Surface(
                        shape = CircleShape,
                        color = if (active) palette.violet else palette.card,
                        border = BorderStroke(1.dp, if (active) palette.violet else palette.cardBorder),
                        modifier = Modifier.height(38.dp).clickable { filterTab = idx },
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) Color.White else palette.textPrimary,
                            )
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Rounded.BugReport,
                            contentDescription = null,
                            tint = palette.violet.copy(alpha = 0.7f),
                            modifier = Modifier.size(56.dp),
                        )
                        Text(
                            text =
                                if (state.reports.isEmpty()) "No Bug Reports Submitted Yet"
                                else "No reports match this filter",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary,
                        )
                        Text(
                            text =
                                "Bug reports you submit—or offline drafts waiting to retry—will appear here with real-time status tracking.",
                            fontSize = 13.sp,
                            color = palette.textSecondary,
                        )
                        Button(
                            onClick = onOpenNewReport,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = palette.violet,
                                    contentColor = Color.White,
                                ),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("Create Bug Report", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filtered, key = { it.localId }) { record ->
                        BugReportListCard(
                            record = record,
                            palette = palette,
                            isBusy = state.isSubmitting || state.isRefreshingStatus,
                            onClick = { onOpenDetail(record.localId) },
                            onRetry = { viewModel.retryReport(record.localId) },
                            onRefresh = { viewModel.refreshStatus(record.localId) },
                            onDeleteRequest = { pendingDeleteTarget = record },
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    pendingDeleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTarget = null },
            title = { Text("Delete Local Report Record?") },
            text = {
                Text(
                    "Remove \"${target.title}\" and its cached screenshots from this device? Submitted server reports keep their remote ID."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLocalRecord(target.localId)
                        pendingDeleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.coral),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
internal fun BugReportStatusPill(
    record: LocalBugReportRecord,
    palette: BugReportPalette,
) {
    val subState = record.resolvedSubmissionState()
    val (label, color, icon) =
        if (subState != LocalSubmissionState.SUBMITTED) {
            Triple(
                if (subState == LocalSubmissionState.PENDING_RETRY) "Pending Retry" else "Failed",
                if (subState == LocalSubmissionState.PENDING_RETRY) palette.amber else palette.coral,
                Icons.Rounded.SyncProblem,
            )
        } else {
            when (record.resolvedStatus()) {
                BugReportStatus.FIXED -> Triple("Fixed", palette.emerald, Icons.Rounded.CheckCircle)
                BugReportStatus.IN_PROGRESS ->
                    Triple("In Progress", palette.cyan, Icons.Rounded.Schedule)
                BugReportStatus.REVIEWING ->
                    Triple("Reviewing", palette.amber, Icons.Rounded.Schedule)
                BugReportStatus.REJECTED ->
                    Triple("Rejected", palette.coral, Icons.Rounded.ErrorOutline)
                BugReportStatus.CLOSED ->
                    Triple("Closed", palette.textSecondary, Icons.Rounded.CheckCircle)
                BugReportStatus.OPEN -> Triple("Open", palette.violet, Icons.Rounded.BugReport)
            }
        }

    BugPillBadge(label = label, color = color, icon = icon)
}

@Composable
internal fun BugPillBadge(label: String, color: Color, icon: ImageVector) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}

@Composable
private fun BugReportListCard(
    record: LocalBugReportRecord,
    palette: BugReportPalette,
    isBusy: Boolean,
    onClick: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    val needsRetry = record.resolvedSubmissionState() != LocalSubmissionState.SUBMITTED
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = palette.card,
        modifier =
            Modifier.fillMaxWidth()
                .border(1.dp, palette.cardBorder, RoundedCornerShape(18.dp))
                .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = record.remoteId ?: "Saved Offline",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.violet,
                )
                BugReportStatusPill(record, palette)
            }

            Text(
                text = record.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.5.sp,
                color = palette.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text =
                    "${record.category}  ·  ${record.createdAt.substringBefore("T").ifBlank { "Draft" }}" +
                        if (record.attachmentCount > 0) "  ·  ${record.attachmentCount} screenshot(s)"
                        else "",
                fontSize = 12.sp,
                color = palette.textSecondary,
            )

            if (needsRetry && !record.lastError.isNullOrBlank()) {
                Text(
                    text = record.lastError,
                    fontSize = 11.5.sp,
                    color = palette.coral,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (needsRetry) {
                        OutlinedButton(
                            onClick = onRetry,
                            enabled = !isBusy,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Retry", fontSize = 12.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onRefresh,
                            enabled = !isBusy,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Sync Status", fontSize = 12.sp)
                        }
                    }
                    IconButton(onClick = onDeleteRequest) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Delete local record",
                            tint = palette.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.textSecondary)
            }
        }
    }
}
