package com.personalai.instantread.preview

import com.personalai.instantread.importing.FileType
import com.personalai.instantread.importing.ImportedFile
import com.personalai.instantread.importing.SourceKind
import com.personalai.instantread.PreviewDocumentMode
import com.personalai.instantread.io.FileTaskCancellationToken
import com.personalai.instantread.io.FileTaskCancelledException
import com.personalai.instantread.io.ImportResourcePolicy
import com.personalai.instantread.io.SourceFileTooLargeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.Charset

class PreviewControllerTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun htmlFilesLoadDirectlyFromPrivateFile() {
        val file = temp.newFile("source.html").apply { writeText("<html></html>") }

        val preview = PreviewController().preview(imported(file, FileType.HTML, "report.html"))

        val webFile = preview as PreviewModel.WebFile
        assertEquals(file.canonicalFile, webFile.file)
        assertEquals("report.html", webFile.title)
        assertFalse(webFile.javaScriptEnabled)
        // 单 HTML 的授权根是该副本所在目录，不是整个私有目录。
        assertEquals(file.canonicalFile.parentFile, webFile.resourceRoot)
    }

    @Test
    fun oversizedLegacyHtmlPrivateCopyReturnsStableErrorInsteadOfLoadingWebView() {
        val file = temp.newFile("legacy-too-large.html")
        RandomAccessFile(file, "rw").use {
            it.setLength(ImportResourcePolicy.DEFAULT.htmlMaxBytes + 1L)
        }

        val preview = PreviewController().preview(
            imported(file, FileType.HTML, "legacy-too-large.html")
        ) as PreviewModel.Error

        assertEquals("HTML 文件过大", preview.title)
        assertEquals(
            "文件超过 HTML 的 50 MiB 阅读上限。即阅已停止加载，请选择较小的文件。",
            preview.message
        )
        assertFalse(preview.message.contains(file.absolutePath))
    }

    @Test
    fun htmlPreviewUsesExplicitJavaScriptOption() {
        val file = temp.newFile("page.html").apply { writeText("<script>window.ok = true</script>") }

        val preview = PreviewController().preview(
            imported(file, FileType.HTML, "page.html"),
            PreviewOptions(javaScriptEnabledForHtml = true)
        )

        val webFile = preview as PreviewModel.WebFile
        assertTrue(webFile.javaScriptEnabled)
    }

    @Test
    fun markdownFilesRenderToHtml() {
        val file = temp.newFile("source.md").apply { writeText("# Title") }

        val preview = PreviewController().preview(
            imported(file, FileType.MARKDOWN, "notes.md"),
            PreviewOptions(
                readingDisplayOptions = ReadingDisplayOptions(
                    fontSizeSp = 19,
                    lineHeight = 1.9f
                )
            )
        )

        val webHtml = preview as PreviewModel.WebHtml
        assertEquals("notes.md", webHtml.title)
        assertTrue(webHtml.html.contains("<h1 id=\"title\">Title</h1>"))
        assertTrue(webHtml.html.contains("font-size: 19px"))
        assertTrue(webHtml.html.contains("line-height: 1.9"))
        assertTrue(webHtml.javaScriptEnabled)
    }

    @Test
    fun markdownSingleFileImportDoesNotClaimUnavailableSiblingAssets() {
        val file = temp.newFile("source.md").apply { writeText("![Local](images/pic.png)") }

        val preview = PreviewController().preview(imported(file, FileType.MARKDOWN, "source.md"))

        val webHtml = preview as PreviewModel.WebHtml
        assertTrue(webHtml.html.contains("![Local](images/pic.png)"))
        assertFalse(webHtml.html.contains("src=\"images/pic.png\""))
    }

    @Test
    fun generatedMarkdownReadingPreviewUsesControlledJavaScriptIndependentOfHtmlOption() {
        val file = temp.newFile("note.md").apply { writeText("# Title") }

        val preview = PreviewController().preview(
            imported(file, FileType.MARKDOWN, "notes.md"),
            PreviewOptions(javaScriptEnabledForHtml = true)
        )

        val webHtml = preview as PreviewModel.WebHtml
        assertTrue(webHtml.javaScriptEnabled)
        assertTrue(webHtml.html.contains("function updateCurrentOutline()"))
    }

    @Test
    fun markdownBodyCannotEnableJavaScriptByImitatingOutlineMarker() {
        val file = temp.newFile("marker.md").apply {
            writeText("data-instantread-markdown-outline-script")
        }

        val preview = PreviewController().preview(
            imported(file, FileType.MARKDOWN, "marker.md")
        )

        val webHtml = preview as PreviewModel.WebHtml
        assertFalse(webHtml.javaScriptEnabled)
        assertFalse(webHtml.html.contains("<script data-instantread-markdown-outline-script>"))
    }

    @Test
    fun markdownReviewModeShowsRawMarkdownSourceInsteadOfRenderedHeadings() {
        val file = temp.newFile("review.md").apply {
            writeText("# Title\n\n- item\n\n`inline`")
        }

        val preview = PreviewController().preview(
            imported(file, FileType.MARKDOWN, "review.md"),
            PreviewOptions(markdownDocumentMode = PreviewDocumentMode.REVIEW)
        )

        val webHtml = preview as PreviewModel.WebHtml
        assertTrue(webHtml.html.contains("# Title"))
        assertTrue(webHtml.html.contains("- item"))
        assertTrue(webHtml.html.contains("`inline`"))
        assertFalse(webHtml.html.contains("<h1>Title</h1>"))
        assertFalse(webHtml.javaScriptEnabled)
    }

    @Test
    fun textFilesRenderToHtml() {
        val file = temp.newFile("source.txt").apply { writeText("hello") }

        val preview = PreviewController().preview(imported(file, FileType.TEXT, "draft.txt"))

        val webHtml = preview as PreviewModel.WebHtml
        assertEquals("draft.txt", webHtml.title)
        assertTrue(webHtml.html.contains("hello"))
        assertTrue(webHtml.html.contains("font: 17px/1.75 sans-serif"))
        assertFalse(webHtml.javaScriptEnabled)
    }

    @Test
    fun markdownReadingAndReviewModesUseInjectedDecodedTextOncePerPreview() {
        val calls = mutableListOf<FileType>()
        val decoder = object : TextDecoder {
            override fun decode(
                file: File,
                fileType: FileType,
                cancellationToken: FileTaskCancellationToken
            ): DecodedText {
                calls += fileType
                return DecodedText("# 已解码标题", LocalTextEncoding.GB18030)
            }
        }
        val file = temp.newFile("encoded.md").apply { writeBytes(byteArrayOf(1)) }
        val controller = PreviewController(textDecoder = decoder)

        val reading = controller.preview(
            imported(file, FileType.MARKDOWN, "encoded.md")
        ) as PreviewModel.WebHtml
        val review = controller.preview(
            imported(file, FileType.MARKDOWN, "encoded.md"),
            PreviewOptions(markdownDocumentMode = PreviewDocumentMode.REVIEW)
        ) as PreviewModel.WebHtml

        assertTrue(reading.html.contains("已解码标题"))
        assertTrue(review.html.contains("# 已解码标题"))
        assertEquals(listOf(FileType.MARKDOWN, FileType.MARKDOWN), calls)
    }

    @Test
    fun textUsesInjectedDecodedTextOnceAndReceivesTheSameCancellationToken() {
        val expectedToken = FileTaskCancellationToken { }
        var actualToken: FileTaskCancellationToken? = null
        var decodeCalls = 0
        val decoder = object : TextDecoder {
            override fun decode(
                file: File,
                fileType: FileType,
                cancellationToken: FileTaskCancellationToken
            ): DecodedText {
                decodeCalls += 1
                actualToken = cancellationToken
                return DecodedText("已解码正文", LocalTextEncoding.UTF16_LE)
            }
        }
        val file = temp.newFile("encoded.txt").apply { writeBytes(byteArrayOf(1)) }

        val preview = PreviewController(textDecoder = decoder).preview(
            imported(file, FileType.TEXT, "encoded.txt"),
            cancellationToken = expectedToken
        ) as PreviewModel.WebHtml

        assertTrue(preview.html.contains("已解码正文"))
        assertEquals(1, decodeCalls)
        assertSame(expectedToken, actualToken)
    }

    @Test
    fun cancellationAfterDecodeStopsBeforeGenerationStageAndRendering() {
        val expected = FileTaskCancelledException()
        var cancelled = false
        val token = FileTaskCancellationToken {
            if (cancelled) throw expected
        }
        val decoder = object : TextDecoder {
            override fun decode(
                file: File,
                fileType: FileType,
                cancellationToken: FileTaskCancellationToken
            ): DecodedText {
                cancelled = true
                return DecodedText("must not render", LocalTextEncoding.UTF8)
            }
        }
        val stages = mutableListOf<PreviewPreparationStage>()

        val actual = assertThrows(FileTaskCancelledException::class.java) {
            PreviewController(textDecoder = decoder).preview(
                imported(temp.newFile("cancel-after-decode.txt"), FileType.TEXT, "cancel.txt"),
                cancellationToken = token,
                onStage = stages::add
            )
        }

        assertSame(expected, actual)
        assertEquals(listOf(PreviewPreparationStage.CHECKING_TEXT_ENCODING), stages)
    }

    @Test
    fun defaultDecoderRendersRealGb18030Text() {
        val source = "简体中文报告"
        val file = temp.newFile("gb18030.txt").apply {
            writeBytes(source.toByteArray(Charset.forName("GB18030")))
        }

        val preview = PreviewController().preview(
            imported(file, FileType.TEXT, "gb18030.txt")
        ) as PreviewModel.WebHtml

        assertTrue(preview.html.contains(source))
    }

    @Test
    fun markdownAndTextReportEncodingThenReadingPageStages() {
        val markdown = temp.newFile("stages.md").apply { writeText("# Title") }
        val text = temp.newFile("stages.txt").apply { writeText("body") }
        val controller = PreviewController()
        val markdownStages = mutableListOf<PreviewPreparationStage>()
        val textStages = mutableListOf<PreviewPreparationStage>()

        controller.preview(
            imported(markdown, FileType.MARKDOWN, "stages.md"),
            onStage = markdownStages::add
        )
        controller.preview(
            imported(text, FileType.TEXT, "stages.txt"),
            onStage = textStages::add
        )

        val expected = listOf(
            PreviewPreparationStage.CHECKING_TEXT_ENCODING,
            PreviewPreparationStage.GENERATING_READING_PAGE
        )
        assertEquals(expected, markdownStages)
        assertEquals(expected, textStages)
    }

    @Test
    fun htmlAndZipDoNotInvokeTextDecoder() {
        val htmlFile = temp.newFile("direct.html").apply { writeText("<html></html>") }
        val zipFile = temp.newFile("direct.zip").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val htmlStages = mutableListOf<PreviewPreparationStage>()
        var decodeCalls = 0
        val decoder = object : TextDecoder {
            override fun decode(
                file: File,
                fileType: FileType,
                cancellationToken: FileTaskCancellationToken
            ): DecodedText {
                decodeCalls += 1
                return DecodedText("unexpected", LocalTextEncoding.UTF8)
            }
        }
        val controller = PreviewController(
            textDecoder = decoder,
            zipRenderer = object : ZipPreviewRenderer {
                override fun render(
                    importedFile: ImportedFile,
                    options: PreviewOptions,
                    cancellationToken: FileTaskCancellationToken
                ): PreviewModel = PreviewModel.WebFile(
                    importedFile.localFile,
                    "zip",
                    javaScriptEnabled = false
                )
            }
        )

        controller.preview(
            imported(htmlFile, FileType.HTML, "direct.html"),
            onStage = htmlStages::add
        )
        controller.preview(imported(zipFile, FileType.ZIP, "direct.zip"))

        assertEquals(0, decodeCalls)
        assertTrue(htmlStages.isEmpty())
    }

    @Test
    fun sourceFileTooLargeReturnsStableRecoverableChineseError() {
        val file = temp.newFile("too-large.txt")
        val controller = PreviewController(textDecoder = throwingDecoder {
            throw SourceFileTooLargeException(actualBytes = 12_345_678L, limitBytes = 10L)
        })

        val preview = controller.preview(
            imported(file, FileType.TEXT, "too-large.txt")
        ) as PreviewModel.Error

        assertEquals("文本文件过大", preview.title)
        assertEquals(
            "文件超过 Markdown / TXT 的 10 MiB 阅读上限。即阅已停止读取，请选择较小的文件。",
            preview.message
        )
        assertFalse(preview.message.contains(file.absolutePath))
        assertFalse(preview.message.contains("12345678"))
    }

    @Test
    fun unknownEncodingReturnsStableRecoverableChineseError() {
        val file = temp.newFile("unknown.txt")
        val controller = PreviewController(textDecoder = throwingDecoder {
            throw UnknownTextEncodingException()
        })

        val preview = controller.preview(
            imported(file, FileType.TEXT, "unknown.txt")
        ) as PreviewModel.Error

        assertEquals("无法识别文本编码", preview.title)
        assertEquals(
            "即阅只支持 UTF-8、带 BOM 的 UTF-16 或 GB18030 文本。请确认文件编码后重新选择文件。",
            preview.message
        )
        assertFalse(preview.message.contains(file.absolutePath))
        assertFalse(preview.message.contains("Unknown text encoding"))
    }

    @Test
    fun nonTextContentReturnsStableRecoverableChineseError() {
        val file = temp.newFile("binary.txt")
        val controller = PreviewController(textDecoder = throwingDecoder {
            throw NotTextContentException()
        })

        val preview = controller.preview(
            imported(file, FileType.TEXT, "binary.txt")
        ) as PreviewModel.Error

        assertEquals("文件内容不像文本", preview.title)
        assertEquals(
            "文件包含二进制数据或过多异常控制字符，无法作为文本阅读。请重新选择 Markdown 或 TXT 文本文件。",
            preview.message
        )
        assertFalse(preview.message.contains(file.absolutePath))
        assertFalse(preview.message.contains("Decoded content is not plausible text"))
    }

    @Test
    fun cancellationFromTextDecoderIsRethrownUnchanged() {
        val expected = FileTaskCancelledException()
        val controller = PreviewController(textDecoder = throwingDecoder { throw expected })

        val actual = assertThrows(FileTaskCancelledException::class.java) {
            controller.preview(
                imported(temp.newFile("cancel.txt"), FileType.TEXT, "cancel.txt")
            )
        }

        assertSame(expected, actual)
    }

    @Test
    fun genericDecoderIoFailureStillPropagatesUnchanged() {
        val expected = IOException("decoder unavailable")
        val controller = PreviewController(textDecoder = throwingDecoder { throw expected })

        val actual = assertThrows(IOException::class.java) {
            controller.preview(
                imported(temp.newFile("io.txt"), FileType.TEXT, "io.txt")
            )
        }

        assertSame(expected, actual)
    }

    @Test
    fun markdownAndTextUseGeneratedDarkThemeWithoutChangingHtmlOrZip() {
        val markdownFile = temp.newFile("dark.md").apply { writeText("# Title") }
        val textFile = temp.newFile("dark.txt").apply { writeText("hello") }
        val htmlFile = temp.newFile("dark.html").apply { writeText("<html><body>light</body></html>") }
        val zipFile = temp.newFile("dark.zip").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val controller = PreviewController(
            zipRenderer = object : ZipPreviewRenderer {
                override fun render(
                    importedFile: ImportedFile,
                    options: PreviewOptions,
                    cancellationToken: FileTaskCancellationToken
                ): PreviewModel {
                    return PreviewModel.WebFile(importedFile.localFile, "zip", javaScriptEnabled = false)
                }
            }
        )

        val markdown = controller.preview(
            imported(markdownFile, FileType.MARKDOWN, "dark.md"),
            PreviewOptions(generatedContentDarkTheme = true)
        ) as PreviewModel.WebHtml
        val text = controller.preview(
            imported(textFile, FileType.TEXT, "dark.txt"),
            PreviewOptions(generatedContentDarkTheme = true)
        ) as PreviewModel.WebHtml
        val html = controller.preview(
            imported(htmlFile, FileType.HTML, "dark.html"),
            PreviewOptions(generatedContentDarkTheme = true)
        ) as PreviewModel.WebFile
        val zip = controller.preview(
            imported(zipFile, FileType.ZIP, "dark.zip"),
            PreviewOptions(generatedContentDarkTheme = true)
        ) as PreviewModel.WebFile

        assertTrue(markdown.html.contains("background: #111827"))
        assertTrue(text.html.contains("background: #111827"))
        assertEquals(htmlFile.canonicalFile, html.file)
        assertEquals(zipFile, zip.file)
        assertTrue(markdown.javaScriptEnabled)
    }

    @Test
    fun markdownAndTextUseReadingDisplayOptionsWithoutChangingHtmlOrZip() {
        val markdownFile = temp.newFile("reading.md").apply { writeText("# Title") }
        val textFile = temp.newFile("reading.txt").apply { writeText("hello") }
        val htmlFile = temp.newFile("reading.html").apply { writeText("<html><body>light</body></html>") }
        val zipFile = temp.newFile("reading.zip").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val options = PreviewOptions(
            readingDisplayOptions = ReadingDisplayOptions(
                fontSizeSp = 21,
                letterSpacingEm = 0.04f,
                lineHeight = 2.05f,
                paragraphSpacingEm = 1.08f
            )
        )
        val controller = PreviewController(
            zipRenderer = object : ZipPreviewRenderer {
                override fun render(
                    importedFile: ImportedFile,
                    options: PreviewOptions,
                    cancellationToken: FileTaskCancellationToken
                ): PreviewModel {
                    return PreviewModel.WebFile(importedFile.localFile, "zip", javaScriptEnabled = false)
                }
            }
        )

        val markdown = controller.preview(imported(markdownFile, FileType.MARKDOWN, "reading.md"), options) as PreviewModel.WebHtml
        val text = controller.preview(imported(textFile, FileType.TEXT, "reading.txt"), options) as PreviewModel.WebHtml
        val html = controller.preview(imported(htmlFile, FileType.HTML, "reading.html"), options) as PreviewModel.WebFile
        val zip = controller.preview(imported(zipFile, FileType.ZIP, "reading.zip"), options) as PreviewModel.WebFile

        assertTrue(markdown.html.contains("font-size: 21px"))
        assertTrue(markdown.html.contains("letter-spacing: 0.04em"))
        assertTrue(markdown.html.contains("p { margin: 1.08em 0; }"))
        assertTrue(text.html.contains("font: 21px/2.05 sans-serif"))
        assertTrue(text.html.contains("letter-spacing: 0.04em"))
        assertEquals(htmlFile.canonicalFile, html.file)
        assertEquals(zipFile, zip.file)
    }

    @Test
    fun zipFilesDelegateToZipRenderer() {
        val file = temp.newFile("source.zip").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val controller = PreviewController(
            zipRenderer = object : ZipPreviewRenderer {
                override fun render(
                    importedFile: ImportedFile,
                    options: PreviewOptions,
                    cancellationToken: FileTaskCancellationToken
                ): PreviewModel {
                    return PreviewModel.WebFile(
                        importedFile.localFile,
                        "zip",
                        javaScriptEnabled = options.javaScriptEnabledForHtml
                    )
                }
            }
        )

        val preview = controller.preview(
            imported(file, FileType.ZIP, "site.zip"),
            PreviewOptions(javaScriptEnabledForHtml = true)
        )

        val webFile = preview as PreviewModel.WebFile
        assertEquals(file, webFile.file)
        assertTrue(webFile.javaScriptEnabled)
    }

    @Test
    fun zipFilesReceiveTheSameCancellationTokenInstance() {
        val file = temp.newFile("cancel.zip").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val expectedToken = FileTaskCancellationToken { }
        var actualToken: FileTaskCancellationToken? = null
        val stages = mutableListOf<PreviewPreparationStage>()
        val controller = PreviewController(
            zipRenderer = object : ZipPreviewRenderer {
                override fun render(
                    importedFile: ImportedFile,
                    options: PreviewOptions,
                    cancellationToken: FileTaskCancellationToken
                ): PreviewModel {
                    actualToken = cancellationToken
                    return PreviewModel.WebFile(importedFile.localFile, "zip", javaScriptEnabled = false)
                }
            }
        )

        controller.preview(
            imported(file, FileType.ZIP, "cancel.zip"),
            cancellationToken = expectedToken,
            onStage = stages::add
        )

        assertSame(expectedToken, actualToken)
        assertEquals(listOf(PreviewPreparationStage.EXTRACTING_ZIP), stages)
    }

    @Test
    fun missingImportedFileReturnsError() {
        val missing = File(temp.root, "missing.html")
        val stages = mutableListOf<PreviewPreparationStage>()

        val preview = PreviewController().preview(
            imported(missing, FileType.HTML, "missing.html"),
            onStage = stages::add
        )

        val error = preview as PreviewModel.Error
        assertEquals("文件不可用", error.title)
        assertTrue(error.message.contains("导入副本"))
        assertTrue(error.message.contains("删除这条记录"))
        assertTrue(error.message.contains("重新选择文件"))
        assertTrue(stages.isEmpty())
    }

    @Test
    fun defaultZipRendererUnavailableErrorExplainsRecovery() {
        val file = temp.newFile("source.zip").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val preview = PreviewController().preview(imported(file, FileType.ZIP, "site.zip"))

        val error = preview as PreviewModel.Error
        assertEquals("ZIP 预览暂不可用", error.title)
        assertTrue(error.message.contains("内部预览器没有完成配置"))
        assertTrue(error.message.contains("请返回首页"))
        assertTrue(error.message.contains("重新选择文件"))
    }

    private fun imported(file: File, fileType: FileType, displayName: String): ImportedFile {
        return ImportedFile(
            id = "id",
            displayName = displayName,
            fileType = fileType,
            sourceKind = SourceKind.APP_PICKER,
            localFile = file,
            sizeBytes = if (file.exists()) file.length() else 0L
        )
    }

    private fun throwingDecoder(failure: () -> Nothing): TextDecoder = object : TextDecoder {
        override fun decode(
            file: File,
            fileType: FileType,
            cancellationToken: FileTaskCancellationToken
        ): DecodedText = failure()
    }
}
