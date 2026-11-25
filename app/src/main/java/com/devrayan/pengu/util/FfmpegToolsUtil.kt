package com.devrayan.pengu.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.OpenableColumns
import com.devrayan.pengu.App
import com.devrayan.pengu.database.objects.DownloadedVideoInfo
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Offline FFmpeg media tools (Video-to-MP3 & Video/Audio Trimmer) built on top of Pengu's
 * bundled `libffmpeg.so` native binary and MediaMetadataRetriever.
 */
object FfmpegToolsUtil {

    data class MediaProbeInfo(
        val displayName: String,
        val durationMs: Long,
        val isAudioOnly: Boolean,
        val extension: String,
    )

    fun probeMedia(context: Context, uri: Uri): MediaProbeInfo {
        val displayName = resolveDisplayName(context, uri)
        val ext = displayName.substringAfterLast('.', "").lowercase().ifBlank {
            val mime = context.contentResolver.getType(uri).orEmpty()
            when {
                mime.contains("mp3") || mime.contains("mpeg") -> "mp3"
                mime.contains("mp4") -> "mp4"
                mime.contains("m4a") || mime.contains("aac") -> "m4a"
                mime.contains("webm") -> "webm"
                else -> "mp4"
            }
        }
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        var hasVideo = false
        try {
            retriever.setDataSource(context, uri)
            durationMs =
                retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            hasVideo =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
        } catch (_: Exception) {
        } finally {
            runCatching { retriever.release() }
        }
        val audioExt = ext in setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")
        return MediaProbeInfo(
            displayName = displayName,
            durationMs = durationMs,
            isAudioOnly = !hasVideo && audioExt,
            extension = ext,
        )
    }

    fun probeLocalFile(filePath: String): MediaProbeInfo {
        val file = File(filePath)
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        var hasVideo = false
        try {
            retriever.setDataSource(filePath)
            durationMs =
                retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            hasVideo =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
        } catch (_: Exception) {
        } finally {
            runCatching { retriever.release() }
        }
        val ext = file.extension.lowercase().ifBlank { "mp4" }
        val audioExt = ext in setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")
        return MediaProbeInfo(
            displayName = file.name,
            durationMs = durationMs,
            isAudioOnly = !hasVideo && audioExt,
            extension = ext,
        )
    }

