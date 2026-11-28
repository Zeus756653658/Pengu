package com.devrayan.pengu.bugreport

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.devrayan.pengu.App
import com.devrayan.pengu.BuildConfig
import com.devrayan.pengu.util.BETA_SERVER_URL
import com.devrayan.pengu.util.BUG_REPORT_RECORDS
import com.devrayan.pengu.util.PreferenceUtil.getString
import com.devrayan.pengu.util.PreferenceUtil.updateString
import com.devrayan.pengu.util.UpdateUtil
import com.devrayan.pengu.util.YT_DLP_VERSION
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface BugReportLocalStore {
    fun loadRecords(): List<LocalBugReportRecord>
    fun saveRecords(records: List<LocalBugReportRecord>)
    fun getServerUrl(): String
    fun setServerUrl(url: String)
}

class MmkvBugReportStore : BugReportLocalStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun loadRecords(): List<LocalBugReportRecord> {
        val raw = BUG_REPORT_RECORDS.getString()
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<LocalBugReportRecord>>(raw) }
            .getOrDefault(emptyList())
    }

    override fun saveRecords(records: List<LocalBugReportRecord>) {
        BUG_REPORT_RECORDS.updateString(json.encodeToString(records))
    }

    override fun getServerUrl(): String = BETA_SERVER_URL.getString().trim().trimEnd('/')

    override fun setServerUrl(url: String) {
        BETA_SERVER_URL.updateString(url.trim().trimEnd('/'))
    }
}

interface AttachmentCodec {
    fun copyUriToInternal(uri: Uri): Result<String>
    fun encodePathToPayload(path: String): BugReportAttachmentPayload?
    fun deleteStoredAttachment(path: String)
}

class AndroidAttachmentCodec(private val context: Context) : AttachmentCodec {
    private val attachmentsDir: File
        get() = File(context.filesDir, "bug_report_attachments").apply { mkdirs() }

    override fun copyUriToInternal(uri: Uri): Result<String> = runCatching {
        val mime = context.contentResolver.getType(uri) ?: "image/png"
        val ext =
            when {
                mime.contains("jpeg", ignoreCase = true) || mime.contains("jpg", ignoreCase = true) ->
                    "jpg"
                mime.contains("webp", ignoreCase = true) -> "webp"
                else -> "png"
            }
        val outFile = File(attachmentsDir, "bugshot_${UUID.randomUUID().toString().take(8)}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            if (bytes.size > BugReportLimits.MAX_ATTACHMENT_BYTES) {
                throw IllegalArgumentException("Screenshot exceeds 3 MB limit")
            }
            outFile.writeBytes(bytes)
        } ?: throw IllegalStateException("Could not read selected image")
        outFile.absolutePath
    }

    override fun encodePathToPayload(path: String): BugReportAttachmentPayload? {
        val f = File(path)
        if (!f.exists() || !f.isFile) return null
        val bytes = f.readBytes()
        if (bytes.isEmpty() || bytes.size > BugReportLimits.MAX_ATTACHMENT_BYTES) return null
        val mime =
            when (f.extension.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                else -> "image/png"
            }
        return BugReportAttachmentPayload(
            mimeType = mime,
            base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
        )
    }

    override fun deleteStoredAttachment(path: String) {
        runCatching { File(path).delete() }
    }
}

interface DiagnosticsProvider {
    fun collect(rawCrashTrace: String?): BugDiagnostics
}

class AndroidDiagnosticsProvider(private val context: Context) : DiagnosticsProvider {
    override fun collect(rawCrashTrace: String?): BugDiagnostics {
        val versionCode =
            runCatching {
                val pi = App.packageInfo
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode
                else @Suppress("DEPRECATION") pi.versionCode.toLong()
            }.getOrDefault(BuildConfig.VERSION_CODE.toLong())
        val androidVer =
            if (Build.VERSION.SDK_INT >= 30) Build.VERSION.RELEASE_OR_CODENAME
            else Build.VERSION.RELEASE
        val ytVer = runCatching { YT_DLP_VERSION.getString() }.getOrDefault("")
        val redactedTrace =
            rawCrashTrace?.let { DiagnosticRedactor.redact(it).take(16_000) }.orEmpty()

        return BugDiagnostics(
            appVersion = BuildConfig.VERSION_NAME,
            versionCode = versionCode,
            androidVersion = androidVer,
            apiLevel = Build.VERSION.SDK_INT,
            deviceManufacturer = Build.MANUFACTURER.orEmpty(),
            deviceModel = Build.MODEL.orEmpty(),
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            ytDlpVersion = ytVer,
            crashStackTrace = redactedTrace,
        )
    }
}

