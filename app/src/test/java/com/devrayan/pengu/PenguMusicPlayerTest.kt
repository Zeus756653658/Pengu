package com.devrayan.pengu

import com.devrayan.pengu.util.PenguAudioManager
import com.devrayan.pengu.util.PenguAudioManager.RepeatMode
import com.devrayan.pengu.util.PenguLyricsUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PenguMusicPlayerTest {

    @Test
    fun parseLrcLines_parsesTimestampsAndSortsChronologically() {
        val raw =
            """
            [ti:Sample Track]
            [01:10.50]Second Chorus
            [00:12.25]First Verse
            [00:25.0]Pre Chorus
            [00:45.100][01:30.200]Repeated Hook
            """.trimIndent()

        val lines = PenguLyricsUtil.parseLrcLines(raw)
        assertEquals(5, lines.size)
        assertEquals(12_250L, lines[0].timeMs)
        assertEquals("First Verse", lines[0].text)
        assertEquals(25_000L, lines[1].timeMs)
        assertEquals(45_100L, lines[2].timeMs)
        assertEquals("Repeated Hook", lines[2].text)
        assertEquals(70_500L, lines[3].timeMs)
        assertEquals(90_200L, lines[4].timeMs)
    }

    @Test
    fun findActiveLyricIndex_tracksPlaybackPositionCleanly() {
        val lines =
            listOf(
                PenguLyricsUtil.LyricLine(5_000L, "Line 1"),
                PenguLyricsUtil.LyricLine(12_000L, "Line 2"),
                PenguLyricsUtil.LyricLine(20_000L, "Line 3"),
            )
        assertEquals(-1, PenguLyricsUtil.findActiveLyricIndex(lines, 1_000L))
        assertEquals(0, PenguLyricsUtil.findActiveLyricIndex(lines, 5_000L))
        assertEquals(1, PenguLyricsUtil.findActiveLyricIndex(lines, 15_400L))
        assertEquals(2, PenguLyricsUtil.findActiveLyricIndex(lines, 25_000L))
    }

    @Test
    fun cleanTrackForLyricsSearch_stripsNoiseAndSplitsArtist() {
        val (title, artist) =
            PenguLyricsUtil.cleanTrackForLyricsSearch(
                rawTitle = "Arijit Singh - Kesariya (Official Video) [4K] #bollywood",
                rawArtist = "",
            )
        assertEquals("Kesariya", title)
        assertEquals("Arijit Singh", artist)
    }

    @Test
    fun queueNavigation_handlesRepeatAndBoundsProperly() {
        assertEquals(1, PenguAudioManager.computeNextQueueIndex(0, 3, false, RepeatMode.OFF))
        assertNull(PenguAudioManager.computeNextQueueIndex(2, 3, false, RepeatMode.OFF))
        assertEquals(0, PenguAudioManager.computeNextQueueIndex(2, 3, false, RepeatMode.ALL))
        assertEquals(0, PenguAudioManager.computeNextQueueIndex(2, 3, false, RepeatMode.OFF, forceAdvance = true))

        assertEquals(0, PenguAudioManager.computePreviousQueueIndex(1, 3, RepeatMode.OFF))
        assertEquals(0, PenguAudioManager.computePreviousQueueIndex(0, 3, RepeatMode.OFF))
        assertEquals(2, PenguAudioManager.computePreviousQueueIndex(0, 3, RepeatMode.ALL))

        val shuffledNext = PenguAudioManager.computeNextQueueIndex(1, 4, true, RepeatMode.OFF)
        assertTrue(shuffledNext != null && shuffledNext in 0..3 && shuffledNext != 1)
    }
}
