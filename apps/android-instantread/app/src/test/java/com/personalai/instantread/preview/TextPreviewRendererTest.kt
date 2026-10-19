package com.personalai.instantread.preview

import com.personalai.instantread.io.FileTaskCancellationToken
import com.personalai.instantread.io.FileTaskCancelledException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPreviewRendererTest {
    @Test
    fun cooperativelyCancelsLongHtmlEscapingWithOriginalException() {
        val expected = FileTaskCancelledException()
        var checks = 0
        val cancelAt = 32
        val token = FileTaskCancellationToken {
            checks += 1
            if (checks == cancelAt) throw expected
        }

        val actual = assertThrows(FileTaskCancelledException::class.java) {
            TextPreviewRenderer().renderText(
                title = "cancel.txt",
                text = "<&\"'>".repeat(32_768),
                cancellationToken = token
            )
        }

        assertSame(expected, actual)
        assertEquals(cancelAt, checks)
    }

    @Test
    fun rendersPlainTextWithEscapingAndLineBreaks() {
        val html = TextPreviewRenderer().renderText(
            title = "draft.txt",
            text = "hello\n<unsafe>"
        )

        assertTrue(html.contains("<title>draft.txt</title>"))
        assertTrue(html.contains("hello\n&lt;unsafe&gt;"))
        assertTrue(html.contains("<pre"))
        assertFalse(html.contains("<unsafe>"))
    }

    @Test
    fun appliesReadingStyleToPlainText() {
        val html = TextPreviewRenderer().renderText(
            title = "draft.txt",
            text = "hello",
            fontSizeSp = 19,
            lineHeight = 1.9f
        )

        assertTrue(html.contains("font: 19px/1.9 sans-serif"))
    }

    @Test
    fun canRenderGeneratedTextWithDarkReadingTheme() {
        val html = TextPreviewRenderer().renderText(
            title = "dark.txt",
            text = "hello",
            darkTheme = true
        )

        assertTrue(html.contains("background: #111827"))
        assertTrue(html.contains("color: #e5e7eb"))
    }

    @Test
    fun appliesCustomReadingDisplayOptionsToPlainTextCss() {
        val html = TextPreviewRenderer().renderText(
            title = "custom.txt",
            text = "hello",
            readingDisplayOptions = ReadingDisplayOptions(
                fontSizeSp = 21,
                letterSpacingEm = 0.04f,
                lineHeight = 2.05f,
                paragraphSpacingEm = 1.08f
            )
        )

        assertTrue(html.contains("font: 21px/2.05 sans-serif"))
        assertTrue(html.contains("letter-spacing: 0.04em"))
    }
}
