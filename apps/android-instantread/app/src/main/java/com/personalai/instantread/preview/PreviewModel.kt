package com.personalai.instantread.preview

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

sealed class PreviewModel {
    data class WebFile(
        val file: File,
        val title: String,
        val javaScriptEnabled: Boolean = false,
        // 授权根限定到当前文档所属目录：HTML 为导入副本目录，ZIP 为本次解包目录。
        // 预览只允许读取该根内的资源，避免文件 A 的页面访问文件 B 的副本。
        val resourceRoot: File? = file.parentFile,
        // HTML/ZIP 网页包可对当前文档授权执行脚本；由 Markdown 生成的 ZIP 阅读页没有任何
        // 可授权脚本，界面据此隐藏脚本入口，避免出现无法生效的开关。
        val supportsUserScriptAuthorization: Boolean = true
    ) : PreviewModel() {
        private var preparedResources: PreparedPreviewResources? = null

        internal fun attachPreparedResources(resources: PreparedPreviewResources): WebFile {
            check(preparedResources == null) { "Prepared preview resources already attached" }
            preparedResources = resources
            return this
        }

        internal fun publishPreparedResources() {
            preparedResources?.publish()
        }

        internal fun discardPreparedResources() {
            preparedResources?.discard()
        }
    }

    data class WebHtml(
        val html: String,
        val title: String,
        val javaScriptEnabled: Boolean = false
    ) : PreviewModel()

    data class Error(
        val title: String,
        val message: String
    ) : PreviewModel()
}

internal class PreparedPreviewResources(
    private val onPublish: () -> Unit,
    private val onDiscard: () -> Unit
) {
    private val consumed = AtomicBoolean(false)

    fun publish() {
        if (consumed.compareAndSet(false, true)) onPublish()
    }

    fun discard() {
        if (consumed.compareAndSet(false, true)) onDiscard()
    }
}

internal fun PreviewModel.publishPreparedResources() {
    if (this is PreviewModel.WebFile) publishPreparedResources()
}

internal fun PreviewModel.discardPreparedResources() {
    if (this is PreviewModel.WebFile) discardPreparedResources()
}
