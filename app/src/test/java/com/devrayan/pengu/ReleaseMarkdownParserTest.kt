package com.devrayan.pengu

import androidx.compose.ui.graphics.Color
import com.devrayan.pengu.ui.component.ChangelogFilterMode
import com.devrayan.pengu.ui.component.ReleaseNoteBlock
import com.devrayan.pengu.ui.component.filterMarkdownBlocksBySection
import com.devrayan.pengu.ui.component.parseInlineMarkdown
import com.devrayan.pengu.ui.component.parseReleaseMarkdownBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseMarkdownParserTest {

    @Test
    fun parseReleaseMarkdownBlocks_parsesHeadingsBulletsDividersAndTableGroups() {
        val sample =
            """
            # Pengu v1.2.0
            ## Title
            **Pengu — Modern Video & Audio Downloader (`1.2.0`)**

            ---

            ## 1. About
            Built with `yt-dlp`.

            ## 2. Add
            - **Music Player**: Added dynamic themes.
              - Nested bullet

            ## 4. 1.3.0 Plan
            - **Playlist Hub**: Custom offline playlists.

            ## 5. Compare
            | Feature | v1.1.0 | v1.2.0 |
            | :--- | :--- | :--- |
            | **Audio** | Basic | Pengu Music Engine |
            """.trimIndent()

        val blocks = parseReleaseMarkdownBlocks(sample)

        assertTrue(blocks.any { it is ReleaseNoteBlock.Title && it.text == "Pengu v1.2.0" })
        assertFalse(blocks.any { it is ReleaseNoteBlock.SectionHeader && it.text == "Title" })
        assertTrue(blocks.any { it is ReleaseNoteBlock.Divider })
        assertTrue(blocks.any { it is ReleaseNoteBlock.SectionHeader && it.text == "1. About" })
        assertTrue(blocks.any { it is ReleaseNoteBlock.SectionHeader && it.text == "4. 1.3.0 Plan" })
        assertTrue(
            blocks.any {
                it is ReleaseNoteBlock.Bullet && !it.nested && it.text == "**Music Player**: Added dynamic themes."
            }
        )
        assertTrue(
            blocks.any {
                it is ReleaseNoteBlock.Bullet && it.nested && it.text == "Nested bullet"
            }
        )
        val tableGroups = blocks.filterIsInstance<ReleaseNoteBlock.TableGroup>()
        assertEquals(1, tableGroups.size)
        val rows = tableGroups.first().rows
        assertEquals(2, rows.size)
        assertTrue(rows[0].isHeader)
        assertFalse(rows[1].isHeader)
        assertEquals(listOf("**Audio**", "Basic", "Pengu Music Engine"), rows[1].columns)

        val planOnly = filterMarkdownBlocksBySection(blocks, ChangelogFilterMode.PLAN_130)
        assertTrue(planOnly.any { it is ReleaseNoteBlock.SectionHeader && it.text == "4. 1.3.0 Plan" })
        assertFalse(planOnly.any { it is ReleaseNoteBlock.SectionHeader && it.text == "1. About" })

        val compareOnly = filterMarkdownBlocksBySection(blocks, ChangelogFilterMode.COMPARE)
        assertTrue(compareOnly.any { it is ReleaseNoteBlock.TableGroup })
        assertFalse(compareOnly.any { it is ReleaseNoteBlock.SectionHeader && it.text == "4. 1.3.0 Plan" })
    }

    @Test
    fun parseInlineMarkdown_stripsRawMarkersAndFormatsSpans() {
        val annotated =
            parseInlineMarkdown(
                text = "**Pengu** uses `yt-dlp` and [GitHub](https://github.com) cleanly.",
                codeBg = Color.LightGray,
                codeFg = Color.Blue,
                boldColor = Color.Black,
            )

        assertEquals("Pengu uses  yt-dlp  and GitHub cleanly.", annotated.text)
        assertTrue(annotated.spanStyles.size >= 3)
    }
}
