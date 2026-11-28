package com.devrayan.pengu

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.devrayan.pengu.database.objects.CommandTemplate
import com.devrayan.pengu.download.DownloaderV2
import com.devrayan.pengu.download.Task
import com.devrayan.pengu.ui.common.Route
import com.devrayan.pengu.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.devrayan.pengu.ui.page.downloadv2.configure.DownloadDialogViewModel.Action
import com.devrayan.pengu.ui.page.downloadv2.configure.DownloadDialogViewModel.SheetState
import com.devrayan.pengu.ui.page.downloadv2.configure.DownloadDialogViewModel.SheetValue
import com.devrayan.pengu.ui.page.shouldPopBackToHomeForSheet
import com.devrayan.pengu.util.DownloadUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test covering the Instagram paste -> download -> bottom navigation freeze bug:
 * verifies DownloadDialogViewModel resets SheetState after starting downloads, that active tasks
 * remain intact across tab switches, and that AppEntry only pops back to Home when Expanded.
 */
class NavigationDownloadBugTest {

    private class RecordingDownloader : DownloaderV2 {
        private val map = mutableStateMapOf<Task, Task.State>()

        override fun getTaskStateMap(): SnapshotStateMap<Task, Task.State> = map

        override fun cancel(task: Task): Boolean = false

        override fun restart(task: Task) {}

        override fun enqueue(task: Task) {
            val existing = map[task]
            if (existing != null && existing.downloadState !is Task.DownloadState.Completed) {
                return
            }
            map[task] =
                Task.State(
                    downloadState = Task.DownloadState.Running(taskId = task.id, progress = 0.25f),
                    videoInfo = null,
                    viewState = Task.ViewState(url = task.url, title = "Instagram Reel"),
                )
        }

        override fun enqueue(task: Task, state: Task.State) {
            map[task] = state
        }

        override fun remove(task: Task): Boolean = map.remove(task) != null
    }

    @Test
    fun instagramDownload_doesNotTrapNavigationOnHome() {
        val downloader = RecordingDownloader()
        val viewModel = DownloadDialogViewModel(downloader)
        val instagramUrl = "https://www.instagram.com/reel/C8xTestReel123/"

        // 1. User pastes Instagram URL -> sheet expands in Configure state
        viewModel.postAction(Action.ShowSheet(listOf(instagramUrl)))
        assertEquals(SheetValue.Expanded, viewModel.sheetValueFlow.value)
        assertTrue(viewModel.sheetStateFlow.value is SheetState.Configure)
        assertTrue(
            shouldPopBackToHomeForSheet(
                sheetValue = viewModel.sheetValueFlow.value,
                sheetState = viewModel.sheetStateFlow.value,
                currentRoute = Route.DOWNLOAD_QUEUE,
            )
        )

        // 2. Download starts with preset
        viewModel.postAction(
            Action.DownloadWithPreset(
                urlList = listOf(instagramUrl),
                preferences = DownloadUtil.DownloadPreferences.EMPTY,
            )
        )

        // Sheet closes AND resets out of Configure so tabs never snap back to Home
        assertEquals(SheetValue.Hidden, viewModel.sheetValueFlow.value)
        assertEquals(SheetState.InputUrl, viewModel.sheetStateFlow.value)
        assertEquals(1, downloader.getTaskStateMap().size)

        // 3. Repeatedly switching across all bottom navigation tabs must NEVER pop back to Home
        val allTabs =
            listOf(
                Route.DOWNLOAD_QUEUE,
                Route.DOWNLOADS,
                Route.SETTINGS_PAGE,
                Route.HOME,
                Route.DOWNLOAD_QUEUE,
                Route.DOWNLOADS,
                Route.SETTINGS_PAGE,
            )
        allTabs.forEach { tabRoute ->
            assertFalse(
                "Tab $tabRoute should stay open while download runs",
                shouldPopBackToHomeForSheet(
                    sheetValue = viewModel.sheetValueFlow.value,
                    sheetState = viewModel.sheetStateFlow.value,
                    currentRoute = tabRoute,
                ),
            )
        }

        // Active download state stays preserved in DownloaderV2 during navigation
        val activeEntry = downloader.getTaskStateMap().entries.single()
        assertEquals(instagramUrl, activeEntry.key.url)
        assertTrue(activeEntry.value.downloadState is Task.DownloadState.Running)
    }

    @Test
    fun runCommand_and_hideSheet_alsoResetConfigureState() {
        val downloader = RecordingDownloader()
        val viewModel = DownloadDialogViewModel(downloader)
        val instagramUrl = "https://www.instagram.com/p/C8xCommand/"

        viewModel.postAction(Action.ShowSheet(listOf(instagramUrl)))
        viewModel.postAction(Action.HideSheet)
        assertEquals(SheetValue.Hidden, viewModel.sheetValueFlow.value)
        assertEquals(SheetState.InputUrl, viewModel.sheetStateFlow.value)

        viewModel.postAction(Action.ShowSheet(listOf(instagramUrl)))
        viewModel.postAction(
            Action.RunCommand(
                url = instagramUrl,
                template = CommandTemplate(id = 1, name = "Custom", template = "-F"),
                preferences = DownloadUtil.DownloadPreferences.EMPTY,
            )
        )
        assertEquals(SheetValue.Hidden, viewModel.sheetValueFlow.value)
        assertEquals(SheetState.InputUrl, viewModel.sheetStateFlow.value)
        assertEquals(1, downloader.getTaskStateMap().size)
    }
}
