package com.neurasamu.build.ui.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neurasamu.build.ui.theme.LocalFontScale
import com.neurasamu.build.ui.theme.LocalSaMuColors
import com.neurasamu.build.ui.theme.SaMuColors
import com.neurasamu.build.ui.theme.SaMuRadius
import kotlinx.coroutines.delay

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class CodeBlock(val language: String, val code: String) : MdBlock()
    data class Blockquote(val lines: List<String>) : MdBlock()
    data class BulletList(val items: List<String>) : MdBlock()
    data class OrderedList(val items: List<Pair<String, String>>) : MdBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock()
    data object Divider : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalSaMuColors.current
    val scale = LocalFontScale.current
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val spSize = when (block.level) {
                        1 -> 17f
                        2 -> 16f
                        3 -> 15f
                        else -> 14.5f
                    } * scale
                    Text(
                        text = parseInlineMarkdown(block.text, colors),
                        color = colors.onSurface,
                        fontSize = spSize.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = (spSize * 1.35f).sp
                    )
                }

                is MdBlock.CodeBlock -> {
                    CodeBlockCard(language = block.language, code = block.code)
                }

                is MdBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .clip(SaMuRadius.xs)
                            .background(colors.surfaceVariant.copy(alpha = 0.35f))
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(colors.primary)
                        )
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            block.lines.forEach { line ->
                                Text(
                                    text = parseInlineMarkdown(line, colors),
                                    color = colors.textSecondary,
                                    fontSize = (13.5f * scale).sp,
                                    lineHeight = (13.5f * scale * 1.5f).sp
                                )
                            }
                        }
                    }
                }

                is MdBlock.BulletList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "•",
                                    color = colors.primary,
                                    fontSize = (14f * scale).sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = parseInlineMarkdown(item, colors),
                                    color = colors.onSurface,
                                    fontSize = (14f * scale).sp,
                                    lineHeight = (14f * scale * 1.55f).sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                is MdBlock.OrderedList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.items.forEach { (num, item) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "$num.",
                                    color = colors.primary,
                                    fontSize = (14f * scale).sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Text(
                                    text = parseInlineMarkdown(item, colors),
                                    color = colors.onSurface,
                                    fontSize = (14f * scale).sp,
                                    lineHeight = (14f * scale * 1.55f).sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                is MdBlock.Table -> {
                    MarkdownTableCard(block.headers, block.rows)
                }

                is MdBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 1.dp,
                        color = colors.divider
                    )
                }

                is MdBlock.Paragraph -> {
                    Text(
                        text = parseInlineMarkdown(block.text, colors),
                        color = colors.onSurface,
                        fontSize = (14f * scale).sp,
                        lineHeight = (14f * scale * 1.55f).sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(language: String, code: String) {
    val colors = LocalSaMuColors.current
    val scale = LocalFontScale.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500L)
            copied = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SaMuRadius.sm)
            .border(1.dp, colors.divider, SaMuRadius.sm)
            .background(colors.codeBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = language.ifBlank { "code" }.lowercase(),
                color = colors.textSecondary,
                fontSize = (11f * scale).sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (copied) {
                    Text(
                        text = "Copied",
                        color = colors.success,
                        fontSize = (11f * scale).sp,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (copied) colors.success else colors.textSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(10.dp)
        ) {
            Text(
                text = code,
                color = colors.codeText,
                fontSize = (12f * scale).sp,
                lineHeight = (12f * scale * 1.45f).sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun MarkdownTableCard(headers: List<String>, rows: List<List<String>>) {
    val colors = LocalSaMuColors.current
    val scale = LocalFontScale.current
    val colCount = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0)
    if (colCount == 0) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .clip(SaMuRadius.sm)
            .border(1.dp, colors.divider, SaMuRadius.sm)
    ) {
        Row(
            modifier = Modifier.background(colors.surfaceVariant)
        ) {
            for (c in 0 until colCount) {
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = parseInlineMarkdown(headers.getOrElse(c) { "" }, colors),
                        color = colors.onSurface,
                        fontSize = (12.5f * scale).sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        rows.forEachIndexed { idx, row ->
            HorizontalDivider(thickness = 1.dp, color = colors.divider)
            Row(
                modifier = Modifier.background(
                    if (idx % 2 == 0) colors.surface else colors.surfaceVariant.copy(alpha = 0.25f)
                )
            ) {
                for (c in 0 until colCount) {
                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = parseInlineMarkdown(row.getOrElse(c) { "" }, colors),
                            color = colors.onSurface,
                            fontSize = (12.5f * scale).sp
                        )
                    }
                }
            }
        }
    }
}

private fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = raw.replace("\r\n", "\n").split("\n")
    var i = 0
    // Uses precompiled regexes

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        // 1. Fenced code block (``` or ~~~), streaming-safe even when unclosed!
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            val fence = if (trimmed.startsWith("```")) "```" else "~~~"
            val lang = trimmed.removePrefix(fence).trim()
            val codeBuf = StringBuilder()
            i++
            while (i < lines.size) {
                if (lines[i].trim().startsWith(fence)) {
                    i++
                    break
                }
                if (codeBuf.isNotEmpty()) codeBuf.append("\n")
                codeBuf.append(lines[i])
                i++
            }
            blocks.add(MdBlock.CodeBlock(lang, codeBuf.toString()))
            continue
        }

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // 2. Divider
        if (MD_DIVIDER_REGEX.matches(trimmed)) {
            blocks.add(MdBlock.Divider)
            i++
            continue
        }

        // 3. Heading H1-H4
        val hMatch = MD_HEADING_REGEX.find(trimmed)
        if (hMatch != null) {
            blocks.add(MdBlock.Heading(hMatch.groupValues[1].length, hMatch.groupValues[2].trim()))
            i++
            continue
        }

        // 4. Table
        if (trimmed.contains("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
            val headers = splitTableRow(trimmed)
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trim().contains("|") && lines[i].trim().isNotEmpty()) {
                rows.add(splitTableRow(lines[i].trim()))
                i++
            }
            blocks.add(MdBlock.Table(headers, rows))
            continue
        }

        // 5. Blockquote
        if (trimmed.startsWith(">")) {
            val qLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                qLines.add(lines[i].trim().removePrefix(">").trim())
                i++
            }
            blocks.add(MdBlock.Blockquote(qLines))
            continue
        }

        // 6. Unordered list
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
            val items = mutableListOf<String>()
            while (i < lines.size) {
                val t = lines[i].trim()
                if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ")) {
                    items.add(t.drop(2).trim())
                    i++
                } else break
            }
            blocks.add(MdBlock.BulletList(items))
            continue
        }

        // 7. Ordered list
        if (MD_ORDERED_REGEX.matches(trimmed)) {
            val items = mutableListOf<Pair<String, String>>()
            while (i < lines.size) {
                val m = MD_ORDERED_REGEX.find(lines[i].trim()) ?: break
                items.add(m.groupValues[1] to m.groupValues[2].trim())
                i++
            }
            blocks.add(MdBlock.OrderedList(items))
            continue
        }

        // 8. Paragraph (collect until blank line or structural block)
        val pBuf = StringBuilder(line)
        i++
        while (i < lines.size) {
            val next = lines[i].trim()
            if (next.isEmpty() ||
                next.startsWith("```") || next.startsWith("~~~") ||
                next.startsWith("#") || next.startsWith(">") ||
                next.startsWith("- ") || next.startsWith("* ") ||
                MD_ORDERED_REGEX.matches(next) || MD_DIVIDER_REGEX.matches(next) ||
                (next.contains("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1]))
            ) {
                break
            }
            pBuf.append("\n").append(lines[i])
            i++
        }
        blocks.add(MdBlock.Paragraph(pBuf.toString()))
    }
    return blocks
}

