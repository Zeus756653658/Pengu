package com.devrayan.pengu.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.devrayan.pengu.App
import com.devrayan.pengu.App.Companion.context
import com.devrayan.pengu.BuildConfig
import com.devrayan.pengu.R
import com.devrayan.pengu.util.FileUtil.getFileProvider
import com.devrayan.pengu.util.PreferenceUtil.getInt
import com.devrayan.pengu.util.PreferenceUtil.updateLong
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody

object UpdateUtil {

    private const val OWNER = "Zeus756653658"
    private const val REPO = "Pengu"
    private const val ARM64 = "arm64-v8a"
    private const val ARM32 = "armeabi-v7a"
    private const val X86 = "x86"
    private const val X64 = "x86_64"
    private const val TAG = "UpdateUtil"

    private val userAgent = "Pengu-Android/${BuildConfig.VERSION_NAME}"

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

    private val requestForLatestRelease =
        Request.Builder()
            .url("https://api.github.com/repos/${OWNER}/${REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", userAgent)
            .build()

    private val requestForReleases =
        Request.Builder()
            .url("https://api.github.com/repos/${OWNER}/${REPO}/releases")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", userAgent)
            .build()

    private const val ytdlpNightlyBuildRelease =
        "https://api.github.com/repos/yt-dlp/yt-dlp-nightly-builds/releases/latest"

    private val jsonFormat = Json { ignoreUnknownKeys = true }

    fun Release.resolvedVersion(): Version =
        name.toVersionOrNull() ?: tagName.toVersionOrNull() ?: EMPTY_VERSION

    fun Release.displayTitle(): String =
        name?.takeIf { it.isNotBlank() } ?: tagName.orEmpty()

    suspend fun updateYtDlp(): YoutubeDL.UpdateStatus? =
        withContext(Dispatchers.IO) {
            val channel =
                when (YT_DLP_UPDATE_CHANNEL.getInt()) {
                    YT_DLP_NIGHTLY -> YoutubeDL.UpdateChannel.NIGHTLY
                    else -> YoutubeDL.UpdateChannel.STABLE
                }

            YoutubeDL.getInstance()
                .updateYoutubeDL(appContext = context, updateChannel = channel)
                .also {
                    if (it == YoutubeDL.UpdateStatus.DONE) {
                        YoutubeDL.getInstance().version(context)?.let {
                            PreferenceUtil.encodeString(YT_DLP_VERSION, it)
                        }
                    }
                    val now = System.currentTimeMillis()
                    YT_DLP_UPDATE_TIME.updateLong(now)
                }
        }

    fun isBetaOrPreRelease(release: Release): Boolean {
        val tag = release.tagName.orEmpty().lowercase()
        val name = release.name.orEmpty().lowercase()
        val version = release.resolvedVersion()
        return release.preRelease == true ||
            version !is Version.Stable ||
            tag.contains("beta") ||
            tag.contains("alpha") ||
            tag.contains("-rc") ||
            name.contains("beta") ||
            name.contains("alpha")
    }

    fun matchesChannel(release: Release, channel: Int): Boolean {
        if (release.draft == true || release.resolvedVersion() == EMPTY_VERSION) return false
        val isPre = isBetaOrPreRelease(release)
        return when (channel) {
            PRE_RELEASE -> isPre
            else -> !isPre
        }
    }

    fun selectLatestReleaseForChannel(releases: List<Release>, channel: Int): Release? =
        releases.filter { matchesChannel(it, channel) }.maxByOrNull { it.resolvedVersion() }

