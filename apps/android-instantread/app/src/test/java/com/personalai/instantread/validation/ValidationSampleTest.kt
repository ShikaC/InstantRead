package com.personalai.instantread.validation

import android.graphics.BitmapFactory
import com.personalai.instantread.importing.FileType
import com.personalai.instantread.importing.FileTypeDetector
import com.personalai.instantread.importing.ImportedFile
import com.personalai.instantread.importing.SourceKind
import com.personalai.instantread.preview.PreviewController
import com.personalai.instantread.preview.PreviewModel
import com.personalai.instantread.preview.MarkdownPreviewRenderer
import com.personalai.instantread.preview.ZipWebPackageRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ValidationSampleTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun committedSamplesAreDetectedAsSupportedTypes() {
        val sampleRoot = validationSampleRoot()

        assertEquals(FileType.HTML, FileTypeDetector.detect("sample.html", "text/html"))
        assertEquals(FileType.MARKDOWN, FileTypeDetector.detect("sample.md", "text/markdown"))
        assertEquals(FileType.TEXT, FileTypeDetector.detect("sample.txt", "text/plain"))
        assertTrue(File(sampleRoot, "sample.html").isFile)
        assertTrue(File(sampleRoot, "sample.md").isFile)
        assertTrue(File(sampleRoot, "sample.txt").isFile)
    }

    @Test
    fun committedChineseNamedSampleIsDetectedAndPreviewable() {
        val sample = File(validationSampleRoot(), "中文 文件名 样例.html")

        assertTrue("The Chinese-named fixture should stay in the sample set.", sample.isFile)
        assertEquals(FileType.HTML, FileTypeDetector.detect(sample.name, "text/html"))

        val preview = PreviewController().preview(imported(sample, "committed-cn-sample", FileType.HTML))

        val webFile = preview as PreviewModel.WebFile
        assertTrue(webFile.file.readText(Charsets.UTF_8).contains("中文文件名样例"))
    }

    @Test
    fun committedHtmlSampleUsesCompactReferencePreviewContent() {
        val sample = File(validationSampleRoot(), "sample.html")

        val html = sample.readText(Charsets.UTF_8)

        assertTrue(html.contains("<h1>HTML Sample Page</h1>"))
        assertTrue(html.contains("This is a test page for local HTML preview."))
        assertTrue(html.contains("class=\"note\""))
        assertTrue(html.contains("class=\"external-link\""))
        assertTrue(html.contains("Security Sandbox"))
        assertTrue(html.contains("window.androidInstantReadScriptProbe"))
    }

    @Test
    fun committedMarkdownSampleCoversHeadingLevelsOneToSix() {
        val sample = File(validationSampleRoot(), "sample.md")
        val markdown = sample.readText(Charsets.UTF_8)
        val preview = PreviewController().preview(imported(sample, "committed-md-sample", FileType.MARKDOWN))
        val html = (preview as PreviewModel.WebHtml).html

        (1..6).forEach { level ->
            assertTrue("sample.md should contain level $level heading", markdown.contains("${"#".repeat(level)} Level $level"))
            assertTrue(
                "sample.md should render level $level heading",
                html.contains("<h$level id=\"level-$level\">Level $level</h$level>")
            )
        }
    }

    @Test
    fun sampleMarkdownCoversCompletedReadingExperienceMatrix() {
        val markdown = File(validationSampleRoot(), "sample.md").readText(Charsets.UTF_8)

        assertTrue(markdown.contains("# Markdown 阅读体验样例"))
        assertTrue(markdown.contains("```kotlin"))
        assertTrue(markdown.contains("> 第一段引用"))
        assertTrue(markdown.contains("1. 第一项"))
        assertTrue(markdown.contains("- [x] 已完成任务"))
        assertTrue(markdown.contains("| 名称 | 状态 |"))
        assertTrue(markdown.contains("![嵌入图片](data:image/png;base64,"))
        assertFalse(markdown.contains("assets/sample-image.png"))
        assertTrue(markdown.contains("[章节链接](#markdown-阅读体验样例)"))
    }

    @Test
    fun committedMarkdownCompatibilityCorpusRendersSupportedSyntax() {
        val sample = File(validationSampleRoot(), "markdown-compatibility.md")

        assertTrue("Expected committed markdown-compatibility.md", sample.isFile)

        val markdown = sample.readText(Charsets.UTF_8)
        val html = MarkdownPreviewRenderer().renderMarkdown(title = sample.name, markdown = markdown)

        assertTrue(html.contains("<h3 id=\"三级标题\">三级标题</h3>"))
        assertTrue(html.contains("<strong>粗体</strong>"))
        assertTrue(html.contains("<em>斜体</em>"))
        assertTrue(html.contains("<del>删除线</del>"))
        assertTrue(html.contains("<code>行内代码</code>"))
        assertTrue(html.contains("<blockquote>"))
        assertTrue(html.contains("<ol>"))
        assertTrue(html.contains("<li class=\"task-list-item\"><input type=\"checkbox\" disabled checked> 已完成任务</li>"))
        assertTrue(html.contains("<div class=\"table-scroll\" aria-label=\"表格可横向滚动\"><table>"))
        assertTrue(html.contains("<h3 id=\"细节\">") || html.contains("<a class=\"outline-link"))
        // 未实现语法保持为文本，且不得放行网络图片或相对文档跳转。
        assertFalse(html.contains("src=\"https://example.com/remote.png\""))
        assertFalse(html.contains("href=\"notes/guide.md\""))
    }

    @Test
    fun committedSingleHtmlMissingAssetSampleReferencesUnbundledResources() {
        val sample = File(validationSampleRoot(), "r4-html-missing-asset.html")

        assertTrue("Expected committed r4-html-missing-asset.html", sample.isFile)

        val html = sample.readText(Charsets.UTF_8)
        assertTrue(html.contains("href=\"assets/missing-style.css\""))
        assertTrue(html.contains("src=\"assets/missing-image.png\""))
    }

    @Test
    fun zipFixtureWithRelativeAssetsPreviewsEntryAndKeepsJavaScriptDisabled() {
        val zip = temp.newFile("site.zip")
        zipFile(
            zip,
            "index.html" to """<html><head><link rel="stylesheet" href="assets/style.css"></head><body>ZIP sample</body></html>""",
            "assets/style.css" to "body { color: #123456; }"
        )
        val controller = PreviewController(
            zipRenderer = ZipWebPackageRenderer(temp.newFolder("zip-cache"))
        )

        val preview = controller.preview(imported(zip, "zip-sample", FileType.ZIP))

        val webFile = preview as PreviewModel.WebFile
        assertEquals("index.html", webFile.file.name)
        assertTrue(File(webFile.file.parentFile, "assets/style.css").isFile)
        assertFalse(webFile.javaScriptEnabled)
    }

    @Test
    fun committedZipSampleWithRelativeAssetsPreviewsEntryAndKeepsJavaScriptDisabled() {
        val sampleRoot = validationSampleRoot()
        val zip = File(sampleRoot, "sample-site.zip")
        val controller = PreviewController(
            zipRenderer = ZipWebPackageRenderer(temp.newFolder("zip-cache"))
        )

        assertTrue("Expected committed sample-site.zip", zip.isFile)

        val preview = controller.preview(imported(zip, "committed-zip-sample", FileType.ZIP))

        val webFile = preview as PreviewModel.WebFile
        assertEquals("index.html", webFile.file.name)
        assertTrue(File(webFile.file.parentFile, "assets/style.css").isFile)
        assertTrue(File(webFile.file.parentFile, "assets/pixel.png").isFile)
        assertTrue(File(webFile.file.parentFile, "assets/pixel.png").hasVisibleNonWhitePixel())
        assertFalse(webFile.javaScriptEnabled)
    }

    @Test
    fun committedMarkdownAssetsZipRendersReadingPageWithPackageImage() {
        val sampleRoot = validationSampleRoot()
        val zip = File(sampleRoot, "markdown-assets.zip")
        val controller = PreviewController(
            zipRenderer = ZipWebPackageRenderer(temp.newFolder("zip-cache"))
        )

        assertTrue("Expected committed markdown-assets.zip", zip.isFile)

        val preview = controller.preview(imported(zip, "committed-markdown-zip", FileType.ZIP))

        val webFile = preview as PreviewModel.WebFile
        assertEquals(".instantread-markdown-preview.html", webFile.file.name)
        val html = webFile.file.readText()
        assertTrue(html.contains("<img src=\"assets/purple-square.png\" alt=\"紫色方块\">"))
        assertFalse(html.contains("src=\"https://example.com/remote.png\""))
        assertFalse(webFile.javaScriptEnabled)
        assertFalse(webFile.supportsUserScriptAuthorization)
    }

    @Test
    fun zipFixtureWithoutHtmlReturnsError() {
        val zip = temp.newFile("no-html.zip")
        zipFile(zip, "assets/style.css" to "body {}")
        val controller = PreviewController(
            zipRenderer = ZipWebPackageRenderer(temp.newFolder("zip-cache"))
        )

        val preview = controller.preview(imported(zip, "no-html", FileType.ZIP))

        val error = preview as PreviewModel.Error
        assertEquals("ZIP 中没有可预览的网页入口", error.title)
        assertTrue(error.message.contains("没有找到 index.html、index.htm 或其他 HTML 文件"))
        assertTrue(error.message.contains("重新选择文件"))
    }

    @Test
    fun traversalZipFixtureReturnsUnsafeError() {
        val zip = temp.newFile("unsafe.zip")
        zipFile(zip, "../escape.txt" to "owned", "index.html" to "safe")
        val controller = PreviewController(
            zipRenderer = ZipWebPackageRenderer(temp.newFolder("zip-cache"))
        )

        val preview = controller.preview(imported(zip, "unsafe", FileType.ZIP))

        val error = preview as PreviewModel.Error
        assertEquals("ZIP 文件不安全，已阻止预览", error.title)
        assertTrue(error.message.contains("路径穿越"))
        assertTrue(error.message.contains("不会正常预览"))
    }

    @Test
    fun committedTraversalZipSampleReturnsUnsafeError() {
        val sampleRoot = validationSampleRoot()
        val zip = File(sampleRoot, "unsafe-traversal.zip")
        val controller = PreviewController(
            zipRenderer = ZipWebPackageRenderer(temp.newFolder("zip-cache"))
        )

        assertTrue("Expected committed unsafe-traversal.zip", zip.isFile)

        val preview = controller.preview(imported(zip, "committed-unsafe-zip", FileType.ZIP))

        val error = preview as PreviewModel.Error
        assertEquals("ZIP 文件不安全，已阻止预览", error.title)
        assertTrue(error.message.contains("路径穿越"))
        assertTrue(error.message.contains("不会正常预览"))
    }

    private fun imported(file: File, id: String, fileType: FileType): ImportedFile {
        return ImportedFile(
            id = id,
            displayName = file.name,
            fileType = fileType,
            sourceKind = SourceKind.APP_PICKER,
            localFile = file,
            sizeBytes = file.length()
        )
    }

    private fun zipFile(file: File, vararg entries: Pair<String, String>) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (entryName, content) ->
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    private fun validationSampleRoot(): File {
        return listOf(
            File("../../docs/validation/android-instantread/samples"),
            File("../../../docs/validation/android-instantread/samples")
        )
            .map { it.canonicalFile }
            .first { it.isDirectory }
    }

    private fun File.hasVisibleNonWhitePixel(): Boolean {
        val bitmap = BitmapFactory.decodeFile(path) ?: return false
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                val argb = bitmap.getPixel(x, y)
                val alpha = argb ushr 24
                val red = argb shr 16 and 0xff
                val green = argb shr 8 and 0xff
                val blue = argb and 0xff
                if (alpha > 0 && !(red > 245 && green > 245 && blue > 245)) {
                    return true
                }
            }
        }
        return false
    }
}
