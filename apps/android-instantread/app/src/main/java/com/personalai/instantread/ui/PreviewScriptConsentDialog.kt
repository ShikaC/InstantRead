package com.personalai.instantread.ui

import android.app.AlertDialog
import android.content.Context

/**
 * 单文档脚本授权的确认弹窗。
 *
 * 文案固定在这里，便于测试断言，也避免授权说明散落在 Activity 中。
 */
object PreviewScriptConsentDialog {
    const val TITLE = "为当前文档允许脚本？"
    const val MESSAGE = "脚本可以修改页面内容，并读取该文档目录下的本地资源。授权只对这一次导入的副本生效，重新导入后需要再次确认。"
    const val CONFIRM_LABEL = "允许脚本"
    const val CANCEL_LABEL = "取消"

    fun show(context: Context, onConfirm: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle(TITLE)
            .setMessage(MESSAGE)
            .setPositiveButton(CONFIRM_LABEL) { _, _ -> onConfirm() }
            .setNegativeButton(CANCEL_LABEL, null)
            .show()
    }
}
