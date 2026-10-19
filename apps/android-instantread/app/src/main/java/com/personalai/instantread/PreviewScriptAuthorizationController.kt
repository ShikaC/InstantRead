package com.personalai.instantread

import com.personalai.instantread.history.PreviewHistory
import com.personalai.instantread.history.PreviewHistoryRepository

/**
 * 单文档脚本授权。
 *
 * HTML 与 ZIP 网页包默认不执行脚本；用户只能在预览页对当前导入副本显式授权。
 * 撤销立即生效且不再询问；授权只写入当前记录的 `js_allowed`，不会影响其它文档。
 */
class PreviewScriptAuthorizationController(
    private val historyRepository: PreviewHistoryRepository,
    private val requestConsent: (PreviewHistory, () -> Unit) -> Unit,
    private val onAuthorizationChanged: (PreviewHistory) -> Unit
) {
    fun toggle(id: String) {
        val history = historyRepository.findById(id) ?: return
        if (history.jsAllowed) {
            historyRepository.setJavaScriptAllowed(id, false)?.let(onAuthorizationChanged)
        } else {
            requestConsent(history) {
                historyRepository.setJavaScriptAllowed(id, true)?.let(onAuthorizationChanged)
            }
        }
    }
}
