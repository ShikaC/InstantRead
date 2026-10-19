package com.personalai.instantread

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.*
import com.personalai.instantread.history.*
import com.personalai.instantread.importing.*
import com.personalai.instantread.preview.*
import com.personalai.instantread.settings.PreviewSettingsRepository
import com.personalai.instantread.tasks.*
import com.personalai.instantread.ui.*
import java.io.File

class MainActivity : Activity() {
    private lateinit var previewController: PreviewController
    private lateinit var foregroundFileTaskController: ForegroundFileTaskController
    private lateinit var importWorkflowController: ImportWorkflowController
    private lateinit var previewPreparationController: PreviewPreparationController
    private lateinit var previewTaskPresentationController: PreviewTaskPresentationController
    private lateinit var historyRepository: PreviewHistoryRepository
    private lateinit var settingsRepository: PreviewSettingsRepository
    private lateinit var homeViewFactory: HomeViewFactory
    private lateinit var settingsViewFactory: SettingsViewFactory
    private lateinit var zipCacheSettingsController: ZipCacheSettingsController
    private lateinit var foregroundTaskViewFactory: ForegroundTaskViewFactory
    private lateinit var previewWebViewFactory: PreviewWebViewFactory
    private lateinit var previewWorkspaceViewFactory: PreviewWorkspaceViewFactory
    private lateinit var previewChromeController: PreviewChromeController
    private lateinit var previewSearchController: PreviewSearchController
    private lateinit var previewScrollPositionController: PreviewScrollPositionController
    private lateinit var previewPageReadyController: PreviewPageReadyController
    private lateinit var previewReadingDisplayController: PreviewReadingDisplayController
    private lateinit var previewScriptAuthorizationController: PreviewScriptAuthorizationController
    private lateinit var previewActionsController: PreviewWorkspaceActionsController
    private var isShowingPreview = false
    private var isShowingSettings = false
    private var isShowingTask = false
    private var restoreHomeAfterStoppedTask = false
    private var pendingDeleteHistoryId: String? = null
    private var homeListFilter = HomeListFilter.ALL
    private var homeSearchQuery = ""
    private var previewDocumentMode = PreviewDocumentMode.READING
    private var pendingPreviewDocumentMode: PreviewDocumentMode? = null
    private var previewJavaScriptEnabled = false
    private var previewSupportsUserScriptAuthorization = true
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SystemBars.applyFigmaCanvas(this)
        val mainHandler = Handler(Looper.getMainLooper())
        previewTaskPresentationController = PreviewTaskPresentationController(HandlerDelayedTaskScheduler(mainHandler))
        foregroundFileTaskController = ForegroundFileTaskController(
            callbackDispatcher = FileTaskCallbackDispatcher { block -> mainHandler.post(block) }
        )
        previewController = PreviewController(zipRenderer = ZipWebPackageRenderer(File(cacheDir, "zip-preview")))
        importWorkflowController = ImportWorkflowController(
            UriImportRequestFactory(contentResolver),
            FileImportController(ImportedFileStore(filesDir)),
            foregroundFileTaskController
        )
        previewPreparationController = PreviewPreparationController(foregroundFileTaskController, previewController)
        historyRepository = PreviewHistoryRepository(PreviewHistoryDatabase(this))
        settingsRepository = PreviewSettingsRepository(this)
        homeViewFactory = HomeViewFactory(this)
        settingsViewFactory = SettingsViewFactory(this)
        zipCacheSettingsController = ZipCacheSettingsController(foregroundFileTaskController, cacheDir)
        foregroundTaskViewFactory = ForegroundTaskViewFactory(this)
        previewWorkspaceViewFactory = PreviewWorkspaceViewFactory(this)
        previewSearchController = PreviewSearchController { refreshPreviewChrome(it) }
        previewScrollPositionController = PreviewScrollPositionController(PreviewReadingPositionRepository(this))
        previewPageReadyController = PreviewPageReadyController(previewScrollPositionController, previewSearchController)
        previewReadingDisplayController = PreviewReadingDisplayController(::refreshPreviewChrome, ::refreshPreviewContent, settingsRepository::setReadingDisplayOptions)
        previewScriptAuthorizationController = PreviewScriptAuthorizationController(historyRepository, { _, confirm -> PreviewScriptConsentDialog.show(this, confirm) }, { openPreview(it) })
        previewActionsController = PreviewWorkspaceActionsController(settingsRepository, previewSearchController, previewReadingDisplayController, previewScriptAuthorizationController, { renderHome() }, ::togglePreviewFavorite, ::switchPreviewDocumentMode)
        previewChromeController = PreviewChromeController(previewWorkspaceViewFactory, previewActionsController::actions, ::previewState)
        previewWebViewFactory = PreviewWebViewFactory(this, ::openExternalUrl)
        ImportCleanup.scheduleDetachedCleanup(filesDir, cacheDir, historyRepository.listRecent().mapTo(mutableSetOf()) { it.id }); renderHome()
        if (savedInstanceState == null) handleIncomingIntent(intent)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); restoreHomeAfterStoppedTask = false; renderHome(); handleIncomingIntent(intent) }
    override fun onPause() { previewScrollPositionController.saveCurrent(); super.onPause() }
    override fun onStop() {
        if (isShowingTask) {
            isShowingTask = false
            restoreHomeAfterStoppedTask = true
        }
        previewReadingDisplayController.cancelPending()
        cancelPreviewTaskPresentation()
        foregroundFileTaskController.cancelCurrent()
        super.onStop()
    }
    override fun onStart() { super.onStart(); if (restoreHomeAfterStoppedTask) { restoreHomeAfterStoppedTask = false; renderHome() } }
    override fun onDestroy() { if (::previewTaskPresentationController.isInitialized) cancelPreviewTaskPresentation(); if (::foregroundFileTaskController.isInitialized) foregroundFileTaskController.close(); super.onDestroy() }
    @Deprecated("Use Activity Result APIs after the first minimal import flow is stable.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OPEN_DOCUMENT && resultCode == RESULT_OK) data?.data?.let { importUri(it, SourceKind.APP_PICKER) }
    }
    @Deprecated("Use OnBackPressedDispatcher when migrating to AppCompat or AndroidX activity.")
    override fun onBackPressed() { if (isShowingPreview || isShowingSettings || isShowingTask) renderHome() else super.onBackPressed() }
    private fun renderHome(message: String? = null) {
        cancelPreviewTaskPresentation(); foregroundFileTaskController.cancelCurrent()
        previewScrollPositionController.saveCurrent()
        isShowingPreview = false; isShowingSettings = false; isShowingTask = false
        previewChromeController.clear(); previewSearchController.clear(); previewScrollPositionController.clear(); previewPageReadyController.clear(); previewReadingDisplayController.clear()
        val settings = settingsRepository.getSettings()
        val allRecentFiles = historyRepository.listRecent().map { it.toRecentFileItem() }
        val pendingDeleteFile = allRecentFiles.firstOrNull { it.id == pendingDeleteHistoryId }
        setContentView(homeViewFactory.create(
            message = message,
            recentFiles = allRecentFiles.filterFor(homeListFilter).filterBySearch(homeSearchQuery),
            isDarkTheme = settings.isDarkTheme,
            selectedFilter = homeListFilter,
            searchQuery = homeSearchQuery,
            totalRecentCount = allRecentFiles.size,
            favoriteRecentCount = allRecentFiles.count { it.isFavorite },
            actions = HomeActions(
                onChooseFile = ::openDocumentPicker,
                onOpenSettings = { renderSettings() },
                onClearCache = {},
                onOpenHistory = ::reopenPreview,
                onToggleFavorite = { id -> historyRepository.toggleFavorite(id); pendingDeleteHistoryId = null; renderHome() },
                onDeleteHistory = { id ->
                    if (pendingDeleteHistoryId == id) deleteHistory(id) else {
                        pendingDeleteHistoryId = id
                        renderHome()
                    }
                },
                onCancelDelete = { pendingDeleteHistoryId = null; renderHome() },
                onToggleTheme = { settingsRepository.setDarkTheme(!settings.isDarkTheme); renderHome() },
                onFilterChanged = { filter -> homeListFilter = filter; pendingDeleteHistoryId = null; renderHome() },
                onSearchQueryChanged = { query -> homeSearchQuery = query; pendingDeleteHistoryId = null; renderHome() }
            ),
            pendingDeleteFile = pendingDeleteFile
        ))
    }
    private fun renderSettings(message: String? = null) {
        foregroundFileTaskController.cancelCurrent()
        previewScrollPositionController.saveCurrent()
        isShowingPreview = false; isShowingSettings = true; isShowingTask = false
        previewChromeController.clear(); previewSearchController.clear(); previewScrollPositionController.clear(); previewPageReadyController.clear(); previewReadingDisplayController.clear()
        val settings = settingsRepository.getSettings()
        setContentView(settingsViewFactory.create(
            state = SettingsViewState(
                message = message,
                zipCacheSummary = "正在统计",
                readingComfort = settings.readingComfort
            ),
            actions = SettingsActions(
                onReadingComfortChanged = { comfort -> settingsRepository.setReadingComfort(comfort); renderSettings() },
                onClearCache = {
                    zipCacheSettingsController.clear(
                        onCompleted = { renderSettings(ZipPreviewCacheCopy.clearResultMessage(it)) },
                        onFailure = { renderSettings(ImportCleanupCopy.zipCacheClearFailureMessage()) }
                    )
                },
                onBack = { renderHome() }
            )
        ))
        zipCacheSettingsController.load { if (isShowingSettings) settingsViewFactory.updateZipCacheSummary(findViewById(android.R.id.content), ZipPreviewCacheCopy.summary(it)) }
    }
    private fun showPreview(history: PreviewHistory, preview: PreviewModel) {
        when (preview) {
            is PreviewModel.WebFile -> showWebView(history, preview.javaScriptEnabled, preview.resourceRoot, preview.supportsUserScriptAuthorization) { it.loadUrl(preview.file.toURI().toString()) }
            is PreviewModel.WebHtml -> showWebView(history, preview.javaScriptEnabled, null, true) { it.loadDataWithBaseURL(null, preview.html, "text/html", "utf-8", null) }
            is PreviewModel.Error -> renderHome("${preview.title}\n${preview.message}")
        }
    }
    private fun showWebView(history: PreviewHistory, javaScriptEnabled: Boolean, resourceRoot: File?, supportsUserScriptAuthorization: Boolean, load: (android.webkit.WebView) -> Unit) {
        isShowingPreview = true; isShowingSettings = false; isShowingTask = false
        previewJavaScriptEnabled = javaScriptEnabled
        previewSupportsUserScriptAuthorization = supportsUserScriptAuthorization
        val pageLoad = previewPageReadyController.beginLoad(history.id)
        val webView = previewWebViewFactory.create(
            javaScriptEnabled = javaScriptEnabled,
            resourceRoot = resourceRoot,
            onPageFinished = { previewPageReadyController.onPageFinished(pageLoad) },
            onResourceBlocked = { previewChromeController.noteBlockedResource(history.id); refreshPreviewChrome(history.id) },
            onLocalResourceMissing = { previewChromeController.noteMissingLocalResource(history.id); refreshPreviewChrome(history.id) },
            onMainFrameLoadFailed = { renderHome(PREVIEW_LOAD_FAILURE_MESSAGE) }
        )
        bindPreviewDocument(history, webView)
        val workspace = previewWorkspace(history, webView)
        previewChromeController.bind(history.id, workspace)
        setContentView(workspace)
        load(webView)
    }
    private fun previewWorkspace(history: PreviewHistory, contentView: android.view.View): android.view.View =
        previewWorkspaceViewFactory.create(previewState(history), contentView, previewActionsController.actions(history.id))
    private fun bindPreviewDocument(history: PreviewHistory, webView: android.webkit.WebView) {
        // Markdown 与 TXT 都渲染为生成阅读页，搜索和阅读位置绑定保持同一条件。
        val isDecodedText = history.fileType == FileType.MARKDOWN || history.fileType == FileType.TEXT
        if (isDecodedText) previewSearchController.bind(history.id, WebViewSearchablePreviewDocument(webView)) else previewSearchController.clear()
        if (isDecodedText) previewScrollPositionController.bind(history.id, previewDocumentMode, WebViewScrollablePreviewDocument(webView)) else previewScrollPositionController.clear()
    }
    private fun openDocumentPicker() = startActivityForResult(
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, REQUEST_OPEN_DOCUMENT
    )
    private fun handleIncomingIntent(intent: Intent?) {
        IncomingIntentUriResolver.resolve(intent)?.let { incoming ->
            importUri(incoming.uri, incoming.sourceKind, incoming.mimeType, incoming.entryAction, incoming.entryUriSource)
        }
    }
    private fun importUri(uri: Uri, sourceKind: SourceKind, intentMimeType: String? = null, entryAction: String? = null, entryUriSource: String? = null) {
        foregroundFileTaskController.cancelCurrent(); isShowingPreview = false; isShowingSettings = false; isShowingTask = true
        importWorkflowController.start(
            ImportWorkflowInput(uri, sourceKind, intentMimeType, entryAction, entryUriSource),
            onProgress = ::renderForegroundTask,
            onSuccess = { imported ->
                val history = historyRepository.upsertImported(imported)
                homeListFilter = HomeListFilter.ALL; homeSearchQuery = ""
                openPreview(history)
            },
            onFailure = { renderHome(it.toUserMessage()) }
        )
    }
    private fun reopenPreview(id: String) = historyRepository.markOpened(id)
        ?.let(::openPreview) ?: renderHome("记录不存在或已删除。")
    private fun openPreview(history: PreviewHistory, mode: PreviewDocumentMode = PreviewDocumentMode.READING) {
        previewReadingDisplayController.abandonPending()
        cancelPreviewTaskPresentation(); foregroundFileTaskController.cancelCurrent()
        isShowingPreview = false; isShowingSettings = false; isShowingTask = false
        previewDocumentMode = mode
        if (history.fileType == FileType.HTML) showPreview(history, previewController.preview(history.toImportedFile(), currentPreviewOptions(history)))
        else {
            renderForegroundTask(previewPreparationController.initialState(history))
            preparePreview(history, ::renderForegroundTask)
        }
    }
    private fun switchPreviewDocumentMode(id: String, mode: PreviewDocumentMode) {
        if (pendingPreviewDocumentMode == mode) return
        if (mode == previewDocumentMode && !previewTaskPresentationController.isPending) return
        previewScrollPositionController.saveCurrent(); previewSearchController.close(id); previewReadingDisplayController.cancelPending()
        cancelPreviewTaskPresentation(); foregroundFileTaskController.cancelCurrent()
        if (mode == previewDocumentMode) return
        val history = historyRepository.markOpened(id) ?: return renderHome("记录不存在或已删除。")
        previewTaskPresentationController.begin(previewPreparationController.initialState(history), ::renderForegroundTask); pendingPreviewDocumentMode = mode
        preparePreview(history, previewTaskPresentationController::update, currentPreviewOptions(history).copy(markdownDocumentMode = mode)) { current, preview ->
            previewTaskPresentationController.complete(); pendingPreviewDocumentMode = null
            if (preview !is PreviewModel.Error) previewDocumentMode = mode
            showPreview(current, preview)
        }
    }
    private fun cancelPreviewTaskPresentation() { pendingPreviewDocumentMode = null; previewTaskPresentationController.cancel() }
    private fun preparePreview(history: PreviewHistory, onProgress: (ForegroundTaskState) -> Unit, options: PreviewOptions = currentPreviewOptions(history), onCompleted: (PreviewHistory, PreviewModel) -> Unit = ::showPreview) {
        previewPreparationController.start(history, options, onProgress) { preview ->
            historyRepository.findById(history.id)?.let { current -> onCompleted(current, preview) }
                ?: renderHome("记录不存在或已删除。")
        }
    }
    private fun renderForegroundTask(state: ForegroundTaskState) {
        isShowingPreview = false; isShowingSettings = false; isShowingTask = true
        setContentView(
            foregroundTaskViewFactory.create(state, settingsRepository.getSettings().isDarkTheme) {
                foregroundFileTaskController.cancelCurrent(); renderHome()
            }
        )
    }
    private fun refreshPreviewChrome(id: String): Boolean =
        historyRepository.findById(id)?.let { previewChromeController.refresh(id, it) } ?: false
    private fun refreshPreviewContent(request: ReadingDisplayPreparationRequest) {
        cancelPreviewTaskPresentation(); foregroundFileTaskController.cancelCurrent()
        val id = request.historyId
        val history = historyRepository.findById(id) ?: return renderHome("记录不存在或已删除。")
        previewScrollPositionController.saveCurrent()
        if (history.fileType == FileType.HTML) showPreview(history, previewController.preview(history.toImportedFile(), currentPreviewOptions(history)))
        else preparePreview(history, {}, currentPreviewOptions(history).copy(readingDisplayOptions = request.options)) { current, preview ->
            val success = preview !is PreviewModel.Error
            if (previewReadingDisplayController.complete(request, success, previewScrollPositionController::saveCurrent) && success) showPreview(current, preview)
        }
    }
    private fun togglePreviewFavorite(id: String) {
        val updated = historyRepository.toggleFavorite(id) ?: return renderHome("记录不存在或已删除。")
        if (!refreshPreviewChrome(updated.id)) openPreview(updated, previewDocumentMode)
    }
    private fun previewState(history: PreviewHistory): PreviewWorkspaceState = settingsRepository.getSettings().let { settings ->
        history.toPreviewWorkspaceState(previewJavaScriptEnabled, settings.isDarkTheme, previewDocumentMode, previewSearchController.stateFor(history.id), previewReadingDisplayController.isPanelOpen, previewReadingDisplayController.optionsFor(history.id, settings.readingDisplayOptions), previewSupportsUserScriptAuthorization)
    }
    private fun deleteHistory(id: String) {
        pendingDeleteHistoryId = null
        val deleted = historyRepository.delete(id) ?: return renderHome("记录不存在或已删除。")
        val cleanupScheduled = runCatching { ImportCleanup.deleteImportAndCache(deleted.id, filesDir, cacheDir) }.isSuccess
        renderHome(ImportCleanupCopy.deletedMessage(deleted.displayTitle(), cleanupScheduled))
    }
    private fun openExternalUrl(url: String) = try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        renderHome("无法打开外部链接：手机上没有可处理该链接的应用。")
    }
    private fun currentPreviewOptions(history: PreviewHistory) = history.toPreviewOptions(settingsRepository.getSettings(), previewDocumentMode)
    private companion object {
        const val REQUEST_OPEN_DOCUMENT = 1001
        const val PREVIEW_LOAD_FAILURE_MESSAGE = "页面加载失败，文件可能已损坏或被移除。请返回后重新打开，或重新选择文件导入。"
    }
}
