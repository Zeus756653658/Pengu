package com.devrayan.pengu

import android.net.Uri
import com.devrayan.pengu.bugreport.AttachmentCodec
import com.devrayan.pengu.bugreport.BugCategory
import com.devrayan.pengu.bugreport.BugDiagnostics
import com.devrayan.pengu.bugreport.BugReportApiClient
import com.devrayan.pengu.bugreport.BugReportApiException
import com.devrayan.pengu.bugreport.BugReportAttachmentPayload
import com.devrayan.pengu.bugreport.BugReportLocalStore
import com.devrayan.pengu.bugreport.BugReportPublicStatusResponse
import com.devrayan.pengu.bugreport.BugReportRepository
import com.devrayan.pengu.bugreport.BugReportStatus
import com.devrayan.pengu.bugreport.BugReportSubmissionRequest
import com.devrayan.pengu.bugreport.BugReportSubmissionResponse
import com.devrayan.pengu.bugreport.BugReportViewModel
import com.devrayan.pengu.bugreport.DiagnosticRedactor
import com.devrayan.pengu.bugreport.DiagnosticsProvider
import com.devrayan.pengu.bugreport.LocalBugReportRecord
import com.devrayan.pengu.bugreport.LocalSubmissionState
import com.devrayan.pengu.bugreport.validateBugReportForm
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BugReportSystemTest {

    private class InMemoryBugStore(initialUrl: String = "https://bugs.pengu.test") :
        BugReportLocalStore {
        private var url = initialUrl
        private var records = emptyList<LocalBugReportRecord>()

        override fun loadRecords(): List<LocalBugReportRecord> = records

        override fun saveRecords(records: List<LocalBugReportRecord>) {
            this.records = records
        }

        override fun getServerUrl(): String = url

        override fun setServerUrl(url: String) {
            this.url = url
        }
    }

    private class InMemoryAttachmentCodec : AttachmentCodec {
        override fun copyUriToInternal(uri: Uri): Result<String> = Result.success("/tmp/test.png")

        override fun encodePathToPayload(path: String): BugReportAttachmentPayload? = null

        override fun deleteStoredAttachment(path: String) {}
    }

    private class FakeDiagnosticsProvider : DiagnosticsProvider {
        override fun collect(rawCrashTrace: String?): BugDiagnostics =
            BugDiagnostics(
                appVersion = "1.2.0",
                versionCode = 102000400L,
                androidVersion = "15",
                apiLevel = 35,
                deviceManufacturer = "Google",
                deviceModel = "Pixel 8",
                supportedAbis = listOf("arm64-v8a"),
                ytDlpVersion = "2025.01.15",
                crashStackTrace = rawCrashTrace?.let { DiagnosticRedactor.redact(it) }.orEmpty(),
            )
    }

    private class ConfigurableBugApi : BugReportApiClient {
        var submitCalls = 0
        var lastRequest: BugReportSubmissionRequest? = null
        var failMode: Throwable? = null
        var nextStatus = "In Progress"
        var nextDevReply = "Investigating dash muxer fix"

        override suspend fun submitBugReport(
            baseUrl: String,
            request: BugReportSubmissionRequest,
        ): BugReportSubmissionResponse {
            submitCalls += 1
            lastRequest = request
            failMode?.let { throw it }
            return BugReportSubmissionResponse(
                id = "BR-20261128-TEST01",
                trackingToken = "tok-secret-42",
                status = "Open",
                category = request.category,
                createdAt = "2026-11-28T10:00:00Z",
                attachmentCount = request.attachments.size,
            )
        }

        override suspend fun fetchReportStatus(
            baseUrl: String,
            reportId: String,
            trackingToken: String,
        ): BugReportPublicStatusResponse =
            BugReportPublicStatusResponse(
                id = reportId,
                title = lastRequest?.title ?: "Bug",
                category = lastRequest?.category ?: "Downloading",
                status = nextStatus,
                developerResponse = nextDevReply,
                attachmentCount = 0,
                createdAt = "2026-11-28T10:00:00Z",
                updatedAt = "2026-11-28T11:00:00Z",
            )
    }

    @Test
    fun formValidation_enforcesMandatoryFieldsLimitsAndAttachments() {
        val emptyCheck = validateBugReportForm(title = "abc", description = "too short")
        assertFalse(emptyCheck.isValid)
        assertNotNull(emptyCheck.titleError)
        assertNotNull(emptyCheck.descriptionError)

        val tooLongTitle = validateBugReportForm(title = "a".repeat(141), description = "d".repeat(20))
        assertFalse(tooLongTitle.isValid)
        assertNotNull(tooLongTitle.titleError)

        val tooManyShots =
            validateBugReportForm(
                title = "Valid Bug Title",
                description = "Detailed description of more than fifteen chars",
                attachmentCount = 4,
            )
        assertFalse(tooManyShots.isValid)
        assertNotNull(tooManyShots.attachmentsError)

        val valid =
            validateBugReportForm(
                title = "4K merge stalls cleanly at 99%",
                description = "Muxing ffmpeg step hangs on certain MP4 formats.",
                attachmentCount = 2,
            )
        assertTrue(valid.isValid)
    }

    @Test
    fun diagnosticRedactor_scrubsSecretsCookiesTokensEmailsAndUserPaths() {
        val noisyTrace =
            """
            java.io.IOException: Failed downloading https://example.com/v?token=secret999&password=pass123
            Cookie: session_id=xyz789; auth=abc
            Authorization: Bearer super_jwt_token_value
            File: /data/user/0/com.devrayan.pengu/files/cookies.txt
            User: tester@example.com
            """.trimIndent()

        val scrubbed = DiagnosticRedactor.redact(noisyTrace)
        assertFalse(scrubbed.contains("secret999"))
        assertFalse(scrubbed.contains("pass123"))
        assertFalse(scrubbed.contains("super_jwt_token_value"))
        assertFalse(scrubbed.contains("tester@example.com"))
        assertFalse(scrubbed.contains("cookies.txt"))
        assertFalse(scrubbed.contains("/data/user/0/"))
        assertTrue(scrubbed.contains("[REDACTED]"))
        assertTrue(scrubbed.contains("[REDACTED_EMAIL]"))
    }

    @Test
    fun repository_handlesConsentOfflinePreserveRetryDuplicateGuardAndStatusSync() = runBlocking {
        val api = ConfigurableBugApi()
        val store = InMemoryBugStore()
        val repo =
            BugReportRepository(
                api = api,
                localStore = store,
                attachmentCodec = InMemoryAttachmentCodec(),
                diagnosticsProvider = FakeDiagnosticsProvider(),
            )

        // 1. Offline failure preserves local PENDING_RETRY record without uploading diagnostics unless consented
        api.failMode = BugReportApiException.Offline(UnknownHostException("offline"))
        val failed =
            repo.submitBugReport(
                title = "Audio player stops on lock",
                category = BugCategory.AUDIO_PLAYER,
                description = "Playback halts after 15 seconds when screen locks.",
                stepsToReproduce = "Play track then press power",
                expectedBehavior = "Plays continuously",
                actualBehavior = "Stops at 15s",
                includeDiagnostics = false,
            )
        assertTrue(failed.isFailure)
        val pending = repo.recordsFlow.value.first()
        assertEquals(LocalSubmissionState.PENDING_RETRY, pending.resolvedSubmissionState())
        assertNull(api.lastRequest?.diagnostics)

        // 2. Retry succeeds cleanly when server comes back
        api.failMode = null
        val retried = repo.retryReport(pending.localId)
        assertTrue(retried.isSuccess)
        val saved = retried.getOrThrow()
        assertEquals("BR-20261128-TEST01", saved.remoteId)
        assertEquals(LocalSubmissionState.SUBMITTED, saved.resolvedSubmissionState())

        // 3. Duplicate submission prevented client-side cleanly
        val callsBeforeDup = api.submitCalls
        val dupAttempt =
            repo.submitBugReport(
                title = "Audio player stops on lock",
                category = BugCategory.AUDIO_PLAYER,
                description = "Playback halts after 15 seconds when screen locks.",
                stepsToReproduce = "Play track then press power",
                expectedBehavior = "Plays continuously",
                actualBehavior = "Stops at 15s",
                includeDiagnostics = false,
            )
        assertTrue(dupAttempt.isFailure)
        assertTrue(dupAttempt.exceptionOrNull() is BugReportApiException.Duplicate)
        assertEquals(callsBeforeDup, api.submitCalls)

        // 4. Status refresh retrieves server update & developer reply
        val refreshed = repo.refreshStatus(saved.localId)
        assertTrue(refreshed.isSuccess)
        val updated = refreshed.getOrThrow()
        assertEquals(BugReportStatus.IN_PROGRESS, updated.resolvedStatus())
        assertEquals("Investigating dash muxer fix", updated.developerResponse)
    }

    @Test
    fun repository_collectsRedactedDiagnosticsOnlyWhenConsented() = runBlocking {
        val api = ConfigurableBugApi()
        val store = InMemoryBugStore()
        val repo =
            BugReportRepository(
                api = api,
                localStore = store,
                attachmentCodec = InMemoryAttachmentCodec(),
                diagnosticsProvider = FakeDiagnosticsProvider(),
            )

        assertNull(repo.previewDiagnostics(includeDiagnostics = false, rawCrashTrace = "secret=123"))

        val consented =
            repo.submitBugReport(
                title = "Crash when rotating full-screen",
                category = BugCategory.APPLICATION_CRASH,
                description = "App crashed cleanly while entering landscape.",
                stepsToReproduce = "Rotate screen twice",
                expectedBehavior = "Player rotates smoothly",
                actualBehavior = "Crash dialog appeared",
                includeDiagnostics = true,
                rawCrashTrace = "RuntimeException password=mySecretValue9",
            )
        assertTrue(consented.isSuccess)
        val sentDiag = api.lastRequest?.diagnostics
        assertNotNull(sentDiag)
        assertFalse(sentDiag!!.crashStackTrace.contains("mySecretValue9"))
        assertTrue(sentDiag.crashStackTrace.contains("[REDACTED]"))
    }

    @Test
    fun viewModel_transitionsValidationLoadingSuccessErrorAndCrashPrefillStates() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val api = ConfigurableBugApi()
        val store = InMemoryBugStore()
        val repo =
            BugReportRepository(
                api = api,
                localStore = store,
                attachmentCodec = InMemoryAttachmentCodec(),
                diagnosticsProvider = FakeDiagnosticsProvider(),
            )
        val vm = BugReportViewModel(repo, externalScope = testScope)

        try {
            // Invalid submit marks hasAttemptedSubmit and shows error
            vm.submitForm()
            assertTrue(vm.uiState.value.hasAttemptedSubmit)
            assertNotNull(vm.uiState.value.errorMessage)
            assertNotNull(vm.uiState.value.validation.titleError)

            // Timeout error state sets friendly retry message
            api.failMode = BugReportApiException.Timeout(SocketTimeoutException("timed out"))
            vm.prefillFromCrash("IllegalStateException Cookie: sid=abc123")
            vm.submitForm()
            delay(80)
            assertFalse(vm.uiState.value.isSubmitting)
            assertTrue(vm.uiState.value.reports.isNotEmpty())
            assertTrue(vm.uiState.value.errorMessage?.contains("timed out", ignoreCase = true) == true)

            // Retry transitions to SUBMITTED with remote ID
            api.failMode = null
            val localId = vm.uiState.value.reports.first().localId
            vm.retryReport(localId)
            delay(80)
            assertEquals("BR-20261128-TEST01", vm.uiState.value.lastSubmittedReportId)
            assertNull(vm.uiState.value.errorMessage)
        } finally {
            testScope.cancel()
        }
    }
}
