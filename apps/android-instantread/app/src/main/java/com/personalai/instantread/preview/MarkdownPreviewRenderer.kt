package com.personalai.instantread.preview

import com.personalai.instantread.io.FileTaskCancellationToken

data class MarkdownRenderResult(
    val html: String,
    val requiresAppJavaScript: Boolean
)

internal data class MarkdownLinkMatch(
    val startIndex: Int,
    val endIndexExclusive: Int,
    val label: String,
    val href: String,
    val isImage: Boolean
)

private const val MARKDOWN_LINK_SCAN_CHECK_INTERVAL = 256

internal fun findNextMarkdownLink(
    text: String,
    fromIndex: Int,
    cancellationToken: FileTaskCancellationToken
): MarkdownLinkMatch? {
    require(fromIndex in 0..text.length)
    cancellationToken.throwIfCancelled()

    var nextCancellationCheck = fromIndex + MARKDOWN_LINK_SCAN_CHECK_INTERVAL
    fun checkScanProgress(index: Int) {
        if (index >= nextCancellationCheck) {
            cancellationToken.throwIfCancelled()
            nextCancellationCheck = index + MARKDOWN_LINK_SCAN_CHECK_INTERVAL
        }
    }

    var searchIndex = fromIndex
    while (searchIndex < text.length) {
        checkScanProgress(searchIndex)
        if (text[searchIndex] != '[') {
            searchIndex += 1
            continue
        }
        if (isBackslashEscaped(text, searchIndex)) {
            searchIndex += 1
            continue
        }

        val bracketIndex = searchIndex
        var labelEnd = bracketIndex + 1
        while (labelEnd < text.length && text[labelEnd] != ']') {
            checkScanProgress(labelEnd)
            labelEnd += 1
        }
        if (labelEnd == text.length) return null
        if (labelEnd == bracketIndex + 1 || labelEnd + 1 >= text.length || text[labelEnd + 1] != '(') {
            searchIndex = labelEnd + 1
            continue
        }

        val hrefStart = labelEnd + 2
        var hrefEnd = hrefStart
        while (hrefEnd < text.length && text[hrefEnd] != ')') {
            checkScanProgress(hrefEnd)
            hrefEnd += 1
        }
        if (hrefEnd == text.length) return null
        if (hrefEnd == hrefStart) {
            searchIndex = hrefEnd + 1
            continue
        }

        val isImage = bracketIndex > fromIndex &&
            text[bracketIndex - 1] == '!' &&
            !isBackslashEscaped(text, bracketIndex - 1)
        val matchStart = if (isImage) bracketIndex - 1 else bracketIndex
        return MarkdownLinkMatch(
            startIndex = matchStart,
            endIndexExclusive = hrefEnd + 1,
            label = text.substring(bracketIndex + 1, labelEnd),
            href = text.substring(hrefStart, hrefEnd),
            isImage = isImage
        )
    }
    cancellationToken.throwIfCancelled()
    return null
}

private fun isBackslashEscaped(text: String, index: Int): Boolean {
    var backslashes = 0
    var cursor = index - 1
    while (cursor >= 0 && text[cursor] == '\\') {
        backslashes += 1
        cursor -= 1
    }
    return backslashes % 2 == 1
}

class MarkdownPreviewRenderer {
    fun renderMarkdown(
        title: String,
        markdown: String,
        fontSizeSp: Int = 17,
        lineHeight: Float = 1.75f,
        darkTheme: Boolean = false,
        readingDisplayOptions: ReadingDisplayOptions = ReadingDisplayOptions(
            fontSizeSp = fontSizeSp,
            lineHeight = lineHeight
        ),
        cancellationToken: FileTaskCancellationToken = FileTaskCancellationToken.NONE
    ): String = renderMarkdownResult(
        title = title,
        markdown = markdown,
        darkTheme = darkTheme,
        readingDisplayOptions = readingDisplayOptions,
        cancellationToken = cancellationToken
    ).html

    fun renderMarkdownResult(
        title: String,
        markdown: String,
        darkTheme: Boolean = false,
        readingDisplayOptions: ReadingDisplayOptions = ReadingDisplayOptions.DEFAULT,
        includeFloatingOutline: Boolean = true,
        allowRelativeImageSources: Boolean = false,
        cancellationToken: FileTaskCancellationToken = FileTaskCancellationToken.NONE
    ): MarkdownRenderResult {
        cancellationToken.throwIfCancelled()
        val inlineOptions = MarkdownInlineOptions(
            allowRelativeImageSources = allowRelativeImageSources
        )
        val rendered = markdownToHtml(markdown, inlineOptions, cancellationToken)
        val outline = if (includeFloatingOutline) {
            renderFloatingOutline(rendered.headings, cancellationToken)
        } else {
            ""
        }
        val body = rendered.body + outline
        val result = MarkdownRenderResult(
            html = htmlDocument(title, body, readingDisplayOptions.clamped(), darkTheme, cancellationToken),
            requiresAppJavaScript = includeFloatingOutline && rendered.headings.isNotEmpty()
        )
        cancellationToken.throwIfCancelled()
        return result
    }

