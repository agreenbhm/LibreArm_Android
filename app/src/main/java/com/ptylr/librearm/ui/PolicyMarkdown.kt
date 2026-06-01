package com.ptylr.librearm.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Renders the bundled privacy policy. Deliberately handles only the small,
 * fixed Markdown subset the policy uses (headings, bullets, bold/italic,
 * links, rules). Any unrecognized line falls through to plain body text, so
 * unfamiliar syntax can never blank or break the screen — worst case is one
 * unstyled line. Links open in the browser via ACTION_VIEW (no INTERNET — the
 * browser app handles the request).
 */
@Composable
fun PolicyMarkdown(markdown: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        for (raw in markdown.lineSequence()) {
            val line = raw.trimEnd()
            val trimmed = line.trimStart()
            when {
                line.isBlank() ->
                    Spacer(Modifier.height(8.dp))

                line == "---" ->
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                line.startsWith("## ") ->
                    MarkdownText(
                        inlineMarkdown(line.removePrefix("## ")),
                        MaterialTheme.typography.titleMedium,
                        Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )

                line.startsWith("# ") ->
                    MarkdownText(
                        inlineMarkdown(line.removePrefix("# ")),
                        MaterialTheme.typography.headlineSmall,
                        Modifier.padding(bottom = 8.dp)
                    )

                trimmed.startsWith("- ") -> {
                    val indentSpaces = line.length - trimmed.length
                    Row(modifier = Modifier.padding(start = (12 + indentSpaces * 4).dp, bottom = 2.dp)) {
                        MarkdownText(AnnotatedString("•  "), MaterialTheme.typography.bodyMedium)
                        MarkdownText(
                            inlineMarkdown(trimmed.removePrefix("- ")),
                            MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                else ->
                    MarkdownText(
                        inlineMarkdown(line),
                        MaterialTheme.typography.bodyMedium,
                        Modifier.padding(bottom = 2.dp)
                    )
            }
        }
    }
}

/** A line of policy text whose `[text](url)` spans are tappable. */
@Composable
private fun MarkdownText(text: AnnotatedString, style: TextStyle, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    ClickableText(
        text = text,
        modifier = modifier,
        style = style.copy(color = LocalContentColor.current),
        onClick = { offset ->
            text.getStringAnnotations(URL_TAG, offset, offset).firstOrNull()?.let { annotation ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item)))
                }
            }
        }
    )
}

private const val URL_TAG = "url"

private val INLINE_PATTERN =
    Regex("""\*\*(.+?)\*\*|\*(.+?)\*|\[([^\]]+)]\(([^)]+)\)""")

/**
 * Applies **bold**, *italic*, and [text](url) styling. Links are underlined and
 * carry a [URL_TAG] annotation so [MarkdownText] can open them. Anything that
 * doesn't match a pattern is appended verbatim, so this never throws.
 */
private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (match in INLINE_PATTERN.findAll(text)) {
        if (match.range.first > cursor) {
            append(text.substring(cursor, match.range.first))
        }
        val (bold, italic, linkText, linkUrl) = match.destructured
        when {
            bold.isNotEmpty() ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            italic.isNotEmpty() ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
            linkUrl.isNotEmpty() -> {
                pushStringAnnotation(URL_TAG, linkUrl)
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(linkText) }
                pop()
            }
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}