private fun isTableSeparator(line: String): Boolean {
    val t = line.trim().removePrefix("|").removeSuffix("|")
    if (t.isBlank() || !t.contains("-")) return false
    return t.split("|").all { cell ->
        val c = cell.trim()
        c.isNotEmpty() && c.all { it == '-' || it == ':' }
    }
}

private fun splitTableRow(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

private val MD_ORDERED_REGEX = Regex("^(\\d+)\\.\\s+(.*)$")
private val MD_HEADING_REGEX = Regex("^(#{1,4})\\s+(.*)$")
private val MD_DIVIDER_REGEX = Regex("^([-*_])\\1{2,}\\s*$")
private val MD_INLINE_REGEX = Regex(
    """(`[^`]+`)|(\[[^\]]+]\(https?://[^)]+\))|(\*\*\*[^*]+\*\*\*)|(\*\*[^*]+\*\*)|(__[^_]+__)|(\*[^*]+\*)"""
)

private fun parseInlineMarkdown(text: String, colors: SaMuColors): AnnotatedString {
    return buildAnnotatedString {
        var lastIdx = 0
        for (match in MD_INLINE_REGEX.findAll(text)) {
            if (match.range.first > lastIdx) {
                append(text.substring(lastIdx, match.range.first))
            }
            val token = match.value
            when {
                token.startsWith("`") && token.endsWith("`") -> {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = colors.codeBg.copy(alpha = 0.75f),
                            color = colors.codeText
                        )
                    ) {
                        append(" ${token.substring(1, token.length - 1)} ")
                    }
                }
                token.startsWith("[") -> {
                    val label = token.substringAfter("[").substringBefore("](")
                    val url = token.substringAfter("](").substringBeforeLast(")")
                    withLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = colors.linkColor,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                        )
                    ) {
                        append(label)
                    }
                }
                token.startsWith("***") && token.endsWith("***") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                        append(token.substring(3, token.length - 3))
                    }
                }
                token.startsWith("**") && token.endsWith("**") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(token.substring(2, token.length - 2))
                    }
                }
                token.startsWith("__") && token.endsWith("__") -> {
                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                        append(token.substring(2, token.length - 2))
                    }
                }
                token.startsWith("*") && token.endsWith("*") -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(token.substring(1, token.length - 1))
                    }
                }
                else -> append(token)
            }
            lastIdx = match.range.last + 1
        }
        if (lastIdx < text.length) {
            append(text.substring(lastIdx))
        }
    }
}