    private fun getLatestRelease(channel: Int = UPDATE_CHANNEL.getInt()): Release? {
        val stable = channel == STABLE
        val listResult =
            runCatching {
                client.newCall(requestForReleases).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("GitHub API error: HTTP ${response.code}")
                    }
                    val releaseList = jsonFormat.decodeFromString<List<Release>>(response.body.string())
                    selectLatestReleaseForChannel(releaseList, channel)
                }
            }
        val selected = listResult.getOrNull()
        if (selected != null) return selected

        if (stable) {
            return client.newCall(requestForLatestRelease).execute().use { response ->
                if (!response.isSuccessful) {
                    throw listResult.exceptionOrNull()
                        ?: IOException("GitHub API error: HTTP ${response.code}")
                }
                val latest = jsonFormat.decodeFromString<Release>(response.body.string())
                latest.takeIf { matchesChannel(it, STABLE) }
            }
        }
        return listResult.getOrThrow()
    }

    fun checkForUpdate(
        context: Context = App.context,
        channel: Int = UPDATE_CHANNEL.getInt(),
    ): Release? {
        val currentVersion = context.getCurrentVersion()
        val latestRelease = getLatestRelease(channel) ?: return null
        val latestVersion = latestRelease.resolvedVersion()
        return if (currentVersion < latestVersion) latestRelease else null
    }

    suspend fun fetchAllReleases(): Result<List<Release>> =
        withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(requestForReleases).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("GitHub API error: HTTP ${response.code}")
                    }
                    jsonFormat
                        .decodeFromString<List<Release>>(response.body.string())
                        .filter { it.draft != true }
                        .sortedByDescending { it.resolvedVersion() }
                }
            }
        }

    private fun Context.getCurrentVersion(): Version =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager
                .getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                .versionName
                .toVersion()
        } else {
            packageManager.getPackageInfo(packageName, 0).versionName.toVersion()
        }

    private fun Context.getLatestApk() = File(getExternalFilesDir("apk"), "latest.apk")

    fun installLatestApk(context: Context = App.context) =
        context.run {
            kotlin
                .runCatching {
                    val contentUri =
                        FileProvider.getUriForFile(this, getFileProvider(), getLatestApk())
                    val intent =
                        Intent(Intent.ACTION_VIEW).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            setDataAndType(contentUri, "application/vnd.android.package-archive")
                        }
                    startActivity(intent)
                }
                .onFailure { throwable: Throwable ->
                    throwable.printStackTrace()
                    ToastUtil.makeToast(R.string.app_update_failed)
                }
        }

    suspend fun deleteOutdatedApk(context: Context = App.context) =
        context.runCatching {
            val apkFile = getLatestApk()
            if (apkFile.exists()) {
                val apkVersion =
                    context.packageManager
                        .getPackageArchiveInfo(apkFile.absolutePath, 0)
                        ?.versionName
                        .toVersion()
                if (apkVersion <= context.getCurrentVersion()) {
                    apkFile.delete()
                }
            }
        }

    private fun selectApkAsset(assets: List<AssetsItem>?): AssetsItem? {
        val apkAssets =
            assets
                ?.filter {
                    it.name?.endsWith(".apk", ignoreCase = true) == true &&
                        !it.browserDownloadUrl.isNullOrBlank()
                }
                .orEmpty()
        if (apkAssets.isEmpty()) return null

        val isDebug = App.isDebugBuild()
        val preferredPool =
            apkAssets
                .filter { asset ->
                    val name = asset.name.orEmpty().lowercase()
                    if (isDebug) name.contains("debug") else !name.contains("debug")
                }
                .ifEmpty { apkAssets }

        for (abi in Build.SUPPORTED_ABIS) {
            preferredPool.firstOrNull { it.name?.contains(abi, ignoreCase = true) == true }?.let {
                return it
            }
        }

        return preferredPool.firstOrNull {
            it.name?.contains("universal", ignoreCase = true) == true
        } ?: preferredPool.firstOrNull()
    }

    internal fun isHttpsUrl(url: String): Boolean =
        url.trim().startsWith("https://", ignoreCase = true)

    internal fun requireHttpsUrl(url: String): String {
        val trimmed = url.trim()
        if (!isHttpsUrl(trimmed)) {
            throw SecurityException("Only HTTPS downloads are permitted for Pengu updates")
        }
        return trimmed
    }

    internal fun extractSha256ForAsset(releaseBody: String?, assetName: String?): String? {
        if (releaseBody.isNullOrBlank()) return null
        val shaRegex = Regex("""([a-fA-F0-9]{64})""")
        if (!assetName.isNullOrBlank()) {
            val specificLine =
                releaseBody.lineSequence().firstOrNull { line ->
                    line.contains(assetName, ignoreCase = true) && shaRegex.containsMatchIn(line)
                }
            if (specificLine != null) {
                return shaRegex.find(specificLine)?.groupValues?.get(1)?.lowercase()
            }
        }
        val labeledLine =
            releaseBody.lineSequence().firstOrNull { line ->
                (line.contains("sha256", ignoreCase = true) ||
                    line.contains("sha-256", ignoreCase = true)) && shaRegex.containsMatchIn(line)
            }
        return labeledLine?.let { shaRegex.find(it)?.groupValues?.get(1)?.lowercase() }
    }

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8_192)
            var read = input.read(buf)
            while (read != -1) {
                digest.update(buf, 0, read)
                read = input.read(buf)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    data class CertVerifyResult(
        val matched: Boolean,
        val installedSha256: String,
        val candidateSha256: String,
        val reason: String? = null,
    )

    @Suppress("DEPRECATION")
    private fun extractPackageCertSha256s(context: Context, archivePath: String?): Set<String> {
        val pm = context.packageManager
        val signatures =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val flags = PackageManager.GET_SIGNING_CERTIFICATES
                val pkgInfo =
                    if (archivePath == null) pm.getPackageInfo(context.packageName, flags)
                    else pm.getPackageArchiveInfo(archivePath, flags)
                pkgInfo?.signingInfo?.let { info ->
                    if (info.hasMultipleSigners()) info.apkContentsSigners
                    else info.signingCertificateHistory
                }
            } else {
                val flags = PackageManager.GET_SIGNATURES
                val pkgInfo =
                    if (archivePath == null) pm.getPackageInfo(context.packageName, flags)
                    else pm.getPackageArchiveInfo(archivePath, flags)
                pkgInfo?.signatures
            } ?: return emptySet()

        val md = MessageDigest.getInstance("SHA-256")
        return signatures
            .map { sig ->
                md.reset()
                md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
            }
            .toSet()
    }

    fun verifyApkSigningCertificate(context: Context, apkFile: File): CertVerifyResult {
        val installedDigests = extractPackageCertSha256s(context, archivePath = null)
        val candidateDigests = extractPackageCertSha256s(context, archivePath = apkFile.absolutePath)
        val installedFirst = installedDigests.firstOrNull().orEmpty()
        val candidateFirst = candidateDigests.firstOrNull().orEmpty()

        if (candidateDigests.isEmpty()) {
            return CertVerifyResult(
                matched = false,
                installedSha256 = installedFirst,
                candidateSha256 = "",
                reason = "Downloaded APK has no readable signing certificate",
            )
        }
        val intersection = installedDigests.intersect(candidateDigests)
        val matched = installedDigests.isEmpty() || intersection.isNotEmpty()
        return CertVerifyResult(
            matched = matched,
            installedSha256 = installedFirst,
            candidateSha256 = candidateFirst,
            reason =
                if (matched) null
                else "APK signing certificate does not match installed Pengu signature",
        )
    }

    fun getCachedApkFile(context: Context = App.context): File = context.getLatestApk()

    suspend fun downloadApk(
        context: Context = App.context,
        release: Release,
        expectedSha256: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): Flow<DownloadStatus> =
        withContext(Dispatchers.IO) {
            val latestApk = context.getLatestApk()
            latestApk.parentFile?.mkdirs()

            val selectedAsset =
                selectApkAsset(release.assets)
                    ?: throw IOException(
                        "No compatible APK asset found in release ${release.displayTitle()}"
                    )
            val targetUrl = requireHttpsUrl(selectedAsset.browserDownloadUrl.orEmpty())

            val resolvedExpectedSha =
                expectedSha256?.takeIf { it.isNotBlank() }?.lowercase()
                    ?: selectedAsset.digest?.removePrefix("sha256:")?.takeIf { it.length == 64 }?.lowercase()
                    ?: extractSha256ForAsset(release.body, selectedAsset.name)
                    ?: fetchCompanionSha256(release.assets, selectedAsset.name)

            val apkVersion =
                if (latestApk.exists() && latestApk.length() > 0L) {
                    runCatching {
                            context.packageManager
                                .getPackageArchiveInfo(latestApk.absolutePath, 0)
                                ?.versionName
                                .toVersion()
                        }
                        .getOrDefault(EMPTY_VERSION)
                } else {
                    EMPTY_VERSION
                }

            if (latestApk.exists() && apkVersion == EMPTY_VERSION) {
                latestApk.delete()
            }

            Log.d(TAG, "Cached APK version: $apkVersion")

            val targetVersion = release.resolvedVersion()
            if (apkVersion == targetVersion && targetVersion != EMPTY_VERSION && latestApk.exists()) {
                val cert = verifyApkSigningCertificate(context, latestApk)
                val sha = computeSha256(latestApk)
                val shaMatches = resolvedExpectedSha == null || resolvedExpectedSha.equals(sha, ignoreCase = true)
                if (cert.matched && shaMatches) {
                    return@withContext flow {
                        emit(
                            DownloadStatus.Finished(
                                file = latestApk,
                                sha256Hex = sha,
                                certVerified = true,
                            )
                        )
                    }
                } else {
                    latestApk.delete()
                }
            }

            val request =
                Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", userAgent)
                    .apply { extraHeaders.forEach { (k, v) -> header(k, v) } }
                    .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                throw IOException("Failed to download APK: HTTP ${response.code}")
            }
            response.body.downloadFileWithVerify(
                context = context,
                saveFile = latestApk,
                expectedSha256 = resolvedExpectedSha,
            )
        }

    private fun fetchCompanionSha256(assets: List<AssetsItem>?, apkName: String?): String? {
        if (assets.isNullOrEmpty() || apkName.isNullOrBlank()) return null
        val shaAsset =
            assets.firstOrNull {
                val n = it.name.orEmpty()
                n.equals("$apkName.sha256", ignoreCase = true) ||
                    n.equals("SHA256SUMS.txt", ignoreCase = true) ||
                    n.equals("checksums.sha256", ignoreCase = true)
            } ?: return null
        val url = shaAsset.browserDownloadUrl?.takeIf { isHttpsUrl(it) } ?: return null
        return runCatching {
            val req = Request.Builder().url(url).header("User-Agent", userAgent).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                extractSha256ForAsset(resp.body?.string(), apkName)
            }
        }.getOrNull()
    }

    private fun ResponseBody.downloadFileWithVerify(
        context: Context,
        saveFile: File,
        expectedSha256: String?,
    ): Flow<DownloadStatus> =
        flow {
                emit(DownloadStatus.Progress(0))

                val tempFile = File(saveFile.parentFile, "${saveFile.name}.tmp")
                var completed = false

                try {
                    this@downloadFileWithVerify.use { body ->
                        body.byteStream().use { inputStream ->
                            tempFile.outputStream().use { outputStream ->
                                val totalBytes = body.contentLength()
                                val data = ByteArray(8_192)
                                var progressBytes = 0L

                                while (true) {
                                    val bytes = inputStream.read(data)
                                    if (bytes == -1) break

                                    outputStream.write(data, 0, bytes)
                                    progressBytes += bytes
                                    if (totalBytes > 0L) {
                                        emit(
                                            DownloadStatus.Progress(
                                                percent =
                                                    ((progressBytes * 100L) / totalBytes)
                                                        .toInt()
                                                        .coerceIn(0, 100)
                                            )
                                        )
                                    }
                                }

                                if (totalBytes > 0L) {
                                    when {
                                        progressBytes < totalBytes ->
                                            throw IOException("Incomplete download: missing bytes")
                                        progressBytes > totalBytes ->
                                            throw IOException("Corrupt download: extra bytes")
                                    }
                                }
                            }
                        }
                    }

                    emit(DownloadStatus.Verifying)
                    val actualSha256 = computeSha256(tempFile)
                    if (!expectedSha256.isNullOrBlank() && !expectedSha256.equals(actualSha256, ignoreCase = true)) {
                        throw SecurityException("SHA-256 verification failed: expected $expectedSha256 but got $actualSha256")
                    }
                    val certResult = verifyApkSigningCertificate(context, tempFile)
                    if (!certResult.matched) {
                        throw SecurityException(certResult.reason ?: "APK signing certificate mismatch")
                    }

                    if (saveFile.exists()) saveFile.delete()
                    if (!tempFile.renameTo(saveFile)) {
                        tempFile.copyTo(saveFile, overwrite = true)
                        tempFile.delete()
                    }
                    completed = true
                    emit(
                        DownloadStatus.Finished(
                            file = saveFile,
                            sha256Hex = actualSha256,
                            certVerified = certResult.matched,
                        )
                    )
                } finally {
                    if (!completed) {
                        tempFile.delete()
                    }
                }
            }
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()

    @Serializable
    data class Release(
        val id: Long = 0L,
        @SerialName("html_url") val htmlUrl: String? = null,
        @SerialName("tag_name") val tagName: String? = null,
        val name: String? = null,
        val draft: Boolean? = null,
        @SerialName("prerelease") val preRelease: Boolean? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("published_at") val publishedAt: String? = null,
        val assets: List<AssetsItem>? = null,
        val body: String? = null,
    )

    @Serializable
    data class AssetsItem(
        val id: Long = 0L,
        val name: String? = null,
        @SerialName("content_type") val contentType: String? = null,
        val size: Int? = null,
        @SerialName("download_count") val downloadCount: Int? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null,
        @SerialName("browser_download_url") val browserDownloadUrl: String? = null,
        val digest: String? = null,
    )

    sealed class DownloadStatus {
        object NotYet : DownloadStatus()

        data class Progress(val percent: Int) : DownloadStatus()

        data object Verifying : DownloadStatus()

        data class Finished(
            val file: File,
            val sha256Hex: String = "",
            val certVerified: Boolean = true,
        ) : DownloadStatus()
    }

    private val pattern = Pattern.compile("""v?(\d+)\.(\d+)\.(\d+)(-(\w+)\.(\d+))?""")
    private val EMPTY_VERSION = Version.Stable()

    fun String?.toVersionOrNull(): Version? =
        this?.run {
            val matcher = pattern.matcher(this)
            if (matcher.find()) {
                val major = matcher.group(1)?.toInt() ?: 0
                val minor = matcher.group(2)?.toInt() ?: 0
                val patch = matcher.group(3)?.toInt() ?: 0
                val buildNumber = matcher.group(6)?.toInt() ?: 0
                when (matcher.group(5)) {
                    "alpha" -> Version.Alpha(major, minor, patch, buildNumber)
                    "beta" -> Version.Beta(major, minor, patch, buildNumber)
                    "rc" -> Version.ReleaseCandidate(major, minor, patch, buildNumber)
                    else -> Version.Stable(major, minor, patch)
                }
            } else null
        }

    fun String?.toVersion(): Version = toVersionOrNull() ?: EMPTY_VERSION

    sealed class Version(val major: Int, val minor: Int, val patch: Int, val build: Int = 0) :
        Comparable<Version> {
        companion object {
            // private const val ABI = 1L
            private const val BUILD = 10L
            private const val VARIANT = 100L
            private const val PATCH = 10_000L
            private const val MINOR = 1_000_000L
            private const val MAJOR = 100_000_000L

            private const val STABLE = VARIANT * 4
            private const val ALPHA = VARIANT * 1
            private const val BETA = VARIANT * 2
            private const val RELEASE_CANDIDATE = VARIANT * 3
        }

        abstract fun toVersionName(): String

        abstract fun toNumber(): Long

        class Alpha(
            versionMajor: Int = 0,
            versionMinor: Int = 0,
            versionPatch: Int = 0,
            versionBuild: Int = 0,
        ) : Version(versionMajor, versionMinor, versionPatch, versionBuild) {
            override fun toVersionName(): String = "${major}.${minor}.${patch}-alpha.$build"

            override fun toNumber(): Long =
                major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + ALPHA
        }

        class Beta(versionMajor: Int, versionMinor: Int, versionPatch: Int, versionBuild: Int) :
            Version(versionMajor, versionMinor, versionPatch, versionBuild) {
            override fun toVersionName(): String = "${major}.${minor}.${patch}-beta.$build"

            override fun toNumber(): Long =
                major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + BETA
        }

        class ReleaseCandidate(
            versionMajor: Int,
            versionMinor: Int,
            versionPatch: Int,
            versionBuild: Int,
        ) : Version(versionMajor, versionMinor, versionPatch, versionBuild) {
            override fun toVersionName(): String = "${major}.${minor}.${patch}-rc.$build"

            override fun toNumber(): Long =
                major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + RELEASE_CANDIDATE
        }

        class Stable(versionMajor: Int = 0, versionMinor: Int = 0, versionPatch: Int = 0) :
            Version(versionMajor, versionMinor, versionPatch) {
            override fun toVersionName(): String = "${major}.${minor}.${patch}"

            override fun toNumber(): Long =
                major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + STABLE
            // Prioritize stable versions

        }

        override operator fun compareTo(other: Version): Int =
            this.toNumber().compareTo(other.toNumber())

        override fun equals(other: Any?): Boolean =
            other is Version && this.toNumber() == other.toNumber()

        override fun hashCode(): Int = toNumber().hashCode()
    }
}
