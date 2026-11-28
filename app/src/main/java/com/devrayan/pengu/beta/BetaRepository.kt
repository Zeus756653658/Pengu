package com.devrayan.pengu.beta

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.devrayan.pengu.App
import com.devrayan.pengu.BuildConfig
import com.devrayan.pengu.util.BETA_ENROLLMENT_STATUS
import com.devrayan.pengu.util.BETA_ENROLLMENT_TYPE
import com.devrayan.pengu.util.BETA_INVITE_CODE
import com.devrayan.pengu.util.BETA_SERVER_URL
import com.devrayan.pengu.util.BETA_TESTER_ID
import com.devrayan.pengu.util.PRE_RELEASE
import com.devrayan.pengu.util.PreferenceUtil.getInt
import com.devrayan.pengu.util.PreferenceUtil.getString
import com.devrayan.pengu.util.PreferenceUtil.updateInt
import com.devrayan.pengu.util.PreferenceUtil.updateString
import com.devrayan.pengu.util.STABLE
import com.devrayan.pengu.util.UPDATE_CHANNEL
import com.devrayan.pengu.util.UpdateUtil
import com.devrayan.pengu.util.UpdateUtil.displayTitle
import com.devrayan.pengu.util.UpdateUtil.resolvedVersion
import com.devrayan.pengu.util.UpdateUtil.toVersion
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

enum class ReleaseCategory {
    BETA,
    STABLE,
    ARCHIVED,
}

enum class BetaEnrollmentType(val wireValue: String, val label: String) {
    NONE("none", "Not Enrolled"),
    OPEN("open", "Open Beta"),
    CLOSED("closed", "Closed Beta");

    companion object {
        fun fromWire(raw: String?): BetaEnrollmentType =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: NONE
    }
}

enum class BetaEnrollmentStatus(val wireValue: String, val label: String) {
    NOT_ENROLLED("not_enrolled", "Not Enrolled"),
    PENDING_APPROVAL("pending", "Pending Approval"),
    ACTIVE("active", "Active Tester"),
    REMOVED("removed", "Access Revoked");

    companion object {
        fun fromWire(raw: String?): BetaEnrollmentStatus =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: NOT_ENROLLED
    }
}

@Serializable
data class BetaConfigDto(
    val openBetaEnabled: Boolean = true,
    val closedBetaEnabled: Boolean = true,
    val closedCapacity: Int = 100,
    val closedActiveCount: Int = 0,
    val knownBugs: List<String> = emptyList(),
    val experimentalFeatures: List<String> = emptyList(),
)

@Serializable
data class BetaServerReleaseDto(
    val id: String = "",
    val version: String = "",
    val channel: String = "beta",
    val releaseNotes: String = "",
    val knownBugs: List<String> = emptyList(),
    val experimentalFeatures: List<String> = emptyList(),
    val apkUrl: String = "",
    val sha256: String = "",
    val enabled: Boolean = true,
    val downloadCount: Int = 0,
    val publishedAt: String = "",
)

@Serializable
data class BetaServerReleasesEnvelope(val releases: List<BetaServerReleaseDto> = emptyList())

@Serializable
data class BetaStatusDto(
    val enrolled: Boolean = false,
    val enrollmentType: String = "none",
    val status: String = "not_enrolled",
    val joinedAt: String? = null,
    val closedActiveCount: Int = 0,
    val closedCapacity: Int = 100,
)

@Serializable
data class BetaEnrollResponseDto(
    val deviceId: String = "",
    val deviceModel: String = "Android Device",
    val enrollmentType: String = "open",
    val status: String = "active",
    val joinedAt: String = "",
)

data class ParsedReleaseMetadata(
    val knownBugs: List<String>,
    val experimentalFeatures: List<String>,
    val sha256Hex: String?,
)

data class BetaReleaseItem(
    val id: String,
    val tagName: String,
    val versionName: String,
    val title: String,
    val category: ReleaseCategory,
    val channelLabel: String,
    val isClosedBetaOnly: Boolean = false,
    val isFromServer: Boolean = false,
    val isEnabled: Boolean = true,
    val publishedAt: String,
    val releaseNotes: String,
    val knownBugs: List<String>,
    val experimentalFeatures: List<String>,
    val apkUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long,
    val sha256: String?,
    val downloadCount: Int,
    val rawGitHubRelease: UpdateUtil.Release? = null,
) {
    fun toUpdateRelease(): UpdateUtil.Release =
        rawGitHubRelease
            ?: UpdateUtil.Release(
                tagName = tagName.ifBlank { versionName },
                name = title.ifBlank { "Pengu $versionName" },
                draft = !isEnabled,
                preRelease = category == ReleaseCategory.BETA,
                publishedAt = publishedAt,
                body = releaseNotes,
                assets =
                    apkUrl?.let { url ->
                        listOf(
                            UpdateUtil.AssetsItem(
                                name = apkFileName ?: "Pengu-$versionName.apk",
                                size = apkSizeBytes.toInt(),
                                downloadCount = downloadCount,
                                browserDownloadUrl = url,
                                digest = sha256?.let { "sha256:$it" },
                            )
                        )
                    },
            )
}