class BugReportRepository(
    private val api: BugReportApiClient,
    private val localStore: BugReportLocalStore,
    private val attachmentCodec: AttachmentCodec,
    private val diagnosticsProvider: DiagnosticsProvider,
) {
    constructor(context: Context) : this(
        api = OkHttpBugReportApi(),
        localStore = MmkvBugReportStore(),
        attachmentCodec = AndroidAttachmentCodec(context),
        diagnosticsProvider = AndroidDiagnosticsProvider(context),
    )

    private val _recordsFlow = MutableStateFlow(localStore.loadRecords())
    val recordsFlow: StateFlow<List<LocalBugReportRecord>> = _recordsFlow.asStateFlow()

    private val inFlightSignatures = ConcurrentHashMap.newKeySet<String>()

    fun getServerUrl(): String = localStore.getServerUrl()

    fun saveServerUrl(rawUrl: String): Result<String> = runCatching {
        val cleaned = rawUrl.trim().trimEnd('/')
        if (cleaned.isNotEmpty()) {
            UpdateUtil.requireHttpsUrl(cleaned)
        }
        localStore.setServerUrl(cleaned)
        cleaned
    }

    fun copyAttachment(uri: Uri): Result<String> = attachmentCodec.copyUriToInternal(uri)

    fun previewDiagnostics(includeDiagnostics: Boolean, rawCrashTrace: String?): BugDiagnostics? =
        if (!includeDiagnostics) null else diagnosticsProvider.collect(rawCrashTrace)

    suspend fun submitBugReport(
        title: String,
        category: BugCategory,
        description: String,
        stepsToReproduce: String,
        expectedBehavior: String,
        actualBehavior: String,
        includeDiagnostics: Boolean,
        rawCrashTrace: String? = null,
        attachmentPaths: List<String> = emptyList(),
    ): Result<LocalBugReportRecord> =
        withContext(Dispatchers.IO) {
            val validation =
                validateBugReportForm(
                    title = title,
                    description = description,
                    stepsToReproduce = stepsToReproduce,
                    expectedBehavior = expectedBehavior,
                    actualBehavior = actualBehavior,
                    attachmentCount = attachmentPaths.size,
                )
            if (!validation.isValid) {
                val firstErr =
                    validation.titleError
                        ?: validation.descriptionError
                        ?: validation.attachmentsError
                        ?: "Validation failed"
                return@withContext Result.failure(IllegalArgumentException(firstErr))
            }

            val redactedTitle = DiagnosticRedactor.redact(title.trim())
            val redactedDesc = DiagnosticRedactor.redact(description.trim())
            val redactedSteps = DiagnosticRedactor.redact(stepsToReproduce.trim())
            val redactedExpected = DiagnosticRedactor.redact(expectedBehavior.trim())
            val redactedActual = DiagnosticRedactor.redact(actualBehavior.trim())

            val signature =
                computeBugReportSignature(
                    category = category.wireName,
                    title = redactedTitle,
                    description = redactedDesc,
                    actualBehavior = redactedActual,
                )

            if (!inFlightSignatures.add(signature)) {
                return@withContext Result.failure(
                    BugReportApiException.Duplicate("Submission already in progress")
                )
            }

            try {
                // Prevent client-side duplicate re-submit if already successfully submitted
                val existingSubmitted =
                    _recordsFlow.value.firstOrNull {
                        it.payloadSignature == signature &&
                            it.resolvedSubmissionState() == LocalSubmissionState.SUBMITTED
                    }
                if (existingSubmitted != null) {
                    return@withContext Result.failure(
                        BugReportApiException.Duplicate(
                            "Already submitted as ${existingSubmitted.remoteId}"
                        )
                    )
                }

                val nowIso = currentIsoUtc()
                val diagnostics =
                    if (includeDiagnostics) diagnosticsProvider.collect(rawCrashTrace) else null

                val pendingRecord =
                    LocalBugReportRecord(
                        localId = "local_${UUID.randomUUID().toString().take(8)}",
                        payloadSignature = signature,
                        title = redactedTitle,
                        category = category.wireName,
                        description = redactedDesc,
                        stepsToReproduce = redactedSteps,
                        expectedBehavior = redactedExpected,
                        actualBehavior = redactedActual,
                        diagnostics = diagnostics,
                        attachmentPaths = attachmentPaths,
                        attachmentCount = attachmentPaths.size,
                        status = BugReportStatus.OPEN.wireName,
                        submissionState = LocalSubmissionState.PENDING_RETRY.name,
                        createdAt = nowIso,
                        updatedAt = nowIso,
                    )

                attemptRemoteSend(pendingRecord)
            } finally {
                inFlightSignatures.remove(signature)
            }
        }

    suspend fun retryReport(localId: String): Result<LocalBugReportRecord> =
        withContext(Dispatchers.IO) {
            val target =
                _recordsFlow.value.firstOrNull { it.localId == localId }
                    ?: return@withContext Result.failure(IllegalStateException("Local report not found"))
            if (!inFlightSignatures.add(target.payloadSignature)) {
                return@withContext Result.failure(
                    BugReportApiException.Duplicate("Retry already in progress")
                )
            }
            try {
                attemptRemoteSend(target)
            } finally {
                inFlightSignatures.remove(target.payloadSignature)
            }
        }

    suspend fun refreshStatus(localId: String): Result<LocalBugReportRecord> =
        withContext(Dispatchers.IO) {
            val target =
                _recordsFlow.value.firstOrNull { it.localId == localId }
                    ?: return@withContext Result.failure(IllegalStateException("Report not found"))
            val remoteId = target.remoteId
            val token = target.trackingToken
            if (remoteId.isNullOrBlank() || token.isNullOrBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("Report has not been synced to the server yet")
                )
            }
            runCatching {
                val res = api.fetchReportStatus(localStore.getServerUrl(), remoteId, token)
                val updated =
                    target.copy(
                        status = BugReportStatus.fromWire(res.status).wireName,
                        developerResponse = res.developerResponse,
                        updatedAt = res.updatedAt.ifBlank { currentIsoUtc() },
                    )
                upsertRecord(updated)
                updated
            }
        }

    fun deleteLocalRecord(localId: String) {
        val current = _recordsFlow.value
        val target = current.firstOrNull { it.localId == localId }
        target?.attachmentPaths?.forEach { attachmentCodec.deleteStoredAttachment(it) }
        val filtered = current.filterNot { it.localId == localId }
        localStore.saveRecords(filtered)
        _recordsFlow.value = filtered
    }

    private suspend fun attemptRemoteSend(record: LocalBugReportRecord): Result<LocalBugReportRecord> {
        val attachments = record.attachmentPaths.mapNotNull { attachmentCodec.encodePathToPayload(it) }
        val req =
            BugReportSubmissionRequest(
                title = record.title,
                category = record.category,
                description = record.description,
                stepsToReproduce = record.stepsToReproduce,
                expectedBehavior = record.expectedBehavior,
                actualBehavior = record.actualBehavior,
                diagnostics = record.diagnostics,
                attachments = attachments,
            )

        return try {
            val response = api.submitBugReport(localStore.getServerUrl(), req)
            val submitted =
                record.copy(
                    remoteId = response.id,
                    trackingToken = response.trackingToken,
                    status = BugReportStatus.fromWire(response.status).wireName,
                    submissionState = LocalSubmissionState.SUBMITTED.name,
                    lastError = null,
                    createdAt = response.createdAt.ifBlank { record.createdAt },
                    updatedAt = currentIsoUtc(),
                )
            upsertRecord(submitted)
            Result.success(submitted)
        } catch (err: Throwable) {
            val friendly =
                (err as? BugReportApiException)?.userFriendlyMessage
                    ?: err.message
                    ?: "Failed to submit bug report"
            val preserved =
                record.copy(
                    submissionState =
                        if (err is BugReportApiException.ValidationOrServer && err.httpCode in 400..499)
                            LocalSubmissionState.FAILED.name
                        else LocalSubmissionState.PENDING_RETRY.name,
                    lastError = friendly,
                    updatedAt = currentIsoUtc(),
                )
            upsertRecord(preserved)
            Result.failure(err)
        }
    }

    private fun upsertRecord(record: LocalBugReportRecord) {
        val mutable = _recordsFlow.value.toMutableList()
        val idx = mutable.indexOfFirst { it.localId == record.localId }
        if (idx >= 0) mutable[idx] = record else mutable.add(0, record)
        localStore.saveRecords(mutable)
        _recordsFlow.value = mutable
    }

    private fun currentIsoUtc(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
