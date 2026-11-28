package com.devrayan.pengu

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devrayan.pengu.ui.common.LocalDarkTheme
import com.devrayan.pengu.ui.common.SettingsProvider
import com.devrayan.pengu.ui.page.downloadv2.VidSnapQuickSheet
import com.devrayan.pengu.ui.page.downloadv2.configure.Config
import com.devrayan.pengu.ui.page.downloadv2.configure.DownloadDialog
import com.devrayan.pengu.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.devrayan.pengu.ui.page.downloadv2.configure.DownloadDialogViewModel.Action
import com.devrayan.pengu.ui.page.downloadv2.configure.DownloadDialogViewModel.SelectionState
import com.devrayan.pengu.ui.page.downloadv2.configure.FormatPage
import com.devrayan.pengu.ui.page.downloadv2.configure.PlaylistSelectionPage
import com.devrayan.pengu.ui.theme.SealTheme
import com.devrayan.pengu.util.DownloadUtil
import com.devrayan.pengu.util.PreferenceUtil
import com.devrayan.pengu.util.matchUrlFromSharedText
import com.devrayan.pengu.util.setLanguage
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.androidx.viewmodel.ext.android.getViewModel

private const val TAG = "QuickDownloadActivity"

/**
 * Apple/VidSnap-style overlay activity for Share -> Pengu links without leaving the source app.
 */
class QuickDownloadActivity : ComponentActivity() {
    private var sharedUrlCached: String = ""

    private fun Intent.getSharedURL(): String? {
        val intent = this

        return when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.dataString
            }

            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedContent ->
                    intent.removeExtra(Intent.EXTRA_TEXT)
                    matchUrlFromSharedText(sharedContent)
                }
            }

            else -> {
                null
            }
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getSharedURL()?.let { sharedUrlCached = it }

        if (sharedUrlCached.isEmpty()) {
            finish()
            return
        }

        App.startService()

        enableEdgeToEdge()

        window.run {
            setBackgroundDrawable(ColorDrawable(0))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            } else {
                setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
            }
        }

        if (Build.VERSION.SDK_INT < 33) {
            runBlocking { setLanguage(PreferenceUtil.getLocaleFromPreference()) }
        }

        val viewModel: DownloadDialogViewModel = getViewModel()

        setContent {
            SettingsProvider(calculateWindowSizeClass(this).widthSizeClass) {
                SealTheme(
                    darkTheme = LocalDarkTheme.current.isDarkTheme(),
                    isHighContrastModeEnabled = LocalDarkTheme.current.isHighContrastModeEnabled,
                ) {
                    var preferences by remember {
                        mutableStateOf(DownloadUtil.DownloadPreferences.createFromPreferences())
                    }

                    var showQuickShareSheet by remember { mutableStateOf(true) }

                    val sheetValue = viewModel.sheetValueFlow.collectAsStateWithLifecycle().value
                    val state = viewModel.sheetStateFlow.collectAsStateWithLifecycle().value
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    val selectionState =
                        viewModel.selectionStateFlow.collectAsStateWithLifecycle().value

                    var showDialog by remember { mutableStateOf(false) }

                    LaunchedEffect(sheetValue, selectionState) {
                        if (sheetValue == DownloadDialogViewModel.SheetValue.Expanded) {
                            showDialog = true
                        } else if (sheetValue == DownloadDialogViewModel.SheetValue.Hidden && showDialog) {
                            launch { sheetState.hide() }
                                .invokeOnCompletion {
                                    showDialog = false
                                    if (selectionState == SelectionState.Idle && !showQuickShareSheet) {
                                        this@QuickDownloadActivity.finish()
                                    }
                                }
                        }
                    }

                    if (showQuickShareSheet) {
                        VidSnapQuickSheet(
                            initialAudioMode = preferences.extractAudio,
                            initialUrl = sharedUrlCached,
                            onDismiss = {
                                showQuickShareSheet = false
                                if (
                                    !showDialog &&
                                        sheetValue == DownloadDialogViewModel.SheetValue.Hidden &&
                                        selectionState == SelectionState.Idle
                                ) {
                                    this@QuickDownloadActivity.finish()
                                }
                            },
                            onStartPresetDownload = { url, presetPrefs ->
                                preferences = presetPrefs
                                showQuickShareSheet = false
                                viewModel.postAction(
                                    Action.DownloadWithPreset(listOf(url), presetPrefs)
                                )
                                this@QuickDownloadActivity.finish()
                            },
                            onOpenFullOptions = { url ->
                                val target = url.ifBlank { sharedUrlCached }
                                viewModel.postAction(Action.ShowSheet(listOf(target)))
                                showQuickShareSheet = false
                            },
                            onOpenBrowserForSite = { siteUrl ->
                                startActivity(
                                    Intent(this@QuickDownloadActivity, MainActivity::class.java).apply {
                                        action = Intent.ACTION_VIEW
                                        data = android.net.Uri.parse(siteUrl)
                                    }
                                )
                                this@QuickDownloadActivity.finish()
                            },
                        )
                    }

                    if (showDialog) {
                        DownloadDialog(
                            state = state,
                            sheetState = sheetState,
                            config = Config(),
                            preferences = preferences,
                            onPreferencesUpdate = { preferences = it },
                            onActionPost = { viewModel.postAction(it) },
                        )
                    }

                    when (selectionState) {
                        is SelectionState.FormatSelection ->
                            FormatPage(
                                state = selectionState,
                                onDismissRequest = {
                                    viewModel.postAction(Action.Reset)
                                    this.finish()
                                },
                            )

                        SelectionState.Idle -> {}
                        is SelectionState.PlaylistSelection -> {
                            PlaylistSelectionPage(
                                state = selectionState,
                                onDismissRequest = {
                                    viewModel.postAction(Action.Reset)
                                    this.finish()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
