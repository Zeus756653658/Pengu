package com.devrayan.pengu.ui.page

import android.net.Uri
import android.webkit.CookieManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.devrayan.pengu.App
import com.devrayan.pengu.R
import com.devrayan.pengu.ui.common.HapticFeedback.slightHapticFeedback
import com.devrayan.pengu.ui.common.LocalDarkTheme
import com.devrayan.pengu.ui.common.LocalWindowWidthState
import com.devrayan.pengu.ui.common.Route
import com.devrayan.pengu.ui.common.animatedComposable
import com.devrayan.pengu.ui.common.animatedComposableVariant
import com.devrayan.pengu.ui.common.arg
import com.devrayan.pengu.ui.common.id
import com.devrayan.pengu.ui.common.slideInVerticallyComposable
import com.devrayan.pengu.ui.component.PenguBottomBar
import com.devrayan.pengu.ui.page.browser.BrowserPage
import com.devrayan.pengu.ui.page.command.TaskListPage
import com.devrayan.pengu.ui.page.command.TaskLogPage
import com.devrayan.pengu.ui.page.downloadv2.DownloadPageV2
import com.devrayan.pengu.ui.page.downloadv2.ProgressPage
import com.devrayan.pengu.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.devrayan.pengu.ui.page.player.MediaPlayerPage
import com.devrayan.pengu.ui.page.settings.SettingsPage
import com.devrayan.pengu.ui.page.settings.about.AboutPage
import com.devrayan.pengu.ui.page.settings.about.CreditsPage
import com.devrayan.pengu.ui.page.settings.about.UpdatePage
import com.devrayan.pengu.ui.page.settings.appearance.AppearancePreferences
import com.devrayan.pengu.ui.page.settings.appearance.DarkThemePreferences
import com.devrayan.pengu.ui.page.settings.appearance.LanguagePage
import com.devrayan.pengu.ui.page.settings.command.TemplateEditPage
import com.devrayan.pengu.ui.page.settings.command.TemplateListPage
import com.devrayan.pengu.ui.page.settings.directory.DownloadDirectoryPreferences
import com.devrayan.pengu.ui.page.settings.format.DownloadFormatPreferences
import com.devrayan.pengu.ui.page.settings.format.SubtitlePreference
import com.devrayan.pengu.ui.page.settings.general.GeneralDownloadPreferences
import com.devrayan.pengu.ui.page.settings.interaction.InteractionPreferencePage
import com.devrayan.pengu.ui.page.settings.network.CookieProfilePage
import com.devrayan.pengu.ui.page.settings.network.CookiesViewModel
import com.devrayan.pengu.ui.page.settings.network.NetworkPreferences
import com.devrayan.pengu.ui.page.settings.network.WebViewPage
import com.devrayan.pengu.ui.page.settings.troubleshooting.TroubleShootingPage
import com.devrayan.pengu.ui.page.tools.MediaToolsPage
import com.devrayan.pengu.ui.page.videolist.VideoListPage
import com.devrayan.pengu.util.DatabaseUtil
import com.devrayan.pengu.util.FileUtil
import com.devrayan.pengu.util.findURLsFromString
import com.devrayan.pengu.util.makeToast
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

private const val TAG = "HomeEntry"

private val TopDestinations =
    listOf(Route.HOME, Route.DOWNLOAD_QUEUE, Route.SETTINGS_PAGE, Route.DOWNLOADS)

