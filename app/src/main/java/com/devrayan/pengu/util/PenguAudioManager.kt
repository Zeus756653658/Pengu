package com.devrayan.pengu.util

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.os.Build
import android.view.SurfaceHolder
import com.devrayan.pengu.App
import com.devrayan.pengu.database.objects.DownloadedVideoInfo
import com.devrayan.pengu.util.PreferenceUtil.getBoolean
import com.devrayan.pengu.util.PreferenceUtil.getInt
import com.devrayan.pengu.util.PreferenceUtil.updateBoolean
import com.devrayan.pengu.util.PreferenceUtil.updateInt
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared playback manager backing Pengu Music Player UI, video playback, Queue controls,
 * Equalizer/BassBoost DSP, LRCLIB synced lyrics, Mini-Player swipe controls, notification actions,
 * A-B looping, and sleep timer countdowns.
 */
object PenguAudioManager {

    enum class RepeatMode {
        OFF,
        ALL,
        ONE,
    }

    val eqPresetLabels = listOf("Flat", "Bass+", "Pop", "Rock", "Vocal", "Jazz")

    // Band gain bias in Normalized [-1f..+1f] mapped safely onto each device's bandLevelRange
    private val eqPresetCurves =
        listOf(
            floatArrayOf(0f, 0f, 0f, 0f, 0f),         // Flat
            floatArrayOf(0.85f, 0.55f, 0.1f, 0f, 0.2f), // Bass+
            floatArrayOf(-0.15f, 0.3f, 0.55f, 0.45f, 0.2f), // Pop
            floatArrayOf(0.55f, 0.25f, -0.15f, 0.35f, 0.6f), // Rock
            floatArrayOf(-0.25f, 0.15f, 0.65f, 0.55f, 0.15f), // Vocal
            floatArrayOf(0.35f, 0.15f, -0.1f, 0.3f, 0.45f), // Jazz
        )

    data class PlaybackState(
        val mediaInfo: DownloadedVideoInfo? = null,
        val isAudioOnly: Boolean = false,
        val isPlaying: Boolean = false,
        val isPrepared: Boolean = false,
        val durationMs: Int = 0,
        val positionMs: Int = 0,
        val videoRatio: Float = 16f / 9f,
        val playbackSpeed: Float = 1f,
        val sleepTimerMinutes: Int = 0,
        val sleepRemainingMs: Long = 0L,
        val abLoopStartMs: Int? = null,
        val abLoopEndMs: Int? = null,
        val fileNotFound: Boolean = false,
        val errorMessage: String? = null,
        val queue: List<DownloadedVideoInfo> = emptyList(),
        val queueIndex: Int = -1,
        val shuffleEnabled: Boolean = false,
        val repeatMode: RepeatMode = RepeatMode.OFF,
        val embeddedArtBytes: ByteArray? = null,
        val artistTag: String = "",
        val albumTag: String = "",
        val codecLabel: String = "",
        val bitrateKbps: Int = 0,
        val lyrics: PenguLyricsUtil.LyricsResult = PenguLyricsUtil.LyricsResult(),
        val isLoadingLyrics: Boolean = false,
        val eqEnabled: Boolean = false,
        val eqPresetIndex: Int = 0,
        val bassStrength: Int = 0,
    )

    private val _state = MutableStateFlow(PlaybackState())
    val state = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var activeSurface: SurfaceHolder? = null
    private var tickerJob: Job? = null
    private var sleepJob: Job? = null
    private var metadataJob: Job? = null
    private var lyricsJob: Job? = null

    private fun ensurePlayer(): MediaPlayer {
        val current = player
        if (current != null) return current
        return MediaPlayer().also { player = it }
    }

    fun attachVideoSurface(holder: SurfaceHolder) {
        activeSurface = holder
        runCatching { player?.setDisplay(holder) }
    }

    fun detachVideoSurface(holder: SurfaceHolder) {
        if (activeSurface == holder) activeSurface = null
        runCatching { player?.setDisplay(null) }
    }

