package com.devrayan.pengu.ui.page.settings.bugreport

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devrayan.pengu.bugreport.BugCategory
import com.devrayan.pengu.bugreport.BugReportLimits
import com.devrayan.pengu.bugreport.BugReportViewModel
import com.devrayan.pengu.bugreport.LocalSubmissionState
import com.devrayan.pengu.ui.common.LocalDarkTheme
import com.devrayan.pengu.ui.component.BackButton
import com.devrayan.pengu.util.ToastUtil
import com.devrayan.pengu.util.UpdateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

/**
 * Better-UI-UX harmonized tokens matching Pengu Beta & Settings screens
 * (60-30-10 surface hierarchy, WCAG AA/AAA text contrast, desaturated dark neutrals).
 */
internal data class BugReportPalette(
    val canvas: Color,
    val card: Color,
    val cardBorder: Color,
    val softTint: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val violet: Color,
    val cyan: Color,
    val emerald: Color,
    val amber: Color,
    val coral: Color,
)

@Composable
internal fun rememberBugReportPalette(): BugReportPalette {
    val isDark = LocalDarkTheme.current.isDarkTheme()
    return remember(isDark) {
        if (isDark) {
            BugReportPalette(
                canvas = Color(0xFF0B0F19),
                card = Color(0xFF131929),
                cardBorder = Color(0xFF232C43),
                softTint = Color(0xFF1A2238),
                textPrimary = Color(0xFFF8FAFC),
                textSecondary = Color(0xFF94A3B8),
                violet = Color(0xFF8B5CF6),
                cyan = Color(0xFF06B6D4),
                emerald = Color(0xFF10B981),
                amber = Color(0xFFF59E0B),
                coral = Color(0xFFF87171),
            )
        } else {
            BugReportPalette(
                canvas = Color(0xFFF8FAFC),
                card = Color(0xFFFFFFFF),
                cardBorder = Color(0xFFE2E8F0),
                softTint = Color(0xFFF5F3FF),
                textPrimary = Color(0xFF0F172A),
                textSecondary = Color(0xFF475569),
                violet = Color(0xFF6D28D9),
                cyan = Color(0xFF0891B2),
                emerald = Color(0xFF059669),
                amber = Color(0xFFD97706),
                coral = Color(0xFFDC2626),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BugReportPage(
    onNavigateBack: () -> Unit,
    onNavigateToMyReports: () -> Unit,
    viewModel: BugReportViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val palette = rememberBugReportPalette()
    val clipboard = LocalClipboardManager.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var showEndpointPanel by rememberSaveable { mutableStateOf(state.serverUrl.isBlank()) }
    var endpointDraft by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }
    var showDiagnosticsPreview by rememberSaveable { mutableStateOf(false) }

    val endpointUrlError =
        remember(endpointDraft) {
            val cleaned = endpointDraft.trim()
            if (cleaned.isNotEmpty() && !UpdateUtil.isHttpsUrl(cleaned)) {
                "Security requirement: Server URL must use https://"
            } else null
        }

    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            uris.forEach { uri -> viewModel.addAttachmentUri(uri) }
        }

    val pendingCount =
        remember(state.reports) {
            state.reports.count { it.resolvedSubmissionState() != LocalSubmissionState.SUBMITTED }
        }

    Scaffold(
        containerColor = palette.canvas,
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = palette.canvas,
                        scrolledContainerColor = palette.card,
                    ),
                title = {
                    Text(
                        "Report a Bug",
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.textPrimary,
                    )
                },
                navigationIcon = { BackButton { onNavigateBack() } },
                actions = {
                    Surface(
                        shape = CircleShape,
                        color = palette.softTint,
                        border = BorderStroke(1.dp, palette.cardBorder),
                        modifier =
                            Modifier.padding(end = 4.dp)
                                .clickable { onNavigateToMyReports() },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Rounded.History,
                                contentDescription = null,
                                tint = palette.violet,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                "My Reports (${state.reports.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary,
                            )
                        }
                    }
                    IconButton(onClick = { showEndpointPanel = !showEndpointPanel }) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = "Server Settings",
                            tint = palette.textSecondary,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier.fillMaxSize()
                    .background(palette.canvas)
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header Card
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFF4338CA),
                                            Color(0xFF6D28D9),
                                            Color(0xFF7C3AED),
                                        )
                                    )
                                )
                                .padding(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.BugReport,
                                    contentDescription = null,
                                    tint = Color.White,
                                )
                                Text(
                                    "PENGU BUG TRACKER",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp,
                                )
                            }
                            Text(
                                "Help make Pengu rock-solid",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                "Every submission generates a private Report ID to track developer review & status. Failed sends stay saved locally for offline retry.",
                                color = Color.White.copy(alpha = 0.88f),
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }
            }

            // Unique Report ID Success Banner
            state.lastSubmittedReportId?.let { assignedId ->
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = palette.emerald.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, palette.emerald.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = palette.emerald,
                                )
                                Text(
                                    "Report Submitted Successfully",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = palette.emerald,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Report ID: $assignedId",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.textPrimary,
                                )
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(assignedId))
                                        ToastUtil.makeToast("Report ID copied")
                                    }
                                ) {
                                    Icon(
                                        Icons.Rounded.ContentCopy,
                                        contentDescription = "Copy Report ID",
                                        tint = palette.emerald,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = onNavigateToMyReports,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Track Live Status in My Bug Reports")
                            }
                        }
                    }
                }
            }

            // Error / Info / Pending Retry Banners
            state.errorMessage?.let { err ->
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = palette.coral.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, palette.coral.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.clearBanners() },
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = palette.coral)
                            Text(err, fontSize = 13.sp, color = palette.coral, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            if (pendingCount > 0) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = palette.amber.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, palette.amber.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth().clickable { onNavigateToMyReports() },
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.Info, contentDescription = null, tint = palette.amber)
                            Text(
                                "$pendingCount unsent report(s) preserved locally — tap to retry submission.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = palette.textPrimary,
                            )
                        }
                    }
                }
            }

            // HTTPS Server Config Card (collapsible)
            if (showEndpointPanel) {
                item {
                    BugSectionCard(palette) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = palette.emerald,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    "Backend Endpoint (HTTPS Required)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = palette.textPrimary,
                                )
                            }
                            OutlinedTextField(
                                value = endpointDraft,
                                onValueChange = { endpointDraft = it },
                                label = { Text("Bug Report Server URL") },
                                placeholder = { Text("https://api.example.com") },
                                isError = endpointUrlError != null,
                                supportingText = {
                                    endpointUrlError?.let { Text(it, color = palette.coral, fontSize = 11.sp) }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.saveServerUrl(endpointDraft) },
                                    enabled = endpointUrlError == null,
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = palette.violet,
                                            contentColor = Color.White,
                                        ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(46.dp),
                                ) {
                                    Text("Save HTTPS URL", fontWeight = FontWeight.Bold)
                                }
                                if (state.serverUrl.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = { showEndpointPanel = false },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(46.dp),
                                    ) {
                                        Text("Hide")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Category Picker
            item {
                BugSectionCard(palette) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "BUG CATEGORY *",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = palette.violet,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            BugCategory.entries.forEach { cat ->
                                val selected = state.category == cat
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.updateCategory(cat) },
                                    label = { Text(cat.wireName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = palette.violet,
                                            selectedLabelColor = Color.White,
                                        ),
                                )
                            }
                        }
                        Text(
                            text = state.category.subtitle,
                            fontSize = 12.sp,
                            color = palette.textSecondary,
                        )
                    }
                }
            }

            // Required & Structured Fields
            item {
                BugSectionCard(palette) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FieldWithCounter(
                            label = "Bug Title *",
                            value = state.title,
                            onValueChange = { viewModel.updateTitle(it) },
                            placeholder = "Short summary of what failed",
                            maxLen = BugReportLimits.TITLE_MAX,
                            errorText =
                                if (state.hasAttemptedSubmit) state.validation.titleError else null,
                            palette = palette,
                            singleLine = true,
                        )

                        FieldWithCounter(
                            label = "Detailed Description *",
                            value = state.description,
                            onValueChange = { viewModel.updateDescription(it) },
                            placeholder = "Describe what happened clearly (min 15 characters)...",
                            maxLen = BugReportLimits.DESCRIPTION_MAX,
                            errorText =
                                if (state.hasAttemptedSubmit) state.validation.descriptionError
                                else null,
                            palette = palette,
                            minLines = 4,
                        )

                        FieldWithCounter(
                            label = "Steps to Reproduce",
                            value = state.stepsToReproduce,
                            onValueChange = { viewModel.updateSteps(it) },
                            placeholder = "1. Open Downloads\n2. Tap...",
                            maxLen = BugReportLimits.STEPS_MAX,
                            errorText = state.validation.stepsError,
                            palette = palette,
                            minLines = 2,
                        )

                        FieldWithCounter(
                            label = "Expected Behavior",
                            value = state.expectedBehavior,
                            onValueChange = { viewModel.updateExpected(it) },
                            placeholder = "What you expected Pengu to do",
                            maxLen = BugReportLimits.EXPECTED_MAX,
                            errorText = state.validation.expectedError,
                            palette = palette,
                            minLines = 2,
                        )

                        FieldWithCounter(
                            label = "Actual Behavior",
                            value = state.actualBehavior,
                            onValueChange = { viewModel.updateActual(it) },
                            placeholder = "What actually occurred instead",
                            maxLen = BugReportLimits.ACTUAL_MAX,
                            errorText = state.validation.actualError,
                            palette = palette,
                            minLines = 2,
                        )
                    }
                }
            }

            // Screenshot Attachments
            item {
                BugSectionCard(palette) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    "Screenshots (${state.attachmentPaths.size}/${BugReportLimits.MAX_ATTACHMENTS})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = palette.textPrimary,
                                )
                                Text(
                                    "PNG, JPEG or WebP · max 3 MB each",
                                    fontSize = 12.sp,
                                    color = palette.textSecondary,
                                )
                            }
                            OutlinedButton(
                                onClick = { imagePicker.launch("image/*") },
                                enabled = state.attachmentPaths.size < BugReportLimits.MAX_ATTACHMENTS,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.AddPhotoAlternate,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Attach")
                            }
                        }

                        if (state.attachmentPaths.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                state.attachmentPaths.forEach { path ->
                                    AttachmentThumbnailBox(
                                        path = path,
                                        borderColor = palette.cardBorder,
                                        onRemove = { viewModel.removeAttachment(path) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Diagnostics Opt-in + Redacted Preview
            item {
                val diagPreview =
                    remember(showDiagnosticsPreview, state.includeDiagnostics, state.crashStackTrace) {
                        if (showDiagnosticsPreview) viewModel.previewDiagnostics() else null
                    }
                BugSectionCard(palette, modifier = Modifier.animateContentSize()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(
                                    "Include Redacted Diagnostics",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = palette.textPrimary,
                                )
                                Text(
                                    "App version, Android API, device model, ABI & yt-dlp version. Sensitive credentials are never included.",
                                    fontSize = 12.sp,
                                    color = palette.textSecondary,
                                )
                            }
                            Switch(
                                checked = state.includeDiagnostics,
                                onCheckedChange = { viewModel.updateIncludeDiagnostics(it) },
                            )
                        }

                        if (state.includeDiagnostics) {
                            Text(
                                text =
                                    if (showDiagnosticsPreview) "Hide Diagnostic Data"
                                    else "Preview Redacted Diagnostic Data",
                                color = palette.violet,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier =
                                    Modifier.clickable {
                                        showDiagnosticsPreview = !showDiagnosticsPreview
                                    },
                            )
                        }

                        if (showDiagnosticsPreview && diagPreview != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = palette.softTint,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text =
                                        "App: Pengu ${diagPreview.appVersion} (${diagPreview.versionCode})\n" +
                                            "OS: Android ${diagPreview.androidVersion} (API ${diagPreview.apiLevel})\n" +
                                            "Device: ${diagPreview.deviceManufacturer} ${diagPreview.deviceModel}\n" +
                                            "ABIs: ${diagPreview.supportedAbis.joinToString()}\n" +
                                            "yt-dlp: ${diagPreview.ytDlpVersion.ifBlank { "Unknown" }}" +
                                            if (diagPreview.crashStackTrace.isNotBlank())
                                                "\nTrace: ${diagPreview.crashStackTrace.take(280)}"
                                            else "",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = palette.textPrimary,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Submit Button
            item {
                Button(
                    onClick = { viewModel.submitForm() },
                    enabled = !state.isSubmitting,
                    shape = RoundedCornerShape(16.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = palette.violet,
                            contentColor = Color.White,
                        ),
                    modifier = Modifier.fillMaxWidth().height(52.dp).padding(bottom = 0.dp),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Submitting Report...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Submit Bug Report", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
internal fun BugSectionCard(
    palette: BugReportPalette,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = palette.card,
        modifier =
            modifier
                .fillMaxWidth()
                .border(1.dp, palette.cardBorder, RoundedCornerShape(20.dp)),
    ) {
        Box(modifier = Modifier.padding(18.dp)) { content() }
    }
}

@Composable
private fun FieldWithCounter(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    maxLen: Int,
    errorText: String?,
    palette: BugReportPalette,
    singleLine: Boolean = false,
    minLines: Int = 1,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary,
            )
            Text(
                text = "${value.length} / $maxLen",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = palette.textSecondary,
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = 13.sp) },
            isError = errorText != null,
            supportingText = errorText?.let { err -> { Text(err, color = palette.coral, fontSize = 11.sp) } },
            singleLine = singleLine,
            minLines = minLines,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun AttachmentThumbnailBox(
    path: String,
    borderColor: Color,
    onRemove: (() -> Unit)? = null,
) {
    val bmp by
        produceState<ImageBitmap?>(initialValue = null, key1 = path) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                        BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
                    }.getOrNull()
                }
        }
    Box(
        modifier =
            Modifier.size(88.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, borderColor, RoundedCornerShape(14.dp))
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp!!,
                contentDescription = "Screenshot thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (onRemove != null) {
            Box(
                modifier =
                    Modifier.align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable { onRemove() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Remove screenshot",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
