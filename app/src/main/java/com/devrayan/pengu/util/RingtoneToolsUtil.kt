package com.devrayan.pengu.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dedicated offline Ringtone Maker engine backed by bundled libffmpeg.so, MediaStore,
 * RingtoneManager, and ContactsContract.
 */
object RingtoneToolsUtil {

    enum class RingtoneTarget(val title: String, val subDirectory: String) {
        SAVE("Save Ringtone", "Ringtones"),
        SET_RINGTONE("Set Ringtone", "Ringtones"),
        SET_CONTACT_RINGTONE("Set Contact Ringtone", "Ringtones"),
        SET_ALARM("Set Alarm Sound", "Alarms"),
        SET_NOTIFICATION("Set Notification", "Notifications"),
    }

    data class MediaProbeInfo(
        val displayName: String,
        val durationMs: Long,
        val isAudioOnly: Boolean,
        val extension: String,
        val fileSizeBytes: Long = 0L,
    )

    data class RingtoneResult(
        val target: RingtoneTarget,
        val savedUri: Uri,
        val fileName: String,
        val displayPath: String,
        val assignedContactName: String? = null,
    )

    private const val MIN_CUT_DURATION_MS = 500L
    private val ffmpegTimeRegex = Regex("""time=(\d+):(\d+):(\d+(?:\.\d+)?)""")
    private val activeProcess = AtomicReference<Process?>(null)

    fun cancelActiveProcess() {
        activeProcess.getAndSet(null)?.let { proc ->
            runCatching { proc.destroyForcibly() }
            runCatching { proc.destroy() }
        }
    }

