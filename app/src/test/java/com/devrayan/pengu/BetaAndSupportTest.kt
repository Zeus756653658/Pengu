package com.devrayan.pengu

import com.devrayan.pengu.beta.BetaRepository
import com.devrayan.pengu.beta.ReleaseCategory
import com.devrayan.pengu.util.PRE_RELEASE
import com.devrayan.pengu.util.STABLE
import com.devrayan.pengu.util.UpdateUtil
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BetaAndSupportTest {

    @Test
    fun updateUtil_enforcesHttpsOnlyUrls() {
        assertTrue(UpdateUtil.isHttpsUrl("https://api.github.com/repos/Zeus756653658/Pengu"))
        assertTrue(UpdateUtil.isHttpsUrl("  HTTPS://beta.example.com/pengu.apk "))
        assertFalse(UpdateUtil.isHttpsUrl("http://insecure.example.com/pengu.apk"))
        assertFalse(UpdateUtil.isHttpsUrl("file:///sdcard/Download/pengu.apk"))
        assertFalse(UpdateUtil.isHttpsUrl(""))

        assertEquals(
            "https://beta.example.com/pengu.apk",
            UpdateUtil.requireHttpsUrl("  https://beta.example.com/pengu.apk  "),
        )

        var rejected = false
        try {
            UpdateUtil.requireHttpsUrl("http://insecure.example.com/pengu.apk")
        } catch (_: SecurityException) {
            rejected = true
        }
        assertTrue("Non-HTTPS download URL must throw SecurityException", rejected)
    }

    @Test
    fun updateUtil_extractsAndComputesSha256Correctly() {
        val sampleHash = "a".repeat(64)
        val otherHash = "b".repeat(64)
        val body =
            """
            ### Checksums
            - `Pengu-1.2.0-generic-universal.apk` SHA256: `$otherHash`
            - `Pengu-1.2.0-generic-arm64-v8a.apk` SHA256: `$sampleHash`
            """.trimIndent()

        assertEquals(
            sampleHash,
            UpdateUtil.extractSha256ForAsset(body, "Pengu-1.2.0-generic-arm64-v8a.apk"),
        )
        assertEquals(
            otherHash,
            UpdateUtil.extractSha256ForAsset(body, "Pengu-1.2.0-generic-universal.apk"),
        )
        assertNull(UpdateUtil.extractSha256ForAsset("No digest available", "Pengu.apk"))

        val tempFile = Files.createTempFile("pengu-sha-test-", ".bin").toFile()
        try {
            tempFile.writeText("pengu-beta-verification")
            val digest = UpdateUtil.computeSha256(tempFile)
            assertEquals(64, digest.length)
            assertTrue(digest.all { it in '0'..'9' || it in 'a'..'f' })
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun releaseChannels_keepStableAndBetaStrictlySeparated() {
        val stableOld = UpdateUtil.Release(tagName = "v1.1.0", name = "Pengu 1.1.0", preRelease = false)
        val stableNew = UpdateUtil.Release(tagName = "v1.2.0", name = "Pengu 1.2.0", preRelease = false)
        val beta1 = UpdateUtil.Release(tagName = "v1.3.0-beta.1", name = "Pengu 1.3.0-beta.1", preRelease = true)
        val beta2 = UpdateUtil.Release(tagName = "v1.3.0-beta.2", name = "Pengu 1.3.0-beta.2", preRelease = true)
        val draftBeta = UpdateUtil.Release(tagName = "v1.3.0-beta.3", name = "Draft", preRelease = true, draft = true)
        val all = listOf(stableOld, stableNew, beta1, beta2, draftBeta)

        val pickedStable = UpdateUtil.selectLatestReleaseForChannel(all, STABLE)
        val pickedBeta = UpdateUtil.selectLatestReleaseForChannel(all, PRE_RELEASE)

        assertNotNull(pickedStable)
        assertEquals("v1.2.0", pickedStable?.tagName)
        assertNotNull(pickedBeta)
        assertEquals("v1.3.0-beta.2", pickedBeta?.tagName)

        // Strict verification: Stable must exclude Beta; Beta channel picks pre-release builds
        assertFalse(UpdateUtil.matchesChannel(beta2, STABLE))
        assertFalse(UpdateUtil.matchesChannel(stableNew, PRE_RELEASE))
        assertFalse(UpdateUtil.matchesChannel(draftBeta, PRE_RELEASE))
    }

    @Test
    fun betaRepository_classifiesBetaStableArchivedAndNeverAutoSwitchesChannel() {
        val stableOld = UpdateUtil.Release(tagName = "v1.1.0", name = "Pengu 1.1.0", preRelease = false)
        val stableNew = UpdateUtil.Release(tagName = "v1.2.0", name = "Pengu 1.2.0", preRelease = false)
        val betaOld = UpdateUtil.Release(tagName = "v1.3.0-beta.1", name = "Pengu 1.3.0-beta.1", preRelease = true)
        val betaNew = UpdateUtil.Release(tagName = "v1.3.0-beta.2", name = "Pengu 1.3.0-beta.2", preRelease = true)
        val draftRel = UpdateUtil.Release(tagName = "v1.3.0-beta.3", draft = true, preRelease = true)

        assertEquals(
            ReleaseCategory.STABLE,
            BetaRepository.classifyReleaseCategory(stableNew, stableNew, betaNew),
        )
        assertEquals(
            ReleaseCategory.ARCHIVED,
            BetaRepository.classifyReleaseCategory(stableOld, stableNew, betaNew),
        )
        assertEquals(
            ReleaseCategory.BETA,
            BetaRepository.classifyReleaseCategory(betaNew, stableNew, betaNew),
        )
        assertEquals(
            ReleaseCategory.ARCHIVED,
            BetaRepository.classifyReleaseCategory(betaOld, stableNew, betaNew),
        )
        assertEquals(
            ReleaseCategory.ARCHIVED,
            BetaRepository.classifyReleaseCategory(draftRel, stableNew, betaNew),
        )

        // Background checks must NEVER change the user's selected channel
        assertEquals(
            STABLE,
            BetaRepository.validateChannelSwitch(
                requestedChannel = PRE_RELEASE,
                isAutoBackgroundCheck = true,
                currentChannel = STABLE,
            ),
        )
        // Explicit user opt-in switches channel cleanly
        assertEquals(
            PRE_RELEASE,
            BetaRepository.validateChannelSwitch(
                requestedChannel = PRE_RELEASE,
                isAutoBackgroundCheck = false,
                currentChannel = STABLE,
            ),
        )
    }

    @Test
    fun betaRepository_parsesKnownBugsExperimentalFeaturesAndShaFromNotes() {
        val expectedHash = "c1d2".repeat(16)
        val notes =
            """
            # Pengu 1.3.0-beta.1
            ## Experimental Features
            - Fast Beta Ringtone Presets
            - Hardware EQ Boost v2

            ## Known Bugs
            - Minor delay refreshing queue count on cold start
            - PiP auto-enter toggle needs restart

            SHA256: $expectedHash
            """.trimIndent()

        val parsed =
            BetaRepository.parseBetaReleaseMetadata(
                body = notes,
                explicitKnownBugs = listOf("Minor delay refreshing queue count on cold start"),
            )

        assertEquals(2, parsed.experimentalFeatures.size)
        assertEquals(2, parsed.knownBugs.size)
        assertEquals(expectedHash, parsed.sha256Hex)
    }
}
