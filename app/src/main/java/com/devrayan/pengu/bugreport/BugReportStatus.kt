package com.devrayan.pengu.bugreport

import kotlinx.serialization.Serializable

enum class BugCategory(val wireName: String, val subtitle: String) {
    DOWNLOADING("Downloading", "Link extraction, formats, speed or failed downloads"),
    VIDEO_PLAYER("Video Player", "Playback stutter, aspect ratio, audio sync or PiP"),
    AUDIO_PLAYER("Audio Player", "Music tab, lyrics, equalizer or mini-player controls"),
    PLAYLIST("Playlist", "Playlist parsing or multi-item queues"),
    SUBTITLES("Subtitles", "Subtitle fetch, embed or format conversion"),
    NETWORK("Network", "Timeouts, proxy, cookies or SSL connection issues"),
    USER_INTERFACE("User Interface", "Layout overlap, theme contrast or navigation bugs"),
    APPLICATION_CRASH("Application Crash", "Unexpected force closes or freezes"),
    OTHER("Other", "Any bug not covered above");

    companion object {
        fun fromWire(raw: String?): BugCategory =
            entries.firstOrNull { it.wireName.equals(raw, ignoreCase = true) } ?: OTHER
    }
}

enum class BugReportStatus(val wireName: String) {
    OPEN("Open"),
    REVIEWING("Reviewing"),
    IN_PROGRESS("In Progress"),
    FIXED("Fixed"),
    REJECTED("Rejected"),
    CLOSED("Closed");

    companion object {
        fun fromWire(raw: String?): BugReportStatus =
            entries.firstOrNull { it.wireName.equals(raw, ignoreCase = true) } ?: OPEN
    }
}

enum class LocalSubmissionState {
    SUBMITTED,
    PENDING_RETRY,
    FAILED,
}

@Serializable
data class BugDiagnostics(
    val appVersion: String = "",
    val versionCode: Long = 0L,
    val androidVersion: String = "",
    val apiLevel: Int = 0,
    val deviceManufacturer: String = "",
    val deviceModel: String = "",
    val supportedAbis: List<String> = emptyList(),
    val ytDlpVersion: String = "",
    val crashStackTrace: String = "",
)

@Serializable
data class BugReportAttachmentPayload(
    val mimeType: String,
    val base64: String,
)

@Serializable
data class BugReportSubmissionRequest(
    val title: String,
    val category: String,
    val description: String,
    val stepsToReproduce: String = "",
    val expectedBehavior: String = "",
    val actualBehavior: String = "",
    val diagnostics: BugDiagnostics? = null,
    val attachments: List<BugReportAttachmentPayload> = emptyList(),
)

@Serializable
data class BugReportSubmissionResponse(
    val id: String = "",
    val trackingToken: String = "",
    val status: String = "Open",
    val category: String = "",
    val createdAt: String = "",
    val attachmentCount: Int = 0,
)

@Serializable
data class BugReportPublicStatusResponse(
    val id: String = "",
    val title: String = "",
    val category: String = "",
    val status: String = "Open",
    val developerResponse: String = "",
    val attachmentCount: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class LocalBugReportRecord(
    val localId: String,
    val remoteId: String? = null,
    val trackingToken: String? = null,
    val payloadSignature: String,
    val title: String,
    val category: String,
    val description: String,
    val stepsToReproduce: String = "",
    val expectedBehavior: String = "",
    val actualBehavior: String = "",
    val diagnostics: BugDiagnostics? = null,
    val attachmentPaths: List<String> = emptyList(),
    val attachmentCount: Int = 0,
    val status: String = BugReportStatus.OPEN.wireName,
    val developerResponse: String = "",
    val submissionState: String = LocalSubmissionState.PENDING_RETRY.name,
    val lastError: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
) {
    fun resolvedStatus(): BugReportStatus = BugReportStatus.fromWire(status)

    fun resolvedSubmissionState(): LocalSubmissionState =
        LocalSubmissionState.entries.firstOrNull { it.name == submissionState }
            ?: LocalSubmissionState.PENDING_RETRY
}
