package com.devrayan.pengu.ui.page.settings.beta

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devrayan.pengu.R
import com.devrayan.pengu.beta.BetaEnrollmentStatus
import com.devrayan.pengu.beta.BetaEnrollmentType
import com.devrayan.pengu.beta.BetaProgramUiState
import com.devrayan.pengu.beta.BetaProgramViewModel
import com.devrayan.pengu.beta.BetaReleaseItem
import com.devrayan.pengu.beta.ReleaseCategory
import com.devrayan.pengu.ui.common.LocalDarkTheme
import com.devrayan.pengu.ui.common.Route
import com.devrayan.pengu.ui.component.BackButton
import com.devrayan.pengu.ui.component.ReleaseNoteBlocksColumn
import com.devrayan.pengu.ui.component.parseReleaseMarkdownBlocks
import com.devrayan.pengu.util.PRE_RELEASE
import com.devrayan.pengu.util.STABLE
import com.devrayan.pengu.util.ToastUtil
import com.devrayan.pengu.util.UpdateUtil
import org.koin.androidx.compose.koinViewModel

/**
 * Semantic design tokens harmonized from Better-UI-UX `education-modern` + `gaming-future`
 * palettes (60-30-10 surface hierarchy, WCAG AA/AAA text contrast, desaturated dark neutrals).
 */
