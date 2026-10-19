package com.personalai.instantread.preview

import com.personalai.instantread.io.FileTaskCancellationToken

class TextPreviewRenderer {
    fun renderText(
        title: String,
        text: String,
        fontSizeSp: Int = 17,
        lineHeight: Float = 1.75f,
        darkTheme: Boolean = false,
        readingDisplayOptions: ReadingDisplayOptions = ReadingDisplayOptions(
            fontSizeSp = fontSizeSp,
            lineHeight = lineHeight
        ),
        cancellationToken: FileTaskCancellationToken = FileTaskCancellationToken.NONE
    ): String {
        cancellationToken.throwIfCancelled()
        val displayOptions = readingDisplayOptions.clamped()
        val background = if (darkTheme) "#111827" else "#ffffff"
        val textColor = if (darkTheme) "#e5e7eb" else "#202124"
        val html = """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>${escapeHtml(title, cancellationToken)}</title>
              <style>
                body { margin: 0; color: $textColor; background: $background; }
                pre { box-sizing: border-box; width: 100%; min-height: 100vh; margin: 0; padding: 20px; white-space: pre-wrap; word-wrap: break-word; font: ${displayOptions.fontSizeSp}px/${cssNumber(displayOptions.lineHeight)} sans-serif; letter-spacing: ${cssNumber(displayOptions.letterSpacingEm)}em; }
              </style>
            </head>
            <body>
            <pre>${escapeHtml(text, cancellationToken)}</pre>
            </body>
            </html>
        """.trimIndent()
        cancellationToken.throwIfCancelled()
        return html
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

    private fun cssNumber(value: Float): String {
        return java.lang.Float.toString(value)
    }

    private companion object {
        const val ESCAPE_CHECK_INTERVAL = 256
    }
}
