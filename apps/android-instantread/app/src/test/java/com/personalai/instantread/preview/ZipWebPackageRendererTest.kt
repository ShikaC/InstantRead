package com.personalai.instantread.preview

import com.personalai.instantread.importing.FileType
import com.personalai.instantread.history.ImportCleanup
import com.personalai.instantread.importing.ImportedFile
import com.personalai.instantread.importing.SourceKind
import com.personalai.instantread.io.FileTaskCancellationToken
import com.personalai.instantread.io.FileTaskCancelledException
import com.personalai.instantread.io.ImportResourcePolicy
import com.personalai.instantread.io.SourceFileTooLargeException
import com.personalai.instantread.io.TooManyZipEntriesException
import com.personalai.instantread.io.ZipEntryTooLargeException
import com.personalai.instantread.io.ZipExpandedTooLargeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipWebPackageRendererTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun rendersRootIndexHtmlFromExtractedCache() {
        val zip = zipFile("site.zip", "index.html" to "<html>home</html>", "assets/style.css" to "body{}")
        val renderer = ZipWebPackageRenderer(cacheRoot = temp.newFolder("cache"))

        val preview = renderer.render(imported(zip, "zip-1"))

        val webFile = preview as PreviewModel.WebFile
        assertEquals("site.zip", webFile.title)
        assertFalse(webFile.javaScriptEnabled)
        assertEquals("index.html", webFile.file.name)
        assertEquals("<html>home</html>", webFile.file.readText())
        assertTrue(File(webFile.file.parentFile, "assets/style.css").exists())
        // 授权根是本次解包目录，包内资源可加载，其它导入的缓存不可见。
        assertEquals(
            requireNotNull(webFile.file.parentFile).canonicalFile,
            requireNotNull(webFile.resourceRoot).canonicalFile
        )
    }

    @Test
    fun nestedEntryKeepsPackageRootAsResourceRoot() {
        val zip = zipFile(
            "nested.zip",
            "site/index.html" to "<html>nested</html>",
            "site/assets/style.css" to "body{}"
        )
        val cacheRoot = temp.newFolder("cache-nested")
        val renderer = ZipWebPackageRenderer(cacheRoot = cacheRoot)

        val webFile = renderer.render(imported(zip, "zip-nested")) as PreviewModel.WebFile

        assertEquals("site", requireNotNull(webFile.file.parentFile).name)
        assertEquals(
            File(cacheRoot, "zip-nested").canonicalFile,
            requireNotNull(webFile.resourceRoot).canonicalFile
        )
        assertTrue(File(requireNotNull(webFile.resourceRoot), "site/assets/style.css").exists())
    }

    @Test
    fun zipPreviewUsesExplicitJavaScriptOption() {
        val zip = zipFile("site.zip", "index.html" to "<script>window.ok = true</script>")
        val renderer = ZipWebPackageRenderer(cacheRoot = temp.newFolder("cache"))

        val preview = renderer.render(
            imported(zip, "zip-js"),
            PreviewOptions(javaScriptEnabledForHtml = true)
        )

        val webFile = preview as PreviewModel.WebFile
        assertTrue(webFile.javaScriptEnabled)
    }

    @Test
    fun rendersNestedShortestIndexHtml() {
        val zip = zipFile(
            "nested.zip",
            "deep/path/index.html" to "deep",
            "site/index.htm" to "site",
            "a/index.html" to "short"
        )
        val renderer = ZipWebPackageRenderer(cacheRoot = temp.newFolder("cache"))

        val preview = renderer.render(imported(zip, "zip-2"))

        val webFile = preview as PreviewModel.WebFile
        assertEquals("short", webFile.file.readText())
    }

    @Test
    fun returnsErrorWhenZipHasNoHtmlEntry() {
        val zip = zipFile("no-html.zip", "assets/style.css" to "body{}")
        val renderer = ZipWebPackageRenderer(cacheRoot = temp.newFolder("cache"))

        val preview = renderer.render(imported(zip, "zip-3"))

        val error = preview as PreviewModel.Error
        assertEquals("ZIP 中没有可预览的网页入口", error.title)
        assertTrue(error.message.contains("没有找到 index.html、index.htm 或其他 HTML 文件"))
        assertTrue(error.message.contains("请确认这是网页包 ZIP"))
        assertTrue(error.message.contains("重新选择文件"))
    }

    @Test
    fun invalidZipReturnsFormatErrorInsteadOfMissingEntry() {
        val corrupt = temp.newFile("corrupt.zip").apply {
            writeBytes(ByteArray(4096) { index -> (index * 31).toByte() })
        }
        val renderer = ZipWebPackageRenderer(cacheRoot = temp.newFolder("cache-invalid-zip"))

        val error = renderer.render(imported(corrupt, "zip-invalid")) as PreviewModel.Error

        assertEquals("ZIP 预览失败", error.title)
        assertTrue(error.message.contains("可能不是有效的 ZIP 文件"))
    }

    @Test
    fun validEmptyZipStillReportsMissingPreviewEntry() {
        val renderer = ZipWebPackageRenderer(cacheRoot = temp.newFolder("cache-empty-zip"))

        val error = renderer.render(imported(zipFile("empty.zip"), "zip-empty")) as PreviewModel.Error

        assertEquals("ZIP 中没有可预览的网页入口", error.title)
    }

    @Test
    fun rendersMarkdownPackageIntoGeneratedReadingPage() {
        val zip = zipFile(
            "notes.zip",
            "index.md" to "# 标题\n\n![图](assets/purple.png)\n",
            "assets/purple.png" to "png"
        )
        val cacheRoot = temp.newFolder("cache-markdown")
        val renderer = ZipWebPackageRenderer(cacheRoot = cacheRoot)

        val preview = renderer.render(imported(zip, "zip-markdown"))

        val webFile = preview as PreviewModel.WebFile
        assertEquals("notes.zip", webFile.title)
        assertEquals(".instantread-markdown-preview.html", webFile.file.name)
        val html = webFile.file.readText()
        assertTrue(html.contains("<h1 id=\"标题\">标题</h1>"))
        assertTrue(html.contains("<img src=\"assets/purple.png\" alt=\"图\">"))
        // 生成页不内置目录面板与目录脚本，避免出现无法点击的交互入口。
        assertFalse(html.contains("<aside class=\"floating-outline\""))
        assertFalse(html.contains("function updateCurrentOutline()"))
        assertFalse(webFile.javaScriptEnabled)
        assertFalse(webFile.supportsUserScriptAuthorization)
        assertEquals(
            File(cacheRoot, "zip-markdown").canonicalFile,
            requireNotNull(webFile.resourceRoot).canonicalFile
        )
    }

    @Test
    fun markdownPackageKeepsNestedEntryResourcesRelativeToEntry() {
        val zip = zipFile(
            "nested-notes.zip",
            "docs/index.md" to "![图](assets/purple.png)",
            "docs/assets/purple.png" to "png"
        )
        val cacheRoot = temp.newFolder("cache-markdown-nested")
        val renderer = ZipWebPackageRenderer(cacheRoot = cacheRoot)

        val webFile = renderer.render(imported(zip, "zip-markdown-nested")) as PreviewModel.WebFile

        assertEquals("docs", requireNotNull(webFile.file.parentFile).name)
        assertEquals(".instantread-markdown-preview.html", webFile.file.name)
        assertTrue(File(webFile.file.parentFile, "assets/purple.png").isFile)
    }

    @Test
    fun markdownPackageRejectsNetworkAndSchemeImagesInGeneratedPage() {
        val zip = zipFile(
            "remote.zip",
            "index.md" to "![远程](https://example.com/pic.png)\n\n![文件](file:///sdcard/secret.png)"
        )
        val renderer = ZipWebPackageRenderer(cacheRoot = temp.newFolder("cache-markdown-remote"))

        val webFile = renderer.render(imported(zip, "zip-markdown-remote")) as PreviewModel.WebFile

        val html = webFile.file.readText()
        assertFalse(html.contains("src=\"https://example.com/pic.png\""))
        assertFalse(html.contains("src=\"file:///sdcard/secret.png\""))
    }

    @Test
    fun markdownPackageIgnoresHtmlJavaScriptOption() {
        val zip = zipFile("md-js.zip", "index.md" to "# Title")
        val renderer = ZipWebPackageRenderer(cacheRoot = temp.newFolder("cache-markdown-js"))

        val preview = renderer.render(
            imported(zip, "zip-markdown-js"),
            PreviewOptions(javaScriptEnabledForHtml = true)
        )

        val webFile = preview as PreviewModel.WebFile
        assertFalse(webFile.javaScriptEnabled)
        assertFalse(webFile.supportsUserScriptAuthorization)
    }

    @Test
    fun markdownPackageWithBinaryEntryReturnsClearErrorWithoutLeakingPath() {
        val binary = ByteArray(2048) { 0x00 }
        val zip = temp.newFile("binary-md.zip")
        ZipOutputStream(FileOutputStream(zip)).use { stream ->
            stream.putNextEntry(ZipEntry("index.md"))
            stream.write(binary)
            stream.closeEntry()
        }
        val cacheRoot = temp.newFolder("cache-markdown-binary")
        val renderer = ZipWebPackageRenderer(cacheRoot = cacheRoot)

        val preview = renderer.render(imported(zip, "zip-markdown-binary"))

        val error = preview as PreviewModel.Error
        assertFalse(error.message.contains(cacheRoot.path))
        assertFalse(error.message.contains("index.md"))
    }

    @Test
    fun returnsErrorAndDoesNotWriteTraversalEntryOutsideCache() {
        val zip = zipFile("evil.zip", "../escape.txt" to "owned", "index.html" to "home")
        val cacheRoot = temp.newFolder("cache")
        val renderer = ZipWebPackageRenderer(cacheRoot = cacheRoot)

        val preview = renderer.render(imported(zip, "zip-4"))

        val error = preview as PreviewModel.Error
        assertEquals("ZIP 文件不安全，已阻止预览", error.title)
        assertTrue(error.message.contains("路径穿越"))
        assertTrue(error.message.contains("即阅已阻止解包"))
        assertTrue(error.message.contains("不会正常预览"))
        assertFalse(File(cacheRoot.parentFile, "escape.txt").exists())
    }

    @Test
    fun mapsAndroidInvalidZipEntryPathToUnsafeZipError() {
        val renderer = ZipWebPackageRenderer(cacheRoot = temp.newFolder("cache"))

        val preview = renderer.errorForZipFailure(IllegalArgumentException("Invalid zip entry path: ../escape.txt"))

        val error = preview as PreviewModel.Error
        assertEquals("ZIP 文件不安全，已阻止预览", error.title)
        assertTrue(error.message.contains("路径穿越"))
        assertTrue(error.message.contains("即阅已阻止解包"))
        assertTrue(error.message.contains("不会正常预览"))
    }

    @Test
    fun mapsGenericZipFailureToChineseRecoveryMessage() {
        val renderer = ZipWebPackageRenderer(cacheRoot = temp.newFolder("cache"))
        val privatePath = "C:\\Users\\example\\Documents\\private-site.zip"

        val preview = renderer.errorForZipFailure(
            IllegalStateException("central directory is corrupt: $privatePath")
        )

        val error = preview as PreviewModel.Error
        assertEquals("ZIP 预览失败", error.title)
        assertTrue(error.message.contains("可能不是有效的 ZIP 文件"))
        assertTrue(error.message.contains("请重新选择文件"))
        assertFalse(error.message.contains("central directory is corrupt"))
        assertFalse(error.message.contains(privatePath))
    }

    @Test
    fun deferredCacheCommitKeepsRollbackBackupUntilPublished() {
        val cacheRoot = temp.newFolder("cache-deferred-publish")
        File(cacheRoot, "zip-deferred-publish").apply {
            mkdirs()
            File(this, "index.html").writeText("old")
        }
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            stagingIdGenerator = { "run-deferred-publish" }
        )

        val preview = renderer.prepare(
            imported(
                zipFile("deferred-publish.zip", "index.html" to "new"),
                "zip-deferred-publish"
            )
        ) as PreviewModel.WebFile

        assertEquals("new", preview.file.readText())
        assertEquals(
            "old",
            File(
                cacheRoot,
                "zip-deferred-publish.previous-run-deferred-publish/index.html"
            ).readText()
        )

        preview.publishPreparedResources()

        assertEquals("new", File(cacheRoot, "zip-deferred-publish/index.html").readText())
        assertFalse(
            File(cacheRoot, "zip-deferred-publish.previous-run-deferred-publish").exists()
        )
    }

    @Test
    fun discardingDeferredCacheRestoresPreviousWorkingCache() {
        val cacheRoot = temp.newFolder("cache-deferred-discard")
        File(cacheRoot, "zip-deferred-discard").apply {
            mkdirs()
            File(this, "index.html").writeText("old")
        }
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            stagingIdGenerator = { "run-deferred-discard" }
        )

        val preview = renderer.prepare(
            imported(
                zipFile("deferred-discard.zip", "index.html" to "new"),
                "zip-deferred-discard"
            )
        ) as PreviewModel.WebFile
        assertEquals("new", preview.file.readText())

        preview.discardPreparedResources()

        assertEquals("old", File(cacheRoot, "zip-deferred-discard/index.html").readText())
        assertFalse(
            File(cacheRoot, "zip-deferred-discard.previous-run-deferred-discard").exists()
        )
        assertFalse(File(cacheRoot, "zip-deferred-discard.partial-run-deferred-discard").exists())
    }

    @Test
    fun deferredPublishRenameFailureQueuesBackupDeletionBeforeNextSameIdWork() {
        val cacheRoot = temp.newFolder("deferred-publish-fallback")
        File(cacheRoot, "publish-fallback").apply {
            mkdirs()
            File(this, "index.html").writeText("old")
        }
        var renameCalls = 0
        val finalizations = mutableListOf<() -> Unit>()
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            stagingIdGenerator = { "publish-fallback-run" },
            renameDirectory = { source, target ->
                renameCalls += 1
                if (renameCalls == 3) false else source.renameTo(target)
            },
            scheduleFinalization = { finalizations += it }
        )
        val preview = renderer.prepare(
            imported(zipFile("publish-fallback.zip", "index.html" to "new"), "publish-fallback")
        ) as PreviewModel.WebFile

        preview.publishPreparedResources()

        assertEquals(3, renameCalls)
        assertEquals(1, finalizations.size)
        assertTrue(File(cacheRoot, "publish-fallback.previous-publish-fallback-run").exists())
        finalizations.single().invoke()
        assertFalse(File(cacheRoot, "publish-fallback.previous-publish-fallback-run").exists())
        assertEquals("new", File(cacheRoot, "publish-fallback/index.html").readText())
    }

    @Test
    fun deferredDiscardRenameFailureQueuesRollbackBeforeNextSameIdWork() {
        val cacheRoot = temp.newFolder("deferred-discard-fallback")
        File(cacheRoot, "discard-fallback").apply {
            mkdirs()
            File(this, "index.html").writeText("old")
        }
        var renameCalls = 0
        val finalizations = mutableListOf<() -> Unit>()
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            stagingIdGenerator = { "discard-fallback-run" },
            renameDirectory = { source, target ->
                renameCalls += 1
                if (renameCalls == 3) false else source.renameTo(target)
            },
            scheduleFinalization = { finalizations += it }
        )
        val preview = renderer.prepare(
            imported(zipFile("discard-fallback.zip", "index.html" to "new"), "discard-fallback")
        ) as PreviewModel.WebFile

        preview.discardPreparedResources()

        assertEquals(3, renameCalls)
        assertEquals(1, finalizations.size)
        assertEquals("new", File(cacheRoot, "discard-fallback/index.html").readText())
        finalizations.single().invoke()
        assertEquals("old", File(cacheRoot, "discard-fallback/index.html").readText())
        assertFalse(File(cacheRoot, "discard-fallback.previous-discard-fallback-run").exists())
    }

    @Test
    fun rebuildsCacheOnEveryRender() {
        val zip = zipFile("site.zip", "index.html" to "home")
        val cacheRoot = temp.newFolder("cache")
        val renderer = ZipWebPackageRenderer(cacheRoot = cacheRoot)
        val imported = imported(zip, "zip-5")

        val first = renderer.render(imported) as PreviewModel.WebFile
        File(first.file.parentFile, "stale.txt").writeText("stale")
        val second = renderer.render(imported) as PreviewModel.WebFile

        assertFalse(File(second.file.parentFile, "stale.txt").exists())
        assertEquals("home", second.file.readText())
    }

    @Test
    fun clearingPreviewCacheKeepsSourceZipAndRebuildsOnNextRender() {
        val sourceZip = zipFile("recoverable.zip", "index.html" to "restored")
        val cacheDir = temp.newFolder("cache-clear-rebuild")
        val cacheRoot = File(cacheDir, "zip-preview")
        val renderer = ZipWebPackageRenderer(cacheRoot = cacheRoot)
        val imported = imported(sourceZip, "zip-clear-rebuild")

        val first = renderer.render(imported) as PreviewModel.WebFile
        assertEquals("restored", first.file.readText())

        ImportCleanup.clearZipPreviewCache(
            cacheDir = cacheDir,
            scheduleDeletion = { it.deleteRecursively() }
        )

        assertTrue(sourceZip.exists())
        assertFalse(File(cacheRoot, "zip-clear-rebuild").exists())

        val rebuilt = renderer.render(imported) as PreviewModel.WebFile
        assertTrue(sourceZip.exists())
        assertEquals("restored", rebuilt.file.readText())
    }

    @Test
    fun failedRebuildKeepsPreviousWorkingCache() {
        val cacheRoot = temp.newFolder("cache-preserve")
        val old = File(cacheRoot, "zip-keep").apply { mkdirs() }
        File(old, "index.html").writeText("old")
        val oversized = zipFile("large.zip", "index.html" to "12345")
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            resourcePolicy = ImportResourcePolicy.DEFAULT.copy(maxZipEntryBytes = 4),
            stagingIdGenerator = { "run-1" }
        )

        val preview = renderer.render(imported(oversized, "zip-keep"))

        assertTrue(preview is PreviewModel.Error)
        assertEquals("old", File(cacheRoot, "zip-keep/index.html").readText())
        assertFalse(File(cacheRoot, "zip-keep.partial-run-1").exists())
        assertFalse(File(cacheRoot, "zip-keep.previous-run-1").exists())
    }

    @Test
    fun successfulRebuildReplacesPreviousCacheAndRemovesStaleTransactionFiles() {
        val cacheRoot = temp.newFolder("cache-replace")
        val old = File(cacheRoot, "zip-replace").apply { mkdirs() }
        File(old, "index.html").writeText("old")
        File(old, "stale.txt").writeText("stale")
        File(cacheRoot, "zip-replace.partial-run-2").apply {
            mkdirs()
            File(this, "partial.txt").writeText("partial")
        }
        File(cacheRoot, "zip-replace.previous-run-2").apply {
            mkdirs()
            File(this, "backup.txt").writeText("backup")
        }
        File(cacheRoot, "zip-replace.partial-old-run").apply {
            mkdirs()
            File(this, "partial.txt").writeText("old partial")
        }
        File(cacheRoot, "zip-replace.previous-old-run").apply {
            mkdirs()
            File(this, "backup.txt").writeText("old backup")
        }
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            stagingIdGenerator = { "run-2" }
        )

        val preview = renderer.render(
            imported(zipFile("new.zip", "index.html" to "new"), "zip-replace")
        ) as PreviewModel.WebFile

        assertEquals(File(cacheRoot, "zip-replace/index.html").canonicalFile, preview.file)
        assertEquals("new", preview.file.readText())
        assertFalse(File(cacheRoot, "zip-replace/stale.txt").exists())
        assertFalse(File(cacheRoot, "zip-replace.partial-run-2").exists())
        assertFalse(File(cacheRoot, "zip-replace.previous-run-2").exists())
        assertFalse(File(cacheRoot, "zip-replace.partial-old-run").exists())
        assertFalse(File(cacheRoot, "zip-replace.previous-old-run").exists())
    }

    @Test
    fun zipWithoutHtmlKeepsPreviousWorkingCache() {
        val cacheRoot = temp.newFolder("cache-no-html")
        File(cacheRoot, "zip-no-html").apply {
            mkdirs()
            File(this, "index.html").writeText("old")
        }
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            stagingIdGenerator = { "run-no-html" }
        )

        val preview = renderer.render(
            imported(zipFile("no-html-rebuild.zip", "assets/style.css" to "body{}"), "zip-no-html")
        )

        assertEquals("ZIP 中没有可预览的网页入口", (preview as PreviewModel.Error).title)
        assertEquals("old", File(cacheRoot, "zip-no-html/index.html").readText())
        assertFalse(File(cacheRoot, "zip-no-html.partial-run-no-html").exists())
    }

    @Test
    fun zipSlipKeepsPreviousWorkingCacheAndDoesNotEscapeStaging() {
        val cacheRoot = temp.newFolder("cache-slip-preserve")
        File(cacheRoot, "zip-slip").apply {
            mkdirs()
            File(this, "index.html").writeText("old")
        }
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            stagingIdGenerator = { "run-slip" }
        )

        val preview = renderer.render(
            imported(zipFile("slip-rebuild.zip", "../escape.txt" to "owned", "index.html" to "new"), "zip-slip")
        )

        assertEquals("ZIP 文件不安全，已阻止预览", (preview as PreviewModel.Error).title)
        assertEquals("old", File(cacheRoot, "zip-slip/index.html").readText())
        assertFalse(File(cacheRoot, "escape.txt").exists())
        assertFalse(File(cacheRoot, "zip-slip.partial-run-slip").exists())
    }

    @Test
    fun failedSecondPromotionStepRollsBackPreviousCache() {
        val cacheRoot = temp.newFolder("cache-rollback")
        File(cacheRoot, "zip-rollback").apply {
            mkdirs()
            File(this, "index.html").writeText("old")
        }
        var renameCalls = 0
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            stagingIdGenerator = { "run-rollback" },
            renameDirectory = { source, target ->
                renameCalls += 1
                if (renameCalls == 2) false else source.renameTo(target)
            }
        )

        val preview = renderer.render(
            imported(zipFile("rollback.zip", "index.html" to "new"), "zip-rollback")
        )

        assertEquals("ZIP 预览失败", (preview as PreviewModel.Error).title)
        assertEquals(3, renameCalls)
        assertEquals("old", File(cacheRoot, "zip-rollback/index.html").readText())
        assertFalse(File(cacheRoot, "zip-rollback.partial-run-rollback").exists())
        assertFalse(File(cacheRoot, "zip-rollback.previous-run-rollback").exists())
    }

    @Test
    fun secondPromotionRenameExceptionAttemptsRollbackAndRestoresPreviousCache() {
        val cacheRoot = temp.newFolder("cache-rename-exception")
        File(cacheRoot, "zip-rename-exception").apply {
            mkdirs()
            File(this, "index.html").writeText("old")
        }
        val calls = mutableListOf<String>()
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            stagingIdGenerator = { "run-rename-exception" },
            renameDirectory = { source, target ->
                calls += "${source.name}->${target.name}"
                when (calls.size) {
                    1 -> source.renameTo(target)
                    2 -> throw IOException("new cache rename failed")
                    else -> source.renameTo(target)
                }
            }
        )

        val preview = renderer.render(
            imported(
                zipFile("rename-exception.zip", "index.html" to "new"),
                "zip-rename-exception"
            )
        )

        assertEquals("ZIP 预览失败", (preview as PreviewModel.Error).title)
        assertEquals(
            listOf(
                "zip-rename-exception->zip-rename-exception.previous-run-rename-exception",
                "zip-rename-exception.partial-run-rename-exception->zip-rename-exception",
                "zip-rename-exception.previous-run-rename-exception->zip-rename-exception"
            ),
            calls
        )
        assertEquals("old", File(cacheRoot, "zip-rename-exception/index.html").readText())
        assertFalse(File(cacheRoot, "zip-rename-exception.partial-run-rename-exception").exists())
        assertFalse(File(cacheRoot, "zip-rename-exception.previous-run-rename-exception").exists())
    }

    @Test
    fun secondPromotionRenameThatMovesThenThrowsRestoresPreviousCache() {
        val cacheRoot = temp.newFolder("cache-second-moves-throws")
        File(cacheRoot, "zip-second-moves-throws").apply {
            mkdirs()
            File(this, "index.html").writeText("old")
        }
        val calls = mutableListOf<String>()
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            stagingIdGenerator = { "run-second-moves-throws" },
            renameDirectory = { source, target ->
                calls += "${source.name}->${target.name}"
                when (calls.size) {
                    1 -> source.renameTo(target)
                    2 -> {
                        check(source.renameTo(target))
                        throw IOException("new cache moved before failure")
                    }
                    else -> source.renameTo(target)
                }
            }
        )

        val preview = renderer.render(
            imported(
                zipFile("second-moves-throws.zip", "index.html" to "new"),
                "zip-second-moves-throws"
            )
        )

        assertTrue(preview is PreviewModel.Error)
        assertEquals(3, calls.size)
        assertEquals("old", File(cacheRoot, "zip-second-moves-throws/index.html").readText())
        assertFalse(File(cacheRoot, "zip-second-moves-throws.partial-run-second-moves-throws").exists())
        assertFalse(File(cacheRoot, "zip-second-moves-throws.previous-run-second-moves-throws").exists())
    }

    @Test
    fun failedDeletionDuringRollbackKeepsPreviousBackupAfterNewTargetWasMoved() {
        val cacheRoot = temp.newFolder("cache-rollback-delete-fails")
        File(cacheRoot, "zip-rollback-delete-fails").apply {
            mkdirs()
            File(this, "index.html").writeText("old")
        }
        var renameCalls = 0
        var targetDeleteAttempts = 0
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            stagingIdGenerator = { "run-rollback-delete-fails" },
            renameDirectory = { source, target ->
                renameCalls += 1
                when (renameCalls) {
                    1 -> source.renameTo(target)
                    else -> {
                        check(source.renameTo(target))
                        throw IOException("new cache moved before failure")
                    }
                }
            },
            deleteDirectory = { directory ->
                if (directory.name == "zip-rollback-delete-fails") {
                    targetDeleteAttempts += 1
                    false
                } else {
                    directory.deleteRecursively()
                }
            }
        )

        val preview = renderer.render(
            imported(
                zipFile("rollback-delete-fails.zip", "index.html" to "new"),
                "zip-rollback-delete-fails"
            )
        )

        assertTrue(preview is PreviewModel.Error)
        assertEquals(2, renameCalls)
        assertEquals(2, targetDeleteAttempts)
        assertEquals("new", File(cacheRoot, "zip-rollback-delete-fails/index.html").readText())
        assertEquals(
            "old",
            File(
                cacheRoot,
                "zip-rollback-delete-fails.previous-run-rollback-delete-fails/index.html"
            ).readText()
        )
        assertFalse(File(cacheRoot, "zip-rollback-delete-fails.partial-run-rollback-delete-fails").exists())
    }

    @Test
    fun failedRollbackKeepsOnlyRecoverableBackupAndCleansStaging() {
        val cacheRoot = temp.newFolder("cache-rollback-false")
        File(cacheRoot, "zip-rollback-false").apply {
            mkdirs()
            File(this, "index.html").writeText("old")
        }
        var calls = 0
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            stagingIdGenerator = { "run-rollback-false" },
            renameDirectory = { source, target ->
                calls += 1
                when (calls) {
                    1 -> source.renameTo(target)
                    else -> false
                }
            }
        )

        val preview = renderer.render(
            imported(zipFile("rollback-false.zip", "index.html" to "new"), "zip-rollback-false")
        )

        assertTrue(preview is PreviewModel.Error)
        assertEquals(3, calls)
        assertFalse(File(cacheRoot, "zip-rollback-false").exists())
        assertEquals(
            "old",
            File(cacheRoot, "zip-rollback-false.previous-run-rollback-false/index.html").readText()
        )
        assertFalse(File(cacheRoot, "zip-rollback-false.partial-run-rollback-false").exists())
    }

    @Test
    fun rollbackExceptionKeepsOnlyRecoverableBackupAndCleansStaging() {
        val cacheRoot = temp.newFolder("cache-rollback-throws")
        File(cacheRoot, "zip-rollback-throws").apply {
            mkdirs()
            File(this, "index.html").writeText("old")
        }
        var calls = 0
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            stagingIdGenerator = { "run-rollback-throws" },
            renameDirectory = { source, target ->
                calls += 1
                when (calls) {
                    1 -> source.renameTo(target)
                    2 -> false
                    else -> throw IOException("rollback failed")
                }
            }
        )

        val preview = renderer.render(
            imported(zipFile("rollback-throws.zip", "index.html" to "new"), "zip-rollback-throws")
        )

        assertTrue(preview is PreviewModel.Error)
        assertEquals(3, calls)
        assertFalse(File(cacheRoot, "zip-rollback-throws").exists())
        assertEquals(
            "old",
            File(cacheRoot, "zip-rollback-throws.previous-run-rollback-throws/index.html").readText()
        )
        assertFalse(File(cacheRoot, "zip-rollback-throws.partial-run-rollback-throws").exists())
    }

    @Test
    fun firstPromotionRenameFalseLeavesPreviousTargetUntouched() {
        val cacheRoot = temp.newFolder("cache-first-false")
        File(cacheRoot, "zip-first-false").apply {
            mkdirs()
            File(this, "index.html").writeText("old")
        }
        var calls = 0
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            stagingIdGenerator = { "run-first-false" },
            renameDirectory = { _, _ ->
                calls += 1
                false
            }
        )

        val preview = renderer.render(
            imported(zipFile("first-false.zip", "index.html" to "new"), "zip-first-false")
        )

        assertTrue(preview is PreviewModel.Error)
        assertEquals(1, calls)
        assertEquals("old", File(cacheRoot, "zip-first-false/index.html").readText())
        assertFalse(File(cacheRoot, "zip-first-false.previous-run-first-false").exists())
        assertFalse(File(cacheRoot, "zip-first-false.partial-run-first-false").exists())
    }

    @Test
    fun firstPromotionRenameThatMovesThenThrowsRollsBackPreviousTarget() {
        val cacheRoot = temp.newFolder("cache-first-throws")
        File(cacheRoot, "zip-first-throws").apply {
            mkdirs()
            File(this, "index.html").writeText("old")
        }
        var calls = 0
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            stagingIdGenerator = { "run-first-throws" },
            renameDirectory = { source, target ->
                calls += 1
                if (calls == 1) {
                    check(source.renameTo(target))
                    throw IOException("old cache rename reported failure")
                }
                source.renameTo(target)
            }
        )

        val preview = renderer.render(
            imported(zipFile("first-throws.zip", "index.html" to "new"), "zip-first-throws")
        )

        assertTrue(preview is PreviewModel.Error)
        assertEquals(2, calls)
        assertEquals("old", File(cacheRoot, "zip-first-throws/index.html").readText())
        assertFalse(File(cacheRoot, "zip-first-throws.previous-run-first-throws").exists())
        assertFalse(File(cacheRoot, "zip-first-throws.partial-run-first-throws").exists())
    }

    @Test
    fun firstCacheRenameThatMovesThenThrowsIsFailureAndRemovesUncommittedTarget() {
        val cacheRoot = temp.newFolder("cache-no-old-moves-throws")
        var renameCalls = 0
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            stagingIdGenerator = { "run-no-old-moves-throws" },
            renameDirectory = { source, target ->
                renameCalls += 1
                check(source.renameTo(target))
                throw IOException("rename reported failure after move")
            }
        )

        val preview = renderer.render(
            imported(
                zipFile("no-old-moves-throws.zip", "index.html" to "new"),
                "zip-no-old-moves-throws"
            )
        )

        assertTrue(preview is PreviewModel.Error)
        assertEquals(1, renameCalls)
        assertFalse(File(cacheRoot, "zip-no-old-moves-throws").exists())
        assertFalse(File(cacheRoot, "zip-no-old-moves-throws.partial-run-no-old-moves-throws").exists())
        assertFalse(File(cacheRoot, "zip-no-old-moves-throws.previous-run-no-old-moves-throws").exists())
    }

    @Test
    fun cancellationImmediatelyBeforePromotionIsRethrownAndKeepsPreviousCache() {
        val cacheRoot = temp.newFolder("cache-cancel")
        File(cacheRoot, "zip-cancel").apply {
            mkdirs()
            File(this, "index.html").writeText("old")
        }
        val expected = FileTaskCancelledException()
        var checks = 0
        val cancellationToken = FileTaskCancellationToken {
            checks += 1
            if (checks == 6) throw expected
        }
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            stagingIdGenerator = { "run-cancel" }
        )

        val actual = assertThrows<FileTaskCancelledException> {
            renderer.render(
                imported(zipFile("cancel.zip", "index.html" to "new"), "zip-cancel"),
                cancellationToken = cancellationToken
            )
        }

        assertSame(expected, actual)
        assertEquals(6, checks)
        assertEquals("old", File(cacheRoot, "zip-cancel/index.html").readText())
        assertFalse(File(cacheRoot, "zip-cancel.partial-run-cancel").exists())
        assertFalse(File(cacheRoot, "zip-cancel.previous-run-cancel").exists())
    }

    @Test
    fun officialEntryCanonicalizationFailureHappensBeforePromotionAndKeepsPreviousCache() {
        val cacheRoot = temp.newFolder("cache-canonical-failure")
        File(cacheRoot, "zip-canonical-failure").apply {
            mkdirs()
            File(this, "index.html").writeText("old")
        }
        var renameCalls = 0
        var canonicalizedFile: File? = null
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            stagingIdGenerator = { "run-canonical-failure" },
            renameDirectory = { source, target ->
                renameCalls += 1
                source.renameTo(target)
            },
            canonicalizeFile = { file ->
                canonicalizedFile = file
                throw IOException("canonicalization failed")
            }
        )

        val preview = renderer.render(
            imported(
                zipFile("canonical-failure.zip", "index.html" to "new"),
                "zip-canonical-failure"
            )
        )

        assertTrue(preview is PreviewModel.Error)
        assertEquals(File(cacheRoot, "zip-canonical-failure/index.html"), canonicalizedFile)
        assertEquals(0, renameCalls)
        assertEquals("old", File(cacheRoot, "zip-canonical-failure/index.html").readText())
        assertFalse(File(cacheRoot, "zip-canonical-failure.partial-run-canonical-failure").exists())
        assertFalse(File(cacheRoot, "zip-canonical-failure.previous-run-canonical-failure").exists())
    }

    @Test
    fun cleanupExceptionDoesNotMaskCancellationBeforePromotion() {
        val cacheRoot = temp.newFolder("cache-cancel-cleanup-throws")
        File(cacheRoot, "zip-cancel-cleanup-throws").apply {
            mkdirs()
            File(this, "index.html").writeText("old")
        }
        val expected = FileTaskCancelledException()
        var checks = 0
        var cleanupCalls = 0
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            stagingIdGenerator = { "run-cancel-cleanup-throws" },
            deleteDirectory = {
                cleanupCalls += 1
                throw IOException("cleanup failed")
            }
        )
        val cancellationToken = FileTaskCancellationToken {
            checks += 1
            if (checks == 6) throw expected
        }

        val actual = assertThrows<FileTaskCancelledException> {
            renderer.render(
                imported(
                    zipFile("cancel-cleanup-throws.zip", "index.html" to "new"),
                    "zip-cancel-cleanup-throws"
                ),
                cancellationToken = cancellationToken
            )
        }

        assertSame(expected, actual)
        assertEquals(6, checks)
        assertTrue(cleanupCalls > 0)
        assertEquals("old", File(cacheRoot, "zip-cancel-cleanup-throws/index.html").readText())
        assertFalse(File(cacheRoot, "zip-cancel-cleanup-throws.previous-run-cancel-cleanup-throws").exists())
    }

    @Test
    fun cleanupExceptionAfterSuccessfulPromotionDoesNotReplaceWebFileResult() {
        val cacheRoot = temp.newFolder("cache-success-cleanup-throws")
        File(cacheRoot, "zip-success-cleanup-throws").apply {
            mkdirs()
            File(this, "index.html").writeText("old")
        }
        var cleanupCalls = 0
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            stagingIdGenerator = { "run-success-cleanup-throws" },
            deleteDirectory = {
                cleanupCalls += 1
                throw IOException("cleanup failed")
            }
        )

        val preview = renderer.render(
            imported(
                zipFile("success-cleanup-throws.zip", "index.html" to "new"),
                "zip-success-cleanup-throws"
            )
        ) as PreviewModel.WebFile

        assertEquals(File(cacheRoot, "zip-success-cleanup-throws/index.html").canonicalFile, preview.file)
        assertEquals("new", preview.file.readText())
        assertTrue(cleanupCalls > 0)
        assertEquals(
            "old",
            File(
                cacheRoot,
                "zip-success-cleanup-throws.previous-run-success-cleanup-throws/index.html"
            ).readText()
        )
    }

    @Test
    fun mapsZipResourceLimitsToDistinctChineseRecoveryCopy() {
        val renderer = ZipWebPackageRenderer(temp.newFolder("copy-cache"))
        val sourceSize = renderer.errorForZipFailure(
            SourceFileTooLargeException(
                100L * ImportResourcePolicy.MIB + 1,
                100L * ImportResourcePolicy.MIB
            )
        ) as PreviewModel.Error
        val entryCount = renderer.errorForZipFailure(
            TooManyZipEntriesException(actualCount = 2, limitCount = 1)
        ) as PreviewModel.Error
        val entrySize = renderer.errorForZipFailure(
            ZipEntryTooLargeException(
                entryName = "video.bin",
                actualBytes = 50L * ImportResourcePolicy.MIB + 1,
                limitBytes = 50L * ImportResourcePolicy.MIB
            )
        ) as PreviewModel.Error
        val totalSize = renderer.errorForZipFailure(
            ZipExpandedTooLargeException(
                actualBytes = 250L * ImportResourcePolicy.MIB + 1,
                limitBytes = 250L * ImportResourcePolicy.MIB
            )
        ) as PreviewModel.Error

        assertTrue(sourceSize.message.contains("ZIP 源文件"))
        assertTrue(sourceSize.message.contains("100 MiB"))
        assertTrue(entryCount.message.contains("条目数量"))
        assertTrue(entryCount.message.contains("安全上限"))
        assertTrue(entrySize.message.contains("单个文件"))
        assertTrue(entrySize.message.contains("video.bin"))
        assertTrue(entrySize.message.contains("50 MiB"))
        assertTrue(totalSize.message.contains("解压后的内容"))
        assertTrue(totalSize.message.contains("250 MiB"))
    }

    @Test
    fun blocksOversizedLegacyZipBeforeCreatingStagingOrExtracting() {
        val source = temp.newFile("legacy.zip").apply {
            RandomAccessFile(this, "rw").use {
                it.setLength(100L * ImportResourcePolicy.MIB + 1)
            }
        }
        val cacheRoot = temp.newFolder("legacy-cache")
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            stagingIdGenerator = { "legacy-run" }
        )

        val error = renderer.render(imported(source, "legacy-id")) as PreviewModel.Error

        assertTrue(error.message.contains("ZIP 源文件"))
        assertTrue(error.message.contains("100 MiB"))
        assertFalse(File(cacheRoot, "legacy-id.partial-legacy-run").exists())
        assertFalse(File(cacheRoot, "legacy-id").exists())
    }

    @Test
    fun cancellationWinsOverOversizedLegacyZipBeforeCreatingTransactionPaths() {
        val source = temp.newFile("cancelled-legacy.zip").apply {
            RandomAccessFile(this, "rw").use {
                it.setLength(100L * ImportResourcePolicy.MIB + 1)
            }
        }
        val cacheRoot = temp.newFolder("cancelled-legacy-cache")
        val expected = FileTaskCancelledException()
        var generatedRunId = false
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            stagingIdGenerator = {
                generatedRunId = true
                "cancelled-legacy-run"
            }
        )

        val actual = assertThrows<FileTaskCancelledException> {
            renderer.render(
                imported(source, "cancelled-legacy-id"),
                cancellationToken = FileTaskCancellationToken { throw expected }
            )
        }

        assertSame(expected, actual)
        assertFalse(generatedRunId)
        assertTrue(cacheRoot.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun deletedImportIdCannotBeRecreatedByALateRenderer() {
        val filesDir = temp.newFolder("deleted-id-files")
        val cacheDir = temp.newFolder("deleted-id-cache")
        val cacheRoot = File(cacheDir, "zip-preview").apply { mkdirs() }
        ImportCleanup.deleteImportAndCache(
            importId = "deleted-id",
            filesDir = filesDir,
            cacheDir = cacheDir,
            scheduleDeletion = { it.deleteRecursively() }
        )
        File(cacheRoot, "deleted-id").apply {
            mkdirs()
            File(this, "index.html").writeText("late official")
        }
        File(cacheRoot, "deleted-id.partial-late-run").apply {
            mkdirs()
            File(this, "index.html").writeText("late partial")
        }
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            scheduleDeletion = { it.deleteRecursively() }
        )

        assertThrows<FileTaskCancelledException> {
            renderer.render(
                imported(zipFile("deleted-id.zip", "index.html" to "late"), "deleted-id")
            )
        }

        assertFalse(File(cacheRoot, "deleted-id").exists())
        assertTrue(cacheRoot.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun oversizedLegacyZipStillRecoversOrphanedPreviousCacheWithoutStartingNewTransaction() {
        val source = temp.newFile("oversized-orphan.zip").apply {
            RandomAccessFile(this, "rw").use {
                it.setLength(100L * ImportResourcePolicy.MIB + 1)
            }
        }
        val cacheRoot = temp.newFolder("oversized-orphan-cache")
        val backup = File(cacheRoot, "oversized-orphan-id.previous-old-run").apply {
            mkdirs()
            File(this, "index.html").writeText("recoverable")
        }
        var generatedRunId = false
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            stagingIdGenerator = {
                generatedRunId = true
                "must-not-run"
            }
        )

        val preview = renderer.render(imported(source, "oversized-orphan-id"))

        assertTrue(preview is PreviewModel.Error)
        assertFalse(generatedRunId)
        assertEquals(
            "recoverable",
            File(cacheRoot, "oversized-orphan-id/index.html").readText()
        )
        assertFalse(backup.exists())
    }

    @Test
    fun transactionRecoveryRestoresBackupWhenUncommittedTargetAlsoExists() {
        val source = temp.newFile("uncommitted-target.zip").apply {
            RandomAccessFile(this, "rw").use {
                it.setLength(100L * ImportResourcePolicy.MIB + 1)
            }
        }
        val cacheRoot = temp.newFolder("uncommitted-target-cache")
        File(cacheRoot, "uncommitted-id").apply {
            mkdirs()
            File(this, "index.html").writeText("uncommitted")
        }
        val backup = File(cacheRoot, "uncommitted-id.previous-crashed-run").apply {
            mkdirs()
            File(this, "index.html").writeText("old")
        }
        val renderer = ZipWebPackageRenderer(cacheRoot = cacheRoot)

        val preview = renderer.render(imported(source, "uncommitted-id"))

        assertTrue(preview is PreviewModel.Error)
        assertEquals("old", File(cacheRoot, "uncommitted-id/index.html").readText())
        assertFalse(backup.exists())
    }

    @Test
    fun failedRenderRestoresOnlyRecoverableBackupWhenTargetIsMissing() {
        val cacheRoot = temp.newFolder("cache-orphan-backup")
        val backup = File(cacheRoot, "zip-orphan.previous-run-orphan").apply {
            mkdirs()
            File(this, "index.html").writeText("recoverable")
        }
        val renderer = ZipWebPackageRenderer(
            cacheRoot = cacheRoot,
            resourcePolicy = ImportResourcePolicy.DEFAULT.copy(maxZipEntryBytes = 1),
            stagingIdGenerator = { "run-orphan" }
        )

        val preview = renderer.render(
            imported(zipFile("orphan.zip", "index.html" to "too large"), "zip-orphan")
        )

        assertTrue(preview is PreviewModel.Error)
        assertEquals("recoverable", File(cacheRoot, "zip-orphan/index.html").readText())
        assertFalse(backup.exists())
        assertFalse(File(cacheRoot, "zip-orphan.partial-run-orphan").exists())
    }

    private fun imported(file: File, id: String): ImportedFile {
        return ImportedFile(
            id = id,
            displayName = file.name,
            fileType = FileType.ZIP,
            sourceKind = SourceKind.APP_PICKER,
            localFile = file,
            sizeBytes = file.length()
        )
    }

    private fun zipFile(name: String, vararg entries: Pair<String, String>): File {
        val file = temp.newFile(name)
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (entryName, content) ->
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return file
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit): T {
        return try {
            block()
            throw AssertionError("Expected ${T::class.java.simpleName}")
        } catch (error: Throwable) {
            if (error !is T) throw error
            error
        }
    }
}