    private fun markdownToHtml(
        markdown: String,
        inlineOptions: MarkdownInlineOptions,
        cancellationToken: FileTaskCancellationToken
    ): RenderedMarkdown {
        cancellationToken.throwIfCancelled()
        val output = StringBuilder()
        val paragraph = StringBuilder()
        val headings = mutableListOf<Heading>()
        val headingIds = mutableMapOf<String, Int>()
        var activeListTag: String? = null
        var activeCodeLanguage: String? = null
        val codeBlock = StringBuilder()

        fun flushParagraph() {
            if (paragraph.isNotBlank()) {
                output.append("<p>")
                    .append(renderInline(paragraph.toString().trim(), inlineOptions, cancellationToken))
                    .append("</p>\n")
                paragraph.clear()
            }
        }

        fun closeList() {
            activeListTag?.let { tag ->
                output.append("</").append(tag).append(">\n")
                activeListTag = null
            }
        }

        fun openList(tag: String) {
            if (activeListTag != tag) {
                closeList()
                output.append("<").append(tag).append(">\n")
                activeListTag = tag
            }
        }

        val lines = markdown.lines()
        var index = 0
        while (index < lines.size) {
            cancellationToken.throwIfCancelled()
            val line = lines[index]
            val heading = parseHeading(line)
            when {
                line.trim().startsWith("```") -> {
                    flushParagraph()
                    closeList()
                    if (activeCodeLanguage != null) {
                        output.append(
                            renderCodeBlock(activeCodeLanguage, codeBlock.toString(), cancellationToken)
                        ).append('\n')
                        codeBlock.clear()
                        activeCodeLanguage = null
                    } else {
                        activeCodeLanguage = line.trim().removePrefix("```").trim()
                    }
                }
                activeCodeLanguage != null -> {
                    codeBlock.append(line).append('\n')
                }
                line.isBlank() -> {
                    flushParagraph()
                    closeList()
                }
                parseTable(lines, index, cancellationToken) != null -> {
                    val table = checkNotNull(parseTable(lines, index, cancellationToken))
                    flushParagraph()
                    closeList()
                    output.append(renderTable(table, inlineOptions, cancellationToken)).append('\n')
                    index += table.lineCount
                    continue
                }
                heading != null -> {
                    flushParagraph()
                    closeList()
                    val headingId = headingIdFor(heading.text, headingIds)
                    val renderedHeading = heading.copy(id = headingId)
                    headings += renderedHeading
                    output.append("<h")
                        .append(heading.level)
                        .append(" id=\"")
                        .append(escapeHtmlAttribute(headingId, cancellationToken))
                        .append("\"")
                        .append(">")
                        .append(renderInline(heading.text, inlineOptions, cancellationToken))
                        .append("</h")
                        .append(heading.level)
                        .append(">")
                        .append('\n')
                }
                isHorizontalRule(line) -> {
                    flushParagraph()
                    closeList()
                    output.append("<hr>\n")
                }
                parseBlockQuote(line) != null -> {
                    val quoteLines = mutableListOf<String>()
                    while (index < lines.size) {
                        cancellationToken.throwIfCancelled()
                        val quoteLine = parseBlockQuote(lines[index]) ?: break
                        quoteLines += quoteLine
                        index++
                    }
                    flushParagraph()
                    closeList()
                    output.append(renderBlockQuote(quoteLines, inlineOptions, cancellationToken))
                    continue
                }
                parseListItem(line) != null -> {
                    val listItems = mutableListOf<ParsedListItem>()
                    while (index < lines.size) {
                        cancellationToken.throwIfCancelled()
                        val item = parseListItem(lines[index]) ?: break
                        listItems += item.copy(level = item.level.coerceAtMost(1))
                        index++
                    }
                    flushParagraph()
                    closeList()
                    output.append(renderListBlock(listItems, inlineOptions, cancellationToken))
                    continue
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line.trim())
                }
            }
            index++
        }

