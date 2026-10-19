package com.personalai.instantread.preview

import com.personalai.instantread.importing.FileType
import com.personalai.instantread.importing.ImportedFile
import com.personalai.instantread.PreviewDocumentMode
import com.personalai.instantread.io.FileTaskCancellationToken
import com.personalai.instantread.io.FileTaskCancelledException
import com.personalai.instantread.io.ImportResourcePolicy
import com.personalai.instantread.io.SourceFileTooLargeException

private object UnavailableZipPreviewRenderer : ZipPreviewRenderer {
    override fun render(
        importedFile: ImportedFile,
        options: PreviewOptions,
        cancellationToken: FileTaskCancellationToken
    ): PreviewModel = PreviewModel.Error(
        title = "ZIP 预览暂不可用",
        message = "内部预览器没有完成配置，无法预览 ${importedFile.displayName ?: importedFile.localFile.name}。请返回首页后重新选择文件。"
    )
}

class PreviewController(
    private val htmlRenderer: HtmlPreviewRenderer = HtmlPreviewRenderer(),
    private val markdownRenderer: MarkdownPreviewRenderer = MarkdownPreviewRenderer(),
    private val textRenderer: TextPreviewRenderer = TextPreviewRenderer(),
    private val textDecoder: TextDecoder = LocalTextDecoder(),
    private val zipRenderer: ZipPreviewRenderer = UnavailableZipPreviewRenderer,
    private val resourcePolicy: ImportResourcePolicy = ImportResourcePolicy.DEFAULT
) {
    fun preview(
        importedFile: ImportedFile,
        options: PreviewOptions = PreviewOptions(),
        cancellationToken: FileTaskCancellationToken = FileTaskCancellationToken.NONE,
        onStage: (PreviewPreparationStage) -> Unit = {}
    ): PreviewModel = previewInternal(
        importedFile = importedFile,
        options = options,
        cancellationToken = cancellationToken,
        onStage = onStage,
        deferZipCommit = false
    )

    internal fun prepare(
        importedFile: ImportedFile,
        options: PreviewOptions = PreviewOptions(),
        cancellationToken: FileTaskCancellationToken = FileTaskCancellationToken.NONE,
        onStage: (PreviewPreparationStage) -> Unit = {}
    ): PreviewModel = previewInternal(
        importedFile = importedFile,
        options = options,
        cancellationToken = cancellationToken,
        onStage = onStage,
        deferZipCommit = true
    )

    private fun previewInternal(
        importedFile: ImportedFile,
        options: PreviewOptions,
        cancellationToken: FileTaskCancellationToken,
        onStage: (PreviewPreparationStage) -> Unit,
        deferZipCommit: Boolean
    ): PreviewModel {
        if (!importedFile.localFile.exists()) {
            return PreviewModel.Error(
                title = "文件不可用",
                message = "导入副本已不存在，无法继续预览。请删除这条记录，或重新选择文件导入。"
            )
        }

        val title = importedFile.displayName ?: importedFile.localFile.name
        return when (importedFile.fileType) {
            FileType.HTML -> previewHtml(importedFile, options)
            FileType.MARKDOWN,
            FileType.TEXT -> previewDecodedText(
                importedFile = importedFile,
                title = title,
                options = options,
                cancellationToken = cancellationToken,
                onStage = onStage
            )
            FileType.ZIP -> {
                onStage(PreviewPreparationStage.EXTRACTING_ZIP)
                if (deferZipCommit) {
                    zipRenderer.prepare(importedFile, options, cancellationToken)
                } else {
                    zipRenderer.render(importedFile, options, cancellationToken)
                }
            }
        }
    }

    private fun previewHtml(
        importedFile: ImportedFile,
        options: PreviewOptions
    ): PreviewModel {
        if (importedFile.localFile.length() > resourcePolicy.maxSourceBytes(FileType.HTML)) {
            return PreviewModel.Error(
                title = "HTML 文件过大",
                message = "文件超过 HTML 的 50 MiB 阅读上限。即阅已停止加载，请选择较小的文件。"
            )
        }
        return htmlRenderer.render(importedFile, options)
    }

    private fun previewDecodedText(
        importedFile: ImportedFile,
        title: String,
        options: PreviewOptions,
        cancellationToken: FileTaskCancellationToken,
        onStage: (PreviewPreparationStage) -> Unit
    ): PreviewModel {
        cancellationToken.throwIfCancelled()
        onStage(PreviewPreparationStage.CHECKING_TEXT_ENCODING)
        val decoded = try {
            textDecoder.decode(importedFile.localFile, importedFile.fileType, cancellationToken)
        } catch (cancelled: FileTaskCancelledException) {
            throw cancelled
        } catch (_: SourceFileTooLargeException) {
            return PreviewModel.Error(
                title = "文本文件过大",
                message = "文件超过 Markdown / TXT 的 10 MiB 阅读上限。即阅已停止读取，请选择较小的文件。"
            )
        } catch (_: UnknownTextEncodingException) {
            return PreviewModel.Error(
                title = "无法识别文本编码",
                message = "即阅只支持 UTF-8、带 BOM 的 UTF-16 或 GB18030 文本。请确认文件编码后重新选择文件。"
            )
        } catch (_: NotTextContentException) {
            return PreviewModel.Error(
                title = "文件内容不像文本",
                message = "文件包含二进制数据或过多异常控制字符，无法作为文本阅读。请重新选择 Markdown 或 TXT 文本文件。"
            )
        }

        cancellationToken.throwIfCancelled()
        onStage(PreviewPreparationStage.GENERATING_READING_PAGE)
        val preview = when (importedFile.fileType) {
            FileType.MARKDOWN -> previewMarkdown(title, decoded.text, options, cancellationToken)
            FileType.TEXT -> PreviewModel.WebHtml(
                html = textRenderer.renderText(
                    title = title,
                    text = decoded.text,
                    darkTheme = options.generatedContentDarkTheme,
                    readingDisplayOptions = options.readingDisplayOptions,
                    cancellationToken = cancellationToken
                ),
                title = title,
                javaScriptEnabled = false
            )
            else -> error("Decoded text preview supports only Markdown and text files")
        }
        cancellationToken.throwIfCancelled()
        return preview
    }

    private fun previewMarkdown(
        title: String,
        markdown: String,
        options: PreviewOptions,
        cancellationToken: FileTaskCancellationToken
    ): PreviewModel.WebHtml {
        val renderResult = if (options.markdownDocumentMode == PreviewDocumentMode.READING) {
            markdownRenderer.renderMarkdownResult(
                title = title,
                markdown = markdown,
                darkTheme = options.generatedContentDarkTheme,
                readingDisplayOptions = options.readingDisplayOptions,
                cancellationToken = cancellationToken
            )
        } else {
            MarkdownRenderResult(
                html = textRenderer.renderText(
                    title = title,
                    text = markdown,
                    darkTheme = options.generatedContentDarkTheme,
                    readingDisplayOptions = options.readingDisplayOptions,
                    cancellationToken = cancellationToken
                ),
                requiresAppJavaScript = false
            )
        }
        return PreviewModel.WebHtml(
            html = renderResult.html,
            title = title,
            javaScriptEnabled = renderResult.requiresAppJavaScript
        )
    }
}
