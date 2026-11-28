package com.devrayan.pengu.ui.page

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.devrayan.pengu.R
import com.devrayan.pengu.util.PreferenceUtil
import com.devrayan.pengu.util.ToastUtil
import com.devrayan.pengu.util.UpdateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppUpdater() {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var showUpdateDialog by rememberSaveable { mutableStateOf(false) }
    var currentDownloadStatus by remember {
        mutableStateOf(UpdateUtil.DownloadStatus.NotYet as UpdateUtil.DownloadStatus)
    }
    val scope = rememberCoroutineScope()
    var updateJob by remember { mutableStateOf<Job?>(null) }
    var release by remember { mutableStateOf(UpdateUtil.Release()) }
    var dismissedTag by rememberSaveable { mutableStateOf("") }
    var lastCheckMs by rememberSaveable { mutableLongStateOf(0L) }

    val installIfAllowed: () -> Unit = {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()) {
            UpdateUtil.installLatestApk()
        }
    }

    val settings =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            installIfAllowed()
        }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
            if (result) {
                UpdateUtil.installLatestApk()
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        settings.launch(
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
        }

    fun checkInBackground(force: Boolean = false) {
        if (!PreferenceUtil.isNetworkAvailableForDownload() || !PreferenceUtil.isAutoUpdateEnabled()) {
            return
        }
        val now = System.currentTimeMillis()
        if (!force && now - lastCheckMs < 60 * 60 * 1000L) return
        lastCheckMs = now
        scope.launch(Dispatchers.IO) {
            runCatching {
                    UpdateUtil.checkForUpdate()?.let { candidate ->
                        val key = candidate.tagName ?: candidate.name.orEmpty()
                        withContext(Dispatchers.Main) {
                            if (key.isNotBlank() && key != dismissedTag && !showUpdateDialog) {
                                release = candidate
                                showUpdateDialog = true
                            }
                        }
                    }
                }
                .onFailure { it.printStackTrace() }
        }
    }

    LaunchedEffect(Unit) { checkInBackground(force = true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkInBackground(force = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showUpdateDialog) {
        UpdateDialogImpl(
            onDismissRequest = {
                dismissedTag = release.tagName ?: release.name.orEmpty()
                showUpdateDialog = false
                updateJob?.cancel()
                updateJob = null
                currentDownloadStatus = UpdateUtil.DownloadStatus.NotYet
            },
            title = release.name?.takeIf { it.isNotBlank() } ?: release.tagName.orEmpty(),
            onConfirmUpdate = {
                updateJob?.cancel()
                updateJob =
                    scope.launch(Dispatchers.IO) {
                        runCatching {
                                UpdateUtil.downloadApk(release = release).collect { downloadStatus
                                    ->
                                    withContext(Dispatchers.Main) {
                                        currentDownloadStatus = downloadStatus
                                    }
                                }
                            }
                            .onFailure {
                                if (it is kotlinx.coroutines.CancellationException) return@onFailure
                                it.printStackTrace()
                                withContext(Dispatchers.Main) {
                                    currentDownloadStatus = UpdateUtil.DownloadStatus.NotYet
                                }
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
            onConfirmInstall = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (context.packageManager.canRequestPackageInstalls()) {
                        UpdateUtil.installLatestApk()
                    } else {
                        launcher.launch(Manifest.permission.REQUEST_INSTALL_PACKAGES)
                    }
                } else {
                    UpdateUtil.installLatestApk()
                }
            },
        )
    }
}
