package com.devrayan.pengu

import com.devrayan.pengu.util.RingtoneToolsUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RingtoneMakerTest {

    @Test
    fun formatTimestamps_handlesMillisecondsAndHours() {
        assertEquals("00:00:01.250", RingtoneToolsUtil.formatFfmpegTimeMs(1_250L))
        assertEquals("01:02:03.050", RingtoneToolsUtil.formatFfmpegTimeMs(3_723_050L))
        assertEquals("00:12.4", RingtoneToolsUtil.formatClockWithTenths(12_400L))
        assertEquals("1:02:03", RingtoneToolsUtil.formatClockMs(3_723_000L))
    }

    @Test
    fun parseFfmpegTimeMs_extractsRealTimeProgress() {
        val sampleLine =
            "frame=  240 fps= 60 q=24.0 size=    2048kB time=00:01:23.45 bitrate= 201.0kbits/s speed=4.2x"
        assertEquals(83_450L, RingtoneToolsUtil.parseFfmpegTimeMs(sampleLine))
        assertNull(RingtoneToolsUtil.parseFfmpegTimeMs("Stream mapping: #0:0 -> #0:0 (copy)"))
    }

    @Test
    fun buildRingtoneCutArgs_buildsFadeAndMp3ArgsCleanly() {
        val fadeArgs =
            RingtoneToolsUtil.buildRingtoneCutArgs(
                inputPath = "/tmp/song.mp4",
                outputPath = "/tmp/ring.mp3",
                startMs = 5_000L,
                endMs = 20_000L,
                bitrateKbps = 192,
                fadeInOut = true,
            )
        assertTrue(fadeArgs.containsAll(listOf("-ss", "00:00:05.000", "-i", "/tmp/song.mp4", "-t", "00:00:15.000", "-vn", "-acodec", "libmp3lame", "-b:a", "192k")))
        assertTrue(fadeArgs.any { it.contains("afade=t=in") && it.contains("afade=t=out") })

        val rawArgs =
            RingtoneToolsUtil.buildRingtoneCutArgs(
                inputPath = "/tmp/song.mp3",
                outputPath = "/tmp/ring_raw.mp3",
                startMs = 0L,
                endMs = 15_000L,
                bitrateKbps = 320,
                fadeInOut = false,
            )
        assertTrue(rawArgs.none { it.contains("afade") })
        assertEquals("pengu_ringtone", RingtoneToolsUtil.sanitizeRingtoneName("///"))
    }
}
