package com.devrayan.pengu.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal sealed interface ReleaseNoteBlock {
    data class Title(val text: String) : ReleaseNoteBlock

    data class SectionHeader(val text: String) : ReleaseNoteBlock

    data class SubHeader(val text: String) : ReleaseNoteBlock

    data class Paragraph(val text: String) : ReleaseNoteBlock

    data class Bullet(val text: String, val nested: Boolean) : ReleaseNoteBlock

    data class TableRow(val columns: List<String>, val isHeader: Boolean) : ReleaseNoteBlock

    data class TableGroup(val rows: List<TableRow>) : ReleaseNoteBlock

    data object Divider : ReleaseNoteBlock
}

internal fun parseReleaseMarkdownBlocks(raw: String): List<ReleaseNoteBlock> {
    if (raw.isBlank()) return emptyList()
    val lines = raw.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<ReleaseNoteBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> {
                i++
            }
            trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                blocks += ReleaseNoteBlock.Divider
                i++
            }
            trimmed.equals("## Title", ignoreCase = true) -> {
                i++
            }
            trimmed.startsWith("### ") -> {
                blocks += ReleaseNoteBlock.SubHeader(trimmed.removePrefix("### ").trim())
                i++
            }
            trimmed.startsWith("## ") -> {
                blocks += ReleaseNoteBlock.SectionHeader(trimmed.removePrefix("## ").trim())
                i++
            }
            trimmed.startsWith("# ") -> {
                blocks += ReleaseNoteBlock.Title(trimmed.removePrefix("# ").trim())
                i++
            }
            trimmed.startsWith("|") && trimmed.endsWith("|") -> {
                val parsedRows = mutableListOf<ReleaseNoteBlock.TableRow>()
                var headerEmitted = false
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (!(t.startsWith("|") && t.endsWith("|"))) break
                    val cells =
                        t.removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
                    val isSeparator =
                        cells.all { cell ->
                            cell.isNotBlank() &&
                                cell.all { ch -> ch == '-' || ch == ':' || ch == ' ' }
                        }
                    if (!isSeparator && cells.isNotEmpty()) {
                        parsedRows += ReleaseNoteBlock.TableRow(cells, isHeader = !headerEmitted)
                        headerEmitted = true
                    }
                    i++
                }
                if (parsedRows.isNotEmpty()) {
                    blocks += ReleaseNoteBlock.TableGroup(parsedRows)
                }
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                val leadingSpaces = line.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
                val nested = leadingSpaces >= 2
                val text = trimmed.drop(2).trim()
                blocks += ReleaseNoteBlock.Bullet(text = text, nested = nested)
                i++
            }
            else -> {
                val paragraphLines = mutableListOf(trimmed)
                i++
                while (i < lines.size) {
                    val nextRaw = lines[i]
                    val nextTrimmed = nextRaw.trim()
                    if (
                        nextTrimmed.isEmpty() ||
                            nextTrimmed == "---" ||
                            nextTrimmed.startsWith("#") ||
                            nextTrimmed.startsWith("- ") ||
                            nextTrimmed.startsWith("* ") ||
                            (nextTrimmed.startsWith("|") && nextTrimmed.endsWith("|"))
                    ) {
                        break
                    }
                    paragraphLines += nextTrimmed
                    i++
                }
                blocks += ReleaseNoteBlock.Paragraph(paragraphLines.joinToString(" "))
            }
        }
    }
    return blocks
}

enum class ChangelogFilterMode(val label: String) {
    ALL("All 1.2.0"),
    PLAN_130("1.3.0 Plan"),
    ADDED_FIXED("Add & Fix"),
    COMPARE("Compare"),
    HISTORY("CHANGELOG.md"),
    ONLINE("GitHub Releases"),
}