private data class BetaPaletteTokens(
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
private fun rememberBetaTokens(): BetaPaletteTokens {
    val isDark = LocalDarkTheme.current.isDarkTheme()
    return remember(isDark) {
        if (isDark) {
            BetaPaletteTokens(
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
            BetaPaletteTokens(
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

@Composable
fun SemanticBetaBadge(
    label: String,
    color: Color,
    icon: ImageVector? = null,
    filled: Boolean = false,
) {
    Surface(
        shape = CircleShape,
        color = if (filled) color else color.copy(alpha = 0.14f),
        border = if (filled) null else androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (filled) Color.White else color,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = if (filled) Color.White else color,
            )
        }
    }
}

@Composable
private fun BetaCardContainer(
    tokens: BetaPaletteTokens,
    modifier: Modifier = Modifier,
    highlightedBorder: Color? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = tokens.card,
        modifier =
            modifier
                .fillMaxWidth()
                .border(
                    width = if (highlightedBorder != null) 1.5.dp else 1.dp,
                    color = highlightedBorder ?: tokens.cardBorder,
                    shape = RoundedCornerShape(20.dp),
                ),
    ) {
        Box(modifier = Modifier.padding(20.dp)) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetaProgramPage(
    onNavigateBack: () -> Unit,
    onNavigateTo: (String) -> Unit,
    viewModel: BetaProgramViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val overview = state.overview
    val tokens = rememberBetaTokens()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var selectedTrackTab by rememberSaveable { mutableIntStateOf(0) }
    var closedInviteCode by rememberSaveable { mutableStateOf("") }

    Scaffold(
        containerColor = tokens.canvas,
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                colors =
                    TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = tokens.canvas,
                        scrolledContainerColor = tokens.card,
                    ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Beta Program",
                            fontWeight = FontWeight.ExtraBold,
                            color = tokens.textPrimary,
                        )
                        SemanticBetaBadge(
                            label = "BETA",
                            color = tokens.violet,
                            icon = Icons.Rounded.Science,
                            filled = true,
                        )
                    }
                },
                navigationIcon = { BackButton { onNavigateBack() } },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Refresh Beta Info",
                            tint = tokens.textPrimary,
                        )
                    }
                    IconButton(onClick = { onNavigateTo(Route.BETA_MY_PROGRAM) }) {
                        Icon(
                            Icons.Rounded.Security,
                            contentDescription = "My Testing Program",
                            tint = tokens.violet,
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
                    .background(tokens.canvas)
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { StatusBanners(state = state, tokens = tokens, onDismiss = { viewModel.clearMessages() }) }

            // Hero Status + Version Comparison Rail
            item {
                val heroGradient =
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF4338CA),
                            Color(0xFF6D28D9),
                            Color(0xFF7C3AED),
                        )
                    )
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    modifier =
                        Modifier.fillMaxWidth()
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.16f),
                                RoundedCornerShape(24.dp),
                            ),
                ) {
                    Box(modifier = Modifier.fillMaxWidth().background(heroGradient).padding(20.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        modifier =
                                            Modifier.size(44.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color.White)
                                                .padding(4.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.pengu_logo),
                                            contentDescription = "Pengu Logo",
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(9.dp)),
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "EARLY ACCESS TRACK",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp,
                                            color = Color.White.copy(alpha = 0.8f),
                                        )
                                        Text(
                                            text = "Pengu Testing Hub",
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                        )
                                    }
                                }

                                val (badgeIcon, badgeLabel) =
                                    when (overview?.enrollmentStatus) {
                                        BetaEnrollmentStatus.ACTIVE ->
                                            Icons.Rounded.CheckCircle to "Active"
                                        BetaEnrollmentStatus.PENDING_APPROVAL ->
                                            Icons.Rounded.Schedule to "Pending"
                                        else -> Icons.Rounded.Shield to "Standard"
                                    }
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.2f),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            imageVector = badgeIcon,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Text(
                                            text = badgeLabel,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }

                            // Tabular Build Timeline
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White.copy(alpha = 0.14f),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "INSTALLED",
                                            color = Color.White.copy(alpha = 0.72f),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.7.sp,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "v${overview?.installedVersion ?: "1.2.0"}",
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp),
                                )
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White.copy(alpha = 0.14f),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "LATEST BETA",
                                            color = Color.White.copy(alpha = 0.72f),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.7.sp,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            overview?.latestBeta?.versionName?.let { "v$it" }
                                                ?: "Up to date",
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }

                            val activeCh =
                                if (overview?.updateChannel == PRE_RELEASE) "Beta Channel"
                                else "Stable Channel"
                            Text(
                                text =
                                    "Track: $activeCh  ·  Tier: ${overview?.enrollmentType?.label ?: "Not Enrolled"}",
                                color = Color.White.copy(alpha = 0.88f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (overview?.latestBeta != null) {
                                    Button(
                                        onClick = {
                                            viewModel.selectRelease(overview.latestBeta)
                                            onNavigateTo(Route.BETA_RELEASE_DETAIL)
                                        },
                                        colors =
                                            ButtonDefaults.buttonColors(
                                                containerColor = Color.White,
                                                contentColor = Color(0xFF4338CA),
                                            ),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.weight(1f).height(48.dp),
                                    ) {
                                        Icon(
                                            Icons.Rounded.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Get Beta APK", fontWeight = FontWeight.Bold)
                                    }
                                }
                                FilledTonalButton(
                                    onClick = { onNavigateTo(Route.BETA_HISTORY) },
                                    colors =
                                        ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color.White.copy(alpha = 0.2f),
                                            contentColor = Color.White,
                                        ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f).height(48.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Release History", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            if (state.isLoading && overview == null) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = tokens.violet)
                    }
                }
            }

            // Interactive Open vs Closed Beta Selector Card
            item {
                val isOpenActive =
                    overview?.enrollmentType == BetaEnrollmentType.OPEN &&
                        overview.enrollmentStatus == BetaEnrollmentStatus.ACTIVE
                val isClosedActive =
                    overview?.enrollmentType == BetaEnrollmentType.CLOSED &&
                        overview.enrollmentStatus == BetaEnrollmentStatus.ACTIVE
                val isPending =
                    overview?.enrollmentType == BetaEnrollmentType.CLOSED &&
                        overview.enrollmentStatus == BetaEnrollmentStatus.PENDING_APPROVAL

                BetaCardContainer(
                    tokens = tokens,
                    highlightedBorder =
                        if (isOpenActive || isClosedActive) tokens.violet else null,
                    modifier = Modifier.animateContentSize(tween(250, easing = FastOutSlowInEasing)),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            "CHOOSE TESTING PROGRAM",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.9.sp,
                            color = tokens.violet,
                        )

                        // Sleek 2-option Segmented Switcher
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = tokens.softTint,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(modifier = Modifier.padding(4.dp)) {
                                SegmentedProgramPill(
                                    title = "Open Beta",
                                    selected = selectedTrackTab == 0,
                                    accent = tokens.violet,
                                    textPrimary = tokens.textPrimary,
                                    onClick = { selectedTrackTab = 0 },
                                    modifier = Modifier.weight(1f),
                                )
                                SegmentedProgramPill(
                                    title = "Closed Beta",
                                    selected = selectedTrackTab == 1,
                                    accent = tokens.violet,
                                    textPrimary = tokens.textPrimary,
                                    onClick = { selectedTrackTab = 1 },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        if (selectedTrackTab == 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "Public Early Access",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.textPrimary,
                                )
                                if (isOpenActive) {
                                    SemanticBetaBadge(
                                        label = "JOINED",
                                        color = tokens.emerald,
                                        icon = Icons.Rounded.CheckCircle,
                                    )
                                }
                            }
                            ProgramBenefitRow(
                                icon = Icons.Rounded.FlashOn,
                                tint = tokens.violet,
                                text = "Instant access with zero manual approval required.",
                                textColor = tokens.textSecondary,
                            )
                            ProgramBenefitRow(
                                icon = Icons.Rounded.VerifiedUser,
                                tint = tokens.emerald,
                                text = "Every APK is verified over HTTPS with SHA-256 & signing cert.",
                                textColor = tokens.textSecondary,
                            )
                            ProgramBenefitRow(
                                icon = Icons.Rounded.SwapHoriz,
                                tint = tokens.cyan,
                                text = "Switch cleanly back to Stable releases at any time.",
                                textColor = tokens.textSecondary,
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { viewModel.joinOpenBeta() },
                                    enabled = !state.isLoading && !isOpenActive,
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = tokens.violet,
                                            contentColor = Color.White,
                                        ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f).height(48.dp),
                                ) {
                                    Text(
                                        text = if (isOpenActive) "Enrolled in Open Beta" else "Join Open Beta",
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                if (overview?.enrollmentStatus != BetaEnrollmentStatus.NOT_ENROLLED &&
                                    overview != null
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.leaveBetaAndSwitchToStable() },
                                        enabled = !state.isLoading,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.height(48.dp),
                                    ) {
                                        Text("Leave", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "Invite & Capacity Track",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.textPrimary,
                                )
                                when {
                                    isClosedActive ->
                                        SemanticBetaBadge(
                                            label = "VIP ACTIVE",
                                            color = tokens.emerald,
                                            icon = Icons.Rounded.VerifiedUser,
                                        )
                                    isPending ->
                                        SemanticBetaBadge(
                                            label = "PENDING",
                                            color = tokens.amber,
                                            icon = Icons.Rounded.Schedule,
                                        )
                                    else ->
                                        SemanticBetaBadge(
                                            label = "LIMITED SEATS",
                                            color = tokens.cyan,
                                            icon = Icons.Rounded.Lock,
                                        )
                                }
                            }

                            val cfg = overview?.config
                            val activeSeats = cfg?.closedActiveCount ?: 0
                            val maxSeats = (cfg?.closedCapacity ?: 100).coerceAtLeast(1)
                            val seatRatio by
                                animateFloatAsState(
                                    targetValue = (activeSeats.toFloat() / maxSeats).coerceIn(0f, 1f),
                                    label = "seatRatio",
                                )

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "Closed Tester Capacity",
                                        fontSize = 12.sp,
                                        color = tokens.textSecondary,
                                    )
                                    Text(
                                        "$activeSeats / $maxSeats seats",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.textPrimary,
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { seatRatio },
                                    color = tokens.violet,
                                    trackColor = tokens.softTint,
                                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                                )
                            }

                            val hasServer = !overview?.serverUrl.isNullOrBlank()
                            OutlinedTextField(
                                value = closedInviteCode,
                                onValueChange = { closedInviteCode = it },
                                label = { Text("Closed Beta Invite Code") },
                                placeholder = { Text("e.g., PENGU-BETA-VIP") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                supportingText = {
                                    Text(
                                        text =
                                            if (hasServer) {
                                                "Validated securely against your HTTPS Beta Server."
                                            } else {
                                                "Server-side access control requires configuring your HTTPS Beta Server URL inside My Testing Program."
                                            },
                                        color = if (hasServer) tokens.textSecondary else tokens.amber,
                                        fontSize = 11.sp,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { viewModel.requestClosedBeta(closedInviteCode) },
                                    enabled = !state.isLoading && !isClosedActive,
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = tokens.violet,
                                            contentColor = Color.White,
                                        ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f).height(48.dp),
                                ) {
                                    Text(
                                        when {
                                            isClosedActive -> "Closed Beta Active"
                                            closedInviteCode.isNotBlank() -> "Join with Invite"
                                            else -> "Request Access"
                                        },
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                if (!hasServer) {
                                    OutlinedButton(
                                        onClick = { onNavigateTo(Route.BETA_MY_PROGRAM) },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.height(48.dp),
                                    ) {
                                        Text("Server Setup", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Beta Notes, Experimental Features & Known Bugs
            val activeBeta = overview?.latestBeta
            val knownBugs =
                (activeBeta?.knownBugs.orEmpty() + overview?.config?.knownBugs.orEmpty()).distinct()
            val experimental =
                (activeBeta?.experimentalFeatures.orEmpty() +
                    overview?.config?.experimentalFeatures.orEmpty())
                    .distinct()

            if (activeBeta != null) {
                item {
                    BetaCardContainer(tokens = tokens) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "What's New in Beta",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.textPrimary,
                                )
                                SemanticBetaBadge(
                                    label = "v${activeBeta.versionName}",
                                    color = tokens.violet,
                                    icon = Icons.Rounded.NewReleases,
                                )
                            }
                            val blocks =
                                remember(activeBeta.releaseNotes) {
                                    parseReleaseMarkdownBlocks(activeBeta.releaseNotes).take(8)
                                }
                            ReleaseNoteBlocksColumn(
                                blocks = blocks,
                                isDark = LocalDarkTheme.current.isDarkTheme(),
                            )
                            FilledTonalButton(
                                onClick = {
                                    viewModel.selectRelease(activeBeta)
                                    onNavigateTo(Route.BETA_RELEASE_DETAIL)
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                            ) {
                                Text("Inspect Checksums & Download Build", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (experimental.isNotEmpty() || knownBugs.isNotEmpty()) {
                item {
                    BetaCardContainer(tokens = tokens) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (experimental.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = tokens.violet,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        "Experimental Features",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = tokens.textPrimary,
                                    )
                                }
                                experimental.forEach { feat ->
                                    ProgramBenefitRow(
                                        icon = Icons.Rounded.Science,
                                        tint = tokens.violet,
                                        text = feat,
                                        textColor = tokens.textSecondary,
                                    )
                                }
                            }
                            if (experimental.isNotEmpty() && knownBugs.isNotEmpty()) {
                                HorizontalDivider(color = tokens.cardBorder)
                            }
                            if (knownBugs.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.Warning,
                                        contentDescription = null,
                                        tint = tokens.amber,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        "Known Issues in Current Beta",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = tokens.textPrimary,
                                    )
                                }
                                knownBugs.forEach { bug ->
                                    ProgramBenefitRow(
                                        icon = Icons.Rounded.ErrorOutline,
                                        tint = tokens.amber,
                                        text = bug,
                                        textColor = tokens.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Eligibility Card
            overview?.eligibility?.let { eligibility ->
                item {
                    val passedCount = eligibility.checks.count { it.passed }
                    BetaCardContainer(tokens = tokens) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.VerifiedUser,
                                        contentDescription = null,
                                        tint = if (eligibility.isEligible) tokens.emerald else tokens.coral,
                                    )
                                    Text(
                                        text = "Tester Eligibility",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = tokens.textPrimary,
                                    )
                                }
                                SemanticBetaBadge(
                                    label = "$passedCount/${eligibility.checks.size} READY",
                                    color = if (eligibility.isEligible) tokens.emerald else tokens.amber,
                                    icon = Icons.Rounded.CheckCircle,
                                )
                            }
                            eligibility.checks.forEach { chk ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            chk.label,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = tokens.textPrimary,
                                        )
                                        Text(
                                            chk.detail,
                                            fontSize = 12.sp,
                                            color = tokens.textSecondary,
                                        )
                                    }
                                    Icon(
                                        imageVector =
                                            if (chk.passed) Icons.Rounded.CheckCircle
                                            else Icons.Rounded.Warning,
                                        contentDescription = null,
                                        tint = if (chk.passed) tokens.emerald else tokens.amber,
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Navigation Cards
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BetaNavRowCard(
                        tokens = tokens,
                        icon = Icons.Rounded.Security,
                        iconTint = tokens.violet,
                        title = "My Testing Program",
                        subtitle = "Tester Pass, release channel switcher & HTTPS server endpoint",
                        onClick = { onNavigateTo(Route.BETA_MY_PROGRAM) },
                    )
                    BetaNavRowCard(
                        tokens = tokens,
                        icon = Icons.Rounded.History,
                        iconTint = tokens.cyan,
                        title = "Beta, Stable & Archived History",
                        subtitle = "Browse all releases with SHA-256 checksum & certificate verification",
                        onClick = { onNavigateTo(Route.BETA_HISTORY) },
                    )
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTestingProgramPage(
    onNavigateBack: () -> Unit,
    viewModel: BetaProgramViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val overview = state.overview
    val tokens = rememberBetaTokens()
    val clipboard = LocalClipboardManager.current
    var serverInput by remember(overview?.serverUrl) { mutableStateOf(overview?.serverUrl.orEmpty()) }
    val urlError =
        remember(serverInput) {
            val trimmed = serverInput.trim()
            if (trimmed.isNotEmpty() && !UpdateUtil.isHttpsUrl(trimmed)) {
                "Security rule: endpoint URL must begin with https://"
            } else null
        }

    Scaffold(
        containerColor = tokens.canvas,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tokens.canvas),
                title = { Text("My Testing Program", fontWeight = FontWeight.Bold) },
                navigationIcon = { BackButton { onNavigateBack() } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier.fillMaxSize()
                    .background(tokens.canvas)
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { StatusBanners(state = state, tokens = tokens, onDismiss = { viewModel.clearMessages() }) }

            // Tester Membership Pass
            item {
                BetaCardContainer(tokens = tokens, highlightedBorder = tokens.violet.copy(alpha = 0.45f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    "TESTER MEMBERSHIP PASS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.9.sp,
                                    color = tokens.violet,
                                )
                                Text(
                                    overview?.enrollmentType?.label ?: "Not Enrolled",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = tokens.textPrimary,
                                )
                            }
                            SemanticBetaBadge(
                                label = overview?.enrollmentStatus?.label ?: "Standard",
                                color =
                                    if (overview?.enrollmentStatus == BetaEnrollmentStatus.ACTIVE)
                                        tokens.emerald
                                    else tokens.amber,
                                icon =
                                    if (overview?.enrollmentStatus == BetaEnrollmentStatus.ACTIVE)
                                        Icons.Rounded.CheckCircle
                                    else Icons.Rounded.Schedule,
                            )
                        }

                        HorizontalDivider(color = tokens.cardBorder)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = tokens.softTint,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        "ANONYMOUS TESTER ID",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.textSecondary,
                                    )
                                    Text(
                                        overview?.testerId ?: "-",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = tokens.textPrimary,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        overview?.testerId?.let {
                                            clipboard.setText(AnnotatedString(it))
                                            ToastUtil.makeToast("Tester ID copied")
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Rounded.ContentCopy,
                                        contentDescription = "Copy Tester ID",
                                        tint = tokens.violet,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Explicit Channel Switcher
            item {
                BetaCardContainer(tokens = tokens) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Release Channel",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = tokens.textPrimary,
                        )
                        Text(
                            "Separate release channels keep your updates predictable. Background checks never switch your channel automatically.",
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = tokens.textSecondary,
                        )

                        val isStable = overview?.updateChannel != PRE_RELEASE
                        ChannelOptionRow(
                            tokens = tokens,
                            selected = isStable,
                            title = "Stable Channel",
                            subtitle = "Official production builds verified for daily use",
                            accent = tokens.emerald,
                            onClick = { viewModel.setChannelExplicit(STABLE) },
                        )
                        ChannelOptionRow(
                            tokens = tokens,
                            selected = !isStable,
                            title = "Beta Channel",
                            subtitle = "Experimental pre-release builds with early features",
                            accent = tokens.violet,
                            onClick = { viewModel.setChannelExplicit(PRE_RELEASE) },
                        )

                        if (overview?.enrollmentStatus != BetaEnrollmentStatus.NOT_ENROLLED &&
                            overview != null
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.leaveBetaAndSwitchToStable() },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                            ) {
                                Text(
                                    "Leave Beta Program & Revert to Stable",
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }

            // HTTPS Server URL Card
            item {
                BetaCardContainer(tokens = tokens, modifier = Modifier.padding(bottom = 24.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Beta Distribution Endpoint",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = tokens.textPrimary,
                        )
                        Text(
                            "Default uses official GitHub Releases over HTTPS. Optionally provide a self-hosted HTTPS Pengu Beta Server URL for Closed Beta seats.",
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = tokens.textSecondary,
                        )
                        OutlinedTextField(
                            value = serverInput,
                            onValueChange = { serverInput = it },
                            label = { Text("HTTPS Server URL") },
                            placeholder = { Text("https://beta.example.com") },
                            isError = urlError != null,
                            supportingText = {
                                if (urlError != null) {
                                    Text(urlError, color = tokens.coral, fontSize = 11.sp)
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { viewModel.saveServerUrl(serverInput) },
                            enabled = urlError == null,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = tokens.violet,
                                    contentColor = Color.White,
                                ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text("Save Endpoint", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetaReleaseDetailsPage(
    onNavigateBack: () -> Unit,
    viewModel: BetaProgramViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val tokens = rememberBetaTokens()
    val release = state.selectedRelease ?: state.overview?.latestBeta ?: state.overview?.latestStable
    val isDark = LocalDarkTheme.current.isDarkTheme()

    val settingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            UpdateUtil.installLatestApk(context)
        }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                UpdateUtil.installLatestApk(context)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                settingsLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    )
                )
            } else {
                UpdateUtil.installLatestApk(context)
            }
        }

    val confirmInstallApk: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (context.packageManager.canRequestPackageInstalls()) {
                UpdateUtil.installLatestApk(context)
            } else {
                permissionLauncher.launch(Manifest.permission.REQUEST_INSTALL_PACKAGES)
            }
        } else {
            UpdateUtil.installLatestApk(context)
        }
        Unit
    }

    Scaffold(
        containerColor = tokens.canvas,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tokens.canvas),
                title = { Text("Release Details", fontWeight = FontWeight.Bold) },
                navigationIcon = { BackButton { onNavigateBack() } },
            )
        },
    ) { padding ->
        if (release == null) {
            Box(
                modifier = Modifier.fillMaxSize().background(tokens.canvas).padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Select a release from Release History to inspect details.", color = tokens.textSecondary)
            }
            return@Scaffold
        }

        val isThisDownloading = state.downloadingReleaseId == release.id
        val status = if (isThisDownloading) state.downloadStatus else UpdateUtil.DownloadStatus.NotYet
        val progressPct = (status as? UpdateUtil.DownloadStatus.Progress)?.percent ?: 0
        val animatedProgress by
            animateFloatAsState(
                targetValue = (progressPct / 100f).coerceIn(0f, 1f),
                label = "apkProgress",
            )
        val isProgress = status is UpdateUtil.DownloadStatus.Progress
        val isVerifying = status is UpdateUtil.DownloadStatus.Verifying
        val finished = status as? UpdateUtil.DownloadStatus.Finished

        LazyColumn(
            modifier =
                Modifier.fillMaxSize()
                    .background(tokens.canvas)
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { StatusBanners(state = state, tokens = tokens, onDismiss = { viewModel.clearMessages() }) }

            // Metadata Header Card
            item {
                val badgeColor =
                    when (release.category) {
                        ReleaseCategory.BETA -> tokens.violet
                        ReleaseCategory.STABLE -> tokens.emerald
                        ReleaseCategory.ARCHIVED -> Color(0xFF64748B)
                    }
                BetaCardContainer(tokens = tokens) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = release.title.ifBlank { "Pengu v${release.versionName}" },
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = tokens.textPrimary,
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                            )
                            SemanticBetaBadge(
                                label = release.channelLabel.uppercase(),
                                color = badgeColor,
                                icon =
                                    if (release.category == ReleaseCategory.BETA) Icons.Rounded.Science
                                    else Icons.Rounded.Shield,
                                filled = true,
                            )
                        }
                        Text(
                            text =
                                "v${release.versionName}  ·  Published ${release.publishedAt.substringBefore("T").ifBlank { "Recent" }}  ·  ${release.downloadCount} downloads",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = tokens.textSecondary,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SemanticBetaBadge("HTTPS ONLY", tokens.emerald, Icons.Rounded.Lock)
                            SemanticBetaBadge("CERT GUARD", tokens.cyan, Icons.Rounded.VerifiedUser)
                            if (!release.sha256.isNullOrBlank()) {
                                SemanticBetaBadge("SHA-256 READY", tokens.violet, Icons.Rounded.Security)
                            }
                        }
                    }
                }
            }

            // Download & Verification Card
            item {
                BetaCardContainer(
                    tokens = tokens,
                    highlightedBorder = if (finished != null) tokens.emerald else null,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Verified APK Installer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = tokens.textPrimary,
                        )

                        if (!release.sha256.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = tokens.softTint,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            "EXPECTED SHA-256 DIGEST",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = tokens.textSecondary,
                                        )
                                        Text(
                                            text = release.sha256,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = tokens.textPrimary,
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(release.sha256))
                                            ToastUtil.makeToast("SHA-256 copied")
                                        }
                                    ) {
                                        Icon(
                                            Icons.Rounded.ContentCopy,
                                            contentDescription = "Copy SHA-256",
                                            tint = tokens.violet,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }

                        if (isProgress) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Downloading APK over HTTPS...", fontSize = 12.sp, color = tokens.textSecondary)
                                Text(
                                    "$progressPct%",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = tokens.violet,
                                )
                            }
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                color = tokens.violet,
                                trackColor = tokens.softTint,
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            )
                        } else if (isVerifying) {
                            Text(
                                "Verifying SHA-256 checksum & APK signing certificate...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = tokens.violet,
                            )
                            LinearProgressIndicator(
                                color = tokens.violet,
                                trackColor = tokens.softTint,
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            )
                        } else if (finished != null) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = tokens.emerald.copy(alpha = 0.14f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = tokens.emerald,
                                    )
                                    Column {
                                        Text(
                                            text =
                                                if (finished.certVerified)
                                                    "SHA-256 & Signing Certificate Verified"
                                                else "SHA-256 Verified",
                                            fontWeight = FontWeight.Bold,
                                            color = tokens.emerald,
                                            fontSize = 13.sp,
                                        )
                                        Text(
                                            text = finished.sha256Hex,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = tokens.textSecondary,
                                        )
                                    }
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { viewModel.downloadAndVerifyRelease(release) },
                                enabled =
                                    !release.apkUrl.isNullOrBlank() &&
                                        release.isEnabled &&
                                        !isProgress &&
                                        !isVerifying,
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = tokens.violet,
                                        contentColor = Color.White,
                                    ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f).height(50.dp),
                            ) {
                                Text(
                                    text =
                                        when {
                                            !release.isEnabled -> "Release Disabled"
                                            release.apkUrl.isNullOrBlank() -> "No APK Asset"
                                            isProgress -> "Downloading $progressPct%"
                                            isVerifying -> "Verifying..."
                                            finished != null -> "Re-Verify"
                                            else -> "Download & Verify APK"
                                        },
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            if (finished != null) {
                                Button(
                                    onClick = confirmInstallApk,
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = tokens.emerald,
                                            contentColor = Color.White,
                                        ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f).height(50.dp),
                                ) {
                                    Text("Install Update", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Text(
                            text =
                                "Existing app data and downloads stay preserved. Installation opens Android's standard confirmation sheet — never silent.",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = tokens.textSecondary,
                        )
                    }
                }
            }

            if (release.knownBugs.isNotEmpty() || release.experimentalFeatures.isNotEmpty()) {
                item {
                    BetaCardContainer(tokens = tokens) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (release.experimentalFeatures.isNotEmpty()) {
                                Text(
                                    "Experimental Highlights",
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.textPrimary,
                                )
                                release.experimentalFeatures.forEach {
                                    ProgramBenefitRow(
                                        icon = Icons.Rounded.Science,
                                        tint = tokens.violet,
                                        text = it,
                                        textColor = tokens.textSecondary,
                                    )
                                }
                            }
                            if (release.knownBugs.isNotEmpty()) {
                                Text(
                                    "Known Bugs",
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.amber,
                                )
                                release.knownBugs.forEach {
                                    ProgramBenefitRow(
                                        icon = Icons.Rounded.ErrorOutline,
                                        tint = tokens.amber,
                                        text = it,
                                        textColor = tokens.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                BetaCardContainer(tokens = tokens, modifier = Modifier.padding(bottom = 28.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Release Notes",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = tokens.textPrimary,
                        )
                        val blocks =
                            remember(release.releaseNotes) {
                                parseReleaseMarkdownBlocks(release.releaseNotes)
                            }
                        if (blocks.isEmpty()) {
                            Text("No release notes published.", fontSize = 13.sp, color = tokens.textSecondary)
                        } else {
                            ReleaseNoteBlocksColumn(blocks = blocks, isDark = isDark)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetaReleaseHistoryPage(
    onNavigateBack: () -> Unit,
    onNavigateTo: (String) -> Unit,
    viewModel: BetaProgramViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val tokens = rememberBetaTokens()
    val all = state.overview?.allReleases.orEmpty()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val counts =
        remember(all) {
            listOf(
                all.size,
                all.count { it.category == ReleaseCategory.BETA },
                all.count { it.category == ReleaseCategory.STABLE },
                all.count { it.category == ReleaseCategory.ARCHIVED },
            )
        }
    val labels = listOf("All", "Beta", "Stable", "Archived")

    val filtered =
        remember(all, selectedTab) {
            when (selectedTab) {
                1 -> all.filter { it.category == ReleaseCategory.BETA }
                2 -> all.filter { it.category == ReleaseCategory.STABLE }
                3 -> all.filter { it.category == ReleaseCategory.ARCHIVED }
                else -> all
            }
        }

    Scaffold(
        containerColor = tokens.canvas,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tokens.canvas),
                title = { Text("Release History", fontWeight = FontWeight.Bold) },
                navigationIcon = { BackButton { onNavigateBack() } },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh History")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(tokens.canvas).padding(padding)) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                labels.forEachIndexed { idx, label ->
                    val active = selectedTab == idx
                    val bg by
                        animateColorAsState(
                            if (active) tokens.violet else tokens.card,
                            label = "filterBg",
                        )
                    Surface(
                        shape = CircleShape,
                        color = bg,
                        modifier =
                            Modifier.height(40.dp)
                                .border(
                                    1.dp,
                                    if (active) tokens.violet else tokens.cardBorder,
                                    CircleShape,
                                )
                                .clickable { selectedTab = idx },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "$label (${counts[idx]})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) Color.White else tokens.textPrimary,
                            )
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No builds in ${labels[selectedTab]} category.",
                        color = tokens.textSecondary,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filtered, key = { it.id }) { item ->
                        ReleaseHistoryRowCard(
                            item = item,
                            tokens = tokens,
                            onClick = {
                                viewModel.selectRelease(item)
                                onNavigateTo(Route.BETA_RELEASE_DETAIL)
                            },
                        )
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SegmentedProgramPill(
    title: String,
    selected: Boolean,
    accent: Color,
    textPrimary: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(if (selected) accent else Color.Transparent, label = "pillBg")
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg,
        modifier = modifier.height(44.dp).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else textPrimary,
            )
        }
    }
}

@Composable
private fun ProgramBenefitRow(
    icon: ImageVector,
    tint: Color,
    text: String,
    textColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text = text, fontSize = 13.sp, lineHeight = 19.sp, color = textColor)
    }
}

@Composable
private fun BetaNavRowCard(
    tokens: BetaPaletteTokens,
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = tokens.card,
        modifier =
            Modifier.fillMaxWidth()
                .border(1.dp, tokens.cardBorder, RoundedCornerShape(18.dp))
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier.size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconTint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(21.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = tokens.textPrimary)
                Text(subtitle, fontSize = 12.sp, lineHeight = 17.sp, color = tokens.textSecondary)
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = tokens.textSecondary,
            )
        }
    }
}

@Composable
private fun ChannelOptionRow(
    tokens: BetaPaletteTokens,
    selected: Boolean,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accent.copy(alpha = 0.1f) else tokens.softTint,
        modifier =
            Modifier.fillMaxWidth()
                .border(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) accent else tokens.cardBorder,
                    shape = RoundedCornerShape(16.dp),
                )
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector =
                    if (selected) Icons.Rounded.RadioButtonChecked
                    else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) accent else tokens.textSecondary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = tokens.textPrimary)
                Text(subtitle, fontSize = 12.sp, color = tokens.textSecondary)
            }
        }
    }
}

