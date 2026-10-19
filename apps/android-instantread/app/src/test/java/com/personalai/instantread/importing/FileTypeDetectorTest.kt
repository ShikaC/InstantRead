package com.personalai.instantread.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileTypeDetectorTest {
    @Test
    fun detectsHtmlByExtensionCaseInsensitively() {
        assertEquals(FileType.HTML, FileTypeDetector.detect("report.HTML", null))
        assertEquals(FileType.HTML, FileTypeDetector.detect("page.htm", "application/octet-stream"))
    }

    @Test
    fun detectsSupportedTypesFromLocalizedMixedCaseNamesBeforeOctetStreamMime() {
        assertEquals(FileType.HTML, FileTypeDetector.detect("阶段 6 Sample.HTML", "application/octet-stream"))
        assertEquals(FileType.MARKDOWN, FileTypeDetector.detect("聊天 导出.MarkDown", "application/octet-stream"))
        assertEquals(FileType.TEXT, FileTypeDetector.detect("浏览器 下载.TXT", "application/octet-stream"))
        assertEquals(FileType.ZIP, FileTypeDetector.detect("网页 包.ZIP", "application/octet-stream"))
    }

    @Test
    fun detectsMarkdownByExtensionBeforePlainTextMime() {
        assertEquals(FileType.MARKDOWN, FileTypeDetector.detect("notes.md", "text/plain"))
        assertEquals(FileType.MARKDOWN, FileTypeDetector.detect("README.markdown", null))
    }

    @Test
    fun detectsTextAndZipByExtension() {
        assertEquals(FileType.TEXT, FileTypeDetector.detect("draft.txt", null))
        assertEquals(FileType.ZIP, FileTypeDetector.detect("site.zip", null))
    }

    @Test
    fun fallsBackToStableMimeTypesWhenNameIsMissing() {
        assertEquals(FileType.HTML, FileTypeDetector.detect(null, "text/html"))
        assertEquals(FileType.MARKDOWN, FileTypeDetector.detect(null, "text/markdown"))
        assertEquals(FileType.TEXT, FileTypeDetector.detect(null, "text/plain"))
        assertEquals(FileType.ZIP, FileTypeDetector.detect(null, "application/zip"))
        assertEquals(FileType.ZIP, FileTypeDetector.detect(null, "application/x-zip"))
        assertEquals(FileType.ZIP, FileTypeDetector.detect(null, "application/x-zip-compressed"))
    }

    @Test
    fun detectsSupportedTypesFromMimeTypesWithParametersWhenNameIsMissing() {
        assertEquals(FileType.HTML, FileTypeDetector.detect(null, "Text/HTML; charset=UTF-8"))
        assertEquals(FileType.MARKDOWN, FileTypeDetector.detect(null, "text/markdown; charset=utf-8"))
        assertEquals(FileType.TEXT, FileTypeDetector.detect(null, "text/plain; charset=gb18030"))
        assertEquals(FileType.ZIP, FileTypeDetector.detect(null, "application/zip; version=2.0"))
    }

    @Test
    fun returnsNullForUnsupportedOrBlankInput() {
        assertNull(FileTypeDetector.detect("photo.png", "image/png"))
        assertNull(FileTypeDetector.detect("archive", "application/octet-stream"))
        assertNull(FileTypeDetector.detect(null, null))
        assertNull(FileTypeDetector.detect("   ", "   "))
    }
}
