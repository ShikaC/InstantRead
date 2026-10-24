package com.personalai.instantread

import com.personalai.instantread.history.PreviewHistory
import com.personalai.instantread.importing.FileType
import com.personalai.instantread.importing.ImportedFile
import com.personalai.instantread.preview.PreviewOptions
import com.personalai.instantread.preview.ReadingDisplayOptions
import com.personalai.instantread.settings.PreviewSettings
import com.personalai.instantread.ui.HomeListFilter
import com.personalai.instantread.ui.PreviewWorkspaceState
import com.personalai.instantread.ui.RecentFileItem

fun PreviewHistory.toPreviewWorkspaceState(
    javaScriptEnabledForPreview: Boolean,
    isDarkTheme: Boolean,
    documentMode: PreviewDocumentMode = PreviewDocumentMode.READING,
    searchState: PreviewSearchState = PreviewSearchState(),
    isReadingDisplayPanelOpen: Boolean = false,
    readingDisplayOptions: ReadingDisplayOptions = ReadingDisplayOptions.DEFAULT,
    // ZIP 的 Markdown 包由 App 生成阅读页，不执行也不授权脚本；预览模型会传入 false 隐藏入口。
    userScriptAuthorizationAvailable: Boolean = true
): PreviewWorkspaceState {
    val isMarkdown = fileType == FileType.MARKDOWN
    val supportsSearch = fileType == FileType.MARKDOWN || fileType == FileType.TEXT
    val supportsReadingDisplayControls = fileType == FileType.MARKDOWN || fileType == FileType.TEXT
    val supportsScriptAuthorization = userScriptAuthorizationAvailable &&
        (fileType == FileType.HTML || fileType == FileType.ZIP)
    return PreviewWorkspaceState(
        title = displayName ?: localFile.name,
        fileTypeLabel = fileType.name,
        isFavorite = favorite,
        isDarkTheme = isDarkTheme,
        javaScriptStatus = javaScriptStatus(javaScriptEnabledForPreview, supportsScriptAuthorization),
        supportsScriptAuthorization = supportsScriptAuthorization,
        navigationStatus = navigationStatus(),
        supportsDocumentReview = isMarkdown,
        documentMode = if (isMarkdown) documentMode else PreviewDocumentMode.READING,
        supportsSearch = supportsSearch,
        searchState = if (supportsSearch) searchState else PreviewSearchState(),
        supportsReadingDisplayControls = supportsReadingDisplayControls,
        isReadingDisplayPanelOpen = supportsReadingDisplayControls && isReadingDisplayPanelOpen,
        readingDisplayOptions = readingDisplayOptions.clamped()
    )
}

fun PreviewHistory.toImportedFile(): ImportedFile {
    return ImportedFile(
        id = id,
        displayName = displayName,
        fileType = fileType,
        sourceKind = sourceKind,
        localFile = localFile,
        sizeBytes = localFile.length()
    )
}

/**
 * HTML/ZIP 的脚本开关来自该文档自己的授权（`js_allowed`），不再读取全局设置。
 * Markdown/TXT 不使用该字段，其内置目录交互由渲染结果单独声明。
 */
fun PreviewHistory.toPreviewOptions(
    settings: PreviewSettings,
    documentMode: PreviewDocumentMode
): PreviewOptions = PreviewOptions(
    javaScriptEnabledForHtml = jsAllowed,
    markdownDocumentMode = documentMode,
    readingDisplayOptions = settings.readingDisplayOptions,
    generatedContentDarkTheme = settings.isDarkTheme
)

fun PreviewHistory.toRecentFileItem(): RecentFileItem {
    return RecentFileItem(
        id = id,
        title = displayTitle(),
        fileTypeLabel = fileType.name,
        isFavorite = favorite,
        lastOpenedAt = lastOpenedAt
    )
}

fun List<RecentFileItem>.filterFor(filter: HomeListFilter): List<RecentFileItem> {
    return when (filter) {
        HomeListFilter.ALL -> this
        HomeListFilter.FAVORITES -> filter { it.isFavorite }
    }
}

fun List<RecentFileItem>.filterBySearch(query: String): List<RecentFileItem> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return this
    return filter {
        it.title.contains(normalized, ignoreCase = true) ||
            it.fileTypeLabel.contains(normalized, ignoreCase = true)
    }
}

fun PreviewHistory.displayTitle(): String = displayName ?: localFile.name

private fun PreviewHistory.navigationStatus(): String {
    return when (fileType) {
        FileType.HTML,
        FileType.ZIP -> "安全沙箱已激活"
        FileType.MARKDOWN,
        FileType.TEXT -> "本地阅读模式"
    }
}

private fun PreviewHistory.javaScriptStatus(
    javaScriptEnabledForPreview: Boolean,
    supportsScriptAuthorization: Boolean
): String {
    return when (fileType) {
        FileType.HTML,
        FileType.ZIP -> when {
            !supportsScriptAuthorization -> "JS 不执行"
            javaScriptEnabledForPreview -> "JS 已启用"
            else -> "JS 已禁用"
        }
        FileType.MARKDOWN -> if (javaScriptEnabledForPreview) "仅运行内置目录交互" else "JS 不执行"
        FileType.TEXT -> "JS 不执行"
    }
}
