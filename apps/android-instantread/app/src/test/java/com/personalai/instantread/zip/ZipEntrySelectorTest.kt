package com.personalai.instantread.zip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZipEntrySelectorTest {
    @Test
    fun prefersRootIndexHtml() {
        val entries = listOf("docs/page.html", "index.htm", "index.html")
        assertEquals(
            ZipPreviewEntry("index.html", ZipPreviewEntryType.HTML),
            ZipEntrySelector.selectEntry(entries)
        )
    }

    @Test
    fun prefersRootIndexHtmWhenRootIndexHtmlIsMissing() {
        val entries = listOf("docs/index.html", "index.htm", "about.html")
        assertEquals(
            ZipPreviewEntry("index.htm", ZipPreviewEntryType.HTML),
            ZipEntrySelector.selectEntry(entries)
        )
    }

    @Test
    fun choosesShortestNestedIndexPathBeforeOtherHtml() {
        val entries = listOf("very/deep/site/index.html", "about.html", "site/index.htm", "a/index.html")
        assertEquals(
            ZipPreviewEntry("a/index.html", ZipPreviewEntryType.HTML),
            ZipEntrySelector.selectEntry(entries)
        )
    }

    @Test
    fun fallsBackToFirstHtmlInZipOrder() {
        val entries = listOf("assets/style.css", "pages/intro.htm", "pages/about.html")
        assertEquals(
            ZipPreviewEntry("pages/intro.htm", ZipPreviewEntryType.HTML),
            ZipEntrySelector.selectEntry(entries)
        )
    }

    @Test
    fun normalizesBackslashesButReturnsNormalizedPath() {
        val entries = listOf("site\\index.html")
        assertEquals(
            ZipPreviewEntry("site/index.html", ZipPreviewEntryType.HTML),
            ZipEntrySelector.selectEntry(entries)
        )
    }

    @Test
    fun ignoresDirectoriesAndDangerousPaths() {
        val entries = listOf("index.html/", "../index.html", "/absolute/index.html", "safe/page.html")
        assertEquals(
            ZipPreviewEntry("safe/page.html", ZipPreviewEntryType.HTML),
            ZipEntrySelector.selectEntry(entries)
        )
    }

    @Test
    fun prefersHtmlOverMarkdownWhenBothExist() {
        val entries = listOf("index.md", "index.html")
        assertEquals(
            ZipPreviewEntry("index.html", ZipPreviewEntryType.HTML),
            ZipEntrySelector.selectEntry(entries)
        )
    }

    @Test
    fun fallsBackToRootIndexMarkdownWhenNoHtmlExists() {
        val entries = listOf("docs/index.md", "index.markdown", "index.md")
        assertEquals(
            ZipPreviewEntry("index.md", ZipPreviewEntryType.MARKDOWN),
            ZipEntrySelector.selectEntry(entries)
        )
    }

    @Test
    fun fallsBackToRootIndexMarkdownExtensionAlias() {
        val entries = listOf("notes/readme.md", "index.markdown")
        assertEquals(
            ZipPreviewEntry("index.markdown", ZipPreviewEntryType.MARKDOWN),
            ZipEntrySelector.selectEntry(entries)
        )
    }

    @Test
    fun choosesShortestNestedMarkdownIndexBeforeOtherMarkdown() {
        val entries = listOf("deep/site/index.md", "other.md", "docs/index.markdown")
        assertEquals(
            ZipPreviewEntry("deep/site/index.md", ZipPreviewEntryType.MARKDOWN),
            ZipEntrySelector.selectEntry(entries)
        )
    }

    @Test
    fun fallsBackToFirstMarkdownInZipOrder() {
        val entries = listOf("assets/style.css", "guide/intro.md", "guide/about.markdown")
        assertEquals(
            ZipPreviewEntry("guide/intro.md", ZipPreviewEntryType.MARKDOWN),
            ZipEntrySelector.selectEntry(entries)
        )
    }

    @Test
    fun returnsNullWhenNoHtmlOrMarkdownEntryExists() {
        assertNull(ZipEntrySelector.selectEntry(listOf("assets/style.css", "README.txt")))
        assertNull(ZipEntrySelector.selectEntry(emptyList()))
    }
}
