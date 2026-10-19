package com.personalai.instantread.architecture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArchitectureBoundaryTest {
    @Test
    fun mainActivityDelegatesUiConstructionAndInfrastructureDetails() {
        val source = mainSource("MainActivity.kt").readText()

        assertTrue("MainActivity should stay small enough to review: ${source.lines().size} lines.", source.lines().size <= 300)
        assertFalse("MainActivity should delegate Android widget construction to ui factories.", source.contains("import android.widget."))
        assertFalse("MainActivity should delegate WebView creation to ui factories.", source.contains("import android.webkit.WebView"))
        assertFalse("MainActivity should not handle raw ZIP streams.", source.contains("ZipInputStream"))
        assertFalse("MainActivity should not own SQLite schema details.", source.contains("SQLiteOpenHelper"))
        assertFalse("MainActivity should delegate byte copying.", source.contains("copyTo("))
        assertFalse("MainActivity should delegate ZIP extraction.", source.contains("SafeZipExtractor"))
        assertFalse("MainActivity should delegate text decoding.", source.contains(".readText("))
        assertTrue("MainActivity should render foreground file tasks.", source.contains("ForegroundTaskViewFactory"))
        assertTrue("MainActivity should delegate import workflows.", source.contains("ImportWorkflowController"))
        assertTrue("MainActivity should delegate preview preparation.", source.contains("PreviewPreparationController"))
        assertTrue("MainActivity should start delegated preview preparation.", source.contains("previewPreparationController.start("))
        assertTrue(
            "MainActivity should retry crash-leftover file cleanup on startup.",
            source.contains("ImportCleanup.scheduleDetachedCleanup(") &&
                source.contains("historyRepository.listRecent().mapTo")
        )
        assertTrue(
            "Settings ZIP cache I/O should run through a background controller.",
            source.contains("ZipCacheSettingsController") &&
                source.contains("zipCacheSettingsController.load") &&
                source.contains("zipCacheSettingsController.clear")
        )
        assertFalse(
            "MainActivity must not scan or recursively clear ZIP caches on the main thread.",
            source.contains("ImportCleanup.zipPreviewCacheStats") ||
                source.contains("ImportCleanup.clearZipPreviewCache")
        )
    }

    @Test
    fun mainActivityPreparesGeneratedPreviewsWithoutBlockingTheUiThread() {
        val source = mainSource("MainActivity.kt").readText()
        val openPreview = source.substringAfter("private fun openPreview")
            .substringBefore("private fun switchPreviewDocumentMode")
        val switchPreviewDocumentMode = source.substringAfter("private fun switchPreviewDocumentMode")
            .substringBefore("private fun cancelPreviewTaskPresentation")
        val cancelPreviewTaskPresentation = source.substringAfter("private fun cancelPreviewTaskPresentation")
            .substringBefore("private fun preparePreview")
        val preparePreview = source.substringAfter("private fun preparePreview")
            .substringBefore("private fun renderForegroundTask")
        val refreshPreview = source.substringAfter("private fun refreshPreviewContent")
            .substringBefore("private fun togglePreviewFavorite")
        // 预览交互动作已抽到独立控制器；这里改为检查该文件，断言内容不放松。
        val previewActions = mainSource("PreviewWorkspaceActionsController.kt").readText()

        assertTrue(
            "HTML should remain a direct synchronous preview.",
            openPreview.contains("history.fileType == FileType.HTML") &&
                openPreview.contains("showPreview(history, previewController.preview")
        )
        assertTrue(
            "Markdown, text, and ZIP should use foreground preview preparation.",
            openPreview.contains("preparePreview(history, ::renderForegroundTask)")
        )
        assertTrue(
            "Generated preview preparation must be lifecycle-visible before its first progress callback.",
            openPreview.indexOf("renderForegroundTask(previewPreparationController.initialState(history))") in 0 until
                openPreview.indexOf("preparePreview(history, ::renderForegroundTask)")
        )
        assertTrue(
            "Document-mode actions must use the delayed replacement flow.",
            previewActions.contains("onDocumentModeChanged = { mode -> onDocumentModeChanged(id, mode) }")
        )
        assertFalse(
            "Document-mode actions must not reopen the preview through the first-open flow.",
            previewActions.contains("openPreview(it, mode)")
        )
        assertTrue(
            "Document-mode replacement must track the target that is already pending.",
            source.contains("private var pendingPreviewDocumentMode: PreviewDocumentMode? = null")
        )
        val pendingTargetGuard = switchPreviewDocumentMode.indexOf("if (pendingPreviewDocumentMode == mode) return")
        val replacementCancellation = switchPreviewDocumentMode.indexOf("cancelPreviewTaskPresentation()")
        val presenterBegin = switchPreviewDocumentMode.indexOf("previewTaskPresentationController.begin(")
        assertTrue(
            "Repeated taps on the same pending target must return before cancellation or restart.",
            pendingTargetGuard >= 0 && replacementCancellation > pendingTargetGuard && presenterBegin > pendingTargetGuard
        )
        assertTrue(
            "Document-mode replacement should buffer task presentation and stream only the latest state.",
            presenterBegin >= 0 &&
                switchPreviewDocumentMode.contains("previewTaskPresentationController::update")
        )
        assertTrue(
            "Document-mode replacement must prepare with immutable target-mode options.",
            switchPreviewDocumentMode.contains("currentPreviewOptions(history).copy(markdownDocumentMode = mode)")
        )
        val pendingTargetAssignment = switchPreviewDocumentMode.indexOf("pendingPreviewDocumentMode = mode")
        val prepareReplacement = switchPreviewDocumentMode.indexOf("preparePreview(history, previewTaskPresentationController::update")
        assertTrue(
            "A replacement target must become pending after presentation begins and before preparation starts.",
            pendingTargetAssignment > presenterBegin && prepareReplacement > pendingTargetAssignment
        )
        val presenterComplete = switchPreviewDocumentMode.indexOf("previewTaskPresentationController.complete()")
        val pendingTargetClear = switchPreviewDocumentMode.indexOf("pendingPreviewDocumentMode = null")
        val successfulModeAssignment = switchPreviewDocumentMode.indexOf("if (preview !is PreviewModel.Error) previewDocumentMode = mode")
        val replacementPreview = switchPreviewDocumentMode.indexOf("showPreview(current, preview)")
        assertTrue(
            "Replacement completion must dismiss presentation, clear pending target, commit only success, then replace the preview.",
            presenterComplete >= 0 && pendingTargetClear > presenterComplete &&
                successfulModeAssignment > pendingTargetClear && replacementPreview > successfulModeAssignment
        )
        assertTrue(
            "Presenter cancellation must clear the pending target before cancelling the reveal.",
            cancelPreviewTaskPresentation.indexOf("pendingPreviewDocumentMode = null") in 0 until
                cancelPreviewTaskPresentation.indexOf("previewTaskPresentationController.cancel()")
        )
        assertTrue(
            "All presenter cancellation boundaries must use the pending-aware helper.",
            Regex("previewTaskPresentationController\\.cancel\\(\\)").findAll(source).count() == 1
        )
        assertTrue(
            "Prepared preview completion must re-fetch the current history record by id.",
            preparePreview.contains("historyRepository.findById(history.id)") &&
                preparePreview.contains("onCompleted(current, preview)")
        )
        assertTrue(
            "Aa refresh should preserve the current preview while preparing replacement content.",
            refreshPreview.indexOf("previewScrollPositionController.saveCurrent()") in 0 until
                refreshPreview.indexOf("preparePreview(history, {},") &&
                Regex("preparePreview\\(history,\\s*\\{\\s*},").containsMatchIn(refreshPreview)
        )
        assertTrue(
            "HTML Aa refresh should remain synchronous.",
            refreshPreview.contains("history.fileType == FileType.HTML") &&
                refreshPreview.contains("showPreview(history, previewController.preview")
        )
        assertTrue(
            "A typed preparation error must keep the existing Aa preview visible.",
            refreshPreview.contains("preview !is PreviewModel.Error")
        )
        assertTrue(
            "Aa refresh must save again after background generation and before replacing the WebView.",
            refreshPreview.indexOf("previewScrollPositionController::saveCurrent") in 0 until
                refreshPreview.indexOf("showPreview(current, preview)")
        )
    }

    @Test
    fun mainActivityCommitsReadingDisplayOptionsOnlyAfterMatchingPreviewSuccess() {
        val source = mainSource("MainActivity.kt").readText()
        val onCreate = source.substringAfter("override fun onCreate")
            .substringBefore("override fun onNewIntent")
        val onStop = source.substringAfter("override fun onStop()")
            .substringBefore("override fun onStart()")
        val openPreview = source.substringAfter("private fun openPreview")
            .substringBefore("private fun preparePreview")
        val refreshPreview = source.substringAfter("private fun refreshPreviewContent")
            .substringBefore("private fun togglePreviewFavorite")
        val previewState = source.substringAfter("private fun previewState")
            .substringBefore("private fun deleteHistory")
        // 预览交互动作已抽到独立控制器；这里改为检查该文件，断言内容不放松。
        val previewActions = mainSource("PreviewWorkspaceActionsController.kt").readText()
        val workspaceSource = mainSource("ui/PreviewWorkspaceViewFactory.kt").readText()

        assertFalse(
            "Reading display changes must not persist before preview generation succeeds.",
            onCreate.contains("settingsRepository.setReadingDisplayOptions(options);")
        )
        assertTrue(
            "The reading display controller should own persistence after completion.",
            onCreate.contains("settingsRepository::setReadingDisplayOptions")
        )
        assertTrue(
            "onStop must discard pending reading display options before cancelling the worker.",
            onStop.indexOf("previewReadingDisplayController.cancelPending()") in 0 until
                onStop.indexOf("foregroundFileTaskController.cancelCurrent()")
        )
        assertTrue(
            "Opening another preview must abandon pending options before starting another generation.",
            openPreview.indexOf("previewReadingDisplayController.abandonPending()") in 0 until
                openPreview.indexOf("foregroundFileTaskController.cancelCurrent()")
        )
        assertTrue(
            "Aa preparation must render with the immutable request and complete that exact generation.",
            refreshPreview.contains("currentPreviewOptions(history).copy(readingDisplayOptions = request.options)") &&
                refreshPreview.contains("previewReadingDisplayController.complete(request, success, previewScrollPositionController::saveCurrent)")
        )
        assertTrue(
            "Preview chrome must expose staged effective options while preparation is running.",
            previewState.contains("previewReadingDisplayController.optionsFor(history.id, settings.readingDisplayOptions)")
        )
        assertTrue(
            "Slider edits must be accumulated from committed or pending controller state.",
            previewActions.contains("readingDisplayController.apply(id, settingsRepository.getSettings().readingDisplayOptions, edit)") &&
                workspaceSource.contains("onReadingDisplayChanged { current ->")
        )
    }

    @Test
    fun mainActivityCancelsVisibleTasksAcrossNavigationAndLifecycle() {
        val source = mainSource("MainActivity.kt").readText()
        val onStop = source.substringAfter("override fun onStop()")
            .substringBefore("override fun onStart()")
        val onDestroy = source.substringAfter("override fun onDestroy()")
            .substringBefore("@Deprecated")
        val renderHome = source.substringAfter("private fun renderHome")
            .substringBefore("private fun renderSettings")
        val openPreview = source.substringAfter("private fun openPreview")
            .substringBefore("private fun switchPreviewDocumentMode")
        val switchPreviewDocumentMode = source.substringAfter("private fun switchPreviewDocumentMode")
            .substringBefore("private fun cancelPreviewTaskPresentation")
        val refreshPreview = source.substringAfter("private fun refreshPreviewContent")
            .substringBefore("private fun togglePreviewFavorite")

        assertTrue(source.contains("isShowingTask"))
        assertTrue(source.contains("restoreHomeAfterStoppedTask"))
        assertTrue(source.contains("override fun onStop()"))
        assertTrue(source.contains("override fun onStart()"))
        assertTrue(source.contains("override fun onDestroy()"))
        assertTrue(source.contains("foregroundFileTaskController.cancelCurrent()"))
        assertTrue(source.contains("foregroundFileTaskController.close()"))
        assertTrue(
            "onStop must also cancel hidden Aa preview preparation after preserving visible-task restore state.",
            onStop.indexOf("foregroundFileTaskController.cancelCurrent()") >
                onStop.indexOf("\n        }")
        )
        assertTrue(
            "onStop must cancel delayed task presentation before cancelling its foreground worker.",
            onStop.indexOf("cancelPreviewTaskPresentation()") in 0 until
                onStop.indexOf("foregroundFileTaskController.cancelCurrent()")
        )
        assertTrue(
            "Home navigation must cancel delayed task presentation before foreground work.",
            renderHome.indexOf("cancelPreviewTaskPresentation()") in 0 until
                renderHome.indexOf("foregroundFileTaskController.cancelCurrent()")
        )
        assertTrue(
            "Opening a preview must replace any delayed presentation before foreground work.",
            openPreview.indexOf("cancelPreviewTaskPresentation()") in 0 until
                openPreview.indexOf("foregroundFileTaskController.cancelCurrent()")
        )
        assertTrue(
            "Aa refresh must replace delayed mode presentation before foreground work.",
            refreshPreview.indexOf("cancelPreviewTaskPresentation()") in 0 until
                refreshPreview.indexOf("foregroundFileTaskController.cancelCurrent()")
        )
        assertTrue(
            "Destroy must defensively cancel delayed task presentation.",
            onDestroy.contains("cancelPreviewTaskPresentation()")
        )
        val saveScroll = switchPreviewDocumentMode.indexOf("previewScrollPositionController.saveCurrent()")
        val closeSearch = switchPreviewDocumentMode.indexOf("previewSearchController.close(id)")
        val cancelReadingDisplay = switchPreviewDocumentMode.indexOf("previewReadingDisplayController.cancelPending()")
        val cancelPresentation = switchPreviewDocumentMode.indexOf("cancelPreviewTaskPresentation()")
        val cancelWorker = switchPreviewDocumentMode.indexOf("foregroundFileTaskController.cancelCurrent()")
        assertTrue(
            "Mode switching must close search state while retaining the current document binding.",
            closeSearch >= 0
        )
        assertFalse(
            "Mode switching must keep the current search document bound until replacement bind or home cleanup.",
            switchPreviewDocumentMode.contains("previewSearchController.clear()")
        )
        assertFalse(
            "Mode switching must refresh chrome when discarding pending Aa options instead of abandoning silently.",
            switchPreviewDocumentMode.contains("previewReadingDisplayController.abandonPending()")
        )
        assertTrue(
            "Mode switching must preserve scroll, close search, cancel pending Aa, then cancel presentation and work.",
            saveScroll >= 0 && closeSearch > saveScroll && cancelReadingDisplay > closeSearch &&
                cancelPresentation > cancelReadingDisplay && cancelWorker > cancelPresentation
        )
        assertTrue(
            "Tapping the committed mode during a pending replacement must cancel and withdraw it.",
            switchPreviewDocumentMode.contains("mode == previewDocumentMode && !previewTaskPresentationController.isPending") &&
                switchPreviewDocumentMode.indexOf("if (mode == previewDocumentMode) return") > cancelWorker
        )
        assertTrue(
            "Imported history must only be written from the successful workflow callback.",
            Regex("onSuccess\\s*=\\s*\\{\\s*imported\\s*->\\s*val history = historyRepository\\.upsertImported\\(imported\\)")
                .containsMatchIn(source)
        )
    }

    @Test
    fun mainActivityConsumesIntentsWithLifecycleSafeOrdering() {
        val source = mainSource("MainActivity.kt").readText()
        val onCreate = source.substringAfter("override fun onCreate")
            .substringBefore("override fun onNewIntent")
        val onNewIntent = source.substringAfter("override fun onNewIntent")
            .substringBefore("override fun onPause")

        assertTrue(
            "A recreated Activity must not import the startup Intent again.",
            onCreate.contains("if (savedInstanceState == null) handleIncomingIntent(intent)")
        )
        val clearRestoreIndex = onNewIntent.indexOf("restoreHomeAfterStoppedTask = false")
        val resetIndex = onNewIntent.indexOf("renderHome()")
        val consumeIndex = onNewIntent.indexOf("handleIncomingIntent(intent)")
        assertTrue(
            "Every new Intent must clear stopped-task restoration, return home, then parse.",
            clearRestoreIndex >= 0 && resetIndex > clearRestoreIndex && consumeIndex > resetIndex
        )
    }

    @Test
    fun mainActivityWrapsPreviewContentInWorkspaceUi() {
        val source = mainSource("MainActivity.kt").readText()

        assertTrue("Preview content should be wrapped by the preview workspace UI.", source.contains("PreviewWorkspaceViewFactory"))
        assertFalse("Preview WebView should not replace the whole screen directly.", source.contains("setContentView(webView)"))
        assertTrue(
            "Single-file generated previews should not claim access to unavailable sibling assets.",
            source.contains("loadDataWithBaseURL(null, preview.html")
        )
    }

    @Test
    fun mainActivityRestoresPreviewStateOnlyAfterThePageFinishesLoading() {
        val source = mainSource("MainActivity.kt").readText()

        assertTrue(
            "Preview state restoration should be triggered from the page-finished callback.",
            source.contains("previewPageReadyController.onPageFinished(pageLoad)")
        )
        assertFalse(
            "Scroll restoration should not run immediately after starting a WebView load.",
            Regex("load\\(webView\\)\\s*previewScrollPositionController\\.restoreCurrent\\(\\)")
                .containsMatchIn(source)
        )
    }

    @Test
    fun mainActivityDoesNotExposeImportDiagnosticsOnHome() {
        val source = mainSource("MainActivity.kt").readText()

        assertFalse(
            "Internal import diagnostics should not be routed into the product home UI.",
            source.contains("ImportSourceDiagnostics")
        )
        assertFalse(
            "Home rendering should not preserve a latest diagnostics string that can reappear after import.",
            source.contains("latestImportDiagnostics")
        )
    }

    @Test
    fun uiFactoriesDoNotImportConcreteDataOrImportAdapters() {
        val uiSources = File(MAIN_SOURCE_ROOT, "ui")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        assertTrue("UI factory package should exist.", uiSources.isNotEmpty())

        uiSources.forEach { sourceFile ->
            val source = sourceFile.readText()
            assertFalse("${sourceFile.name} should not import history persistence.", source.contains("com.personalai.instantread.history"))
            assertFalse("${sourceFile.name} should not import file importing adapters.", source.contains("com.personalai.instantread.importing"))
            assertFalse("${sourceFile.name} should not import ZIP extraction internals.", source.contains("com.personalai.instantread.zip"))
            assertFalse("${sourceFile.name} should not import settings persistence.", source.contains("com.personalai.instantread.settings"))
        }
    }

    private fun mainSource(relativePath: String): File {
        return File(MAIN_SOURCE_ROOT, relativePath)
    }

    private companion object {
        const val MAIN_SOURCE_ROOT = "src/main/java/com/personalai/instantread"
    }
}