data class TesterEligibility(
    val isEligible: Boolean,
    val checks: List<EligibilityCheck>,
)

data class EligibilityCheck(
    val label: String,
    val passed: Boolean,
    val detail: String,
)

data class BetaOverviewData(
    val installedVersion: String,
    val installedFlavor: String,
    val updateChannel: Int,
    val enrollmentType: BetaEnrollmentType,
    val enrollmentStatus: BetaEnrollmentStatus,
    val testerId: String,
    val serverUrl: String,
    val config: BetaConfigDto,
    val eligibility: TesterEligibility,
    val latestBeta: BetaReleaseItem?,
    val latestStable: BetaReleaseItem?,
    val allReleases: List<BetaReleaseItem>,
)

class BetaRepository(private val appContext: Context) {

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun getOrCreateTesterId(): String {
        val current = BETA_TESTER_ID.getString().trim()
        if (current.isNotEmpty()) return current
        val generated = "pg-${UUID.randomUUID().toString().replace("-", "").take(12)}"
        BETA_TESTER_ID.updateString(generated)
        return generated
    }

    fun getConfiguredServerUrl(): String = BETA_SERVER_URL.getString().trim().trimEnd('/')

    fun saveServerUrl(rawUrl: String): Result<String> = runCatching {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isNotEmpty()) {
            UpdateUtil.requireHttpsUrl(trimmed)
        }
        BETA_SERVER_URL.updateString(trimmed)
        trimmed
    }

    fun computeEligibility(context: Context = appContext): TesterEligibility {
        val minApiOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        val notFdroid = !App.isFDroidBuild()
        val canInstallOrUnknown =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.packageManager.canRequestPackageInstalls()
            } else true
        val installedPkgValid =
            runCatching {
                    @Suppress("DEPRECATION")
                    val flags =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                            PackageManager.GET_SIGNING_CERTIFICATES
                        else PackageManager.GET_SIGNATURES
                    context.packageManager.getPackageInfo(context.packageName, flags) != null
                }
                .getOrDefault(true)

        val checks =
            listOf(
                EligibilityCheck(
                    label = "Android OS (API ${Build.VERSION.SDK_INT})",
                    passed = minApiOk,
                    detail = if (minApiOk) "Android 7.0+ supported" else "Unsupported OS",
                ),
                EligibilityCheck(
                    label = "Target ABI ($abi)",
                    passed = abi.isNotBlank(),
                    detail = if (abi.isNotBlank()) "Verified device architecture" else "Unknown ABI",
                ),
                EligibilityCheck(
                    label = "Signing Certificate Guard",
                    passed = installedPkgValid,
                    detail =
                        if (installedPkgValid) "APK signature verification active"
                        else "Unable to read signature",
                ),
                EligibilityCheck(
                    label = "Direct APK Track",
                    passed = notFdroid,
                    detail =
                        if (notFdroid) "Supports direct verified updates"
                        else "Managed by F-Droid",
                ),
                EligibilityCheck(
                    label = "Installer Confirmation",
                    passed = canInstallOrUnknown,
                    detail =
                        if (canInstallOrUnknown) "Authorized to launch system installer"
                        else "System will request install permission",
                ),
            )
        return TesterEligibility(
            isEligible = minApiOk && abi.isNotBlank() && notFdroid && installedPkgValid,
            checks = checks,
        )
    }

    suspend fun loadProgramOverview(): Result<BetaOverviewData> =
        withContext(Dispatchers.IO) {
            runCatching {
                val testerId = getOrCreateTesterId()
                val serverUrl = getConfiguredServerUrl()
                var enrollmentType = BetaEnrollmentType.fromWire(BETA_ENROLLMENT_TYPE.getString())
                var enrollmentStatus = BetaEnrollmentStatus.fromWire(BETA_ENROLLMENT_STATUS.getString())
                var serverConfig = BetaConfigDto()
                val items = mutableListOf<BetaReleaseItem>()

                val githubReleases = UpdateUtil.fetchAllReleases().getOrElse { emptyList() }
                val latestStableGh = UpdateUtil.selectLatestReleaseForChannel(githubReleases, STABLE)
                val latestBetaGh = UpdateUtil.selectLatestReleaseForChannel(githubReleases, PRE_RELEASE)

                githubReleases.forEach { rel ->
                    val category = classifyReleaseCategory(rel, latestStableGh, latestBetaGh)
                    val meta = parseBetaReleaseMetadata(rel.body)
                    val matchingAsset =
                        rel.assets
                            ?.firstOrNull { a ->
                                val n = a.name.orEmpty().lowercase()
                                n.endsWith(".apk") &&
                                    !n.contains("debug") &&
                                    Build.SUPPORTED_ABIS.any { abi ->
                                        n.contains(abi, ignoreCase = true)
                                    }
                            }
                            ?: rel.assets?.firstOrNull {
                                it.name?.endsWith(".apk", ignoreCase = true) == true
                            }
                    val assetSha =
                        matchingAsset?.digest
                            ?.removePrefix("sha256:")
                            ?.takeIf { it.length == 64 }
                            ?: meta.sha256Hex

                    items +=
                        BetaReleaseItem(
                            id = "gh_${rel.id}_${rel.tagName}",
                            tagName = rel.tagName.orEmpty(),
                            versionName = rel.resolvedVersion().toVersionName(),
                            title = rel.displayTitle(),
                            category = category,
                            channelLabel =
                                when (category) {
                                    ReleaseCategory.BETA -> "Beta"
                                    ReleaseCategory.STABLE -> "Stable"
                                    ReleaseCategory.ARCHIVED -> "Archived"
                                },
                            isFromServer = false,
                            isEnabled = rel.draft != true,
                            publishedAt = rel.publishedAt ?: rel.createdAt.orEmpty(),
                            releaseNotes = rel.body.orEmpty(),
                            knownBugs = meta.knownBugs,
                            experimentalFeatures = meta.experimentalFeatures,
                            apkUrl = matchingAsset?.browserDownloadUrl,
                            apkFileName = matchingAsset?.name,
                            apkSizeBytes = (matchingAsset?.size ?: 0).toLong(),
                            sha256 = assetSha,
                            downloadCount = rel.assets?.sumOf { it.downloadCount ?: 0 } ?: 0,
                            rawGitHubRelease = rel,
                        )
                }

                if (serverUrl.isNotBlank() && UpdateUtil.isHttpsUrl(serverUrl)) {
                    runCatching {
                        fetchJson<BetaConfigDto>("$serverUrl/api/beta/config")
                    }.getOrNull()?.let { serverConfig = it }

                    runCatching {
                        fetchJson<BetaStatusDto>("$serverUrl/api/beta/status?deviceId=$testerId")
                    }.getOrNull()?.let { st ->
                        enrollmentType = BetaEnrollmentType.fromWire(st.enrollmentType)
                        enrollmentStatus = BetaEnrollmentStatus.fromWire(st.status)
                        BETA_ENROLLMENT_TYPE.updateString(enrollmentType.wireValue)
                        BETA_ENROLLMENT_STATUS.updateString(enrollmentStatus.wireValue)
                    }

                    runCatching {
                        fetchJson<BetaServerReleasesEnvelope>(
                            "$serverUrl/api/releases?channel=all&deviceId=$testerId"
                        )
                    }.getOrNull()?.releases?.forEach { srv ->
                        val cat =
                            when (srv.channel.lowercase()) {
                                "stable" -> ReleaseCategory.STABLE
                                "archived" -> ReleaseCategory.ARCHIVED
                                else -> ReleaseCategory.BETA
                            }
                        val meta =
                            parseBetaReleaseMetadata(
                                srv.releaseNotes,
                                explicitKnownBugs = srv.knownBugs,
                                explicitExperimental = srv.experimentalFeatures,
                            )
                        items.add(
                            0,
                            BetaReleaseItem(
                                id = srv.id,
                                tagName = "v${srv.version.removePrefix("v")}",
                                versionName = srv.version.removePrefix("v"),
                                title = "Pengu ${srv.version}",
                                category = cat,
                                channelLabel =
                                    when {
                                        srv.channel.equals("closed", ignoreCase = true) ->
                                            "Closed Beta"
                                        cat == ReleaseCategory.BETA -> "Beta"
                                        cat == ReleaseCategory.STABLE -> "Stable"
                                        else -> "Archived"
                                    },
                                isClosedBetaOnly = srv.channel.equals("closed", ignoreCase = true),
                                isFromServer = true,
                                isEnabled = srv.enabled,
                                publishedAt = srv.publishedAt,
                                releaseNotes = srv.releaseNotes,
                                knownBugs = meta.knownBugs,
                                experimentalFeatures = meta.experimentalFeatures,
                                apkUrl = srv.apkUrl.takeIf { it.isNotBlank() },
                                apkFileName = "Pengu-${srv.version}.apk",
                                apkSizeBytes = 0L,
                                sha256 = srv.sha256.takeIf { it.isNotBlank() } ?: meta.sha256Hex,
                                downloadCount = srv.downloadCount,
                                rawGitHubRelease = null,
                            ),
                        )
                    }
                }

                val sorted = items.sortedByDescending { it.versionName.toVersion() }
                val latestBeta = sorted.firstOrNull { it.category == ReleaseCategory.BETA && it.isEnabled }
                val latestStable = sorted.firstOrNull { it.category == ReleaseCategory.STABLE && it.isEnabled }

                BetaOverviewData(
                    installedVersion = BuildConfig.VERSION_NAME,
                    installedFlavor = BuildConfig.FLAVOR,
                    updateChannel = UPDATE_CHANNEL.getInt(),
                    enrollmentType = enrollmentType,
                    enrollmentStatus = enrollmentStatus,
                    testerId = testerId,
                    serverUrl = serverUrl,
                    config = serverConfig,
                    eligibility = computeEligibility(),
                    latestBeta = latestBeta,
                    latestStable = latestStable,
                    allReleases = sorted,
                )
            }
        }

    suspend fun joinOpenBeta(): Result<BetaEnrollmentStatus> =
        withContext(Dispatchers.IO) {
            runCatching {
                val serverUrl = getConfiguredServerUrl()
                val testerId = getOrCreateTesterId()
                if (serverUrl.isNotBlank()) {
                    UpdateUtil.requireHttpsUrl(serverUrl)
                    val payload =
                        """{"deviceId":"$testerId","enrollmentType":"open","deviceModel":"${escapeJson(Build.MANUFACTURER + " " + Build.MODEL)}"}"""
                    val dto = postJson<BetaEnrollResponseDto>("$serverUrl/api/beta/enroll", payload)
                    val resolvedStatus = BetaEnrollmentStatus.fromWire(dto.status)
                    BETA_ENROLLMENT_TYPE.updateString(BetaEnrollmentType.OPEN.wireValue)
                    BETA_ENROLLMENT_STATUS.updateString(resolvedStatus.wireValue)
                    if (resolvedStatus == BetaEnrollmentStatus.ACTIVE) {
                        UPDATE_CHANNEL.updateInt(PRE_RELEASE)
                    }
                    resolvedStatus
                } else {
                    BETA_ENROLLMENT_TYPE.updateString(BetaEnrollmentType.OPEN.wireValue)
                    BETA_ENROLLMENT_STATUS.updateString(BetaEnrollmentStatus.ACTIVE.wireValue)
                    UPDATE_CHANNEL.updateInt(PRE_RELEASE)
                    BetaEnrollmentStatus.ACTIVE
                }
            }
        }

    suspend fun requestClosedBeta(inviteCode: String): Result<BetaEnrollmentStatus> =
        withContext(Dispatchers.IO) {
            runCatching {
                val serverUrl = getConfiguredServerUrl()
                if (serverUrl.isBlank()) {
                    throw IllegalStateException(
                        "Closed Beta enforces server-side invite & seat capacity control. Enter your HTTPS Beta Server URL inside My Testing Program first."
                    )
                }
                UpdateUtil.requireHttpsUrl(serverUrl)
                val testerId = getOrCreateTesterId()
                val payload =
                    """{"deviceId":"$testerId","enrollmentType":"closed","inviteCode":"${escapeJson(inviteCode.trim())}","deviceModel":"${escapeJson(Build.MANUFACTURER + " " + Build.MODEL)}"}"""
                val result = postJson<BetaEnrollResponseDto>("$serverUrl/api/beta/enroll", payload)
                val status = BetaEnrollmentStatus.fromWire(result.status)
                BETA_ENROLLMENT_TYPE.updateString(BetaEnrollmentType.CLOSED.wireValue)
                BETA_ENROLLMENT_STATUS.updateString(status.wireValue)
                BETA_INVITE_CODE.updateString(inviteCode.trim())
                if (status == BetaEnrollmentStatus.ACTIVE) {
                    UPDATE_CHANNEL.updateInt(PRE_RELEASE)
                }
                status
            }
        }

    suspend fun leaveBetaProgram(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val serverUrl = getConfiguredServerUrl()
                val testerId = getOrCreateTesterId()
                if (serverUrl.isNotBlank() && UpdateUtil.isHttpsUrl(serverUrl)) {
                    runCatching {
                        postJson<BetaStatusDto>(
                            "$serverUrl/api/beta/leave",
                            """{"deviceId":"$testerId"}""",
                        )
                    }
                }
                BETA_ENROLLMENT_TYPE.updateString(BetaEnrollmentType.NONE.wireValue)
                BETA_ENROLLMENT_STATUS.updateString(BetaEnrollmentStatus.NOT_ENROLLED.wireValue)
                BETA_INVITE_CODE.updateString("")
                UPDATE_CHANNEL.updateInt(STABLE)
                Unit
            }
        }

    fun setUpdateChannelExplicit(channel: Int) {
        val safeChannel = validateChannelSwitch(channel, isAutoBackgroundCheck = false, UPDATE_CHANNEL.getInt())
        UPDATE_CHANNEL.updateInt(safeChannel)
    }

    suspend fun trackServerDownload(releaseId: String) =
        withContext(Dispatchers.IO) {
            val serverUrl = getConfiguredServerUrl()
            if (serverUrl.isNotBlank() && UpdateUtil.isHttpsUrl(serverUrl)) {
                runCatching {
                    val req =
                        Request.Builder()
                            .url("$serverUrl/api/releases/$releaseId/track-download")
                            .post("{}".toRequestBody("application/json".toMediaType()))
                            .build()
                    client.newCall(req).execute().close()
                }
            }
        }

    private inline fun <reified T> fetchJson(url: String): T {
        val req = Request.Builder().url(UpdateUtil.requireHttpsUrl(url)).build()
        return client.newCall(req).execute().use { resp ->
            val raw = resp.body.string()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: $raw")
            json.decodeFromString(raw)
        }
    }

    private inline fun <reified T> postJson(url: String, jsonPayload: String): T {
        val req =
            Request.Builder()
                .url(UpdateUtil.requireHttpsUrl(url))
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .build()
        return client.newCall(req).execute().use { resp ->
            val raw = resp.body.string()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: $raw")
            json.decodeFromString(raw)
        }
    }

    companion object {
        fun classifyReleaseCategory(
            release: UpdateUtil.Release,
            latestStable: UpdateUtil.Release?,
            latestBeta: UpdateUtil.Release?,
        ): ReleaseCategory {
            if (release.draft == true) return ReleaseCategory.ARCHIVED
            val isPre = UpdateUtil.isBetaOrPreRelease(release)
            return if (isPre) {
                val isLatestBeta =
                    latestBeta != null &&
                        release.tagName == latestBeta.tagName &&
                        release.resolvedVersion() == latestBeta.resolvedVersion()
                if (isLatestBeta) ReleaseCategory.BETA else ReleaseCategory.ARCHIVED
            } else {
                val isLatestStable =
                    latestStable != null &&
                        release.tagName == latestStable.tagName &&
                        release.resolvedVersion() == latestStable.resolvedVersion()
                if (isLatestStable) ReleaseCategory.STABLE else ReleaseCategory.ARCHIVED
            }
        }

        fun validateChannelSwitch(
            requestedChannel: Int,
            isAutoBackgroundCheck: Boolean,
            currentChannel: Int,
        ): Int = if (isAutoBackgroundCheck) currentChannel else requestedChannel

        fun parseBetaReleaseMetadata(
            body: String?,
            explicitKnownBugs: List<String> = emptyList(),
            explicitExperimental: List<String> = emptyList(),
        ): ParsedReleaseMetadata {
            val bugs = mutableListOf<String>()
            val experimental = mutableListOf<String>()
            if (!body.isNullOrBlank()) {
                var section = ""
                for (rawLine in body.replace("\r\n", "\n").split("\n")) {
                    val line = rawLine.trim()
                    when {
                        line.startsWith("#") -> {
                            val lower = line.lowercase()
                            section =
                                when {
                                    lower.contains("known bug") || lower.contains("known issue") -> "bugs"
                                    lower.contains("experimental") -> "experimental"
                                    else -> ""
                                }
                        }
                        line.startsWith("- ") || line.startsWith("* ") -> {
                            val item = line.drop(2).trim()
                            if (section == "bugs" && item.isNotBlank()) bugs += item
                            else if (section == "experimental" && item.isNotBlank()) experimental += item
                        }
                    }
                }
            }
            val sha = UpdateUtil.extractSha256ForAsset(body, null)
            return ParsedReleaseMetadata(
                knownBugs = (explicitKnownBugs + bugs).distinct(),
                experimentalFeatures = (explicitExperimental + experimental).distinct(),
                sha256Hex = sha,
            )
        }

        private fun escapeJson(value: String): String =
            value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }
}