internal fun filterMarkdownBlocksBySection(
    blocks: List<ReleaseNoteBlock>,
    mode: ChangelogFilterMode,
): List<ReleaseNoteBlock> {
    if (mode == ChangelogFilterMode.ALL || mode == ChangelogFilterMode.HISTORY || mode == ChangelogFilterMode.ONLINE) {
        return blocks
    }
    val result = mutableListOf<ReleaseNoteBlock>()
    var activeSectionMatches = false
    for (block in blocks) {
        when (block) {
            is ReleaseNoteBlock.SectionHeader -> {
                val h = block.text.lowercase()
                activeSectionMatches =
                    when (mode) {
                        ChangelogFilterMode.PLAN_130 -> "1.3.0" in h || "plan" in h
                        ChangelogFilterMode.ADDED_FIXED -> "add" in h || "fix" in h
                        ChangelogFilterMode.COMPARE -> "compare" in h
                        else -> true
                    }
                if (activeSectionMatches) {
                    result += block
                }
            }
            is ReleaseNoteBlock.Divider -> {
                if (activeSectionMatches) result += block
            }
            is ReleaseNoteBlock.Title -> Unit
            else -> {
                if (activeSectionMatches) {
                    result += block
                }
            }
        }
    }
    return result
}

internal fun parseInlineMarkdown(
    text: String,
    codeBg: Color,
    codeFg: Color,
    boldColor: Color,
    linkColor: Color = Color(0xFF0969DA),
): AnnotatedString = buildAnnotatedString {
    var idx = 0
    while (idx < text.length) {
        when {
            text.startsWith("**", idx) -> {
                val end = text.indexOf("**", idx + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)) {
                        append(text.substring(idx + 2, end))
                    }
                    idx = end + 2
                } else {
                    append("**")
                    idx += 2
                }
            }
            text[idx] == '`' -> {
                val end = text.indexOf('`', idx + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = codeFg,
                            background = codeBg,
                        )
                    ) {
                        append(" ${text.substring(idx + 1, end)} ")
                    }
                    idx = end + 1
                } else {
                    append('`')
                    idx++
                }
            }
            text[idx] == '[' -> {
                val closeBracket = text.indexOf("](", idx + 1)
                val closeParen =
                    if (closeBracket != -1) text.indexOf(')', closeBracket + 2) else -1
                if (closeBracket != -1 && closeParen != -1) {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Medium,
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                        )
                    ) {
                        append(text.substring(idx + 1, closeBracket))
                    }
                    idx = closeParen + 1
                } else {
                    append('[')
                    idx++
                }
            }
            else -> {
                append(text[idx])
                idx++
            }
        }
    }
}

/**
 * Renders parsed Markdown blocks in authentic GitHub `.md` preview typography and table styling.
 */
