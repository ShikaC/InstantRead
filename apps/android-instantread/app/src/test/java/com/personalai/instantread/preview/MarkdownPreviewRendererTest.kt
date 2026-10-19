package com.personalai.instantread.preview

import com.personalai.instantread.io.FileTaskCancellationToken
import com.personalai.instantread.io.FileTaskCancelledException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownPreviewRendererTest {
    @Test
    fun linkScannerCooperativelyCancelsLargeUnclosedLabels() {
        val expected = FileTaskCancelledException()
        var checks = 0
        val cancelAt = 32
        val token = FileTaskCancellationToken {
            checks += 1
            if (checks == cancelAt) throw expected
        }

        val actual = assertThrows(FileTaskCancelledException::class.java) {
            findNextMarkdownLink(
                text = "[".repeat(32_768),
                fromIndex = 0,
                cancellationToken = token
            )
        }

        assertSame(expected, actual)
        assertEquals(cancelAt, checks)
    }

    @Test
    fun linkScannerSkipsMalformedCandidatesAndFindsLinksAndImagesInOrder() {
        val text = "[](#empty) [broken] ![Logo](data:image/png;base64,abc) [site](https://example.com)"

        val image = checkNotNull(
            findNextMarkdownLink(text, 0, FileTaskCancellationToken.NONE)
        )
        val link = checkNotNull(
            findNextMarkdownLink(text, image.endIndexExclusive, FileTaskCancellationToken.NONE)
        )

        assertEquals(text.indexOf("![Logo]"), image.startIndex)
        assertEquals("Logo", image.label)
        assertEquals("data:image/png;base64,abc", image.href)
        assertTrue(image.isImage)
        assertEquals(text.indexOf("[site]"), link.startIndex)
        assertEquals("site", link.label)
        assertEquals("https://example.com", link.href)
        assertFalse(link.isImage)
        assertEquals(text.length, link.endIndexExclusive)
    }

    @Test
    fun rendererPropagatesCancellationForLargeMalformedLinkInput() {
        val expected = FileTaskCancelledException()
        var checks = 0
        val token = FileTaskCancellationToken {
            checks += 1
            if (checks == 32) throw expected
        }

        val actual = assertThrows(FileTaskCancelledException::class.java) {
            MarkdownPreviewRenderer().renderMarkdown(
                title = "malformed.md",
                markdown = "[".repeat(32_768),
                cancellationToken = token
            )
        }

        assertSame(expected, actual)
    }

    @Test
    fun cooperativelyCancelsLongMarkdownLineAndBlockLoops() {
        assertCancellationAtCheck("\n".repeat(512), cancelAt = 32)
        assertCancellationAtCheck(
            (0 until 512).joinToString("\n") { "> quote $it" },
            cancelAt = 32
        )
        assertCancellationAtCheck(
            (0 until 512).joinToString("\n") { "- list item $it" },
            cancelAt = 32
        )
        assertCancellationAtCheck(
            buildString {
                appendLine("| Name | Value |")
                appendLine("| --- | --- |")
                repeat(512) { appendLine("| row $it | value $it |") }
            },
            cancelAt = 32
        )
    }

    @Test
    fun cooperativelyCancelsManyInlineRegexMatches() {
        val markdown = (0 until 512).joinToString(" ") {
            "[link$it](https://example.com/$it) `code$it` **bold$it**"
        }

        assertCancellationAtCheck(markdown, cancelAt = 32)
    }

    @Test
    fun cooperativelyCancelsLongCodeBlockEscaping() {
        val markdown = "```html\n${"<&\"'>".repeat(32_768)}\n```"

        assertCancellationAtCheck(markdown, cancelAt = 32)
    }

    @Test
    fun rendersBasicMarkdownIntoMobileHtml() {
        val html = MarkdownPreviewRenderer().renderMarkdown(
            title = "notes.md",
            markdown = "# Title\n\n- item\n\n`code`\n\n<script>alert(1)</script>"
        )

        assertTrue(html.contains("<title>notes.md</title>"))
        assertTrue(html.contains("<h1 id=\"title\">Title</h1>"))
        assertTrue(html.contains("<li>item</li>"))
        assertTrue(html.contains("<code>code</code>"))
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertFalse(html.contains("<script>alert(1)</script>"))
    }

    @Test
    fun rendersMarkdownHeadingsFromLevelOneToSix() {
        val html = MarkdownPreviewRenderer().renderMarkdown(
            title = "outline.md",
            markdown = """
                # Level 1
                ## Level 2
                ### Level 3
                #### Level 4
                ##### Level 5
                ###### Level 6
                ####### Not a heading
            """.trimIndent()
        )

        (1..6).forEach { level ->
            assertTrue(html.contains("<h$level id=\"level-$level\">Level $level</h$level>"))
        }
        assertFalse(html.contains("<h6># Not a heading</h6>"))
        assertTrue(html.contains("<p>####### Not a heading</p>"))
    }

    @Test
    fun rendersReadableTypographyAndInPageOutlineForHeadings() {
        val html = MarkdownPreviewRenderer().renderMarkdown(
            title = "guide.md",
            markdown = """
                # Title
                ## Getting Started
                ## Getting Started
                ### 细节
            """.trimIndent()
        )

        assertTrue(html.contains("class=\"markdown-body\""))
        assertTrue(html.contains("font-size: 17px"))
        assertTrue(html.contains("line-height: 1.75"))
        assertTrue(html.contains("overflow-wrap: anywhere"))
        assertTrue(html.contains("<aside class=\"floating-outline\" aria-label=\"文档目录\">"))
        assertTrue(html.contains("<button type=\"button\" class=\"floating-outline-toggle\" aria-expanded=\"false\" aria-controls=\"floating-outline-panel\">"))
        assertTrue(html.contains("<div class=\"floating-outline-panel\" id=\"floating-outline-panel\" hidden>"))
        assertTrue(html.contains("<div class=\"floating-outline-current\" aria-live=\"polite\">当前位置：文首</div>"))
        assertTrue(html.contains("<a class=\"outline-link outline-level-1\" href=\"#title\" data-outline-target=\"title\">Title</a>"))
        assertTrue(html.contains("<a class=\"outline-link outline-level-2\" href=\"#getting-started\" data-outline-target=\"getting-started\">Getting Started</a>"))
        assertTrue(html.contains("<a class=\"outline-link outline-level-2\" href=\"#getting-started-2\" data-outline-target=\"getting-started-2\">Getting Started</a>"))
        assertTrue(html.contains("<a class=\"outline-link outline-level-3\" href=\"#细节\" data-outline-target=\"细节\">细节</a>"))
        assertTrue(html.contains("function updateCurrentOutline()"))
        assertTrue(html.contains("requestAnimationFrame"))
        assertTrue(html.contains("is-active"))
        assertFalse(html.contains("<details class=\"document-outline\""))
        assertFalse(html.contains("<nav class=\"document-outline\""))
        assertTrue(html.contains("<h1 id=\"title\">Title</h1>"))
        assertTrue(html.contains("<h2 id=\"getting-started\">Getting Started</h2>"))
        assertTrue(html.contains("<h2 id=\"getting-started-2\">Getting Started</h2>"))
        assertTrue(html.contains("<h3 id=\"细节\">细节</h3>"))
    }

    @Test
    fun doesNotRenderOutlineWhenMarkdownHasNoHeadings() {
        val html = MarkdownPreviewRenderer().renderMarkdown(
            title = "plain.md",
            markdown = "Just a short paragraph.\n\n- item"
        )

        assertFalse(html.contains("<details class=\"document-outline\""))
        assertFalse(html.contains("class=\"floating-outline\""))
        assertFalse(html.contains("function updateCurrentOutline()"))
    }

    @Test
    fun rendersCommonBlockMarkdownForReadingMode() {
        val html = MarkdownPreviewRenderer().renderMarkdown(
            title = "blocks.md",
            markdown = """
                > Important note

                1. First
                2. Second

                ---

                | Name | Status |
                | --- | --- |
                | 即阅 | Ready |
            """.trimIndent()
        )

        assertTrue(html.contains("<blockquote><p>Important note</p></blockquote>"))
        assertTrue(html.contains("<ol>"))
        assertTrue(html.contains("<li>First</li>"))
        assertTrue(html.contains("<li>Second</li>"))
        assertTrue(html.contains("<hr>"))
        assertTrue(html.contains("<div class=\"table-scroll\" aria-label=\"表格可横向滚动\"><table>"))
        assertTrue(html.contains("<th style=\"text-align:left\">Name</th>"))
        assertTrue(html.contains("<td style=\"text-align:left\">即阅</td>"))
        assertTrue(html.contains("table-scroll"))
        assertTrue(html.contains("border-collapse: collapse"))
    }

    @Test
    fun rendersTwoLevelListsWithoutFlatteningSubItems() {
        val html = MarkdownPreviewRenderer().renderMarkdown(
            title = "lists.md",
            markdown = """
                - Parent
                  - Child
                - Next
            """.trimIndent()
        )

        assertTrue(html.contains("<li>Parent\n<ul>"))
        assertTrue(html.contains("<li>Child</li>"))
        assertTrue(html.contains("</ul>\n</li>"))
        assertTrue(html.contains("<li>Next</li>"))
    }

    @Test
    fun rendersBlockQuotesAsSeparateParagraphs() {
        val html = MarkdownPreviewRenderer().renderMarkdown(
            title = "quote.md",
            markdown = """
                > First paragraph
                >
                > Second paragraph
            """.trimIndent()
        )

        assertTrue(html.contains("<blockquote>"))
        assertTrue(html.contains("<p>First paragraph</p>"))
        assertTrue(html.contains("<p>Second paragraph</p>"))
        assertFalse(html.contains("First paragraph Second paragraph"))
    }

    @Test
    fun rendersTableAlignmentAndScrollHint() {
        val html = MarkdownPreviewRenderer().renderMarkdown(
            title = "table.md",
            markdown = """
                | Name | Count | Status |
                | :--- | ---: | :---: |
                | 即阅 | 3 | Ready |
            """.trimIndent()
        )

        assertTrue(html.contains("<div class=\"table-scroll\" aria-label=\"表格可横向滚动\">"))
        assertTrue(html.contains("<th style=\"text-align:left\">Name</th>"))
        assertTrue(html.contains("<th style=\"text-align:right\">Count</th>"))
        assertTrue(html.contains("<th style=\"text-align:center\">Status</th>"))
        assertTrue(html.contains("<td style=\"text-align:right\">3</td>"))
        assertTrue(html.contains(".table-scroll::after"))
    }

    @Test
    fun rendersCommonInlineMarkdownAndRejectsUnsafeLinks() {
        val html = MarkdownPreviewRenderer().renderMarkdown(
            title = "inline.md",
            markdown = "Open [site](https://example.com), **bold**, *italic*, and [bad](javascript:alert(1))."
        )

        assertTrue(html.contains("<a href=\"https://example.com\">site</a>"))
        assertTrue(html.contains("<strong>bold</strong>"))
        assertTrue(html.contains("<em>italic</em>"))
        assertTrue(html.contains("[bad](javascript:alert(1))"))
        assertFalse(html.contains("href=\"javascript:alert(1)\""))
    }

    @Test
    fun rendersBackslashEscapesAsLiteralTextWithoutActivatingMarkdownSyntax() {
        val html = MarkdownPreviewRenderer().renderMarkdown(
            title = "escapes.md",
            markdown = """
                \*literal asterisks\* and \[not a link](https://example.com)
                \<tag\> and \\ backslash
            """.trimIndent()
        )

        assertTrue(html.contains("&#42;literal asterisks&#42;"))
        assertTrue(html.contains("&#91;not a link](https://example.com)"))
        assertTrue(html.contains("&#60;tag&#62;"))
        assertTrue(html.contains("&#92; backslash"))
        assertFalse(html.contains("<em>literal asterisks</em>"))
        assertFalse(html.contains("<a href=\"https://example.com\">not a link</a>"))
    }

    @Test
    fun rendersRelativeImagesOnlyWhenDocumentResourcesAreAuthorized() {
        val markdown = "![Local](../assets/pic.png)"

        val standalone = MarkdownPreviewRenderer().renderMarkdown(
            title = "standalone.md",
            markdown = markdown
        )
        val packagePage = MarkdownPreviewRenderer().renderMarkdownResult(
            title = "package.md",
            markdown = markdown,
            includeFloatingOutline = false,
            allowRelativeImageSources = true
        )

        assertTrue(standalone.contains("![Local](../assets/pic.png)"))
        assertFalse(standalone.contains("src=\"../assets/pic.png\""))
        assertTrue(packagePage.html.contains("<img src=\"../assets/pic.png\" alt=\"Local\">"))
        assertFalse(packagePage.requiresAppJavaScript)
    }

    @Test
    fun stillRejectsNetworkAndSchemeImagesWhenRelativeSourcesAreAuthorized() {
        val html = MarkdownPreviewRenderer().renderMarkdownResult(
            title = "unsafe-package.md",
            markdown = "![Remote](https://example.com/pic.png)\n\n![File](file:///sdcard/secret.png)",
            includeFloatingOutline = false,
            allowRelativeImageSources = true
        ).html

        assertTrue(html.contains("![Remote](https://example.com/pic.png)"))
        assertTrue(html.contains("![File](file:///sdcard/secret.png)"))
        assertFalse(html.contains("src=\"https://example.com/pic.png\""))
        assertFalse(html.contains("src=\"file:///sdcard/secret.png\""))
    }

    @Test
    fun rendersSafeAutolinksAndAnchorsButRejectsUnavailableRelativeLinks() {
        val html = MarkdownPreviewRenderer().renderMarkdown(
            title = "links.md",
            markdown = """
                Visit <https://example.com>.
                Jump [to section](#section).
                Open [local note](notes/guide.md).
                Block [bad](file:///sdcard/secret.txt).
            """.trimIndent()
        )

        assertTrue(html.contains("<a href=\"https://example.com\">https://example.com</a>"))
        assertTrue(html.contains("<a href=\"#section\">to section</a>"))
        assertTrue(html.contains("[local note](notes/guide.md)"))
        assertFalse(html.contains("href=\"notes/guide.md\""))
        assertTrue(html.contains("[bad](file:///sdcard/secret.txt)"))
        assertFalse(html.contains("href=\"file:///sdcard/secret.txt\""))
    }

    @Test
    fun rendersImagesTaskListsAndStrikethrough() {
        val embeddedImage = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAQAAAAECAIAAAAmkwkpAAAAGElEQVR4nGNk+M8AB0wMDBiYGJiAIAMAJ5oBAvzJN3kAAAAASUVORK5CYII="
        val html = MarkdownPreviewRenderer().renderMarkdown(
            title = "extras.md",
            markdown = """
                ![Logo]($embeddedImage)

                - [x] Done
                - [ ] Todo

                This is ~~removed~~ text.
            """.trimIndent()
        )

        assertTrue(html.contains("<img src=\"$embeddedImage\" alt=\"Logo\">"))
        assertTrue(html.contains("<li class=\"task-list-item\"><input type=\"checkbox\" disabled checked> Done</li>"))
        assertTrue(html.contains("<li class=\"task-list-item\"><input type=\"checkbox\" disabled> Todo</li>"))
        assertTrue(html.contains("<del>removed</del>"))
        assertTrue(html.contains("img { max-width: 100%"))
        assertTrue(html.contains(".task-list-item"))
    }

    @Test
    fun rendersImagesAsFiguresWithCaptions() {
        val embeddedImage = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAQAAAAECAIAAAAmkwkpAAAAGElEQVR4nGNk+M8AB0wMDBiYGJiAIAMAJ5oBAvzJN3kAAAAASUVORK5CYII="
        val html = MarkdownPreviewRenderer().renderMarkdown(
            title = "image.md",
            markdown = "![Architecture Diagram]($embeddedImage)"
        )

        assertTrue(html.contains("<figure class=\"image-figure\">"))
        assertTrue(html.contains("<img src=\"$embeddedImage\" alt=\"Architecture Diagram\">"))
        assertTrue(html.contains("<figcaption>Architecture Diagram</figcaption>"))
        assertTrue(html.contains(".image-figure"))
    }

    @Test
    fun keepsUnavailableRelativeImageSourcesAsEscapedMarkdownText() {
        val html = MarkdownPreviewRenderer().renderMarkdown(
            title = "relative-image.md",
            markdown = "![Local](assets/pic.png)"
        )

        assertTrue(html.contains("![Local](assets/pic.png)"))
        assertFalse(html.contains("src=\"assets/pic.png\""))
    }

    @Test
    fun keepsNetworkImageSourcesAsEscapedTextWithoutNetworkPermission() {
        val html = MarkdownPreviewRenderer().renderMarkdown(
            title = "network-image.md",
            markdown = "![Remote](https://example.com/pic.png)"
        )

        assertTrue(html.contains("![Remote](https://example.com/pic.png)"))
        assertFalse(html.contains("src=\"https://example.com/pic.png\""))
    }

    @Test
    fun rendersFencedCodeBlocksWithLanguageLabelAndPreservedWhitespace() {
        val html = MarkdownPreviewRenderer().renderMarkdown(
            title = "code.md",
            markdown = """
                ```kotlin
                fun main() {
                    println("Hi")
                }
                ```
            """.trimIndent()
        )

        assertTrue(html.contains("<figure class=\"code-block\">"))
        assertTrue(html.contains("<figcaption class=\"code-language\">kotlin</figcaption>"))
        assertTrue(html.contains("<pre><code class=\"language-kotlin\">fun main()"))
        assertTrue(html.contains("    println(&quot;Hi&quot;)"))
        assertTrue(html.contains(".code-block"))
        assertTrue(html.contains(".code-language"))
    }

    @Test
    fun keepsUnsafeImageSourcesAsEscapedMarkdownText() {
        val html = MarkdownPreviewRenderer().renderMarkdown(
            title = "unsafe-image.md",
            markdown = "![bad](javascript:alert(1))"
        )

        assertTrue(html.contains("![bad](javascript:alert(1))"))
        assertFalse(html.contains("src=\"javascript:alert(1)\""))
    }

    @Test
    fun appliesReadingStyleToMarkdownBody() {
        val html = MarkdownPreviewRenderer().renderMarkdown(
            title = "styled.md",
            markdown = "# Title",
            fontSizeSp = 19,
            lineHeight = 1.9f
        )

        assertTrue(html.contains("font-size: 19px"))
        assertTrue(html.contains("line-height: 1.9"))
    }

    @Test
    fun canRenderGeneratedMarkdownWithDarkReadingTheme() {
        val html = MarkdownPreviewRenderer().renderMarkdown(
            title = "dark.md",
            markdown = "# Title",
            darkTheme = true
        )

        assertTrue(html.contains("background: #111827"))
        assertTrue(html.contains("color: #e5e7eb"))
        assertTrue(html.contains("h1, h2, h3, h4, h5, h6 { color: #f8fafc"))
    }

    @Test
    fun appliesCustomReadingDisplayOptionsToMarkdownCss() {
        val html = MarkdownPreviewRenderer().renderMarkdown(
            title = "custom.md",
            markdown = "# Title\n\nParagraph",
            readingDisplayOptions = ReadingDisplayOptions(
                fontSizeSp = 21,
                letterSpacingEm = 0.04f,
                lineHeight = 2.05f,
                paragraphSpacingEm = 1.08f
            )
        )

        assertTrue(html.contains("font-size: 21px"))
        assertTrue(html.contains("letter-spacing: 0.04em"))
        assertTrue(html.contains("line-height: 2.05"))
        assertTrue(html.contains("p { margin: 1.08em 0; }"))
    }

    private fun assertCancellationAtCheck(markdown: String, cancelAt: Int) {
        val expected = FileTaskCancelledException()
        var checks = 0
        val token = FileTaskCancellationToken {
            checks += 1
            if (checks == cancelAt) throw expected
        }

        val actual = assertThrows(FileTaskCancelledException::class.java) {
            MarkdownPreviewRenderer().renderMarkdown(
                title = "cancel.md",
                markdown = markdown,
                cancellationToken = token
            )
        }

        assertSame(expected, actual)
        assertEquals(cancelAt, checks)
    }
}
