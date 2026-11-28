package com.devrayan.pengu

import com.devrayan.pengu.ui.component.PenguRateManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PenguRateManagerTest {

    private val dayMs = 24L * 60L * 60L * 1000L

    @Test
    fun shouldShowRatePrompt_enforcesMaxTwoPerWeekAndSpacing() {
        val base = 1_700_000_000_000L

        assertFalse(PenguRateManager.shouldShowRatePrompt(base, "", starredOrDismissed = false, hasCompletedActivity = false))
        assertTrue(PenguRateManager.shouldShowRatePrompt(base, "", starredOrDismissed = false, hasCompletedActivity = true))

        // Within 72 hours of 1st prompt -> blocked
        assertFalse(
            PenguRateManager.shouldShowRatePrompt(
                nowMs = base + 2 * dayMs,
                historyCsv = "$base",
                starredOrDismissed = false,
                hasCompletedActivity = true,
            )
        )

        // 4 days later (< 7 days, >= 72h spacing, count = 1) -> allowed (2nd prompt in week)
        assertTrue(
            PenguRateManager.shouldShowRatePrompt(
                nowMs = base + 4 * dayMs,
                historyCsv = "$base",
                starredOrDismissed = false,
                hasCompletedActivity = true,
            )
        )

        // Already shown twice within 7 days -> blocked even after 3+ days if both inside 7d
        assertFalse(
            PenguRateManager.shouldShowRatePrompt(
                nowMs = base + 6 * dayMs,
                historyCsv = "${base},${base + 3 * dayMs}",
                starredOrDismissed = false,
                hasCompletedActivity = true,
            )
        )

        // After starred/dismissed permanently -> always blocked
        assertFalse(
            PenguRateManager.shouldShowRatePrompt(
                nowMs = base + 20 * dayMs,
                historyCsv = "",
                starredOrDismissed = true,
                hasCompletedActivity = true,
            )
        )
    }
}