@Composable
internal fun ReleaseNoteBlocksColumn(
    blocks: List<ReleaseNoteBlock>,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val headingColor = if (isDark) Color(0xFFE6EDF3) else Color(0xFF1F2328)
    val bodyColor = if (isDark) Color(0xFFD1D7E0) else Color(0xFF1F2328)
    val mutedColor = if (isDark) Color(0xFF8B949E) else Color(0xFF656D76)
    val dividerColor = if (isDark) Color(0xFF30363D) else Color(0xFFD8DEE4)
    val codeBg = if (isDark) Color(0x476E7681) else Color(0x33AFB8C1)
    val codeFg = if (isDark) Color(0xFFE6EDF3) else Color(0xFF1F2328)
    val linkColor = if (isDark) Color(0xFF58A6FF) else Color(0xFF0969DA)
    val tableHeaderBg = if (isDark) Color(0xFF161B22) else Color(0xFFF6F8FA)
    val tableStripeBg = if (isDark) Color(0xFF161B22) else Color(0xFFF6F8FA)
    val tableBaseBg = if (isDark) Color(0xFF0D1117) else Color.White

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is ReleaseNoteBlock.Title -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = block.text,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 26.sp,
                            color = headingColor,
                        )
                        Spacer(Modifier.height(6.dp))
                        HorizontalDivider(thickness = 1.dp, color = dividerColor)
                    }
                }
                is ReleaseNoteBlock.SectionHeader -> {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text(
                            text =
                                parseInlineMarkdown(
                                    block.text,
                                    codeBg = codeBg,
                                    codeFg = codeFg,
                                    boldColor = headingColor,
                                    linkColor = linkColor,
                                ),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 23.sp,
                            color = headingColor,
                        )
                        Spacer(Modifier.height(5.dp))
                        HorizontalDivider(thickness = 1.dp, color = dividerColor)
                    }
                }
                is ReleaseNoteBlock.SubHeader -> {
                    Text(
                        text =
                            parseInlineMarkdown(
                                block.text,
                                codeBg = codeBg,
                                codeFg = codeFg,
                                boldColor = headingColor,
                                linkColor = linkColor,
                            ),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = headingColor,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                is ReleaseNoteBlock.Divider -> {
                    HorizontalDivider(
                        thickness = 3.dp,
                        color = dividerColor,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                is ReleaseNoteBlock.Paragraph -> {
                    Text(
                        text =
                            parseInlineMarkdown(
                                block.text,
                                codeBg = codeBg,
                                codeFg = codeFg,
                                boldColor = headingColor,
                                linkColor = linkColor,
                            ),
                        fontSize = 13.5.sp,
                        lineHeight = 21.sp,
                        color = bodyColor,
                    )
                }
                is ReleaseNoteBlock.Bullet -> {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(start = if (block.nested) 20.dp else 4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier =
                                Modifier.padding(top = 8.dp)
                                    .size(if (block.nested) 5.dp else 6.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (block.nested) {
                                            Modifier.border(1.2.dp, mutedColor, CircleShape)
                                        } else {
                                            Modifier.background(headingColor.copy(alpha = 0.78f))
                                        }
                                    )
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text =
                                parseInlineMarkdown(
                                    block.text,
                                    codeBg = codeBg,
                                    codeFg = codeFg,
                                    boldColor = headingColor,
                                    linkColor = linkColor,
                                ),
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp,
                            color = bodyColor,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                is ReleaseNoteBlock.TableRow -> {
                    // Single standalone row fallback
                    Surface(
                        color = if (block.isHeader) tableHeaderBg else tableBaseBg,
                        modifier = Modifier.fillMaxWidth().border(1.dp, dividerColor),
                    ) {
                        Text(
                            text = block.columns.joinToString(" | "),
                            fontSize = 12.sp,
                            color = bodyColor,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                is ReleaseNoteBlock.TableGroup -> {
                    val colCount = block.rows.maxOfOrNull { it.columns.size } ?: 1
                    val colWidths =
                        List(colCount) { idx ->
                            when {
                                idx == 0 && colCount >= 3 -> 130.dp
                                colCount >= 3 -> 210.dp
                                else -> 170.dp
                            }
                        }
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, dividerColor, RoundedCornerShape(6.dp))
                    ) {
                        Column {
                            block.rows.forEachIndexed { rowIdx, row ->
                                val bg =
                                    when {
                                        row.isHeader -> tableHeaderBg
                                        rowIdx % 2 == 0 -> tableStripeBg
                                        else -> tableBaseBg
                                    }
                                Row(
                                    modifier =
                                        Modifier.height(IntrinsicSize.Min)
                                            .background(bg)
                                ) {
                                    for (cIdx in 0 until colCount) {
                                        val cellText = row.columns.getOrElse(cIdx) { "" }
                                        Box(
                                            modifier =
                                                Modifier.width(colWidths[cIdx])
                                                    .fillMaxHeight()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            Text(
                                                text =
                                                    parseInlineMarkdown(
                                                        cellText,
                                                        codeBg = codeBg,
                                                        codeFg = codeFg,
                                                        boldColor = headingColor,
                                                        linkColor = linkColor,
                                                    ),
                                                fontSize = 12.sp,
                                                lineHeight = 17.sp,
                                                fontWeight = if (row.isHeader) FontWeight.Bold else FontWeight.Normal,
                                                color = if (row.isHeader) headingColor else bodyColor,
                                            )
                                        }
                                        if (cIdx < colCount - 1) {
                                            Box(
                                                modifier =
                                                    Modifier.width(1.dp)
                                                        .fillMaxHeight()
                                                        .background(dividerColor)
                                            )
                                        }
                                    }
                                }
                                if (rowIdx < block.rows.lastIndex) {
                                    HorizontalDivider(thickness = 1.dp, color = dividerColor)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
