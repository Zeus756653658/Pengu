package com.devrayan.pengu.ui.page.settings.about

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devrayan.pengu.BuildConfig
import com.devrayan.pengu.ui.common.HapticFeedback.slightHapticFeedback
import com.devrayan.pengu.ui.common.LocalDarkTheme
import com.devrayan.pengu.ui.component.BackButton
import com.devrayan.pengu.ui.component.ChangelogFilterMode
import com.devrayan.pengu.ui.component.ReleaseNoteBlocksColumn
import com.devrayan.pengu.ui.component.filterMarkdownBlocksBySection
import com.devrayan.pengu.ui.component.parseReleaseMarkdownBlocks
import com.devrayan.pengu.ui.page.UpdateDialog
import com.devrayan.pengu.util.UpdateUtil
import com.devrayan.pengu.util.UpdateUtil.resolvedVersion
import com.devrayan.pengu.util.UpdateUtil.toVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * GitHub `.md` Preview styled Changelog Viewer displaying release notes, comparison table,
 * v1.3.0 Plan, full version history, and live GitHub Releases.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogPage(
    onNavigateBack: () -> Unit,
    initialMode: ChangelogFilterMode = ChangelogFilterMode.ALL,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isDark = LocalDarkTheme.current.isDarkTheme()
    val scope = rememberCoroutineScope()
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val ghBg = if (isDark) Color(0xFF0D1117) else Color(0xFFFFFFFF)
    val ghHeaderBg = if (isDark) Color(0xFF161B22) else Color(0xFFF6F8FA)
    val ghBorder = if (isDark) Color(0xFF30363D) else Color(0xFFD0D7DE)
    val ghMuted = if (isDark) Color(0xFF8B949E) else Color(0xFF656D76)
    val ghText = if (isDark) Color(0xFFE6EDF3) else Color(0xFF1F2328)
    val ghAccent = Color(0xFF1F6FEB)

    var selectedMode by remember { mutableStateOf(initialMode) }
    var onlineReleases by remember { mutableStateOf<List<UpdateUtil.Release>>(emptyList()) }
    var onlineLoading by remember { mutableStateOf(false) }
    var onlineError by remember { mutableStateOf<String?>(null) }
    var updateCandidate by remember { mutableStateOf<UpdateUtil.Release?>(null) }

    val bundledReleaseMd by
        produceState(initialValue = "", key1 = context) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                            context.assets.open("release_changelog.md").bufferedReader().use {
                                it.readText()
                            }
                        }
                        .getOrDefault("")
                }
        }

    val bundledHistoryMd by
        produceState(initialValue = "", key1 = context) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                            context.assets.open("full_changelog.md").bufferedReader().use {
                                it.readText()
                            }
                        }
                        .getOrDefault("")
                }
        }

    fun loadOnlineReleases() {
        if (onlineLoading) return
        onlineLoading = true
        onlineError = null
        scope.launch {
            val result = UpdateUtil.fetchAllReleases()
            onlineLoading = false
            result
                .onSuccess { list -> onlineReleases = list }
                .onFailure { err ->
                    onlineError = err.message ?: "Unable to fetch GitHub releases"
                }
        }
    }

    LaunchedEffect(selectedMode) {
        if (selectedMode == ChangelogFilterMode.ONLINE && onlineReleases.isEmpty() && onlineError == null) {
            loadOnlineReleases()
        }
    }

    val parsedReleaseBlocks =
        remember(bundledReleaseMd) { parseReleaseMarkdownBlocks(bundledReleaseMd) }
    val parsedHistoryBlocks =
        remember(bundledHistoryMd) { parseReleaseMarkdownBlocks(bundledHistoryMd) }
    val activeLocalBlocks =
        remember(selectedMode, parsedReleaseBlocks, parsedHistoryBlocks) {
            when (selectedMode) {
                ChangelogFilterMode.HISTORY -> parsedHistoryBlocks
                ChangelogFilterMode.ONLINE -> emptyList()
                else -> filterMarkdownBlocksBySection(parsedReleaseBlocks, selectedMode)
            }
        }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = if (isDark) Color(0xFF010409) else Color(0xFFF6F8FA),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Changelog",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                        )
                        Text(
                            text = "Pengu v${BuildConfig.VERSION_NAME} • GitHub Markdown Preview",
                            fontSize = 11.sp,
                            color = ghMuted,
                        )
                    }
                },
                navigationIcon = { BackButton(onNavigateBack) },
                actions = {
                    if (selectedMode == ChangelogFilterMode.ONLINE) {
                        IconButton(onClick = { loadOnlineReleases() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh GitHub releases")
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = ghHeaderBg,
                        scrolledContainerColor = ghHeaderBg,
                    ),
                windowInsets = WindowInsets.statusBars,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding() + 10.dp,
                    bottom = navBottomInset + 24.dp,
                    start = 12.dp,
                    end = 12.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // GitHub Repository Tab Bar Pills
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChangelogFilterMode.entries.forEach { mode ->
                        val active = selectedMode == mode
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (active) ghAccent else ghHeaderBg,
                            modifier =
                                Modifier.border(
                                        1.dp,
                                        if (active) ghAccent else ghBorder,
                                        RoundedCornerShape(8.dp),
                                    )
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        view.slightHapticFeedback()
                                        selectedMode = mode
                                    },
                        ) {
                            Text(
                                text = mode.label,
                                fontSize = 12.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) Color.White else ghText,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            }

            if (selectedMode == ChangelogFilterMode.ONLINE) {
                if (onlineLoading) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(color = ghAccent)
                            Text(
                                text = "Fetching GitHub Releases...",
                                fontSize = 13.sp,
                                color = ghMuted,
                            )
                        }
                    }
                } else if (onlineError != null && onlineReleases.isEmpty()) {
                    item {
                        Box(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ghBg)
                                    .border(1.dp, ghBorder, RoundedCornerShape(8.dp))
                                    .padding(16.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Unable to reach GitHub Releases API",
                                    fontWeight = FontWeight.Bold,
                                    color = ghText,
                                )
                                Text(
                                    text = onlineError.orEmpty(),
                                    fontSize = 12.sp,
                                    color = ghMuted,
                                )
                                Button(
                                    onClick = { loadOnlineReleases() },
                                    colors = ButtonDefaults.buttonColors(containerColor = ghAccent),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                } else if (onlineReleases.isEmpty()) {
                    item {
                        Box(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ghBg)
                                    .border(1.dp, ghBorder, RoundedCornerShape(8.dp))
                                    .padding(18.dp),
                        ) {
                            Text(
                                text = "No public GitHub releases published yet.",
                                color = ghMuted,
                            )
                        }
                    }
                } else {
                    items(onlineReleases, key = { it.tagName ?: it.name ?: it.hashCode().toString() }) { rel ->
                        val relBlocks =
                            remember(rel.body) { parseReleaseMarkdownBlocks(rel.body.orEmpty()) }
                        val installedVer = BuildConfig.VERSION_NAME.toVersion()
                        val isNewer = rel.resolvedVersion() > installedVer
                        Column(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ghBg)
                                    .border(1.dp, ghBorder, RoundedCornerShape(8.dp)),
                        ) {
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .background(ghHeaderBg)
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rel.name?.takeIf { it.isNotBlank() } ?: rel.tagName.orEmpty(),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ghText,
                                    )
                                    rel.publishedAt?.take(10)?.let { date ->
                                        Text(
                                            text = "Published $date",
                                            fontSize = 11.sp,
                                            color = ghMuted,
                                        )
                                    }
                                }
                                Surface(
                                    shape = CircleShape,
                                    color =
                                        if (isNewer) Color(0xFF238636).copy(alpha = 0.18f)
                                        else ghAccent.copy(alpha = 0.16f),
                                    modifier =
                                        Modifier.border(
                                            1.dp,
                                            if (isNewer) Color(0xFF2EA043) else ghAccent,
                                            CircleShape,
                                        ),
                                ) {
                                    Text(
                                        text = if (isNewer) "Latest" else rel.tagName.orEmpty(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isNewer) Color(0xFF3FB950) else ghAccent,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                    )
                                }
                            }
                            HorizontalDivider(thickness = 1.dp, color = ghBorder)
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (relBlocks.isNotEmpty()) {
                                    ReleaseNoteBlocksColumn(blocks = relBlocks, isDark = isDark)
                                }
                                if (isNewer) {
                                    Button(
                                        onClick = { updateCandidate = rel },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                                    ) {
                                        Text("Install ${rel.tagName.orEmpty()}", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Authentic GitHub Markdown Preview Frame (`release/changelog.md` / `CHANGELOG.md`)
                item {
                    val fileNameLabel =
                        if (selectedMode == ChangelogFilterMode.HISTORY) "CHANGELOG.md"
                        else "release/changelog.md"
                    Column(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ghBg)
                                .border(1.dp, ghBorder, RoundedCornerShape(8.dp)),
                    ) {
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .background(ghHeaderBg)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Description,
                                    contentDescription = null,
                                    tint = ghMuted,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = fileNameLabel,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ghText,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ghBg,
                                modifier = Modifier.border(1.dp, ghBorder, RoundedCornerShape(6.dp)),
                            ) {
                                Text(
                                    text = "Preview",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ghMuted,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                        HorizontalDivider(thickness = 1.dp, color = ghBorder)
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            if (activeLocalBlocks.isEmpty()) {
                                Text(
                                    text = "No notes available for this view.",
                                    color = ghMuted,
                                )
                            } else {
                                ReleaseNoteBlocksColumn(blocks = activeLocalBlocks, isDark = isDark)
                            }
                        }
                    }
                }
            }
        }
    }

    updateCandidate?.let { rel ->
        UpdateDialog(onDismissRequest = { updateCandidate = null }, release = rel)
    }
}
