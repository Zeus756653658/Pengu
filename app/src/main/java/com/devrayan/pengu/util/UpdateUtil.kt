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

    private fun getLatestRelease(): Release? =
        client.newCall(requestForReleases).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub API error: HTTP ${response.code}")
            }
            val releaseList = jsonFormat.decodeFromString<List<Release>>(response.body.string())
            val stable = UPDATE_CHANNEL.getInt() == STABLE
            releaseList
                .filter { release ->
                    release.draft != true &&
                        release.resolvedVersion() != EMPTY_VERSION &&
                        (!stable ||
                            (release.preRelease != true &&
                                release.resolvedVersion() is Version.Stable))
                }
                .maxByOrNull { it.resolvedVersion() }
        }

    fun checkForUpdate(context: Context = App.context): Release? {
        val currentVersion = context.getCurrentVersion()
        val latestRelease = getLatestRelease() ?: return null
        val latestVersion = latestRelease.resolvedVersion()
        return if (currentVersion < latestVersion) latestRelease else null
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

    suspend fun downloadApk(
        context: Context = App.context,
        release: Release,
    ): Flow<DownloadStatus> =
        withContext(Dispatchers.IO) {
            val latestApk = context.getLatestApk()
            latestApk.parentFile?.mkdirs()

            val apkVersion =
                if (latestApk.exists()) {
                    context.packageManager
                        .getPackageArchiveInfo(latestApk.absolutePath, 0)
                        ?.versionName
                        .toVersion()
                } else {
                    EMPTY_VERSION
                }

            Log.d(TAG, "Cached APK version: $apkVersion")

            val targetVersion = release.resolvedVersion()
            if (apkVersion >= targetVersion && targetVersion != EMPTY_VERSION) {
                return@withContext flow { emit(DownloadStatus.Finished(latestApk)) }
            }

            val targetUrl =
                selectApkAsset(release.assets)?.browserDownloadUrl
                    ?: throw IOException(
                        "No compatible APK asset found in release ${release.displayTitle()}"
                    )
            val request =
                Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", userAgent)
                    .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                throw IOException("Failed to download APK: HTTP ${response.code}")
            }
            response.body.downloadFileWithProgress(latestApk)
        }

    private fun ResponseBody.downloadFileWithProgress(saveFile: File): Flow<DownloadStatus> =
        flow {
                emit(DownloadStatus.Progress(0))

                val tempFile = File(saveFile.parentFile, "${saveFile.name}.tmp")
                var completed = false

                try {
                    this@downloadFileWithProgress.use { body ->
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

                    if (saveFile.exists()) saveFile.delete()
                    if (!tempFile.renameTo(saveFile)) {
                        tempFile.copyTo(saveFile, overwrite = true)
                        tempFile.delete()
                    }
                    completed = true
                    emit(DownloadStatus.Finished(saveFile))
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
        val name: String? = null,
        @SerialName("content_type") val contentType: String? = null,
        val size: Int? = null,
        @SerialName("download_count") val downloadCount: Int? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null,
        @SerialName("browser_download_url") val browserDownloadUrl: String? = null,
    )

    sealed class DownloadStatus {
        object NotYet : DownloadStatus()

        data class Progress(val percent: Int) : DownloadStatus()

        data class Finished(val file: File) : DownloadStatus()
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