@Composable
fun AppEntry(dialogViewModel: DownloadDialogViewModel) {

    val navController = rememberNavController()
    val context = LocalContext.current
    val view = LocalView.current
    val windowWidth = LocalWindowWidthState.current
    val windowInfo = LocalWindowInfo.current
    val clipboardManager = LocalClipboardManager.current
    val isDark = LocalDarkTheme.current.isDarkTheme()

    val sheetState by dialogViewModel.sheetStateFlow.collectAsStateWithLifecycle()
    val sheetValue by dialogViewModel.sheetValueFlow.collectAsStateWithLifecycle()
    val cookiesViewModel: CookiesViewModel = koinViewModel()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val versionReport = App.packageInfo.versionName.toString()
    val appName = stringResource(R.string.app_name)
    val scope = rememberCoroutineScope()

    var showSplash by rememberSaveable { mutableStateOf(true) }
    var detectedClipboardUrl by remember { mutableStateOf<String?>(null) }
    var lastDismissedClipboardUrl by rememberSaveable { mutableStateOf<String?>(null) }

    val onNavigateBack: () -> Unit = {
        with(navController) {
            if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                popBackStack()
            }
        }
    }

    val openPathInPlayerOrExternal: (String) -> Unit = { path ->
        scope.launch {
            val info = withContext(Dispatchers.IO) { DatabaseUtil.getInfoByPath(path) }
            if (info != null && File(info.videoPath).exists()) {
                navController.navigate(Route.PLAYER id info.id) { launchSingleTop = true }
            } else {
                FileUtil.openFile(path = path) { context.makeToast(R.string.file_unavailable) }
            }
        }
    }

    val navigateToBrowser: (String?) -> Unit = { url ->
        val target =
            if (!url.isNullOrBlank()) "${Route.BROWSER}?${Route.BROWSER_URL}=${Uri.encode(url)}"
            else Route.BROWSER
        navController.navigate(target) { launchSingleTop = true }
    }

    if (sheetState is DownloadDialogViewModel.SheetState.Configure) {
        if (navController.currentDestination?.route != Route.HOME) {
            navController.popBackStack(route = Route.HOME, inclusive = false, saveState = true)
        }
    }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    var currentTopDestination by rememberSaveable { mutableStateOf(currentRoute) }

    LaunchedEffect(currentRoute) {
        if (currentRoute in TopDestinations) {
            currentTopDestination = currentRoute
        }
    }

    LaunchedEffect(windowInfo.isWindowFocused) {
        if (windowInfo.isWindowFocused) {
            delay(250)
            val clipText = clipboardManager.getText()?.text.orEmpty()
            val url = findURLsFromString(clipText, true).firstOrNull()?.trim()
            if (!url.isNullOrBlank() && url != lastDismissedClipboardUrl) {
                detectedClipboardUrl = url
            }
        }
    }

    val showClipboardBanner =
        !showSplash &&
            detectedClipboardUrl != null &&
            sheetValue != DownloadDialogViewModel.SheetValue.Expanded &&
            currentRoute != null &&
            !currentRoute.startsWith(Route.BROWSER) &&
            currentRoute != Route.COOKIE_GENERATOR_WEBVIEW

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavigationDrawer(
            windowWidth = windowWidth,
            drawerState = drawerState,
            currentRoute = currentRoute,
            currentTopDestination = currentTopDestination,
            showQuickSettings = true,
            gesturesEnabled = currentRoute == Route.HOME,
            onDismissRequest = { drawerState.close() },
            onNavigateToRoute = {
                if (currentRoute != it) {
                    navController.navigate(it) {
                        launchSingleTop = true
                        popUpTo(route = Route.HOME)
                    }
                }
            },
            footer = {
                Text(
                    appName + "\n" + versionReport + "\n" + currentRoute,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            },
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    if (currentRoute in TopDestinations && windowWidth == WindowWidthSizeClass.Compact) {
                        PenguBottomBar(
                            currentRoute = currentRoute,
                            onNavigate = { route ->
                                if (currentRoute != route) {
                                    navController.navigate(route) {
                                        launchSingleTop = true
                                        popUpTo(route = Route.HOME)
                                    }
                                }
                            },
                        )
                    }
                },
            ) { innerPadding ->
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding()),
                ) {
                    NavHost(
                        modifier = Modifier.align(Alignment.Center),
                        navController = navController,
                        startDestination = Route.HOME,
                    ) {
                        animatedComposable(Route.HOME) {
                            DownloadPageV2(
                                dialogViewModel = dialogViewModel,
                                onMenuOpen = {
                                    view.slightHapticFeedback()
                                    scope.launch { drawerState.open() }
                                },
                                onNavigateToRoute = { route ->
                                    if (currentRoute != route) {
                                        navController.navigate(route) {
                                            launchSingleTop = true
                                            popUpTo(route = Route.HOME)
                                        }
                                    }
                                },
                                onOpenBrowser = { url -> navigateToBrowser(url) },
                                onOpenTools = {
                                    navController.navigate(Route.TOOLS) { launchSingleTop = true }
                                },
                                onOpenPlayer = { id ->
                                    navController.navigate(Route.PLAYER id id) {
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                        animatedComposable(Route.DOWNLOAD_QUEUE) {
                            ProgressPage(
                                onOpenCompletedFile = openPathInPlayerOrExternal,
                                onNavigateToCustomTasks = {
                                    navController.navigate(Route.TASK_LIST) {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateHome = {
                                    navController.navigate(Route.HOME) {
                                        launchSingleTop = true
                                        popUpTo(route = Route.HOME)
                                    }
                                },
                            )
                        }
                        animatedComposable(Route.DOWNLOADS) {
                            VideoListPage(
                                onOpenPlayer = { id ->
                                    navController.navigate(Route.PLAYER id id) {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateBack = onNavigateBack,
                            )
                        }
                        animatedComposableVariant(
                            route = Route.PLAYER arg Route.MEDIA_ID,
                            arguments =
                                listOf(navArgument(Route.MEDIA_ID) { type = NavType.IntType }),
                        ) { entry ->
                            MediaPlayerPage(
                                mediaId = entry.arguments?.getInt(Route.MEDIA_ID) ?: -1,
                                onNavigateBack = onNavigateBack,
                                onOpenTools = {
                                    navController.navigate(Route.TOOLS) { launchSingleTop = true }
                                },
                            )
                        }
                        animatedComposable(
                            route = "${Route.BROWSER}?${Route.BROWSER_URL}={${Route.BROWSER_URL}}",
                            arguments =
                                listOf(
                                    navArgument(Route.BROWSER_URL) {
                                        type = NavType.StringType
                                        defaultValue = "https://m.youtube.com"
                                    }
                                ),
                        ) { entry ->
                            val encoded = entry.arguments?.getString(Route.BROWSER_URL)
                            val decodedUrl =
                                encoded?.let { Uri.decode(it) } ?: "https://m.youtube.com"
                            BrowserPage(
                                initialUrl = decodedUrl,
                                onNavigateBack = onNavigateBack,
                                onDownloadUrl = { url ->
                                    if (navController.currentDestination?.route != Route.HOME) {
                                        navController.popBackStack(
                                            route = Route.HOME,
                                            inclusive = false,
                                            saveState = true,
                                        )
                                    }
                                    dialogViewModel.postAction(
                                        DownloadDialogViewModel.Action.ShowSheet(listOf(url))
                                    )
                                },
                            )
                        }
                        animatedComposableVariant(Route.TOOLS) {
                            MediaToolsPage(
                                onNavigateBack = onNavigateBack,
                                onOpenDownloadedFile = openPathInPlayerOrExternal,
                            )
                        }
                        animatedComposableVariant(Route.TASK_LIST) {
                            TaskListPage(
                                onNavigateBack = onNavigateBack,
                                onNavigateToDetail = { navController.navigate(Route.TASK_LOG id it) },
                            )
                        }
                        slideInVerticallyComposable(
                            Route.TASK_LOG arg Route.TASK_HASHCODE,
                            arguments =
                                listOf(navArgument(Route.TASK_HASHCODE) { type = NavType.IntType }),
                        ) {
                            TaskLogPage(
                                onNavigateBack = onNavigateBack,
                                taskHashCode = it.arguments?.getInt(Route.TASK_HASHCODE) ?: -1,
                            )
                        }

                        settingsGraph(
                            onNavigateBack = onNavigateBack,
                            onNavigateTo = { route ->
                                navController.navigate(route = route) { launchSingleTop = true }
                            },
                            cookiesViewModel = cookiesViewModel,
                        )
                    }

                    AnimatedVisibility(
                        visible = showClipboardBanner,
                        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
                        modifier =
                            Modifier.align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        val url = detectedClipboardUrl.orEmpty()
                        ClipboardDetectedBanner(
                            url = url,
                            isDark = isDark,
                            onDownloadClick = {
                                view.slightHapticFeedback()
                                lastDismissedClipboardUrl = url
                                detectedClipboardUrl = null
                                if (navController.currentDestination?.route != Route.HOME) {
                                    navController.popBackStack(
                                        route = Route.HOME,
                                        inclusive = false,
                                        saveState = true,
                                    )
                                }
                                dialogViewModel.postAction(
                                    DownloadDialogViewModel.Action.ShowSheet(listOf(url))
                                )
                            },
                            onDismiss = {
                                lastDismissedClipboardUrl = url
                                detectedClipboardUrl = null
                            },
                        )
                    }

                    AppUpdater()
                    YtdlpUpdater()
                }
            }
        }

        PenguSplashOverlay(
            visible = showSplash,
            version = versionReport,
            isDark = isDark,
            onFinished = { showSplash = false },
        )
    }
}

@Composable
private fun ClipboardDetectedBanner(
    url: String,
    isDark: Boolean,
    onDownloadClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier =
            Modifier.fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(20.dp))
                .clickable(onClick = onDownloadClick),
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF1F1F26) else Color(0xFFFFFFFF)
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier.size(38.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF6366F1), Color(0xFFD946EF), Color(0xFFF43F5E))
                            )
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentPaste,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Link detected in clipboard",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = url,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isDark) Color(0xFFAEAEB2) else Color(0xFF6B7280),
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF6366F1),
                modifier = Modifier.clickable(onClick = onDownloadClick),
            ) {
                Text(
                    text = "Download",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = if (isDark) Color(0xFF8E8E93) else Color(0xFF9CA3AF),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun PenguSplashOverlay(
    visible: Boolean,
    version: String,
    isDark: Boolean,
    onFinished: () -> Unit,
) {
    val logoScale = remember { Animatable(0.74f) }

    LaunchedEffect(visible) {
        if (visible) {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
            )
            delay(360)
            onFinished()
        }
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(260)),
        modifier = Modifier.fillMaxSize().zIndex(30f),
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            if (isDark) listOf(Color(0xFF0A0A0E), Color(0xFF1A162B))
                            else listOf(Color(0xFFFFFFFF), Color(0xFFF3E8FF))
                        )
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.scale(logoScale.value),
            ) {
                Box(
                    modifier =
                        Modifier.size(104.dp)
                            .shadow(18.dp, RoundedCornerShape(28.dp))
                            .clip(RoundedCornerShape(28.dp))
                            .background(if (isDark) Color(0xFF23232E) else Color.White)
                            .padding(14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.pengu_logo),
                        contentDescription = "Pengu",
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Pengu",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Video & Audio Downloader",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) Color(0xFFA1A1AA) else Color(0xFF6B7280),
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF6366F1).copy(alpha = 0.14f),
                ) {
                    Text(
                        text = "v$version",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF6366F1),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

fun NavGraphBuilder.settingsGraph(
    onNavigateBack: () -> Unit,
    onNavigateTo: (route: String) -> Unit,
    cookiesViewModel: CookiesViewModel,
) {
    navigation(startDestination = Route.SETTINGS_PAGE, route = Route.SETTINGS) {
        animatedComposable(Route.DOWNLOAD_DIRECTORY) {
            DownloadDirectoryPreferences(onNavigateBack)
        }
        animatedComposable(Route.SETTINGS_PAGE) {
            SettingsPage(onNavigateBack = onNavigateBack, onNavigateTo = onNavigateTo)
        }
        animatedComposable(Route.GENERAL_DOWNLOAD_PREFERENCES) {
            GeneralDownloadPreferences(onNavigateBack = { onNavigateBack() }) {
                onNavigateTo(Route.TEMPLATE)
            }
        }
        animatedComposable(Route.DOWNLOAD_FORMAT) {
            DownloadFormatPreferences(onNavigateBack = onNavigateBack) {
                onNavigateTo(Route.SUBTITLE_PREFERENCES)
            }
        }
        animatedComposable(Route.SUBTITLE_PREFERENCES) { SubtitlePreference { onNavigateBack() } }
        animatedComposable(Route.ABOUT) {
            AboutPage(
                onNavigateBack = onNavigateBack,
                onNavigateToCreditsPage = { onNavigateTo(Route.CREDITS) },
                onNavigateToUpdatePage = { onNavigateTo(Route.AUTO_UPDATE) },
            )
        }
        animatedComposable(Route.CREDITS) { CreditsPage(onNavigateBack) }
        animatedComposable(Route.AUTO_UPDATE) { UpdatePage(onNavigateBack) }
        animatedComposable(Route.APPEARANCE) {
            AppearancePreferences(onNavigateBack = onNavigateBack, onNavigateTo = onNavigateTo)
        }
        animatedComposable(Route.INTERACTION) { InteractionPreferencePage(onBack = onNavigateBack) }
        animatedComposable(Route.LANGUAGES) { LanguagePage { onNavigateBack() } }
        animatedComposable(Route.DOWNLOAD_DIRECTORY) {
            DownloadDirectoryPreferences { onNavigateBack() }
        }
        animatedComposable(Route.TEMPLATE) {
            TemplateListPage(onNavigateBack = onNavigateBack) {
                onNavigateTo(Route.TEMPLATE_EDIT id it)
            }
        }
        animatedComposable(
            Route.TEMPLATE_EDIT arg Route.TEMPLATE_ID,
            arguments = listOf(navArgument(Route.TEMPLATE_ID) { type = NavType.IntType }),
        ) {
            TemplateEditPage(onNavigateBack, it.arguments?.getInt(Route.TEMPLATE_ID) ?: -1)
        }
        animatedComposable(Route.DARK_THEME) { DarkThemePreferences { onNavigateBack() } }
        animatedComposable(Route.NETWORK_PREFERENCES) {
            NetworkPreferences(
                navigateToCookieProfilePage = { onNavigateTo(Route.COOKIE_PROFILE) }
            ) {
                onNavigateBack()
            }
        }
        animatedComposable(Route.COOKIE_PROFILE) {
            CookieProfilePage(
                cookiesViewModel = cookiesViewModel,
                navigateToCookieGeneratorPage = { onNavigateTo(Route.COOKIE_GENERATOR_WEBVIEW) },
            ) {
                onNavigateBack()
            }
        }
        animatedComposable(Route.COOKIE_GENERATOR_WEBVIEW) {
            WebViewPage(cookiesViewModel = cookiesViewModel) {
                onNavigateBack()
                CookieManager.getInstance().flush()
            }
        }
        animatedComposable(Route.TROUBLESHOOTING) {
            TroubleShootingPage(onNavigateTo = onNavigateTo, onBack = onNavigateBack)
        }
    }
}