@Composable
private fun ReleaseHistoryRowCard(
    item: BetaReleaseItem,
    tokens: BetaPaletteTokens,
    onClick: () -> Unit,
) {
    val badgeColor =
        when (item.category) {
            ReleaseCategory.BETA -> tokens.violet
            ReleaseCategory.STABLE -> tokens.emerald
            ReleaseCategory.ARCHIVED -> Color(0xFF64748B)
        }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = tokens.card,
        modifier =
            Modifier.fillMaxWidth()
                .border(1.dp, tokens.cardBorder, RoundedCornerShape(18.dp))
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = item.title.ifBlank { "Pengu v${item.versionName}" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = tokens.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    SemanticBetaBadge(
                        label = item.channelLabel.uppercase(),
                        color = badgeColor,
                    )
                }
                Text(
                    text =
                        "v${item.versionName}  ·  ${item.publishedAt.substringBefore("T").ifBlank { "Recent" }}  ·  ${item.downloadCount} downloads",
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    color = tokens.textSecondary,
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = tokens.textSecondary)
        }
    }
}

@Composable
private fun StatusBanners(
    state: BetaProgramUiState,
    tokens: BetaPaletteTokens,
    onDismiss: () -> Unit,
) {
    state.errorMessage?.let { msg ->
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = tokens.coral.copy(alpha = 0.14f),
            border = androidx.compose.foundation.BorderStroke(1.dp, tokens.coral.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = tokens.coral)
                Text(msg, fontSize = 13.sp, color = tokens.coral, modifier = Modifier.weight(1f))
            }
        }
    }
    state.actionMessage?.let { msg ->
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = tokens.emerald.copy(alpha = 0.14f),
            border = androidx.compose.foundation.BorderStroke(1.dp, tokens.emerald.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = tokens.emerald)
                Text(msg, fontSize = 13.sp, color = tokens.emerald, modifier = Modifier.weight(1f))
            }
        }
    }
}
