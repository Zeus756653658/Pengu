package com.devrayan.pengu.bugreport

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BugReportUiState(
    val title: String = "",
    val category: BugCategory = BugCategory.DOWNLOADING,
    val description: String = "",
    val stepsToReproduce: String = "",
    val expectedBehavior: String = "",
    val actualBehavior: String = "",
    val includeDiagnostics: Boolean = true,
    val crashStackTrace: String = "",
    val attachmentPaths: List<String> = emptyList(),
    val validation: BugReportValidationResult = BugReportValidationResult(isValid = false),
    val hasAttemptedSubmit: Boolean = false,
    val isSubmitting: Boolean = false,
    val isRefreshingStatus: Boolean = false,
    val serverUrl: String = "",
    val lastSubmittedReportId: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val reports: List<LocalBugReportRecord> = emptyList(),
    val selectedReportLocalId: String? = null,
) {
    val selectedReport: LocalBugReportRecord?
        get() = reports.firstOrNull { it.localId == selectedReportLocalId }
}

class BugReportViewModel(
    private val repository: BugReportRepository,
    private val externalScope: CoroutineScope? = null,
) : ViewModel() {

    private val scope: CoroutineScope
        get() = externalScope ?: viewModelScope

    private val _uiState =
        MutableStateFlow(
            BugReportUiState(
                serverUrl = repository.getServerUrl(),
                reports = repository.recordsFlow.value,
            )
        )
    val uiState: StateFlow<BugReportUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            repository.recordsFlow.collect { list ->
                _uiState.update { it.copy(reports = list) }
            }
        }
    }

    fun updateTitle(value: String) {
        if (value.length <= BugReportLimits.TITLE_MAX) {
            _uiState.update { reevaluateValidation(it.copy(title = value, errorMessage = null)) }
        }
    }

    fun updateCategory(category: BugCategory) {
        _uiState.update { it.copy(category = category, errorMessage = null) }
    }

    fun updateDescription(value: String) {
        if (value.length <= BugReportLimits.DESCRIPTION_MAX) {
            _uiState.update { reevaluateValidation(it.copy(description = value, errorMessage = null)) }
        }
    }

    fun updateSteps(value: String) {
        if (value.length <= BugReportLimits.STEPS_MAX) {
            _uiState.update { reevaluateValidation(it.copy(stepsToReproduce = value)) }
        }
    }

    fun updateExpected(value: String) {
        if (value.length <= BugReportLimits.EXPECTED_MAX) {
            _uiState.update { reevaluateValidation(it.copy(expectedBehavior = value)) }
        }
    }

    fun updateActual(value: String) {
        if (value.length <= BugReportLimits.ACTUAL_MAX) {
            _uiState.update { reevaluateValidation(it.copy(actualBehavior = value)) }
        }
    }

    fun updateIncludeDiagnostics(include: Boolean) {
        _uiState.update { it.copy(includeDiagnostics = include) }
    }

    fun prefillFromCrash(redactedStackTrace: String) {
        _uiState.update {
            reevaluateValidation(
                it.copy(
                    title = "Application crash report",
                    category = BugCategory.APPLICATION_CRASH,
                    description =
                        "Application encountered an unexpected crash during use. Crash diagnostics attached with user consent.",
                    crashStackTrace = DiagnosticRedactor.redact(redactedStackTrace),
                    includeDiagnostics = true,
                )
            )
        }
    }

    fun addAttachmentUri(uri: Uri) {
        if (_uiState.value.attachmentPaths.size >= BugReportLimits.MAX_ATTACHMENTS) {
            _uiState.update {
                it.copy(errorMessage = "Maximum ${BugReportLimits.MAX_ATTACHMENTS} screenshots allowed.")
            }
            return
        }
        repository.copyAttachment(uri).fold(
            onSuccess = { path ->
                _uiState.update {
                    reevaluateValidation(
                        it.copy(
                            attachmentPaths = it.attachmentPaths + path,
                            errorMessage = null,
                        )
                    )
                }
            },
            onFailure = { err ->
                _uiState.update {
                    it.copy(errorMessage = err.message ?: "Unable to attach screenshot")
                }
            },
        )
    }

    fun removeAttachment(path: String) {
        _uiState.update {
            reevaluateValidation(it.copy(attachmentPaths = it.attachmentPaths - path))
        }
    }

    fun previewDiagnostics(): BugDiagnostics? {
        val st = _uiState.value
        return repository.previewDiagnostics(
            st.includeDiagnostics,
            st.crashStackTrace.takeIf { it.isNotBlank() },
        )
    }

    fun saveServerUrl(rawUrl: String) {
        repository.saveServerUrl(rawUrl).fold(
            onSuccess = { cleaned ->
                _uiState.update {
                    it.copy(
                        serverUrl = cleaned,
                        infoMessage =
                            if (cleaned.isBlank()) "Server URL cleared"
                            else "Saved HTTPS bug-report endpoint",
                        errorMessage = null,
                    )
                }
            },
            onFailure = { err ->
                _uiState.update {
                    it.copy(errorMessage = err.message ?: "HTTPS URL required")
                }
            },
        )
    }

    fun submitForm() {
        val current = _uiState.value
        if (current.isSubmitting) return

        val checked =
            validateBugReportForm(
                title = current.title,
                description = current.description,
                stepsToReproduce = current.stepsToReproduce,
                expectedBehavior = current.expectedBehavior,
                actualBehavior = current.actualBehavior,
                attachmentCount = current.attachmentPaths.size,
            )
        if (!checked.isValid) {
            _uiState.update {
                it.copy(
                    hasAttemptedSubmit = true,
                    validation = checked,
                    errorMessage = "Please fix highlighted required fields before submitting.",
                )
            }
            return
        }

        scope.launch {
            _uiState.update {
                it.copy(
                    hasAttemptedSubmit = true,
                    isSubmitting = true,
                    errorMessage = null,
                    infoMessage = null,
                    lastSubmittedReportId = null,
                )
            }
            val result =
                repository.submitBugReport(
                    title = current.title,
                    category = current.category,
                    description = current.description,
                    stepsToReproduce = current.stepsToReproduce,
                    expectedBehavior = current.expectedBehavior,
                    actualBehavior = current.actualBehavior,
                    includeDiagnostics = current.includeDiagnostics,
                    rawCrashTrace = current.crashStackTrace.takeIf { it.isNotBlank() },
                    attachmentPaths = current.attachmentPaths,
                )

            result.fold(
                onSuccess = { saved ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            title = "",
                            description = "",
                            stepsToReproduce = "",
                            expectedBehavior = "",
                            actualBehavior = "",
                            crashStackTrace = "",
                            attachmentPaths = emptyList(),
                            hasAttemptedSubmit = false,
                            lastSubmittedReportId = saved.remoteId,
                            selectedReportLocalId = saved.localId,
                            infoMessage =
                                "Bug report ${saved.remoteId} submitted cleanly. Track live developer status inside My Bug Reports.",
                        )
                    }
                },
                onFailure = { err ->
                    val friendly =
                        (err as? BugReportApiException)?.userFriendlyMessage
                            ?: err.message
                            ?: "Submission failed"
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = friendly,
                        )
                    }
                },
            )
        }
    }

    fun retryReport(localId: String) {
        if (_uiState.value.isSubmitting) return
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, infoMessage = null) }
            repository.retryReport(localId).fold(
                onSuccess = { saved ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            lastSubmittedReportId = saved.remoteId,
                            infoMessage = "Retry succeeded! Assigned Report ID ${saved.remoteId}.",
                        )
                    }
                },
                onFailure = { err ->
                    val friendly =
                        (err as? BugReportApiException)?.userFriendlyMessage
                            ?: err.message
                            ?: "Retry failed"
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = friendly) }
                },
            )
        }
    }

    fun refreshStatus(localId: String) {
        scope.launch {
            _uiState.update { it.copy(isRefreshingStatus = true, errorMessage = null) }
            repository.refreshStatus(localId).fold(
                onSuccess = { updated ->
                    _uiState.update {
                        it.copy(
                            isRefreshingStatus = false,
                            infoMessage = "Status updated: ${updated.status}",
                        )
                    }
                },
                onFailure = { err ->
                    val friendly =
                        (err as? BugReportApiException)?.userFriendlyMessage
                            ?: err.message
                            ?: "Could not fetch status"
                    _uiState.update { it.copy(isRefreshingStatus = false, errorMessage = friendly) }
                },
            )
        }
    }

    fun selectReport(localId: String) {
        _uiState.update { it.copy(selectedReportLocalId = localId) }
    }

    fun deleteLocalRecord(localId: String) {
        repository.deleteLocalRecord(localId)
        _uiState.update { it.copy(infoMessage = "Removed local report record.") }
    }

    fun clearBanners() {
        _uiState.update {
            it.copy(errorMessage = null, infoMessage = null, lastSubmittedReportId = null)
        }
    }

    private fun reevaluateValidation(state: BugReportUiState): BugReportUiState =
        state.copy(
            validation =
                validateBugReportForm(
                    title = state.title,
                    description = state.description,
                    stepsToReproduce = state.stepsToReproduce,
                    expectedBehavior = state.expectedBehavior,
                    actualBehavior = state.actualBehavior,
                    attachmentCount = state.attachmentPaths.size,
                )
        )
}
