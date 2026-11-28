package com.devrayan.pengu.util

import android.content.Context
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Synchronized LRC Lyrics parser and LRCLIB fetcher for Pengu Music Player.
 */
object PenguLyricsUtil {

    @Serializable
    data class LyricLine(val timeMs: Long, val text: String)

    @Serializable
    data class LyricsResult(
        val syncedLines: List<LyricLine> = emptyList(),
        val plainText: String = "",
        val source: String = "None",
    )

    @Serializable
    private data class LrcLibApiTrack(
        val id: Int = 0,
        val trackName: String = "",
        val artistName: String = "",
        val duration: Double? = null,
        val plainLyrics: String? = null,
        val syncedLyrics: String? = null,
    )

    private val lrcTimeRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val bracketsSuffixRegex =
        Regex(
            """\s*(\([^)]*(official|video|audio|lyrics|hd|4k|visualizer|mv|remaster)[^)]*\)|\[[^]]*])""",
            RegexOption.IGNORE_CASE,
        )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    internal fun cleanTrackForLyricsSearch(rawTitle: String, rawArtist: String = ""): Pair<String, String> {
        var title = cleanMediaTitle(rawTitle)
            .replace(bracketsSuffixRegex, "")
            .trim()
        var artist = rawArtist
            .takeIf { it.isNotBlank() && !it.equals("Author", true) && !it.equals("Unknown", true) }
            .orEmpty()
            .trim()

        if (artist.isEmpty() && title.contains(" - ")) {
            val parts = title.split(" - ", limit = 2)
            artist = parts[0].trim()
            title = parts[1].trim()
        }
        return title.ifBlank { rawTitle.trim() } to artist
    }

    internal fun parseLrcLines(rawLrc: String): List<LyricLine> {
        if (rawLrc.isBlank()) return emptyList()
        val parsed = mutableListOf<LyricLine>()
        rawLrc.lineSequence().forEach { line ->
            val matches = lrcTimeRegex.findAll(line).toList()
            if (matches.isEmpty()) return@forEach
            val text = line.replace(lrcTimeRegex, "").trim()
            if (text.isEmpty()) return@forEach
            matches.forEach { m ->
                val min = m.groupValues[1].toLongOrNull() ?: 0L
                val sec = m.groupValues[2].toLongOrNull() ?: 0L
                val fracRaw = m.groupValues[3]
                val millis =
                    when (fracRaw.length) {
                        0 -> 0L
                        1 -> (fracRaw.toLongOrNull() ?: 0L) * 100L
                        2 -> (fracRaw.toLongOrNull() ?: 0L) * 10L
                        else -> fracRaw.take(3).toLongOrNull() ?: 0L
                    }
                val timeMs = (min * 60_000L) + (sec * 1_000L) + millis
                parsed.add(LyricLine(timeMs = timeMs, text = text))
            }
        }
        return parsed.sortedBy { it.timeMs }
    }

    internal fun findActiveLyricIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var active = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= positionMs + 150L) {
                active = i
            } else {
                break
            }
        }
        return active
    }

    suspend fun loadLyrics(
        context: Context,
        filePath: String,
        title: String,
        artist: String,
        durationSec: Int,
        forceRefresh: Boolean = false,
    ): LyricsResult =
        withContext(Dispatchers.IO) {
            if (!forceRefresh && filePath.isNotBlank()) {
                val audioFile = File(filePath)
                val sidecarLrc = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
                if (sidecarLrc.exists()) {
                    val raw = runCatching { sidecarLrc.readText() }.getOrDefault("")
                    val synced = parseLrcLines(raw)
                    if (synced.isNotEmpty() || raw.isNotBlank()) {
                        return@withContext LyricsResult(synced, raw.trim(), "Local LRC")
                    }
                }
            }

            val cacheDir = File(context.cacheDir, "pengu_lyrics").apply { mkdirs() }
            val cacheKey = "${filePath.hashCode()}_${title.hashCode()}".replace('-', 'n')
            val cacheFile = File(cacheDir, "$cacheKey.json")
            if (!forceRefresh && cacheFile.exists()) {
                runCatching { json.decodeFromString<LyricsResult>(cacheFile.readText()) }
                    .getOrNull()
                    ?.takeIf { it.syncedLines.isNotEmpty() || it.plainText.isNotBlank() }
                    ?.let { return@withContext it }
            }

            val (cleanTitle, cleanArtist) = cleanTrackForLyricsSearch(title, artist)
            val queries = buildList {
                if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) {
                    add("track_name=${enc(cleanTitle)}&artist_name=${enc(cleanArtist)}")
                }
                if (cleanTitle.isNotBlank()) {
                    add("q=${enc(if (cleanArtist.isNotBlank()) "$cleanArtist $cleanTitle" else cleanTitle)}")
                    add("track_name=${enc(cleanTitle)}")
                }
            }

            for (qs in queries) {
                val tracks = queryLrcLib(qs)
                if (tracks.isEmpty()) continue
                val best =
                    tracks
                        .filter { !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }
                        .minByOrNull { tr ->
                            val hasSyncedPenalty = if (!tr.syncedLyrics.isNullOrBlank()) 0 else 50
                            val durDiff =
                                if (durationSec > 0 && tr.duration != null) {
                                    abs(tr.duration.toInt() - durationSec)
                                } else 10
                            hasSyncedPenalty + durDiff
                        }
                if (best != null) {
                    val synced = parseLrcLines(best.syncedLyrics.orEmpty())
                    val plain = best.plainLyrics.orEmpty().trim()
                    val result = LyricsResult(synced, plain, "LRCLIB")
                    runCatching { cacheFile.writeText(json.encodeToString(result)) }
                    return@withContext result
                }
            }

            LyricsResult()
        }

    private fun queryLrcLib(queryString: String): List<LrcLibApiTrack> {
        return runCatching {
            val req =
                Request.Builder()
                    .url("https://lrclib.net/api/search?$queryString")
                    .header("User-Agent", "Pengu/1.2.0 (Android)")
                    .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return emptyList()
                json.decodeFromString<List<LrcLibApiTrack>>(body)
            }
        }.getOrDefault(emptyList())
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