    fun canWriteSystemSettings(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.System.canWrite(context)
        else true

    fun probeMedia(context: Context, uri: Uri): MediaProbeInfo {
        val (displayName, reportedSize) = resolveUriMetadata(context, uri)
        val ext =
            displayName.substringAfterLast('.', "").lowercase().ifBlank {
                val mime = context.contentResolver.getType(uri).orEmpty()
                when {
                    mime.contains("mp3") || mime.contains("mpeg") -> "mp3"
                    mime.contains("m4a") || mime.contains("aac") -> "m4a"
                    mime.contains("ogg") -> "ogg"
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
            fileSizeBytes = reportedSize,
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
        val ext = file.extension.lowercase().ifBlank { "mp3" }
        val audioExt = ext in setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")
        return MediaProbeInfo(
            displayName = file.name,
            durationMs = durationMs,
            isAudioOnly = !hasVideo && audioExt,
            extension = ext,
            fileSizeBytes = if (file.exists()) file.length() else 0L,
        )
    }

    internal fun sanitizeRingtoneName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim { it.isWhitespace() || it == '_' }
            .ifBlank { "pengu_ringtone" }

    internal fun buildRingtoneCutArgs(
        inputPath: String,
        outputPath: String,
        startMs: Long,
        endMs: Long,
        bitrateKbps: Int = 192,
        fadeInOut: Boolean = true,
    ): List<String> {
        val safeStartMs = startMs.coerceAtLeast(0L)
        val clipDurationMs = (endMs - safeStartMs).coerceAtLeast(MIN_CUT_DURATION_MS)
        val durationSec = clipDurationMs / 1000.0
        val fadeArgs =
            if (fadeInOut && durationSec >= 2.0) {
                val fadeOutStart = String.format(Locale.US, "%.2f", (durationSec - 0.8).coerceAtLeast(0.5))
                listOf("-af", "afade=t=in:st=0:d=0.6,afade=t=out:st=$fadeOutStart:d=0.8")
            } else emptyList()

        return buildList {
            addAll(
                listOf(
                    "-y",
                    "-ss",
                    formatFfmpegTimeMs(safeStartMs),
                    "-i",
                    inputPath,
                    "-t",
                    formatFfmpegTimeMs(clipDurationMs),
                    "-vn",
                    "-acodec",
                    "libmp3lame",
                    "-b:a",
                    "${bitrateKbps}k",
                )
            )
            addAll(fadeArgs)
            add(outputPath)
        }
    }

    suspend fun createAndApplyRingtone(
        context: Context,
        inputUri: Uri?,
        inputFilePath: String?,
        startMs: Long,
        endMs: Long,
        outputBaseName: String,
        bitrateKbps: Int,
        fadeInOut: Boolean,
        target: RingtoneTarget,
        contactUri: Uri? = null,
        onProgress: (Float) -> Unit = {},
    ): Result<RingtoneResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val safeStartMs = startMs.coerceAtLeast(0L)
                if (endMs - safeStartMs < MIN_CUT_DURATION_MS) {
                    throw IllegalArgumentException("Select at least 0.5s for a ringtone clip")
                }
                if (
                    target in
                        setOf(
                            RingtoneTarget.SET_RINGTONE,
                            RingtoneTarget.SET_ALARM,
                            RingtoneTarget.SET_NOTIFICATION,
                        ) && !canWriteSystemSettings(context)
                ) {
                    throw SecurityException("Modify system settings permission is required")
                }

                ensureFfmpegReady(context)
                val (inputFile, tempInput) = prepareReadableInput(context, inputUri, inputFilePath)
                val safeTitle = sanitizeRingtoneName(outputBaseName)
                val tempOut = File(context.cacheDir, "${safeTitle}_${System.currentTimeMillis()}.mp3")
                try {
                    val args =
                        buildRingtoneCutArgs(
                            inputPath = inputFile.absolutePath,
                            outputPath = tempOut.absolutePath,
                            startMs = safeStartMs,
                            endMs = endMs,
                            bitrateKbps = bitrateKbps,
                            fadeInOut = fadeInOut,
                        )
                    runFfmpeg(context, args, endMs - safeStartMs, onProgress)

                    val finalFileName = "${safeTitle}.mp3"
                    val (contentUri, displayPath) =
                        publishRingtoneToMediaStore(
                            context = context,
                            sourceMp3 = tempOut,
                            title = safeTitle,
                            fileName = finalFileName,
                            target = target,
                        )

                    var assignedContact: String? = null
                    when (target) {
                        RingtoneTarget.SAVE -> Unit
                        RingtoneTarget.SET_RINGTONE ->
                            RingtoneManager.setActualDefaultRingtoneUri(
                                context,
                                RingtoneManager.TYPE_RINGTONE,
                                contentUri,
                            )
                        RingtoneTarget.SET_ALARM ->
                            RingtoneManager.setActualDefaultRingtoneUri(
                                context,
                                RingtoneManager.TYPE_ALARM,
                                contentUri,
                            )
                        RingtoneTarget.SET_NOTIFICATION ->
                            RingtoneManager.setActualDefaultRingtoneUri(
                                context,
                                RingtoneManager.TYPE_NOTIFICATION,
                                contentUri,
                            )
                        RingtoneTarget.SET_CONTACT_RINGTONE -> {
                            val selectedContact =
                                contactUri
                                    ?: throw IllegalArgumentException("Please select a contact")
                            assignedContact =
                                assignRingtoneToContact(context, selectedContact, contentUri)
                        }
                    }

                    PreferenceUtil.incrementToolOps()
                    RingtoneResult(
                        target = target,
                        savedUri = contentUri,
                        fileName = finalFileName,
                        displayPath = displayPath,
                        assignedContactName = assignedContact,
                    )
                } finally {
                    tempInput?.delete()
                    tempOut.delete()
                }
            }
        }

    private fun publishRingtoneToMediaStore(
        context: Context,
        sourceMp3: File,
        title: String,
        fileName: String,
        target: RingtoneTarget,
    ): Pair<Uri, String> {
        val resolver = context.contentResolver
        val isRingtone = target == RingtoneTarget.SAVE || target == RingtoneTarget.SET_RINGTONE || target == RingtoneTarget.SET_CONTACT_RINGTONE
        val isAlarm = target == RingtoneTarget.SET_ALARM
        val isNotification = target == RingtoneTarget.SET_NOTIFICATION

        val publicDirName =
            when (target) {
                RingtoneTarget.SET_ALARM -> Environment.DIRECTORY_ALARMS
                RingtoneTarget.SET_NOTIFICATION -> Environment.DIRECTORY_NOTIFICATIONS
                else -> Environment.DIRECTORY_RINGTONES
            }
        val relativePath = "$publicDirName/Pengu"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.Audio.Media.TITLE, title)
                    put(MediaStore.Audio.Media.ARTIST, "Pengu Ringtone Maker")
                    put(MediaStore.Audio.Media.IS_RINGTONE, isRingtone)
                    put(MediaStore.Audio.Media.IS_ALARM, isAlarm)
                    put(MediaStore.Audio.Media.IS_NOTIFICATION, isNotification)
                    put(MediaStore.Audio.Media.IS_MUSIC, false)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri =
                resolver.insert(collection, values)
                    ?: throw IOException("Unable to create MediaStore ringtone entry")
            try {
                resolver.openOutputStream(itemUri)?.use { out ->
                    sourceMp3.inputStream().use { inp -> inp.copyTo(out) }
                } ?: throw IOException("Unable to write ringtone audio bytes")
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
                return itemUri to "$relativePath/$fileName"
            } catch (th: Throwable) {
                runCatching { resolver.delete(itemUri, null, null) }
                throw th
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(publicDirName), "Pengu").apply {
                mkdirs()
            }
            val dest = uniqueFile(dir, title, "mp3")
            sourceMp3.copyTo(dest, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf("audio/mpeg"), null)
            val values =
                ContentValues().apply {
                    @Suppress("DEPRECATION")
                    put(MediaStore.MediaColumns.DATA, dest.absolutePath)
                    put(MediaStore.MediaColumns.TITLE, title)
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.MediaColumns.SIZE, dest.length())
                    put(MediaStore.Audio.Media.ARTIST, "Pengu Ringtone Maker")
                    put(MediaStore.Audio.Media.IS_RINGTONE, isRingtone)
                    put(MediaStore.Audio.Media.IS_ALARM, isAlarm)
                    put(MediaStore.Audio.Media.IS_NOTIFICATION, isNotification)
                    put(MediaStore.Audio.Media.IS_MUSIC, false)
                }
            val legacyTable = MediaStore.Audio.Media.getContentUriForPath(dest.absolutePath)
                ?: MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val uri = resolver.insert(legacyTable, values) ?: Uri.fromFile(dest)
            return uri to dest.absolutePath
        }
    }

    private fun assignRingtoneToContact(
        context: Context,
        contactUri: Uri,
        ringtoneUri: Uri,
    ): String {
        val resolver = context.contentResolver
        var contactId: String? = null
        var displayName = "Selected Contact"
        resolver.query(
            contactUri,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                if (idIdx >= 0) contactId = cursor.getString(idIdx)
                if (nameIdx >= 0) {
                    cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                }
            }
        }
        val safeId = contactId ?: throw IOException("Could not resolve selected contact ID")
        val values =
            ContentValues().apply {
                put(ContactsContract.Contacts.CUSTOM_RINGTONE, ringtoneUri.toString())
            }
        val updated =
            resolver.update(
                ContactsContract.Contacts.CONTENT_URI,
                values,
                "${ContactsContract.Contacts._ID} = ?",
                arrayOf(safeId),
            )
        if (updated <= 0) {
            throw IOException("Could not assign custom ringtone to $displayName")
        }
        return displayName
    }

    internal fun parseFfmpegTimeMs(line: String): Long? {
        val match = ffmpegTimeRegex.find(line) ?: return null
        val h = match.groupValues[1].toLongOrNull() ?: return null
        val m = match.groupValues[2].toLongOrNull() ?: return null
        val s = match.groupValues[3].toDoubleOrNull() ?: return null
        return ((h * 3600L + m * 60L) * 1000L) + (s * 1000.0).toLong()
    }

    private fun ensureFfmpegReady(context: Context) {
        runCatching { YoutubeDL.getInstance().init(context) }
        runCatching { FFmpeg.getInstance().init(context) }
    }

    private fun runFfmpeg(
        context: Context,
        args: List<String>,
        expectedDurationMs: Long = 0L,
        onProgress: (Float) -> Unit = {},
    ) {
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
        activeProcess.set(process)
        val tailLines = ArrayDeque<String>(12)
        var lastEmitMs = 0L
        try {
            process.inputStream.bufferedReader().use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    if (tailLines.size >= 10) tailLines.removeFirst()
                    tailLines.addLast(line)
                    if (expectedDurationMs > 0L) {
                        parseFfmpegTimeMs(line)?.let { encodedMs ->
                            val now = System.currentTimeMillis()
                            if (now - lastEmitMs >= 120L || encodedMs >= expectedDurationMs) {
                                lastEmitMs = now
                                val fraction =
                                    (encodedMs.toFloat() / expectedDurationMs.toFloat()).coerceIn(
                                        0f,
                                        1f,
                                    )
                                onProgress(fraction)
                            }
                        }
                    }
                    line = reader.readLine()
                }
            }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw IOException("FFmpeg failed (exit $exitCode): ${tailLines.joinToString("\n")}")
            }
            onProgress(1f)
        } finally {
            activeProcess.compareAndSet(process, null)
            runCatching { process.destroy() }
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
                uri.path?.let { rawPath ->
                    val realFile = File(rawPath)
                    if (realFile.exists() && realFile.canRead()) {
                        return realFile to null
                    }
                }
            }
            val displayName = resolveUriMetadata(context, uri).first
            val ext = displayName.substringAfterLast('.', "mp4").ifBlank { "mp4" }
            val tempFile = File(context.cacheDir, "pengu_ring_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("Unable to read selected media file")
            return tempFile to tempFile
        }
        throw IllegalArgumentException("No input audio or video selected")
    }

    private fun resolveUriMetadata(context: Context, uri: Uri): Pair<String, Long> {
        var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "media_file"
        var sizeBytes = 0L
        context.contentResolver
            .query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let {
                            displayName = it
                        }
                    }
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        sizeBytes = cursor.getLong(sizeIdx).coerceAtLeast(0L)
                    }
                }
            }
        return displayName to sizeBytes
    }

    private fun uniqueFile(dir: File, base: String, ext: String): File {
        var candidate = File(dir, "$base.$ext")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "${base}_$counter.$ext")
            counter++
        }
        return candidate
    }

    fun formatFfmpegTimeMs(ms: Long): String {
        val safeMs = ms.coerceAtLeast(0L)
        val totalSeconds = safeMs / 1000L
        val millis = safeMs % 1000L
        val h = totalSeconds / 3600L
        val m = (totalSeconds % 3600L) / 60L
        val s = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", h, m, s, millis)
    }

    fun formatClock(seconds: Long): String = formatClockMs(seconds * 1000L)

    fun formatClockMs(ms: Long): String {
        val safeSec = (ms / 1000L).coerceAtLeast(0L)
        val h = safeSec / 3600L
        val m = (safeSec % 3600L) / 60L
        val s = safeSec % 60L
        return if (h > 0L) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun formatClockWithTenths(ms: Long): String {
        val safeMs = ms.coerceAtLeast(0L)
        val totalSec = safeMs / 1000L
        val tenths = (safeMs % 1000L) / 100L
        val h = totalSec / 3600L
        val m = (totalSec % 3600L) / 60L
        val s = totalSec % 60L
        return if (h > 0L) String.format(Locale.US, "%d:%02d:%02d.%d", h, m, s, tenths)
        else String.format(Locale.US, "%02d:%02d.%d", m, s, tenths)
    }
}
