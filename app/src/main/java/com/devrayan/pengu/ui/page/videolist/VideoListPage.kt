package com.devrayan.pengu.ui.page.videolist

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devrayan.pengu.ui.common.LocalDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devrayan.pengu.App
import com.devrayan.pengu.R
import com.devrayan.pengu.database.backup.BackupUtil
import com.devrayan.pengu.database.backup.BackupUtil.BackupDestination.Clipboard
import com.devrayan.pengu.database.backup.BackupUtil.BackupDestination.File
import com.devrayan.pengu.database.backup.BackupUtil.toJsonString
import com.devrayan.pengu.database.backup.BackupUtil.toURLListString
import com.devrayan.pengu.database.objects.DownloadedVideoInfo
import com.devrayan.pengu.ui.common.HapticFeedback.slightHapticFeedback
import com.devrayan.pengu.ui.common.LocalWindowWidthState
import com.devrayan.pengu.ui.component.BackButton
import com.devrayan.pengu.ui.component.CheckBoxItem
import com.devrayan.pengu.ui.component.ConfirmButton
import com.devrayan.pengu.ui.component.DismissButton
import com.devrayan.pengu.ui.component.MediaListItem
import com.devrayan.pengu.ui.component.SealDialog
import com.devrayan.pengu.ui.component.SealSearchBar
import com.devrayan.pengu.ui.component.VideoFilterChip
import com.devrayan.pengu.ui.svg.DynamicColorImageVectors
import com.devrayan.pengu.ui.svg.drawablevectors.videoSteaming
import com.devrayan.pengu.util.AUDIO_REGEX
import com.devrayan.pengu.util.FileUtil
import com.devrayan.pengu.util.ToastUtil
import com.devrayan.pengu.util.toFileSizeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

fun DownloadedVideoInfo.filterByType(
    videoFilter: Boolean = false,
    audioFilter: Boolean = true,
): Boolean {
    return if (!(videoFilter || audioFilter)) true
    else if (audioFilter) this.videoPath.contains(Regex(AUDIO_REGEX))
    else !this.videoPath.contains(Regex(AUDIO_REGEX))
}

fun DownloadedVideoInfo.filterSort(
    viewState: VideoListViewModel.VideoListViewState,
    filterSet: Set<String>,
): Boolean {
    return filterByType(videoFilter = viewState.videoFilter, audioFilter = viewState.audioFilter) &&
        filterByExtractor(filterSet.elementAtOrNull(viewState.activeFilterIndex))
}

fun DownloadedVideoInfo.filterByExtractor(extractor: String?): Boolean {
    return extractor.isNullOrEmpty() || (this.extractor == extractor)
}

