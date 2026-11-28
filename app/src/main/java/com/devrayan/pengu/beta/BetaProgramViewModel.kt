package com.devrayan.pengu.beta

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devrayan.pengu.util.PRE_RELEASE
import com.devrayan.pengu.util.UpdateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BetaProgramUiState(
    val isLoading: Boolean = false,
    val overview: BetaOverviewData? = null,
    val selectedRelease: BetaReleaseItem? = null,
    val downloadStatus: UpdateUtil.DownloadStatus = UpdateUtil.DownloadStatus.NotYet,
    val downloadingReleaseId: String? = null,
    val verifiedReleaseId: String? = null,
    val errorMessage: String? = null,
    val actionMessage: String? = null,
)

class BetaProgramViewModel(
    private val repository: BetaRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BetaProgramUiState())
    val uiState: StateFlow<BetaProgramUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null

    init {
        refresh()
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, actionMessage = null) }
    }

    fun selectRelease(release: BetaReleaseItem) {
        _uiState.update { it.copy(selectedRelease = release) }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.loadProgramOverview().fold(
                onSuccess = { data ->
                    val currentSelectedId = _uiState.value.selectedRelease?.id
                    val updatedSelected =
                        data.allReleases.firstOrNull { it.id == currentSelectedId }
                            ?: data.latestBeta
                            ?: data.latestStable
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            overview = data,
                            selectedRelease = updatedSelected,
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Unable to fetch beta release info",
                        )
                    }
                },
            )
        }
    }

    fun joinOpenBeta() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, actionMessage = null) }
            repository.joinOpenBeta().fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            actionMessage =
                                "Enrolled in Pengu Open Beta. Release channel set to Beta Builds."
                        )
                    }
                    refresh()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Failed to join Open Beta",
                        )
                    }
                },
            )
        }
    }

    fun requestClosedBeta(inviteCode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, actionMessage = null) }
            repository.requestClosedBeta(inviteCode).fold(
                onSuccess = { status ->
                    val msg =
                        if (status == BetaEnrollmentStatus.ACTIVE) {
                            "Closed Beta invite verified. Early access is active."
                        } else {
                            "Closed Beta request registered and awaiting approval."
                        }
                    _uiState.update { it.copy(actionMessage = msg) }
                    refresh()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Closed Beta request failed",
                        )
                    }
                },
            )
        }
    }

    fun leaveBetaAndSwitchToStable() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, actionMessage = null) }
            repository.leaveBetaProgram().fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            actionMessage = "Left Beta Program and switched back to Stable channel."
                        )
                    }
                    refresh()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Could not leave Beta Program",
                        )
                    }
                },
            )
        }
    }

    fun setChannelExplicit(channel: Int) {
        repository.setUpdateChannelExplicit(channel)
        val label = if (channel == PRE_RELEASE) "Beta" else "Stable"
        _uiState.update { it.copy(actionMessage = "Release channel switched to $label") }
        refresh()
    }

    fun saveServerUrl(url: String) {
        repository.saveServerUrl(url).fold(
            onSuccess = { cleaned ->
                val msg =
                    if (cleaned.isBlank()) "Cleared custom server URL (using GitHub Releases HTTPS)."
                    else "Saved HTTPS Beta Server URL."
                _uiState.update { it.copy(actionMessage = msg, errorMessage = null) }
                refresh()
            },
            onFailure = { err ->
                _uiState.update {
                    it.copy(errorMessage = err.message ?: "HTTPS URL is required")
                }
            },
        )
    }

    fun downloadAndVerifyRelease(item: BetaReleaseItem) {
        downloadJob?.cancel()
        downloadJob =
            viewModelScope.launch(Dispatchers.IO) {
                _uiState.update {
                    it.copy(
                        downloadingReleaseId = item.id,
                        downloadStatus = UpdateUtil.DownloadStatus.Progress(0),
                        errorMessage = null,
                        actionMessage = null,
                    )
                }
                val targetRelease = item.toUpdateRelease()
                runCatching {
                        if (item.isFromServer) {
                            repository.trackServerDownload(item.id)
                        }
                        UpdateUtil.downloadApk(
                            context = appContext,
                            release = targetRelease,
                            expectedSha256 = item.sha256,
                        ).collect { status ->
                            _uiState.update { state ->
                                state.copy(
                                    downloadStatus = status,
                                    verifiedReleaseId =
                                        if (status is UpdateUtil.DownloadStatus.Finished) item.id
                                        else state.verifiedReleaseId,
                                )
                            }
                        }
                    }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(
                                downloadStatus = UpdateUtil.DownloadStatus.NotYet,
                                downloadingReleaseId = null,
                                errorMessage =
                                    err.message ?: "Download or SHA-256 verification failed",
                            )
                        }
                    }
            }
    }
}
