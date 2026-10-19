package com.personalai.instantread

import com.personalai.instantread.settings.PreviewSettingsRepository
import com.personalai.instantread.ui.PreviewWorkspaceActions

/**
 * 组装预览页的交互动作。
 *
 * 放在 Activity 之外，既保持 MainActivity 只做生命周期与顶层状态，也让脚本授权、
 * 搜索、正文显示等动作可以在不启动 Activity 的情况下单独测试。
 */
class PreviewWorkspaceActionsController(
    private val settingsRepository: PreviewSettingsRepository,
    private val searchController: PreviewSearchController,
    private val readingDisplayController: PreviewReadingDisplayController,
    private val scriptAuthorizationController: PreviewScriptAuthorizationController,
    private val onBack: () -> Unit,
    private val onFavoriteToggled: (String) -> Unit,
    private val onDocumentModeChanged: (String, PreviewDocumentMode) -> Unit
) {
    fun actions(id: String) = PreviewWorkspaceActions(
        onBack = onBack,
        onToggleFavorite = { onFavoriteToggled(id) },
        onDocumentModeChanged = { mode -> onDocumentModeChanged(id, mode) },
        onSearchOpened = { searchController.open(id) },
        onSearchClosed = { searchController.close(id) },
        onSearchQueryChanged = { searchController.updateQuery(id, it) },
        onSearchNext = { searchController.next(id) },
        onSearchPrevious = { searchController.previous(id) },
        onSearchCleared = { searchController.clearQuery(id) },
        onToggleDocumentJavaScript = { scriptAuthorizationController.toggle(id) },
        onReadingDisplayOpened = { readingDisplayController.open(id) },
        onReadingDisplayClosed = { readingDisplayController.close(id) },
        onReadingDisplayChanged = { edit ->
            readingDisplayController.apply(id, settingsRepository.getSettings().readingDisplayOptions, edit)
        },
        onReadingDisplayReset = { readingDisplayController.reset(id) }
    )
}
