package com.personalai.instantread

import android.view.View
import android.view.ViewGroup
import com.personalai.instantread.history.PreviewHistory
import com.personalai.instantread.importing.FileType
import com.personalai.instantread.ui.PreviewWorkspaceActions
import com.personalai.instantread.ui.PreviewWorkspaceState
import com.personalai.instantread.ui.PreviewWorkspaceViewFactory

class PreviewChromeController(
    private val viewFactory: PreviewWorkspaceViewFactory,
    private val actions: (String) -> PreviewWorkspaceActions,
    private val stateFor: (PreviewHistory) -> PreviewWorkspaceState
) {
    private var historyId: String? = null
    private var workspace: View? = null
    private var blockedResourceNotice = false
    private var missingLocalResourceNotice = false

    fun bind(id: String, workspace: View) {
        historyId = id
        this.workspace = workspace
        blockedResourceNotice = false
        missingLocalResourceNotice = false
    }

    fun clear() {
        historyId = null
        workspace = null
        blockedResourceNotice = false
        missingLocalResourceNotice = false
    }

    /**
     * 记录当前文档加载时被安全策略拦截的资源。
     *
     * 提示只在状态栏展示一次；页面刷新或切换文档后重置。
     */
    fun noteBlockedResource(id: String) {
        if (historyId == id) blockedResourceNotice = true
    }

    /**
     * 记录授权根内实际缺失的本地资源。
     *
     * 单 HTML 只导入自身副本，兄弟图片/样式不会被复制；这里给出“打包为 ZIP 重新导入”的
     * 恢复动作，与越界或网络资源的拦截提示区分。
     */
    fun noteMissingLocalResource(id: String) {
        if (historyId == id) missingLocalResourceNotice = true
    }

    fun refresh(id: String, history: PreviewHistory): Boolean {
        if (historyId != id) return false
        val state = decorate(stateFor(history))
        val currentWorkspace = workspace ?: return false
        val updated = viewFactory.updateChrome(
            workspace = currentWorkspace,
            state = state,
            actions = actions(id)
        )
        return updated
    }

    private fun decorate(state: PreviewWorkspaceState): PreviewWorkspaceState {
        val notices = buildList {
            if (blockedResourceNotice) add("已拦截页面外资源，页面可能不完整")
            if (missingLocalResourceNotice) {
                add(
                    if (state.fileTypeLabel == FileType.HTML.name) {
                        "缺少本地图片或样式；如需完整显示，请将 HTML 与资源一起打包为 ZIP 后重新导入"
                    } else {
                        "包内引用的资源缺失，页面可能不完整"
                    }
                )
            }
        }
        if (notices.isEmpty()) return state
        return state.copy(
            navigationStatus = (listOf(state.navigationStatus) + notices).joinToString(" · ")
        )
    }
}