    suspend fun convertToMp3(
        context: Context,
        inputUri: Uri?,
        inputFilePath: String?,
        bitrateKbps: Int,
        outputBaseName: String,
    ): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureFfmpegReady(context)
                val (inputFile, tempInput) = prepareReadableInput(context, inputUri, inputFilePath)
                try {
                    val outDir = File(App.audioDownloadDir).apply { mkdirs() }
                    val safeName = sanitizeName(outputBaseName).ifBlank { "pengu_audio" }
                    val outFile = uniqueFile(outDir, safeName, "mp3")
                    val args =
                        listOf(
                            "-y",
                            "-i",
                            inputFile.absolutePath,
                            "-vn",
                            "-acodec",
                            "libmp3lame",
                            "-b:a",
                            "${bitrateKbps}k",
                            outFile.absolutePath,
                        )
                    runFfmpeg(context, args)
                    registerOutput(context, outFile, "Converted MP3 ($bitrateKbps kbps)")
                    outFile
                } finally {
                    tempInput?.delete()
                }
            }
        }

    suspend fun trimMedia(
        context: Context,
        inputUri: Uri?,
        inputFilePath: String?,
        startSeconds: Long,
        endSeconds: Long,
        isAudioOnly: Boolean,
        outputExt: String,
        outputBaseName: String,
    ): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (endSeconds <= startSeconds) {
                    throw IllegalArgumentException("End time must be greater than start time")
                }
                ensureFfmpegReady(context)
                val (inputFile, tempInput) = prepareReadableInput(context, inputUri, inputFilePath)
                try {
                    val targetDir =
                        File(if (isAudioOnly) App.audioDownloadDir else App.videoDownloadDir)
                            .apply { mkdirs() }
                    val safeName = sanitizeName(outputBaseName).ifBlank { "pengu_clip" }
                    val cleanExt = outputExt.lowercase().ifBlank { if (isAudioOnly) "mp3" else "mp4" }
                    val outFile = uniqueFile(targetDir, "${safeName}_trim", cleanExt)

                    val args =
                        if (cleanExt == "mp3") {
                            listOf(
                                "-y",
                                "-ss",
                                formatFfmpegTime(startSeconds),
                                "-to",
                                formatFfmpegTime(endSeconds),
                                "-i",
                                inputFile.absolutePath,
                                "-vn",
                                "-acodec",
                                "libmp3lame",
                                "-q:a",
                                "2",
                                outFile.absolutePath,
                            )
                        } else {
                            listOf(
                                "-y",
                                "-ss",
                                formatFfmpegTime(startSeconds),
                                "-to",
                                formatFfmpegTime(endSeconds),
                                "-i",
                                inputFile.absolutePath,
                                "-c",
                                "copy",
                                "-avoid_negative_ts",
                                "make_zero",
                                outFile.absolutePath,
                            )
                        }
                    runFfmpeg(context, args)
                    registerOutput(
                        context,
                        outFile,
                        "Trimmed (${formatClock(startSeconds)} - ${formatClock(endSeconds)})",
                    )
                    outFile
                } finally {
                    tempInput?.delete()
                }
            }
        }

    private fun ensureFfmpegReady(context: Context) {
        runCatching { YoutubeDL.getInstance().init(context) }
        runCatching { FFmpeg.getInstance().init(context) }
    }

    private fun runFfmpeg(context: Context, args: List<String>) {
        val ffmpegBin = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        if (!ffmpegBin.exists()) {
            throw IOException("FFmpeg binary not available on this device")
        }
        val baseDir = File(context.noBackupFilesDir, "youtubedl-android/packages")
        val libPaths =
            listOf(
                    File(baseDir, "python/usr/lib"),
                    File(baseDir, "ffmpeg/usr/lib"),
                    File(baseDir, "aria2c/usr/lib"),
                    File(context.applicationInfo.nativeLibraryDir),
                )
                .filter { it.exists() }
                .joinToString(":") { it.absolutePath }

        val command = mutableListOf(ffmpegBin.absolutePath).apply { addAll(args) }
        val pb = ProcessBuilder(command).redirectErrorStream(true)
        if (libPaths.isNotEmpty()) {
            val currentLd = pb.environment()["LD_LIBRARY_PATH"].orEmpty()
            pb.environment()["LD_LIBRARY_PATH"] =
                if (currentLd.isNotBlank()) "$libPaths:$currentLd" else libPaths
        }
        val process = pb.start()
        val outputLog = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val tail = outputLog.lineSequence().toList().takeLast(6).joinToString("\n")
            throw IOException("FFmpeg failed (exit $exitCode): $tail")
        }
    }

    private fun prepareReadableInput(
        context: Context,
        uri: Uri?,
        filePath: String?,
    ): Pair<File, File?> {
        if (!filePath.isNullOrBlank()) {
            val f = File(filePath)
            if (f.exists() && f.canRead()) {
                return f to null
            }
        }
        if (uri != null) {
            if (uri.scheme == "file") {
                uri.path?.let { filePath ->
                    val realFile = File(filePath)
                    if (realFile.exists() && realFile.canRead()) {
                        return realFile to null
                    }
                }
            }
            val displayName = resolveDisplayName(context, uri)
            val ext = displayName.substringAfterLast('.', "mp4").ifBlank { "mp4" }
            val tempFile = File(context.cacheDir, "pengu_tool_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("Unable to read selected file")
            return tempFile to tempFile
        }
        throw IllegalArgumentException("No input media selected")
    }

    private fun registerOutput(context: Context, outFile: File, authorLabel: String) {
        MediaScannerConnection.scanFile(context, arrayOf(outFile.absolutePath), null, null)
        DatabaseUtil.insertInfo(
            DownloadedVideoInfo(
                id = 0,
                videoTitle = outFile.nameWithoutExtension,
                videoAuthor = authorLabel,
                videoUrl = outFile.absolutePath,
                thumbnailUrl = "",
                videoPath = outFile.absolutePath,
                extractor = "Pengu Tools",
            )
        )
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        cursor.getString(idx)?.let { if (it.isNotBlank()) return it }
                    }
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "media_file"
    }

    private fun sanitizeName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()

    private fun uniqueFile(dir: File, base: String, ext: String): File {
        var candidate = File(dir, "$base.$ext")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "${base}_$counter.$ext")
            counter++
        }
        return candidate
    }

    fun formatFfmpegTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }

    fun formatClock(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }
}