private const val TAG = "VideoListPage"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListPage(
    viewModel: VideoListViewModel = koinViewModel(),
    onOpenPlayer: (Int) -> Unit = {},
    onNavigateBack: () -> Unit,
) {
    val viewState by viewModel.stateFlow.collectAsStateWithLifecycle()
    val fullVideoList by viewModel.videoListFlow.collectAsStateWithLifecycle(emptyList())
    val searchedVideoList by
        viewModel.searchedVideoListFlow.collectAsStateWithLifecycle(emptyList())

    val videoList = if (viewState.isSearching) searchedVideoList else fullVideoList
    val filterSet by viewModel.filterSetFlow.collectAsState(mutableSetOf())

    val scrollBehavior =
        if (fullVideoList.isNotEmpty())
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
                rememberTopAppBarState(),
                canScroll = { true },
            )
        else TopAppBarDefaults.pinnedScrollBehavior()

    val scope = rememberCoroutineScope()
    val softKeyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val fileSizeMap by
        viewModel.fileSizeMapFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    val sheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
    val hostState = remember { SnackbarHostState() }

    var currentVideoInfo by remember { mutableStateOf(DownloadedVideoInfo()) }

    var isSelectEnabled by remember { mutableStateOf(false) }
    var showRemoveMultipleItemsDialog by remember { mutableStateOf(false) }

    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()

    @Composable
    fun FilterChips(modifier: Modifier = Modifier) {
        Row(modifier.horizontalScroll(rememberScrollState()).selectableGroup()) {
            Row(modifier = Modifier.padding(horizontal = 12.dp)) {
                for (i in 0 until filterSet.size) {
                    VideoFilterChip(
                        selected = viewState.activeFilterIndex == i,
                        onClick = { viewModel.clickExtractorFilter(i) },
                        label = filterSet.elementAt(i),
                    )
                }
            }
        }
    }

    val selectedItemIds = remember(videoList, viewState) { mutableStateListOf<Int>() }

    LaunchedEffect(isSelectEnabled) {
        if (!isSelectEnabled) {
            delay(200)
            selectedItemIds.clear()
        }
    }

    val selectedVideoCount =
        remember(selectedItemIds.size) {
            mutableIntStateOf(
                videoList.count { info ->
                    selectedItemIds.contains(info.id) &&
                        info.filterByType(videoFilter = true, audioFilter = false)
                }
            )
        }
    val selectedAudioCount =
        remember(selectedItemIds.size) {
            mutableIntStateOf(
                videoList.count { info ->
                    selectedItemIds.contains(info.id) &&
                        info.filterByType(videoFilter = false, audioFilter = true)
                }
            )
        }

    val selectedFileSizeSum by
        remember(selectedItemIds.size) {
            derivedStateOf {
                selectedItemIds.fold(0L) { acc: Long, id: Int ->
                    acc + fileSizeMap.getOrElse(id) { 0L }
                }
            }
        }

    val visibleItemCount =
        remember(videoList, viewState) {
            mutableIntStateOf(videoList.count { it.filterSort(viewState, filterSet) })
        }

    val checkBoxState by
        remember(selectedItemIds, visibleItemCount) {
            derivedStateOf {
                if (selectedItemIds.isEmpty()) ToggleableState.Off
                else if (
                    selectedItemIds.size == visibleItemCount.intValue &&
                        selectedItemIds.isNotEmpty()
                )
                    ToggleableState.On
                else ToggleableState.Indeterminate
            }
        }

    var showRemoveDialog by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }

    BackHandler(isSelectEnabled || viewState.isSearching) {
        if (isSelectEnabled) {
            isSelectEnabled = false
        } else {
            viewModel.toggleSearch(false)
        }
    }

    LaunchedEffect(sheetState.targetValue, isSelectEnabled) {
        if (showBottomSheet || isSelectEnabled) {
            softKeyboardController?.hide()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(modifier = Modifier, text = stringResource(R.string.downloads_history))
                },
                navigationIcon = { BackButton { onNavigateBack() } },
                actions = {
                    Row {
                        if (fullVideoList.isNotEmpty()) {
                            IconToggleButton(
                                modifier = Modifier,
                                onCheckedChange = {
                                    view.slightHapticFeedback()
                                    viewModel.toggleSearch(it)
                                    if (it) {
                                        scope.launch {
                                            delay(50)
                                            lazyListState.animateScrollToItem(0)
                                        }
                                    }
                                },
                                checked = viewState.isSearching,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = stringResource(R.string.search),
                                )
                            }
                        }
                        var expanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                            IconButton(onClick = { expanded = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription =
                                        stringResource(id = R.string.show_more_actions),
                                )
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                            ) {
                                if (visibleItemCount.intValue > 0) {
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                imageVector =
                                                    Icons.AutoMirrored.Outlined.DriveFileMove,
                                                contentDescription = null,
                                            )
                                        },
                                        text = {
                                            Text(text = stringResource(id = R.string.export_backup))
                                        },
                                        onClick = {
                                            showExportDialog = true
                                            expanded = false
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Restore,
                                            contentDescription = null,
                                        )
                                    },
                                    text = {
                                        Text(text = stringResource(id = R.string.import_backup))
                                    },
                                    onClick = {
                                        showImportDialog = true
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            AnimatedVisibility(
                isSelectEnabled,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                BottomAppBar(modifier = Modifier) {
                    val selectAllText = stringResource(R.string.select_all)
                    TriStateCheckbox(
                        modifier = Modifier.semantics { this.contentDescription = selectAllText },
                        state = checkBoxState,
                        onClick = {
                            view.slightHapticFeedback()
                            when (checkBoxState) {
                                ToggleableState.On -> selectedItemIds.clear()
                                else -> {
                                    for (item in videoList) {
                                        if (
                                            !selectedItemIds.contains(item.id) &&
                                                item.filterSort(viewState, filterSet)
                                        ) {
                                            selectedItemIds.add(item.id)
                                        }
                                    }
                                }
                            }
                        },
                    )
                    Text(
                        modifier = Modifier.weight(1f),
                        text =
                            stringResource(R.string.multiselect_item_count)
                                .format(selectedVideoCount.intValue, selectedAudioCount.intValue),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    IconButton(
                        onClick = {
                            view.slightHapticFeedback()
                            showRemoveMultipleItemsDialog = true
                        },
                        enabled = selectedItemIds.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteSweep,
                            contentDescription = stringResource(id = R.string.remove),
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = hostState) },
    ) { innerPadding ->
        if (fullVideoList.isEmpty())
            Box(modifier = Modifier.fillMaxSize()) {
                val painter =
                    rememberVectorPainter(image = DynamicColorImageVectors.videoSteaming())
                Column(
                    modifier = Modifier.align(Alignment.Center).widthIn(max = 360.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier =
                            Modifier.padding(vertical = 20.dp)
                                .fillMaxWidth(0.5f)
                                .widthIn(max = 240.dp),
                    )
                    Text(
                        text = stringResource(R.string.no_downloaded_media),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

        val cellCount =
            when (LocalWindowWidthState.current) {
                WindowWidthSizeClass.Expanded -> 2
                else -> 1
            }
        val span: (LazyGridItemSpanScope) -> GridItemSpan = { GridItemSpan(cellCount) }
        LazyColumn(modifier = Modifier, state = lazyListState, contentPadding = innerPadding) {
            if (fullVideoList.isNotEmpty()) {
                item {
                    val isDark = LocalDarkTheme.current.isDarkTheme()
                    val totalDownloadsBytes = remember(fileSizeMap) { fileSizeMap.values.sum() }
                    val deviceTotalBytes =
                        remember {
                            runCatching {
                                    val dir =
                                        java.io.File(App.videoDownloadDir).takeIf { it.exists() }
                                            ?: context.filesDir
                                    android.os.StatFs(dir.absolutePath).totalBytes
                                }
                                .getOrElse { 1L }
                                .coerceAtLeast(1L)
                        }
                    val usageFraction =
                        (totalDownloadsBytes.toFloat() / deviceTotalBytes.toFloat())
                            .coerceIn(0.02f, 1f)

                    Column(modifier = Modifier.fillMaxWidth()) {
                        AnimatedVisibility(visible = viewState.isSearching) {
                            SealSearchBar(
                                modifier =
                                    Modifier.padding(horizontal = 12.dp).padding(vertical = 8.dp),
                                text = viewState.searchText,
                                placeholderText = stringResource(R.string.search_in_downloads),
                                onValueChange = viewModel::updateSearchText,
                            )
                        }

                        // VidSnap Screen 4: All / Videos / MP3 segmented tabs
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isDark) Color(0xFF1C1C22) else Color(0xFFF3F4F6))
                                    .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val selectedTypeIdx =
                                when {
                                    viewState.videoFilter && !viewState.audioFilter -> 1
                                    viewState.audioFilter && !viewState.videoFilter -> 2
                                    else -> 0
                                }
                            listOf("All", "Videos", "MP3").forEachIndexed { idx, label ->
                                val active = selectedTypeIdx == idx
                                Box(
                                    modifier =
                                        Modifier.weight(1f)
                                            .clip(RoundedCornerShape(11.dp))
                                            .background(if (active) Color.White else Color.Transparent)
                                            .clickable {
                                                view.slightHapticFeedback()
                                                when (idx) {
                                                    0 -> viewModel.clickAllFilter()
                                                    1 -> if (selectedTypeIdx != 1) viewModel.clickVideoFilter()
                                                    2 -> if (selectedTypeIdx != 2) viewModel.clickAudioFilter()
                                                }
                                            }
                                            .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        color =
                                            if (active) Color(0xFF111827)
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // VidSnap Screen 4: Storage used card
                        Card(
                            modifier =
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        if (isDark) Color(0xFF1C1C22) else Color(0xFFF9FAFB)
                                ),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "Storage used",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text =
                                            "${totalDownloadsBytes.toFileSizeText()} / ${deviceTotalBytes.toFileSizeText()}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { usageFraction },
                                    color = Color(0xFFD946EF),
                                    trackColor =
                                        if (isDark) Color(0xFF2C2C34) else Color(0xFFE5E7EB),
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                )
                            }
                        }

                        if (filterSet.size > 1) {
                            FilterChips(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                        }
                    }
                }
            }
            for (info in videoList) {

                item(key = info.id, contentType = { info.videoPath.contains(AUDIO_REGEX) }) {
                    with(info) {
                        AnimatedVisibility(
                            modifier = Modifier,
                            visible = info.filterSort(viewState, filterSet),
                            exit = shrinkVertically() + fadeOut(),
                            enter = expandVertically() + fadeIn(),
                        ) {
                            MediaListItem(
                                modifier = Modifier,
                                title = videoTitle,
                                author = videoAuthor,
                                thumbnailUrl = thumbnailUrl,
                                videoPath = videoPath,
                                videoFileSize = fileSizeMap.getOrElse(id) { 0L },
                                videoUrl = videoUrl,
                                isSelectEnabled = { isSelectEnabled },
                                isSelected = { selectedItemIds.contains(id) },
                                onSelect = {
                                    if (selectedItemIds.contains(id)) selectedItemIds.remove(id)
                                    else selectedItemIds.add(id)
                                },
                                onClick = {
                                    if (java.io.File(videoPath).exists()) {
                                        onOpenPlayer(id)
                                    } else {
                                        FileUtil.openFile(path = videoPath) {
                                            ToastUtil.makeToastSuspend(
                                                App.context.getString(R.string.file_unavailable)
                                            )
                                        }
                                    }
                                },
                                onLongClick = {
                                    isSelectEnabled = true
                                    selectedItemIds.add(id)
                                },
                                onShowContextMenu = {
                                    view.slightHapticFeedback()
                                    currentVideoInfo = info
                                    scope.launch {
                                        showBottomSheet = true
                                        delay(50)
                                        sheetState.show()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBottomSheet) {
        val isFileAvailable = fileSizeMap[currentVideoInfo.id] != 0L
        VideoDetailDrawer(
            sheetState = sheetState,
            info = currentVideoInfo,
            isFileAvailable = isFileAvailable,
            onDismissRequest = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { showBottomSheet = false }
            },
            onDelete = { showRemoveDialog = true },
        )
    }

    var deleteFile by remember { mutableStateOf(false) }

    if (showRemoveDialog) {
        RemoveItemDialog(
            info = currentVideoInfo,
            deleteFile = deleteFile,
            onDeleteFileToggled = { deleteFile = it },
            onRemoveConfirm = {
                viewModel.deleteDownloadHistory(listOf(currentVideoInfo), deleteFile = deleteFile)
            },
            onDismissRequest = { showRemoveDialog = false },
        )
    }

    if (showRemoveMultipleItemsDialog) {
        SealDialog(
            onDismissRequest = { showRemoveMultipleItemsDialog = false },
            icon = { Icon(Icons.Outlined.DeleteSweep, null) },
            title = { Text(stringResource(R.string.delete_info)) },
            text = {
                Column {
                    Text(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        text =
                            stringResource(R.string.delete_multiple_items_msg)
                                .format(selectedItemIds.size),
                    )
                    CheckBoxItem(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        text =
                            stringResource(R.string.delete_file) +
                                " (${selectedFileSizeSum.toFileSizeText()})",
                        checked = deleteFile,
                    ) {
                        deleteFile = !deleteFile
                    }
                }
            },
            confirmButton = {
                ConfirmButton {
                    viewModel.deleteDownloadHistory(
                        infoList = videoList.filter { selectedItemIds.contains(it.id) },
                        deleteFile = deleteFile,
                    )
                    showRemoveMultipleItemsDialog = false
                    isSelectEnabled = false
                }
            },
            dismissButton = { DismissButton { showRemoveMultipleItemsDialog = false } },
        )
    }

    var backupString by remember { mutableStateOf("") }

    val exportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/plain")
        ) { uri ->
            uri?.let {
                scope.launch(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(backupString.toByteArray())
                    }
                    withContext(Dispatchers.Main) { showExportDialog = false }
                }
            }
        }

    if (showExportDialog) {
        val list =
            if (selectedItemIds.isNotEmpty()) {
                videoList.filter { selectedItemIds.contains(it.id) }
            } else {
                videoList.filter { it.filterSort(viewState, filterSet) }
            }

        ExportDialog(onDismissRequest = { showExportDialog = false }, itemCount = list.size) {
            type,
            destination ->
            list.backupToString(type).let {
                when (destination) {
                    Clipboard -> clipboardManager.setText(AnnotatedString(it))
                    File -> {
                        backupString = it
                        exportLauncher.launch(
                            BackupUtil.getDownloadHistoryExportFilename(context = context)
                        )
                    }
                }
                view.slightHapticFeedback()
                showExportDialog = false
            }
        }
    }

    val importLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                viewModel.importBackupFromUri(context, uri) {
                    viewModel.showImportedSnackbar(hostState, context, it)
                }
            }
        }

    if (showImportDialog) {

        ImportDialog(onDismissRequest = { showImportDialog = false }) { destination ->
            scope.launch {
                when (destination) {
                    Clipboard -> {
                        clipboardManager.getText()?.text?.let { str ->
                            viewModel.importBackupFromText(str) {
                                viewModel.showImportedSnackbar(hostState, context, it)
                            }
                        }
                    }

                    File -> {
                        importLauncher.launch("text/plain")
                    }
                }
            }
            view.slightHapticFeedback()
            showImportDialog = false
        }
    }
}

private fun List<DownloadedVideoInfo>.backupToString(type: BackupUtil.BackupType): String {
    return when (type) {
        BackupUtil.BackupType.DownloadHistory -> reversed().toJsonString()
        BackupUtil.BackupType.URLList -> toURLListString()
        else -> throw IllegalArgumentException()
    }
}