    fun loadAndPlay(info: DownloadedVideoInfo, preserveQueue: Boolean = false) {
        val current = _state.value
        val file = File(info.videoPath)
        if (!file.exists()) {
            _state.update {
                it.copy(mediaInfo = info, fileNotFound = true, isPlaying = false, isPrepared = false)
            }
            return
        }

        val isAudio = info.videoPath.lowercase().contains(Regex(AUDIO_REGEX))
        if (current.mediaInfo?.id == info.id && current.mediaInfo.videoPath == info.videoPath && current.isPrepared) {
            if (!isAudio) {
                activeSurface?.let { runCatching { player?.setDisplay(it) } }
            }
            return
        }

        val initialCodec = file.extension.uppercase().ifBlank { "AUDIO" }
        val existingIdx =
            if (preserveQueue && isAudio) {
                current.queue.indexOfFirst { it.id == info.id && it.videoPath == info.videoPath }
            } else -1

        _state.update {
            it.copy(
                mediaInfo = info,
                isAudioOnly = isAudio,
                isPrepared = false,
                isPlaying = false,
                durationMs = 0,
                positionMs = 0,
                abLoopStartMs = null,
                abLoopEndMs = null,
                fileNotFound = false,
                errorMessage = null,
                embeddedArtBytes = null,
                artistTag = info.videoAuthor.takeIf { a -> a.isNotBlank() && a != "Author" }.orEmpty(),
                albumTag = "",
                codecLabel = initialCodec,
                bitrateKbps = 0,
                lyrics = PenguLyricsUtil.LyricsResult(),
                isLoadingLyrics = isAudio,
                queue = if (preserveQueue && isAudio) it.queue else emptyList(),
                queueIndex = if (preserveQueue && isAudio) existingIdx else -1,
                eqEnabled = runCatching { PENGU_EQ_ENABLED.getBoolean() }.getOrDefault(it.eqEnabled),
                eqPresetIndex = runCatching { PENGU_EQ_PRESET.getInt() }.getOrDefault(it.eqPresetIndex),
                bassStrength = runCatching { PENGU_BASS_STRENGTH.getInt() }.getOrDefault(it.bassStrength),
            )
        }

        if (isAudio) {
            loadAudioQueueAndMetadata(info, preserveQueue)
        } else {
            metadataJob?.cancel()
            lyricsJob?.cancel()
        }

        val mp = ensurePlayer()
        runCatching {
            mp.reset()
            if (!isAudio) {
                activeSurface?.let { mp.setDisplay(it) }
            }
            mp.setDataSource(file.absolutePath)
            mp.setOnPreparedListener { prepared ->
                val dur = prepared.duration.coerceAtLeast(1)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && _state.value.playbackSpeed != 1f) {
                    runCatching {
                        prepared.playbackParams = PlaybackParams().setSpeed(_state.value.playbackSpeed)
                    }
                }
                if (isAudio) {
                    attachAudioEffects(prepared.audioSessionId)
                } else {
                    releaseAudioEffects()
                }
                prepared.start()
                _state.update {
                    it.copy(
                        isPrepared = true,
                        isPlaying = true,
                        durationMs = dur,
                        positionMs = 0,
                        errorMessage = null,
                    )
                }
                startTicker()
                updateMediaNotification()
            }
            mp.setOnVideoSizeChangedListener { _, width, height ->
                if (width > 0 && height > 0) {
                    val ratio = (width.toFloat() / height.toFloat()).coerceIn(0.5f, 2.4f)
                    _state.update { it.copy(videoRatio = ratio) }
                }
            }
            mp.setOnCompletionListener {
                handleTrackCompletion()
            }
            mp.setOnErrorListener { _, _, _ ->
                _state.update {
                    it.copy(isPlaying = false, isPrepared = false, errorMessage = "Cannot play media")
                }
                NotificationUtil.cancelMediaNotification()
                true
            }
            mp.prepareAsync()
        }.onFailure { th ->
            _state.update {
                it.copy(isPlaying = false, isPrepared = false, errorMessage = th.message ?: "Playback error")
            }
        }
    }

    private fun handleTrackCompletion() {
        val st = _state.value
        if (st.isAudioOnly) {
            if (st.repeatMode == RepeatMode.ONE) {
                runCatching {
                    player?.seekTo(0)
                    player?.start()
                }
                _state.update { it.copy(isPlaying = true, positionMs = 0) }
                startTicker()
                updateMediaNotification()
                return
            }
            val nextIdx =
                computeNextQueueIndex(
                    currentIndex = st.queueIndex,
                    queueSize = st.queue.size,
                    shuffleEnabled = st.shuffleEnabled,
                    repeatMode = st.repeatMode,
                )
            if (nextIdx != null && nextIdx in st.queue.indices) {
                loadAndPlay(st.queue[nextIdx], preserveQueue = true)
                return
            }
        }
        val dur = st.durationMs
        _state.update { it.copy(isPlaying = false, positionMs = dur) }
        updateMediaNotification()
    }

    private fun loadAudioQueueAndMetadata(info: DownloadedVideoInfo, preserveQueue: Boolean) {
        metadataJob?.cancel()
        lyricsJob?.cancel()

        metadataJob =
            App.applicationScope.launch(Dispatchers.IO) {
                if (!preserveQueue || _state.value.queue.isEmpty()) {
                    val history = runCatching { DatabaseUtil.getDownloadHistoryFlow().first() }.getOrDefault(emptyList())
                    val audioQueue =
                        history
                            .reversed()
                            .filter { item ->
                                item.videoPath.lowercase().contains(Regex(AUDIO_REGEX)) &&
                                    File(item.videoPath).exists()
                            }
                            .ifEmpty { listOf(info) }
                    val finalQueue =
                        if (audioQueue.any { it.id == info.id && it.videoPath == info.videoPath }) audioQueue
                        else listOf(info) + audioQueue
                    val idx = finalQueue.indexOfFirst { it.id == info.id && it.videoPath == info.videoPath }
                    _state.update { st ->
                        if (st.mediaInfo?.id == info.id) st.copy(queue = finalQueue, queueIndex = idx)
                        else st
                    }
                }

                // Read embedded artwork, artist, album, and real bitrate
                val retriever = MediaMetadataRetriever()
                var art: ByteArray? = null
                var artist = ""
                var album = ""
                var bitrateKbps = 0
                var durSec = 0
                try {
                    retriever.setDataSource(info.videoPath)
                    art = retriever.embeddedPicture
                    artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty().trim()
                    album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty().trim()
                    val brBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
                    bitrateKbps = if (brBps > 0) brBps / 1000 else 0
                    val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    durSec = (durMs / 1000L).toInt()
                } catch (_: Exception) {
                } finally {
                    runCatching { retriever.release() }
                }

                val cleanArtist =
                    artist.ifBlank { info.videoAuthor.takeIf { it.isNotBlank() && it != "Author" }.orEmpty() }
                _state.update { st ->
                    if (st.mediaInfo?.id == info.id) {
                        st.copy(
                            embeddedArtBytes = art,
                            artistTag = cleanArtist,
                            albumTag = album,
                            bitrateKbps = bitrateKbps,
                        )
                    } else st
                }
                withContext(Dispatchers.Main) { updateMediaNotification() }

                // Load synced lyrics cleanly in background
                loadLyricsForCurrentTrack(
                    info = info,
                    artist = cleanArtist,
                    durationSec = durSec,
                    forceRefresh = false,
                )
            }
    }

    fun refreshLyrics() {
        val st = _state.value
        val info = st.mediaInfo ?: return
        lyricsJob?.cancel()
        lyricsJob =
            App.applicationScope.launch(Dispatchers.IO) {
                _state.update { it.copy(isLoadingLyrics = true) }
                loadLyricsForCurrentTrack(
                    info = info,
                    artist = st.artistTag,
                    durationSec = (st.durationMs / 1000).coerceAtLeast(0),
                    forceRefresh = true,
                )
            }
    }

    private suspend fun loadLyricsForCurrentTrack(
        info: DownloadedVideoInfo,
        artist: String,
        durationSec: Int,
        forceRefresh: Boolean,
    ) {
        val res =
            PenguLyricsUtil.loadLyrics(
                context = App.context,
                filePath = info.videoPath,
                title = info.videoTitle,
                artist = artist,
                durationSec = durationSec,
                forceRefresh = forceRefresh,
            )
        withContext(Dispatchers.Main) {
            _state.update { st ->
                if (st.mediaInfo?.id == info.id && st.mediaInfo.videoPath == info.videoPath) {
                    st.copy(lyrics = res, isLoadingLyrics = false)
                } else st
            }
        }
    }

    fun togglePlayPause() {
        val st = _state.value
        val mp = player ?: return
        if (!st.isPrepared) return
        if (runCatching { mp.isPlaying }.getOrDefault(false)) pause() else play()
    }

    fun play() {
        val st = _state.value
        val mp = player ?: return
        if (!st.isPrepared) return
        runCatching {
            if (!mp.isPlaying) {
                mp.start()
            }
            _state.update { it.copy(isPlaying = true) }
            startTicker()
            updateMediaNotification()
        }
    }

    fun pause() {
        val st = _state.value
        val mp = player ?: return
        if (!st.isPrepared) return
        runCatching {
            if (mp.isPlaying) {
                mp.pause()
            }
            _state.update { it.copy(isPlaying = false) }
            updateMediaNotification()
        }
    }

    fun skipToNext() {
        val st = _state.value
        if (!st.isAudioOnly || st.queue.isEmpty()) {
            skipBy(10_000)
            return
        }
        val nextIdx =
            computeNextQueueIndex(
                currentIndex = st.queueIndex,
                queueSize = st.queue.size,
                shuffleEnabled = st.shuffleEnabled,
                repeatMode = if (st.repeatMode == RepeatMode.ONE) RepeatMode.ALL else st.repeatMode,
                forceAdvance = true,
            ) ?: 0
        if (nextIdx in st.queue.indices) {
            loadAndPlay(st.queue[nextIdx], preserveQueue = true)
        }
    }

    fun skipToPrevious() {
        val st = _state.value
        if (!st.isAudioOnly || st.queue.isEmpty()) {
            skipBy(-10_000)
            return
        }
        val curPos = runCatching { player?.currentPosition ?: st.positionMs }.getOrDefault(st.positionMs)
        if (curPos > 3_500 && st.isPrepared) {
            seekTo(0)
            return
        }
        val prevIdx =
            computePreviousQueueIndex(
                currentIndex = st.queueIndex,
                queueSize = st.queue.size,
                repeatMode = st.repeatMode,
            )
        if (prevIdx in st.queue.indices) {
            loadAndPlay(st.queue[prevIdx], preserveQueue = true)
        } else {
            seekTo(0)
        }
    }

    fun playQueueIndex(index: Int) {
        val st = _state.value
        if (index in st.queue.indices) {
            loadAndPlay(st.queue[index], preserveQueue = true)
        }
    }

    fun toggleShuffle() {
        _state.update { it.copy(shuffleEnabled = !it.shuffleEnabled) }
    }

    fun cycleRepeatMode() {
        val next =
            when (_state.value.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
        _state.update { it.copy(repeatMode = next) }
    }

    fun setEqEnabled(enabled: Boolean) {
        PENGU_EQ_ENABLED.updateBoolean(enabled)
        _state.update { it.copy(eqEnabled = enabled) }
        applyAudioFxSettings()
    }

    fun selectEqPreset(presetIndex: Int) {
        val safeIdx = presetIndex.coerceIn(0, eqPresetLabels.lastIndex)
        PENGU_EQ_PRESET.updateInt(safeIdx)
        PENGU_EQ_ENABLED.updateBoolean(true)
        _state.update { it.copy(eqEnabled = true, eqPresetIndex = safeIdx) }
        applyAudioFxSettings()
    }

    fun setBassStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        PENGU_BASS_STRENGTH.updateInt(clamped)
        _state.update { it.copy(bassStrength = clamped) }
        applyAudioFxSettings()
    }

    private fun attachAudioEffects(audioSessionId: Int) {
        releaseAudioEffects()
        if (audioSessionId == 0) return
        runCatching {
            equalizer = Equalizer(0, audioSessionId)
        }
        runCatching {
            bassBoost = BassBoost(0, audioSessionId)
        }
        applyAudioFxSettings()
    }

    private fun applyAudioFxSettings() {
        val st = _state.value
        runCatching {
            equalizer?.let { eq ->
                eq.enabled = st.eqEnabled
                if (st.eqEnabled) {
                    val range = eq.bandLevelRange
                    val minMb = range[0].toInt()
                    val maxMb = range[1].toInt()
                    val curve = eqPresetCurves[st.eqPresetIndex.coerceIn(0, eqPresetCurves.lastIndex)]
                    val bands = eq.numberOfBands.toInt()
                    for (b in 0 until bands) {
                        val curveIdx =
                            if (bands <= 1) 0
                            else ((b.toFloat() / (bands - 1).toFloat()) * (curve.size - 1)).toInt()
                                .coerceIn(0, curve.lastIndex)
                        val bias = curve[curveIdx]
                        val target =
                            if (bias >= 0f) (bias * maxMb).toInt()
                            else (-bias * minMb).toInt()
                        eq.setBandLevel(b.toShort(), target.coerceIn(minMb, maxMb).toShort())
                    }
                }
            }
        }
        runCatching {
            bassBoost?.let { bb ->
                val enableBass = st.eqEnabled && st.bassStrength > 0
                bb.enabled = enableBass
                if (enableBass && bb.strengthSupported) {
                    bb.setStrength(st.bassStrength.coerceIn(0, 1000).toShort())
                }
            }
        }
    }

    private fun releaseAudioEffects() {
        runCatching { equalizer?.release() }
        equalizer = null
        runCatching { bassBoost?.release() }
        bassBoost = null
    }

    fun pauseIfVideoLeaving() {
        val st = _state.value
        if (!st.isAudioOnly) {
            runCatching {
                if (player?.isPlaying == true) {
                    player?.pause()
                    _state.update { it.copy(isPlaying = false) }
                    NotificationUtil.cancelMediaNotification()
                }
            }
        } else if (st.isPrepared) {
            updateMediaNotification()
        }
    }

    fun seekTo(targetMs: Int) {
        val st = _state.value
        val clamped = targetMs.coerceIn(0, st.durationMs.coerceAtLeast(1))
        _state.update { it.copy(positionMs = clamped) }
        if (st.isPrepared) {
            runCatching { player?.seekTo(clamped) }
            if (st.isAudioOnly) updateMediaNotification()
        }
    }

    fun skipBy(deltaMs: Int) {
        val st = _state.value
        if (!st.isPrepared) return
        val cur = runCatching { player?.currentPosition ?: st.positionMs }.getOrDefault(st.positionMs)
        seekTo(cur + deltaMs)
    }

    fun cycleSpeed() {
        val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f)
        val cur = _state.value.playbackSpeed
        val next = speeds[(speeds.indexOf(cur) + 1) % speeds.size]
        _state.update { it.copy(playbackSpeed = next) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && _state.value.isPrepared) {
            runCatching { player?.playbackParams = PlaybackParams().setSpeed(next) }
        }
    }

    fun setAbLoopPoint() {
        val st = _state.value
        val pos = st.positionMs
        when {
            st.abLoopStartMs == null -> _state.update { it.copy(abLoopStartMs = pos, abLoopEndMs = null) }
            st.abLoopEndMs == null && pos > st.abLoopStartMs + 500 ->
                _state.update { it.copy(abLoopEndMs = pos) }
            else -> _state.update { it.copy(abLoopStartMs = null, abLoopEndMs = null) }
        }
    }

    fun clearAbLoop() {
        _state.update { it.copy(abLoopStartMs = null, abLoopEndMs = null) }
    }

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            _state.update { it.copy(sleepTimerMinutes = 0, sleepRemainingMs = 0L) }
            updateMediaNotification()
            return
        }
        val totalMs = minutes * 60_000L
        _state.update { it.copy(sleepTimerMinutes = minutes, sleepRemainingMs = totalMs) }
        updateMediaNotification()
        sleepJob =
            App.applicationScope.launch(Dispatchers.Default) {
                var remaining = totalMs
                while (remaining > 0L) {
                    delay(1_000L)
                    remaining -= 1_000L
                    _state.update { it.copy(sleepRemainingMs = remaining.coerceAtLeast(0L)) }
                }
                runCatching {
                    if (player?.isPlaying == true) {
                        player?.pause()
                    }
                }
                _state.update { it.copy(isPlaying = false, sleepTimerMinutes = 0, sleepRemainingMs = 0L) }
                updateMediaNotification()
            }
    }

    fun refreshMetadata(updated: DownloadedVideoInfo) {
        _state.update { st ->
            val updatedQueue =
                st.queue.map { if (it.id == updated.id) updated else it }
            val updatedCurrent =
                if (st.mediaInfo?.id == updated.id) updated else st.mediaInfo
            st.copy(mediaInfo = updatedCurrent, queue = updatedQueue)
        }
    }

    fun stopAndClear() {
        tickerJob?.cancel()
        sleepJob?.cancel()
        metadataJob?.cancel()
        lyricsJob?.cancel()
        releaseAudioEffects()
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        activeSurface = null
        com.devrayan.pengu.MediaPlaybackService.stop(App.context)
        _state.value = PlaybackState()
        com.devrayan.pengu.PenguIslandOverlayService.syncOverlay(App.context)
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob =
            App.applicationScope.launch(Dispatchers.Default) {
                while (_state.value.isPlaying && _state.value.isPrepared) {
                    val mp = player ?: break
                    val st = _state.value
                    val pos = runCatching { if (mp.isPlaying) mp.currentPosition else st.positionMs }.getOrDefault(st.positionMs)
                    val a = st.abLoopStartMs
                    val b = st.abLoopEndMs
                    if (a != null && b != null && pos >= b) {
                        runCatching { mp.seekTo(a) }
                        _state.update { it.copy(positionMs = a) }
                    } else {
                        _state.update { it.copy(positionMs = pos) }
                    }
                    delay(100L)
                }
            }
    }

    private fun updateMediaNotification() {
        val st = _state.value
        val info = st.mediaInfo ?: return
        if (!st.isAudioOnly) return
        com.devrayan.pengu.MediaPlaybackService.syncPlayback(App.context, st)
        com.devrayan.pengu.PenguIslandOverlayService.syncOverlay(App.context)
    }

    data class OutputRoute(
        val id: Int,
        val name: String,
        val typeLabel: String,
        val isBluetooth: Boolean,
    )

    fun listAudioOutputRoutes(context: android.content.Context): List<OutputRoute> {
        val am = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return emptyList()
        return am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
            .mapNotNull { dev ->
                val (label, isBt) =
                    when (dev.type) {
                        android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone Speaker" to false
                        android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones" to false
                        android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
                        android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Audio" to false
                        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        android.media.AudioDeviceInfo.TYPE_BLE_HEADSET,
                        android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER ->
                            (dev.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth Audio") to true
                        else -> return@mapNotNull null
                    }
                OutputRoute(id = dev.id, name = label, typeLabel = if (isBt) "Bluetooth" else "Device", isBluetooth = isBt)
            }
            .distinctBy { it.name + it.isBluetooth }
    }

    internal fun computeNextQueueIndex(
        currentIndex: Int,
        queueSize: Int,
        shuffleEnabled: Boolean,
        repeatMode: RepeatMode,
        forceAdvance: Boolean = false,
    ): Int? {
        if (queueSize <= 0) return null
        if (queueSize == 1) {
            return if (repeatMode == RepeatMode.ALL || forceAdvance) 0 else null
        }
        if (shuffleEnabled) {
            val candidates = (0 until queueSize).filter { it != currentIndex }
            return candidates.random()
        }
        val next = currentIndex + 1
        return when {
            next < queueSize -> next
            repeatMode == RepeatMode.ALL || forceAdvance -> 0
            else -> null
        }
    }

    internal fun computePreviousQueueIndex(
        currentIndex: Int,
        queueSize: Int,
        repeatMode: RepeatMode,
    ): Int {
        if (queueSize <= 1) return 0
        val prev = currentIndex - 1
        return when {
            prev >= 0 -> prev
            repeatMode == RepeatMode.ALL -> queueSize - 1
            else -> 0
        }
    }
}
