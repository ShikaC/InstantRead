package com.personalai.instantread.preview

import com.personalai.instantread.importing.ImportedFile
import com.personalai.instantread.importing.FileType
import com.personalai.instantread.io.FileTaskCancellationToken
import com.personalai.instantread.io.FileTaskCancelledException
import com.personalai.instantread.io.BackgroundFileDeletionQueue
import com.personalai.instantread.io.ImportResourcePolicy
import com.personalai.instantread.io.SourceFileTooLargeException
import com.personalai.instantread.io.TooManyZipEntriesException
import com.personalai.instantread.io.ZipEntryTooLargeException
import com.personalai.instantread.io.ZipExpandedTooLargeException
import com.personalai.instantread.io.ZipPreviewCacheCoordinator
import com.personalai.instantread.zip.SafeZipExtractor
import com.personalai.instantread.zip.ZipEntrySelector
import com.personalai.instantread.zip.ZipPreviewEntry
import com.personalai.instantread.zip.ZipPreviewEntryType
import com.personalai.instantread.zip.ZipSlipException
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID

class ZipWebPackageRenderer(
    private val cacheRoot: File,
    private val resourcePolicy: ImportResourcePolicy = ImportResourcePolicy.DEFAULT,
    private val textDecoder: TextDecoder = LocalTextDecoder(resourcePolicy),
    private val markdownRenderer: MarkdownPreviewRenderer = MarkdownPreviewRenderer(),
    private val stagingIdGenerator: () -> String = { UUID.randomUUID().toString() },
    private val renameDirectory: (File, File) -> Boolean = { source, target ->
        source.renameTo(target)
    },
    private val canonicalizeFile: (File) -> File = { file -> file.canonicalFile },
    private val deleteDirectory: (File) -> Boolean = { directory -> directory.deleteRecursively() },
    private val scheduleDeletion: (File) -> Unit = BackgroundFileDeletionQueue::schedule,
    private val scheduleFinalization: (() -> Unit) -> Unit = BackgroundFileDeletionQueue::execute
) : ZipPreviewRenderer {
    override fun render(
        importedFile: ImportedFile,
        options: PreviewOptions,
        cancellationToken: FileTaskCancellationToken
    ): PreviewModel = prepareInternal(
        importedFile,
        options,
        cancellationToken,
        deferRecursiveFinalization = false
    ).also { preview ->
        preview.publishPreparedResources()
    }

    override fun prepare(
        importedFile: ImportedFile,
        options: PreviewOptions,
        cancellationToken: FileTaskCancellationToken
    ): PreviewModel = prepareInternal(
        importedFile,
        options,
        cancellationToken,
        deferRecursiveFinalization = true
    )

    private fun prepareInternal(
        importedFile: ImportedFile,
        options: PreviewOptions,
        cancellationToken: FileTaskCancellationToken,
        deferRecursiveFinalization: Boolean
    ): PreviewModel {
        cancellationToken.throwIfCancelled()
        ZipPreviewCacheCoordinator.awaitPendingFinalization(cacheRoot, importedFile.id)
        cancellationToken.throwIfCancelled()
        val targetDirectory = File(cacheRoot, importedFile.id)
        val cacheLease = ZipPreviewCacheCoordinator.lease(cacheRoot, importedFile.id)
        ensureActiveOrDetach(importedFile.id, cacheLease)
        try {
            prepareTransactionPaths(importedFile.id, targetDirectory)
        } catch (error: Exception) {
            return errorForZipFailure(error)
        }
        ensureActiveOrDetach(importedFile.id, cacheLease)

        val sourceBytes = importedFile.localFile.length()
        if (sourceBytes > resourcePolicy.zipSourceMaxBytes) {
            return errorForZipFailure(
                SourceFileTooLargeException(
                    actualBytes = sourceBytes,
                    limitBytes = resourcePolicy.zipSourceMaxBytes
                )
            )
        }

        val runId = stagingIdGenerator()
        val stagingDirectory = File(cacheRoot, "${importedFile.id}.partial-$runId")
        val backupDirectory = File(cacheRoot, "${importedFile.id}.previous-$runId")

        return try {
            SafeZipExtractor.extract(
                zipFile = importedFile.localFile,
                targetDirectory = stagingDirectory,
                policy = resourcePolicy,
                cancellationToken = cancellationToken
            )
            val entryNames = stagingDirectory
                .walkTopDown()
                .filter { it.isFile }
                .map { it.relativeTo(stagingDirectory).invariantSeparatorsPath }
                .toList()
            val entry = ZipEntrySelector.selectEntry(entryNames)
                ?: return PreviewModel.Error(
                    title = "ZIP 中没有可预览的网页入口",
                    message = "没有找到 index.html、index.htm 或其他 HTML 文件，也没有找到 index.md 等 Markdown 文件。请确认这是网页包 ZIP，或重新选择文件。"
                )
            val renderedEntry = when (entry.type) {
                ZipPreviewEntryType.HTML -> entry.path
                ZipPreviewEntryType.MARKDOWN -> renderMarkdownEntry(
                    stagingDirectory = stagingDirectory,
                    entry = entry,
                    options = options,
                    cancellationToken = cancellationToken
                )
            }
            val officialEntry = canonicalizeFile(File(targetDirectory, renderedEntry))

            cancellationToken.throwIfCancelled()
            val hadTarget = ZipPreviewCacheCoordinator.withActiveCache(cacheLease) {
                val targetExisted = targetDirectory.exists()
                promote(
                    staging = stagingDirectory,
                    target = targetDirectory,
                    backup = backupDirectory,
                    hadTarget = targetExisted
                )
                targetExisted
            }
            val preview = PreviewModel.WebFile(
                file = officialEntry,
                title = importedFile.displayName ?: importedFile.localFile.name,
                // Markdown 包渲染成 App 生成的阅读页，不执行任何脚本，也不提供脚本授权入口。
                javaScriptEnabled = entry.type == ZipPreviewEntryType.HTML &&
                    options.javaScriptEnabledForHtml,
                // 入口可能位于子目录，授权根取本次解包目录，包内相对资源可正常解析。
                resourceRoot = targetDirectory,
                supportsUserScriptAuthorization = entry.type == ZipPreviewEntryType.HTML
            ).attachPreparedResources(
                PreparedPreviewResources(
                    onPublish = {
                        if (deferRecursiveFinalization) {
                            publishDeferred(
                                importedFile.id,
                                targetDirectory,
                                backupDirectory,
                                runId,
                                cacheLease
                            )
                        } else {
                            deleteBestEffort(backupDirectory)
                        }
                    },
                    onDiscard = {
                        if (deferRecursiveFinalization) {
                            discardDeferred(
                                importedFile.id,
                                targetDirectory,
                                backupDirectory,
                                runId,
                                hadTarget,
                                cacheLease
                            )
                        } else if (hadTarget) {
                            restoreBackup(targetDirectory, backupDirectory)
                        } else {
                            deleteBestEffort(targetDirectory)
                        }
                    }
                )
            )
            try {
                cancellationToken.throwIfCancelled()
            } catch (cancelled: FileTaskCancelledException) {
                preview.discardPreparedResources()
                throw cancelled
            }
            preview
        } catch (error: FileTaskCancelledException) {
            throw error
        } catch (error: ZipSlipException) {
            unsafeZipError()
        } catch (error: MarkdownEntryPreviewException) {
            error.preview
        } catch (error: Exception) {
            errorForZipFailure(error)
        } finally {
            deleteBestEffort(stagingDirectory)
        }
    }

    /**
     * 把 ZIP 内的 Markdown 入口渲染成与入口同目录的阅读页。
     *
     * 生成文件放在入口所在目录，`assets/pic.png` 这类相对图片仍指向包内同一目录，预览时
     * 会经过授权根校验；入口解码失败时返回明确的中文错误，不泄漏路径，也不放行任何脚本。
     */
    private fun renderMarkdownEntry(
        stagingDirectory: File,
        entry: ZipPreviewEntry,
        options: PreviewOptions,
        cancellationToken: FileTaskCancellationToken
    ): String {
        val entryFile = File(stagingDirectory, entry.path)
        val decoded = try {
            textDecoder.decode(entryFile, FileType.MARKDOWN, cancellationToken)
        } catch (cancelled: FileTaskCancelledException) {
            throw cancelled
        } catch (error: SourceFileTooLargeException) {
            throw MarkdownEntryPreviewException(
                PreviewModel.Error(
                    title = "Markdown 文件过大，已停止预览",
                    message = "ZIP 内的 Markdown 超过 ${error.limitBytes.toBinaryMiBLabel()} 阅读上限。请精简文档后重试。"
                )
            )
        } catch (error: UnknownTextEncodingException) {
            throw MarkdownEntryPreviewException(
                PreviewModel.Error(
                    title = "无法识别 Markdown 编码",
                    message = "ZIP 内的 Markdown 不是 UTF-8、UTF-16 或 GB18030 文本。请转换编码后重新打包。"
                )
            )
        } catch (error: NotTextContentException) {
            throw MarkdownEntryPreviewException(
                PreviewModel.Error(
                    title = "Markdown 内容不像文本",
                    message = "ZIP 内的 Markdown 包含二进制数据或过多异常控制字符，无法作为文本阅读。请重新打包网页包。"
                )
            )
        }
        val generatedHtml = markdownRenderer.renderMarkdownResult(
            title = entryFile.name,
            markdown = decoded.text,
            darkTheme = options.generatedContentDarkTheme,
            readingDisplayOptions = options.readingDisplayOptions,
            includeFloatingOutline = false,
            allowRelativeImageSources = true,
            cancellationToken = cancellationToken
        ).html
        val generatedFile = File(entryFile.parentFile, MARKDOWN_PREVIEW_FILE_NAME)
        generatedFile.writeText(generatedHtml, Charsets.UTF_8)
        return generatedFile.relativeTo(stagingDirectory).invariantSeparatorsPath
    }

    fun errorForZipFailure(error: Exception): PreviewModel.Error {
        return when (error) {
            is SourceFileTooLargeException -> PreviewModel.Error(
                title = "ZIP 源文件过大，已停止预览",
                message = "ZIP 源文件超过 ${error.limitBytes.toBinaryMiBLabel()} 安全上限。请使用较小的 ZIP 文件后重试。"
            )
            is TooManyZipEntriesException -> PreviewModel.Error(
                title = "ZIP 条目过多，已停止预览",
                message = "ZIP 条目数量超过 ${error.limitCount} 个安全上限。请精简网页包内容后重试。"
            )
            is ZipEntryTooLargeException -> PreviewModel.Error(
                title = "ZIP 单个文件过大，已停止预览",
                message = "ZIP 内单个文件 ${error.entryName} 超过 ${error.limitBytes.toBinaryMiBLabel()} 安全上限。请移除或压缩该文件后重试。"
            )
            is ZipExpandedTooLargeException -> PreviewModel.Error(
                title = "ZIP 解压内容过大，已停止预览",
                message = "ZIP 解压后的内容超过 ${error.limitBytes.toBinaryMiBLabel()} 安全上限。即阅已停止解压，没有保留不完整缓存。"
            )
            else -> if (error.message.orEmpty().contains("Invalid zip entry path", ignoreCase = true)) {
                unsafeZipError()
            } else {
                PreviewModel.Error(
                    title = "ZIP 预览失败",
                    message = "可能不是有效的 ZIP 文件，或者压缩包内容无法解包。请重新选择文件，或先用其他工具确认压缩包是否完整。"
                )
            }
        }
    }

    private fun prepareTransactionPaths(importedFileId: String, target: File) {
        val transactionPaths = cacheRoot.listFiles().orEmpty()
        val stagingPrefix = "$importedFileId.partial-"
        val backupPrefix = "$importedFileId.previous-"
        val discardedPrefix = "$importedFileId.discarded-"
        val staleStaging = transactionPaths.filter { it.name.startsWith(stagingPrefix) }
        val staleBackups = transactionPaths.filter { it.name.startsWith(backupPrefix) }
        val discarded = transactionPaths.filter { it.name.startsWith(discardedPrefix) }

        (staleStaging + discarded).forEach { stalePath ->
            if (!deleteBestEffort(stalePath)) {
                throw IOException("Unable to clear stale ZIP preview staging cache")
            }
        }

        val recoverableBackup = staleBackups.maxWithOrNull(
            compareBy<File> { it.lastModified() }.thenBy { it.name }
        ) ?: return
        if (target.exists() && (!deleteBestEffort(target) || target.exists())) {
            throw IOException("Unable to clear uncommitted ZIP preview cache")
        }
        val recovery = tryRename(recoverableBackup, target)
        if (!recovery.returnedTrue || !target.exists() || recoverableBackup.exists()) {
            throw promotionFailure(
                message = "Unable to restore previous ZIP preview cache",
                renameAttempt = recovery,
                rollbackFailure = null
            )
        }
        staleBackups.filterNot { it == recoverableBackup }.forEach { backup ->
            if (!deleteBestEffort(backup)) {
                throw IOException("Unable to clear stale ZIP preview backup cache")
            }
        }
    }

    private fun deleteBestEffort(directory: File): Boolean {
        repeat(CLEANUP_ATTEMPTS) {
            if (!existsBestEffort(directory)) {
                return true
            }
            val deleted = try {
                deleteDirectory(directory)
            } catch (_: Exception) {
                false
            }
            if (deleted || !existsBestEffort(directory)) {
                return true
            }
        }
        return !existsBestEffort(directory)
    }

    private fun existsBestEffort(file: File): Boolean {
        return try {
            file.exists()
        } catch (_: Exception) {
            false
        }
    }

    private fun promote(staging: File, target: File, backup: File, hadTarget: Boolean) {
        if (hadTarget) {
            val oldCacheRename = tryRename(target, backup)
            val oldCacheMoved = !target.exists() && backup.exists()
            if (!oldCacheRename.returnedTrue || !oldCacheMoved) {
                val rollbackFailure = if (oldCacheMoved) {
                    restoreBackup(target, backup)
                } else {
                    null
                }
                throw promotionFailure(
                    message = "Unable to move previous ZIP preview cache",
                    renameAttempt = oldCacheRename,
                    rollbackFailure = rollbackFailure
                )
            }
        }

        val newCacheRename = tryRename(staging, target)
        val targetContainsNewCache = target.exists() && !staging.exists()
        if (newCacheRename.returnedTrue && targetContainsNewCache) {
            return
        }

        val rollbackFailure = if (hadTarget) {
            restoreBackup(target, backup)
        } else {
            removeUncommittedTarget(target)
        }
        throw promotionFailure(
            message = "Unable to replace ZIP preview cache",
            renameAttempt = newCacheRename,
            rollbackFailure = rollbackFailure
        )
    }

    private fun publishDeferred(
        importId: String,
        target: File,
        backup: File,
        runId: String,
        cacheLease: ZipPreviewCacheCoordinator.Lease
    ) {
        var needsFallback = false
        val cleanup = ZipPreviewCacheCoordinator.withCacheState(cacheLease) { invalidated ->
            if (invalidated) {
                val detached = listOfNotNull(
                    detachForDeletion(target, "$importId.discarded-$runId-target"),
                    detachForDeletion(backup, "$importId.discarded-$runId-backup")
                )
                needsFallback = target.exists() || backup.exists()
                detached
            } else {
                val detached = detachForDeletion(backup, "$importId.discarded-$runId-backup")
                needsFallback = backup.exists()
                listOfNotNull(detached)
            }
        }
        cleanup.forEach(scheduleDeletion)
        if (needsFallback) {
            scheduleFallbackFinalization(cacheLease) {
                if (ZipPreviewCacheCoordinator.isInvalidated(cacheLease)) {
                    deleteBestEffort(target)
                }
                deleteBestEffort(backup)
                if (ZipPreviewCacheCoordinator.isInvalidated(cacheLease)) {
                    deleteBestEffort(target)
                }
            }
        }
    }

    private fun discardDeferred(
        importId: String,
        target: File,
        backup: File,
        runId: String,
        hadTarget: Boolean,
        cacheLease: ZipPreviewCacheCoordinator.Lease
    ) {
        var needsFallback = false
        val cleanup = ZipPreviewCacheCoordinator.withCacheState(cacheLease) { invalidated ->
            if (invalidated) {
                val detached = listOfNotNull(
                    detachForDeletion(target, "$importId.discarded-$runId-target"),
                    detachForDeletion(backup, "$importId.discarded-$runId-backup")
                )
                needsFallback = target.exists() || backup.exists()
                detached
            } else if (hadTarget) {
                if (!backup.exists()) {
                    needsFallback = true
                    return@withCacheState emptyList()
                }
                val discardedTarget = detachForDeletion(
                    target,
                    "$importId.discarded-$runId-target"
                )
                if (target.exists()) {
                    needsFallback = true
                } else if (backup.exists()) {
                    val restored = tryRename(backup, target)
                    if (!restored.returnedTrue || !target.exists() || backup.exists()) {
                        needsFallback = true
                    }
                }
                listOfNotNull(discardedTarget)
            } else {
                val detachedTarget = detachForDeletion(
                    target,
                    "$importId.discarded-$runId-target"
                )
                needsFallback = target.exists()
                listOfNotNull(detachedTarget)
            }
        }
        cleanup.forEach(scheduleDeletion)
        if (needsFallback) {
            scheduleFallbackFinalization(cacheLease) {
                finalizeDeferredDiscard(cacheLease, target, backup, hadTarget)
            }
        }
    }

    private fun finalizeDeferredDiscard(
        cacheLease: ZipPreviewCacheCoordinator.Lease,
        target: File,
        backup: File,
        hadTarget: Boolean
    ) {
        if (ZipPreviewCacheCoordinator.isInvalidated(cacheLease)) {
            deleteBestEffort(target)
            deleteBestEffort(backup)
            return
        }

        if (hadTarget) {
            val rollbackFailure = restoreBackup(target, backup)
            if (rollbackFailure != null && !backup.exists()) {
                deleteBestEffort(target)
            }
        } else {
            deleteBestEffort(target)
        }

        if (ZipPreviewCacheCoordinator.isInvalidated(cacheLease)) {
            deleteBestEffort(target)
            deleteBestEffort(backup)
        }
    }

    private fun scheduleFallbackFinalization(
        cacheLease: ZipPreviewCacheCoordinator.Lease,
        action: () -> Unit
    ) {
        val pending = ZipPreviewCacheCoordinator.beginFinalization(cacheLease)
        try {
            scheduleFinalization {
                try {
                    action()
                } finally {
                    ZipPreviewCacheCoordinator.completeFinalization(pending)
                }
            }
        } catch (_: Exception) {
            ZipPreviewCacheCoordinator.completeFinalization(pending)
        }
    }

    private fun detachForDeletion(source: File, detachedName: String): File? {
        if (!source.exists()) return null
        val detached = File(source.parentFile, detachedName)
        val rename = tryRename(source, detached)
        return if (rename.returnedTrue && detached.exists() && !source.exists()) {
            detached
        } else {
            null
        }
    }

    private fun ensureActiveOrDetach(
        importId: String,
        cacheLease: ZipPreviewCacheCoordinator.Lease
    ) {
        try {
            ZipPreviewCacheCoordinator.ensureActive(cacheLease)
        } catch (cancelled: FileTaskCancelledException) {
            detachInvalidatedCachePaths(importId, cacheLease)
            throw cancelled
        }
    }

    private fun detachInvalidatedCachePaths(
        importId: String,
        cacheLease: ZipPreviewCacheCoordinator.Lease
    ) {
        val ownedPaths = ZipPreviewCacheCoordinator.withCacheState(cacheLease) {
            cacheRoot.listFiles().orEmpty().filter { path ->
                path.name == importId ||
                    path.name.startsWith("$importId.partial-") ||
                    path.name.startsWith("$importId.previous-") ||
                    path.name.startsWith("$importId.discarded-")
            }.mapNotNull { path ->
                detachForDeletion(
                    path,
                    "$importId.discarded-invalidated-${UUID.randomUUID()}"
                )
            }
        }
        ownedPaths.forEach(scheduleDeletion)
    }

    private fun removeUncommittedTarget(target: File): Throwable? {
        if (!target.exists()) return null
        return if (deleteBestEffort(target) && !target.exists()) {
            null
        } else {
            IOException("Unable to remove uncommitted ZIP preview cache")
        }
    }

    private fun tryRename(source: File, target: File): RenameAttempt {
        return try {
            RenameAttempt(returnedTrue = renameDirectory(source, target))
        } catch (error: Exception) {
            RenameAttempt(returnedTrue = false, error = error)
        }
    }

    private fun restoreBackup(target: File, backup: File): Throwable? {
        if (target.exists()) {
            if (!backup.exists()) {
                return IOException("Unable to restore previous ZIP preview cache because backup is missing")
            }
            if (!deleteBestEffort(target) || target.exists()) {
                return IOException("Unable to clear failed replacement before restoring previous ZIP preview cache")
            }
        }
        if (!backup.exists()) {
            return IOException("Unable to restore previous ZIP preview cache because backup is missing")
        }

        val rollback = tryRename(backup, target)
        return if (rollback.returnedTrue && target.exists()) {
            null
        } else {
            rollback.error ?: IOException("Unable to restore previous ZIP preview cache")
        }
    }

    private fun promotionFailure(
        message: String,
        renameAttempt: RenameAttempt,
        rollbackFailure: Throwable?
    ): IOException {
        val cause = renameAttempt.error ?: IOException("Directory rename returned false")
        return IOException(message, cause).also { failure ->
            if (rollbackFailure != null && rollbackFailure !== cause) {
                failure.addSuppressed(rollbackFailure)
            }
        }
    }

    private data class RenameAttempt(
        val returnedTrue: Boolean,
        val error: Throwable? = null
    )

    private fun unsafeZipError(): PreviewModel.Error {
        return PreviewModel.Error(
            title = "ZIP 文件不安全，已阻止预览",
            message = "这个 ZIP 包包含路径穿越条目，可能试图写出预览缓存目录；即阅已阻止解包，文件不会正常预览。"
        )
    }

    private fun Long.toBinaryMiBLabel(): String {
        val wholeMiB = this / ImportResourcePolicy.MIB
        val number = if (this % ImportResourcePolicy.MIB == 0L) {
            wholeMiB.toString()
        } else {
            String.format(
                Locale.US,
                "%.2f",
                toDouble() / ImportResourcePolicy.MIB.toDouble()
            ).trimEnd('0').trimEnd('.')
        }
        return "$number MiB"
    }

    private companion object {
        const val CLEANUP_ATTEMPTS = 2
        const val MARKDOWN_PREVIEW_FILE_NAME = ".instantread-markdown-preview.html"
    }
}

/** 把 Markdown 入口的失败语义直接映射为可展示的预览错误，避免泄漏内部异常与路径。 */
private class MarkdownEntryPreviewException(
    val preview: PreviewModel.Error
) : Exception(preview.title)