        if (activeCodeLanguage != null) {
            output.append(
                renderCodeBlock(activeCodeLanguage, codeBlock.toString(), cancellationToken)
            ).append('\n')
        }
        flushParagraph()
        closeList()
        cancellationToken.throwIfCancelled()
        return RenderedMarkdown(output.toString(), headings)
    }

    private fun parseHeading(line: String): Heading? {
        val match = Regex("^(#{1,6})\\s+(.+)$").matchEntire(line) ?: return null
        return Heading(
            level = match.groupValues[1].length,
            text = match.groupValues[2].trim(),
            id = ""
        )
    }

    private fun parseListItem(line: String): ParsedListItem? {
        val unordered = Regex("^(\\s*)[-*+]\\s+(.+)$").matchEntire(line)
        if (unordered != null) {
            return ParsedListItem(
                level = unordered.groupValues[1].length / 2,
                ordered = false,
                item = parseTaskListItem(unordered.groupValues[2].trim())
            )
        }
        val ordered = Regex("^(\\s*)\\d+[.)]\\s+(.+)$").matchEntire(line)
        if (ordered != null) {
            return ParsedListItem(
                level = ordered.groupValues[1].length / 2,
                ordered = true,
                item = parseTaskListItem(ordered.groupValues[2].trim())
            )
        }
        return null
    }

    private fun parseTaskListItem(text: String): MarkdownListItem {
        val task = Regex("^\\[(x|X| )]\\s+(.+)$").matchEntire(text)
        return if (task == null) {
            MarkdownListItem(text = text, taskChecked = null)
        } else {
            MarkdownListItem(
                text = task.groupValues[2].trim(),
                taskChecked = task.groupValues[1].equals("x", ignoreCase = true)
            )
        }
    }

    private fun renderListItem(
        item: MarkdownListItem,
        inlineOptions: MarkdownInlineOptions,
        cancellationToken: FileTaskCancellationToken
    ): String {
        return renderListItemOpening(item, inlineOptions, cancellationToken) + "</li>"
    }

    private fun renderListItemOpening(
        item: MarkdownListItem,
        inlineOptions: MarkdownInlineOptions,
        cancellationToken: FileTaskCancellationToken
    ): String {
        return if (item.taskChecked == null) {
            "<li>${renderInline(item.text, inlineOptions, cancellationToken)}"
        } else {
            val checked = if (item.taskChecked) " checked" else ""
            "<li class=\"task-list-item\"><input type=\"checkbox\" disabled$checked> ${renderInline(item.text, inlineOptions, cancellationToken)}"
        }
    }

    private fun renderListBlock(
        items: List<ParsedListItem>,
        inlineOptions: MarkdownInlineOptions,
        cancellationToken: FileTaskCancellationToken
    ): String {
        if (items.isEmpty()) return ""
        val rootTag = if (items.first().ordered) "ol" else "ul"
        val output = StringBuilder()
        var topLevelOpen = false
        var nestedTag: String? = null
        var currentItemHasNestedList = false

        fun closeNestedList() {
            nestedTag?.let { tag ->
                output.append("</").append(tag).append(">\n")
                nestedTag = null
            }
        }

        fun closeTopLevelItem() {
            if (topLevelOpen) {
                output.append("</li>\n")
                topLevelOpen = false
                currentItemHasNestedList = false
            }
        }

        output.append("<").append(rootTag).append(">\n")
        items.forEach { parsed ->
            cancellationToken.throwIfCancelled()
            val tag = if (parsed.ordered) "ol" else "ul"
            if (parsed.level == 0) {
                closeNestedList()
                closeTopLevelItem()
                output.append(renderListItemOpening(parsed.item, inlineOptions, cancellationToken))
                topLevelOpen = true
            } else {
                if (!topLevelOpen) {
                    output.append("<li>")
                    topLevelOpen = true
                }
                if (nestedTag != tag) {
                    if (!currentItemHasNestedList) output.append('\n')
                    closeNestedList()
                    output.append("<").append(tag).append(">\n")
                    nestedTag = tag
                    currentItemHasNestedList = true
                }
                output.append(renderListItem(parsed.item, inlineOptions, cancellationToken)).append('\n')
            }
        }
        closeNestedList()
        closeTopLevelItem()
        output.append("</").append(rootTag).append(">\n")
        return output.toString()
    }

    private fun parseBlockQuote(line: String): String? {
        return Regex("^\\s*>\\s?(.*)$").matchEntire(line)?.groupValues?.get(1)?.trim()
    }

    private fun renderBlockQuote(
        lines: List<String>,
        inlineOptions: MarkdownInlineOptions,
        cancellationToken: FileTaskCancellationToken
    ): String {
        val paragraphs = mutableListOf<StringBuilder>()
        lines.forEach { line ->
            cancellationToken.throwIfCancelled()
            if (line.isBlank()) {
                if (paragraphs.lastOrNull()?.isNotBlank() == true) paragraphs += StringBuilder()
            } else {
                if (paragraphs.isEmpty()) paragraphs += StringBuilder()
                if (paragraphs.last().isNotEmpty()) paragraphs.last().append(' ')
                paragraphs.last().append(line)
            }
        }
        val body = paragraphs
            .filter { it.isNotBlank() }
            .joinToString(separator = "") {
                cancellationToken.throwIfCancelled()
                "<p>${renderInline(it.toString().trim(), inlineOptions, cancellationToken)}</p>"
            }
        return "<blockquote>$body</blockquote>\n"
    }

    private fun isHorizontalRule(line: String): Boolean {
        val trimmed = line.trim().replace(" ", "")
        return trimmed.length >= 3 && trimmed.all { it == '-' } ||
            trimmed.length >= 3 && trimmed.all { it == '*' } ||
            trimmed.length >= 3 && trimmed.all { it == '_' }
    }

    private fun parseTable(
        lines: List<String>,
        start: Int,
        cancellationToken: FileTaskCancellationToken
    ): MarkdownTable? {
        cancellationToken.throwIfCancelled()
        if (start + 1 >= lines.size) return null
        val header = parseTableRow(lines[start]) ?: return null
        val separator = parseTableRow(lines[start + 1]) ?: return null
        if (header.size < 2 || separator.size != header.size) return null
        if (!separator.all { it.matches(Regex(":?-{3,}:?")) }) return null
        val alignments = separator.map { tableAlignment(it) }

        val rows = mutableListOf<List<String>>()
        var index = start + 2
        while (index < lines.size) {
            cancellationToken.throwIfCancelled()
            val row = parseTableRow(lines[index]) ?: break
            if (row.size != header.size) break
            rows += row
            index++
        }
        return MarkdownTable(header, alignments, rows, index - start)
    }

    private fun parseTableRow(line: String): List<String>? {
        val trimmed = line.trim()
        if (!trimmed.contains("|")) return null
        val content = trimmed.trim('|')
        val cells = content.split("|").map { it.trim() }
        return cells.takeIf { it.size >= 2 }
    }

    private fun renderTable(
        table: MarkdownTable,
        inlineOptions: MarkdownInlineOptions,
        cancellationToken: FileTaskCancellationToken
    ): String {
        val header = table.header.mapIndexed { index, cell ->
            cancellationToken.throwIfCancelled()
            "<th style=\"text-align:${table.alignmentAt(index)}\">${renderInline(cell, inlineOptions, cancellationToken)}</th>"
        }.joinToString(separator = "")
        val rows = table.rows.joinToString(separator = "\n") { row ->
            cancellationToken.throwIfCancelled()
            val cells = row.mapIndexed { index, cell ->
                cancellationToken.throwIfCancelled()
                "<td style=\"text-align:${table.alignmentAt(index)}\">${renderInline(cell, inlineOptions, cancellationToken)}</td>"
            }.joinToString(separator = "")
            "<tr>$cells</tr>"
        }
        return """
            <div class="table-scroll" aria-label="表格可横向滚动"><table>
            <thead><tr>$header</tr></thead>
            <tbody>
            $rows
            </tbody>
            </table></div>
        """.trimIndent()
    }

    private fun tableAlignment(separator: String): String {
        val left = separator.startsWith(":")
        val right = separator.endsWith(":")
        return when {
            left && right -> "center"
            right -> "right"
            else -> "left"
        }
    }

    private fun MarkdownTable.alignmentAt(index: Int): String {
        return alignments.getOrElse(index) { "left" }
    }

    private fun renderCodeBlock(
        language: String?,
        code: String,
        cancellationToken: FileTaskCancellationToken
    ): String {
        cancellationToken.throwIfCancelled()
        val normalizedLanguage = language?.trim()?.takeIf { it.isNotEmpty() }
        val className = normalizedLanguage
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9_-]+"), "-")
            ?.trim('-')
        val caption = normalizedLanguage?.let {
            "<figcaption class=\"code-language\">${escapeHtml(it, cancellationToken)}</figcaption>"
        }.orEmpty()
        val codeClass = className?.let {
            " class=\"language-${escapeHtmlAttribute(it, cancellationToken)}\""
        }.orEmpty()
        return """
            <figure class="code-block">
            $caption
            <pre><code$codeClass>${escapeHtml(code.trimEnd(), cancellationToken)}</code></pre>
            </figure>
        """.trimIndent()
    }

    private fun renderInline(
        text: String,
        inlineOptions: MarkdownInlineOptions,
        cancellationToken: FileTaskCancellationToken
    ): String {
        cancellationToken.throwIfCancelled()
        val output = StringBuilder()
        var lastIndex = 0
        Regex("`([^`]+)`").findAll(text).forEach { match ->
            cancellationToken.throwIfCancelled()
            output.append(
                renderNonCodeInline(
                    text.substring(lastIndex, match.range.first),
                    inlineOptions,
                    cancellationToken
                )
            )
            output.append("<code>")
                .append(escapeHtml(match.groupValues[1], cancellationToken))
                .append("</code>")
            lastIndex = match.range.last + 1
        }
        output.append(renderNonCodeInline(text.substring(lastIndex), inlineOptions, cancellationToken))
        return output.toString()
    }

    private fun renderNonCodeInline(
        text: String,
        inlineOptions: MarkdownInlineOptions,
        cancellationToken: FileTaskCancellationToken
    ): String {
        val output = StringBuilder()
        var lastIndex = 0
        var match = findNextMarkdownLink(text, lastIndex, cancellationToken)
        while (match != null) {
            cancellationToken.throwIfCancelled()
            output.append(
                renderEmphasis(
                    renderAutolinks(
                        escapeInlineText(
                            text.substring(lastIndex, match.startIndex),
                            cancellationToken
                        ),
                        cancellationToken
                    ),
                    cancellationToken
                )
            )
            val label = match.label
            val href = match.href
            val rawMatch = text.substring(match.startIndex, match.endIndexExclusive)
            if (match.isImage) {
                if (isSafeImageSource(href, inlineOptions.allowRelativeImageSources)) {
                    output.append("<figure class=\"image-figure\"><img src=\"")
                        .append(escapeHtmlAttribute(href.trim(), cancellationToken))
                        .append("\" alt=\"")
                        .append(escapeHtmlAttribute(label, cancellationToken))
                        .append("\">")
                    if (label.isNotBlank()) {
                        output.append("<figcaption>")
                            .append(escapeHtml(label, cancellationToken))
                            .append("</figcaption>")
                    }
                    output.append("</figure>")
                } else {
                    output.append(escapeHtml(rawMatch, cancellationToken))
                }
            } else if (isSafeMarkdownLink(href)) {
                output.append("<a href=\"")
                    .append(escapeHtmlAttribute(href.trim(), cancellationToken))
                    .append("\">")
                    .append(
                        renderEmphasis(
                            escapeInlineText(label, cancellationToken),
                            cancellationToken
                        )
                    )
                    .append("</a>")
            } else {
                output.append(escapeHtml(rawMatch, cancellationToken))
            }
            lastIndex = match.endIndexExclusive
            match = findNextMarkdownLink(text, lastIndex, cancellationToken)
        }
        output.append(
            renderEmphasis(
                renderAutolinks(
                    escapeInlineText(text.substring(lastIndex), cancellationToken),
                    cancellationToken
                ),
                cancellationToken
            )
        )
        return output.toString()
    }

    private fun renderAutolinks(
        escapedText: String,
        cancellationToken: FileTaskCancellationToken
    ): String {
        return escapedText.replace(Regex("&lt;(https?://[^\\s&]+)&gt;")) { match ->
            cancellationToken.throwIfCancelled()
            val href = match.groupValues[1]
            "<a href=\"${escapeHtmlAttribute(href, cancellationToken)}\">${escapeHtml(href, cancellationToken)}</a>"
        }
    }

    private fun renderEmphasis(
        escapedText: String,
        cancellationToken: FileTaskCancellationToken
    ): String {
        return escapedText
            .replace(Regex("~~([^~]+)~~")) { match ->
                cancellationToken.throwIfCancelled()
                "<del>${match.groupValues[1]}</del>"
            }
            .replace(Regex("\\*\\*([^*]+)\\*\\*")) { match ->
                cancellationToken.throwIfCancelled()
                "<strong>${match.groupValues[1]}</strong>"
            }
            .replace(Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)")) { match ->
                cancellationToken.throwIfCancelled()
                "<em>${match.groupValues[1]}</em>"
            }
    }

    private fun isSafeMarkdownLink(href: String): Boolean {
        val normalized = href.trim()
        val lower = normalized.lowercase()
        if (normalized.isBlank()) return false
        if (lower.startsWith("https://") || lower.startsWith("http://")) return true
        if (normalized.startsWith("#")) return true
        return false
    }

    private fun isSafeImageSource(src: String, allowRelativeImageSources: Boolean): Boolean {
        val normalized = src.trim()
        val lower = normalized.lowercase()
        if (normalized.isBlank()) return false
        if (lower.startsWith("data:image/")) return true
        return allowRelativeImageSources && isSafeRelativeResourceReference(normalized)
    }

    /**
     * 只放行当前文档资源域内的相对引用。
     *
     * 生成页与 Markdown 入口放在同一目录，`assets/pic.png` 或 `../assets/pic.png` 交给
     * WebView 解析后仍会经过 `PreviewNavigationPolicy` 的授权根校验；显式协议、绝对路径和
     * 网络地址在这里直接拒绝。
     */
    private fun isSafeRelativeResourceReference(src: String): Boolean {
        if (src.startsWith("/") || src.startsWith("\\") || src.startsWith("//")) return false
        if (src.any { it < ' ' || it == '\u007f' }) return false
        if (src.contains('\\')) return false
        val schemeSeparator = src.indexOf(':')
        val firstSlash = src.indexOf('/')
        return schemeSeparator < 0 || (firstSlash in 0 until schemeSeparator)
    }

    private fun htmlDocument(
        title: String,
        body: String,
        readingDisplayOptions: ReadingDisplayOptions,
        darkTheme: Boolean,
        cancellationToken: FileTaskCancellationToken
    ): String {
        cancellationToken.throwIfCancelled()
        val theme = readingTheme(darkTheme)
        val lineHeight = cssNumber(readingDisplayOptions.lineHeight)
        val letterSpacing = cssNumber(readingDisplayOptions.letterSpacingEm)
        val paragraphSpacing = cssNumber(readingDisplayOptions.paragraphSpacingEm)
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>${escapeHtml(title, cancellationToken)}</title>
              <style>
                body { margin: 0; background: ${theme.bodyBackground}; color: ${theme.bodyText}; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
                .markdown-body { box-sizing: border-box; max-width: 760px; margin: 0 auto; padding: 22px 18px 40px; font-size: ${readingDisplayOptions.fontSizeSp}px; line-height: $lineHeight; letter-spacing: ${letterSpacing}em; overflow-wrap: anywhere; }
                h1, h2, h3, h4, h5, h6 { color: ${theme.headingText}; line-height: 1.28; margin: 1.5em 0 0.55em; scroll-margin-top: 84px; }
                h1 { font-size: 1.62em; }
                h2 { font-size: 1.34em; border-bottom: 1px solid ${theme.border}; padding-bottom: 0.24em; }
                h3 { font-size: 1.16em; }
                p { margin: ${paragraphSpacing}em 0; }
                ul { padding-left: 1.35em; margin: ${paragraphSpacing}em 0; }
                ol { padding-left: 1.35em; margin: ${paragraphSpacing}em 0; }
                li { margin: 0.3em 0; }
                blockquote { margin: 1em 0; padding: 0.1em 0 0.1em 0.9em; color: ${theme.mutedText}; border-left: 3px solid ${theme.border}; background: ${theme.softSurface}; }
                blockquote p { margin: 0.52em 0; }
                hr { border: 0; border-top: 1px solid ${theme.border}; margin: 1.45em 0; }
                .code-block { margin: 1em 0; border: 1px solid ${theme.border}; border-radius: 8px; background: ${theme.codeSurface}; overflow: hidden; }
                .code-language { padding: 7px 12px 0; color: ${theme.mutedText}; font-size: 0.76em; font-weight: 700; letter-spacing: 0.04em; }
                pre { overflow-x: auto; margin: 0; padding: 13px 14px; background: transparent; -webkit-overflow-scrolling: touch; }
                code { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; background: ${theme.codeSurface}; padding: 1px 5px; border-radius: 4px; font-size: 0.92em; }
                pre code { display: block; padding: 0; background: transparent; white-space: pre; }
                a { color: ${theme.link}; text-decoration: none; }
                .image-figure { margin: 1.1em 0; }
                .image-figure img { max-width: 100%; height: auto; display: block; margin: 0 auto; border-radius: 8px; }
                .image-figure figcaption { margin-top: 6px; color: ${theme.mutedText}; font-size: 0.84em; text-align: center; }
                del { color: ${theme.mutedText}; }
                .task-list-item { list-style: none; margin-left: -1.25em; }
                .task-list-item input { margin-right: 0.48em; vertical-align: -0.12em; }
                .table-scroll { position: relative; overflow-x: auto; margin: 1em 0; -webkit-overflow-scrolling: touch; }
                .table-scroll::after { content: "横向滑动查看完整表格"; display: block; margin-top: 5px; color: ${theme.mutedText}; font-size: 0.78em; }
                table { width: 100%; border-collapse: collapse; min-width: 420px; }
                th, td { padding: 8px 10px; border: 1px solid ${theme.border}; text-align: left; vertical-align: top; }
                th { background: ${theme.softSurface}; color: ${theme.headingText}; font-weight: 700; }
                .floating-outline { position: fixed; right: 14px; bottom: calc(18px + env(safe-area-inset-bottom)); z-index: 40; font-size: 14px; line-height: 1.4; }
                .floating-outline-toggle { min-width: 58px; height: 40px; padding: 0 14px; border: 1px solid ${theme.border}; border-radius: 999px; background: ${theme.headingText}; color: ${theme.bodyBackground}; font: 700 14px/1 -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; box-shadow: 0 10px 22px rgba(15, 23, 42, 0.16); }
                .floating-outline-panel { position: fixed; left: 14px; right: 14px; bottom: calc(68px + env(safe-area-inset-bottom)); max-height: min(62vh, 430px); overflow-y: auto; padding: 14px; border: 1px solid ${theme.border}; border-radius: 14px; background: ${theme.bodyBackground}; box-shadow: 0 18px 48px rgba(15, 23, 42, 0.22); -webkit-overflow-scrolling: touch; }
                .floating-outline-panel[hidden] { display: none; }
                .floating-outline-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 10px; color: ${theme.headingText}; font-weight: 700; }
                .floating-outline-close { border: 0; background: transparent; color: ${theme.mutedText}; font: 600 13px/1 -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; padding: 6px 2px; }
                .floating-outline-current { margin-bottom: 10px; padding: 8px 10px; border-radius: 9px; background: ${theme.softSurface}; color: ${theme.mutedText}; font-size: 0.9em; }
                .floating-outline-links { display: grid; gap: 3px; }
                .floating-outline .outline-link { display: block; padding: 7px 9px; border-radius: 8px; color: ${theme.bodyText}; font-size: 0.96em; line-height: 1.35; }
                .floating-outline .outline-link.is-active { background: ${theme.softSurface}; color: ${theme.link}; font-weight: 700; }
                .floating-outline .outline-level-2 { padding-left: 20px; }
                .floating-outline .outline-level-3,
                .floating-outline .outline-level-4,
                .floating-outline .outline-level-5,
                .floating-outline .outline-level-6 { padding-left: 32px; }
              </style>
            </head>
            <body>
            <main class="markdown-body">
            $body
            </main>
            </body>
            </html>
        """.trimIndent()
    }

    private fun readingTheme(darkTheme: Boolean): ReadingTheme {
        return if (darkTheme) {
            ReadingTheme("#111827", "#e5e7eb", "#f8fafc", "#94a3b8", "#1f2937", "#334155", "#93c5fd", "#0f172a")
        } else {
            ReadingTheme("#ffffff", "#1f2937", "#0f172a", "#64748b", "#f8fbff", "#e5edf6", "#2563eb", "#f6f8fa")
        }
    }

    private fun renderFloatingOutline(
        headings: List<Heading>,
        cancellationToken: FileTaskCancellationToken
    ): String {
        if (headings.isEmpty()) return ""
        val links = headings.joinToString(separator = "\n") { heading ->
            cancellationToken.throwIfCancelled()
            "<a class=\"outline-link outline-level-${heading.level}\" href=\"#${escapeHtmlAttribute(heading.id, cancellationToken)}\" data-outline-target=\"${escapeHtmlAttribute(heading.id, cancellationToken)}\">${escapeHtml(heading.text, cancellationToken)}</a>"
        }
        return """
            <aside class="floating-outline" aria-label="文档目录">
              <button type="button" class="floating-outline-toggle" aria-expanded="false" aria-controls="floating-outline-panel">目录</button>
              <div class="floating-outline-panel" id="floating-outline-panel" hidden>
                <div class="floating-outline-header">
                  <span>文档目录</span>
                  <button type="button" class="floating-outline-close">关闭</button>
                </div>
                <div class="floating-outline-current" aria-live="polite">当前位置：文首</div>
                <div class="floating-outline-links">
                $links
                </div>
              </div>
            </aside>
            <script data-instantread-markdown-outline-script>
            (function() {
              var root = document.querySelector('.floating-outline');
              if (!root) return;
              var toggle = root.querySelector('.floating-outline-toggle');
              var panel = root.querySelector('#floating-outline-panel');
              var close = root.querySelector('.floating-outline-close');
              var current = root.querySelector('.floating-outline-current');
              var links = Array.prototype.slice.call(root.querySelectorAll('.outline-link'));
              var headings = links.map(function(link) {
                return document.getElementById(link.getAttribute('data-outline-target'));
              }).filter(function(heading) {
                return !!heading;
              });
              function setOutlineOpen(open) {
                panel.hidden = !open;
                toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
              }
              function updateCurrentOutline() {
                var active = null;
                for (var index = 0; index < headings.length; index++) {
                  var rect = headings[index].getBoundingClientRect();
                  if (rect.top <= 112) {
                    active = headings[index];
                  } else {
                    break;
                  }
                }
                var activeId = active ? active.id : '';
                current.textContent = '当前位置：' + (active ? active.textContent : '文首');
                links.forEach(function(link) {
                  var isActive = link.getAttribute('data-outline-target') === activeId;
                  link.classList.toggle('is-active', isActive);
                  if (isActive) link.setAttribute('aria-current', 'true');
                  else link.removeAttribute('aria-current');
                });
              }
              var ticking = false;
              function requestOutlineUpdate() {
                if (ticking) return;
                ticking = true;
                window.requestAnimationFrame(function() {
                  updateCurrentOutline();
                  ticking = false;
                });
              }
              toggle.addEventListener('click', function() {
                setOutlineOpen(panel.hidden);
                updateCurrentOutline();
              });
              close.addEventListener('click', function() {
                setOutlineOpen(false);
              });
              links.forEach(function(link) {
                link.addEventListener('click', function() {
                  setOutlineOpen(false);
                  window.setTimeout(updateCurrentOutline, 120);
                });
              });
              window.addEventListener('scroll', requestOutlineUpdate);
              window.addEventListener('hashchange', requestOutlineUpdate);
              updateCurrentOutline();
            })();
            </script>
        """.trimIndent() + "\n"
    }

    private fun headingIdFor(text: String, usedIds: MutableMap<String, Int>): String {
        val base = text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
            .trim('-')
            .ifBlank { "section" }
        val next = (usedIds[base] ?: 0) + 1
        usedIds[base] = next
        return if (next == 1) base else "$base-$next"
    }

    private fun escapeHtml(value: String, cancellationToken: FileTaskCancellationToken): String {
        val escaped = StringBuilder(value.length)
        value.forEachIndexed { index, character ->
            if (index % ESCAPE_CHECK_INTERVAL == 0) cancellationToken.throwIfCancelled()
            escaped.append(
                when (character) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&#39;"
                    else -> character
                }
            )
        }
        cancellationToken.throwIfCancelled()
        return escaped.toString()
    }

    /**
     * 转义普通行内文本，同时把反斜杠转义还原为不会再次触发 Markdown 语法的实体。
     */
    private fun escapeInlineText(
        value: String,
        cancellationToken: FileTaskCancellationToken
    ): String {
        val output = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            if (index % ESCAPE_CHECK_INTERVAL == 0) cancellationToken.throwIfCancelled()
            val character = value[index]
            if (
                character == '\\' &&
                index + 1 < value.length &&
                isEscapableMarkdownPunctuation(value[index + 1])
            ) {
                output.append(htmlEntityForEscapedPunctuation(value[index + 1]))
                index += 2
                continue
            }
            output.append(htmlEscapedCharacter(character))
            index += 1
        }
        cancellationToken.throwIfCancelled()
        return output.toString()
    }

    private fun htmlEscapedCharacter(character: Char): String = when (character) {
        '&' -> "&amp;"
        '<' -> "&lt;"
        '>' -> "&gt;"
        '"' -> "&quot;"
        '\'' -> "&#39;"
        else -> character.toString()
    }

    private fun htmlEntityForEscapedPunctuation(character: Char): String =
        "&#${character.code};"

    private fun isEscapableMarkdownPunctuation(character: Char): Boolean =
        character in ESCAPABLE_MARKDOWN_PUNCTUATION

    private fun escapeHtmlAttribute(
        value: String,
        cancellationToken: FileTaskCancellationToken
    ): String {
        return escapeHtml(value, cancellationToken)
    }

    private fun cssNumber(value: Float): String {
        return java.lang.Float.toString(value)
    }

    private data class RenderedMarkdown(val body: String, val headings: List<Heading>)
    private data class Heading(val level: Int, val text: String, val id: String)
    private data class ReadingTheme(
        val bodyBackground: String,
        val bodyText: String,
        val headingText: String,
        val mutedText: String,
        val softSurface: String,
        val border: String,
        val link: String,
        val codeSurface: String
    )
    private data class MarkdownTable(
        val header: List<String>,
        val alignments: List<String>,
        val rows: List<List<String>>,
        val lineCount: Int
    )
    private data class MarkdownListItem(val text: String, val taskChecked: Boolean?)
    private data class ParsedListItem(val level: Int, val ordered: Boolean, val item: MarkdownListItem)
    private data class MarkdownInlineOptions(val allowRelativeImageSources: Boolean)

    private companion object {
        const val ESCAPE_CHECK_INTERVAL = 256
        const val ESCAPABLE_MARKDOWN_PUNCTUATION =
            "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
    }
}
