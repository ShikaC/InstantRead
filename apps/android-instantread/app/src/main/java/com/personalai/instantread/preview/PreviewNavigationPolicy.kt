package com.personalai.instantread.preview

import java.io.File
import java.net.URI

enum class PreviewNavigationDecision {
    AllowInPreview,
    OpenExternally,
    Block
}

object PreviewNavigationPolicy {
    fun isAllowed(url: String, allowedLocalRoots: List<File>): Boolean {
        return decide(url, allowedLocalRoots) == PreviewNavigationDecision.AllowInPreview
    }

    /**
     * 子资源请求（图片、CSS、脚本、iframe 等）的授权判定。
     *
     * 与顶层导航不同，子资源不做“交给外部应用打开”的降级：越界文件、网络地址和未知协议
     * 一律阻止，避免本地文档把预览变成任意资源加载器。`about`/`data` 仍放行，
     * 因为 App 生成的阅读页依赖它们。
     */
    fun decideResourceRequest(url: String, allowedLocalRoots: List<File>): PreviewNavigationDecision {
        return try {
            val uri = URI(url)
            when (uri.scheme?.lowercase()) {
                "about", "data" -> PreviewNavigationDecision.AllowInPreview
                "file" -> if (isAllowedFile(uri, allowedLocalRoots)) {
                    PreviewNavigationDecision.AllowInPreview
                } else {
                    PreviewNavigationDecision.Block
                }
                else -> PreviewNavigationDecision.Block
            }
        } catch (error: Exception) {
            PreviewNavigationDecision.Block
        }
    }

    fun decide(url: String, allowedLocalRoots: List<File>): PreviewNavigationDecision {
        return try {
            val uri = URI(url)
            when (uri.scheme?.lowercase()) {
                "about", "data" -> PreviewNavigationDecision.AllowInPreview
                "file" -> if (isAllowedFile(uri, allowedLocalRoots)) {
                    PreviewNavigationDecision.AllowInPreview
                } else {
                    PreviewNavigationDecision.Block
                }
                "http", "https" -> PreviewNavigationDecision.OpenExternally
                else -> PreviewNavigationDecision.Block
            }
        } catch (error: Exception) {
            PreviewNavigationDecision.Block
        }
    }

    /**
     * 判断请求是否指向授权根内、但实际不存在的本地文件。
     *
     * 单 HTML 只导入自身副本，兄弟资源不会被复制；这类缺失请求会被 WebView 静默忽略，
     * 界面无从得知。这里显式识别，供预览层给出“打包为 ZIP 重新导入”的恢复提示。
     */
    fun isMissingLocalFile(url: String, allowedLocalRoots: List<File>): Boolean {
        return try {
            val uri = URI(url)
            if (uri.scheme?.lowercase() != "file") return false
            if (!isAllowedFile(uri, allowedLocalRoots)) return false
            !File(uri).exists()
        } catch (error: Exception) {
            false
        }
    }

    private fun isAllowedFile(uri: URI, allowedLocalRoots: List<File>): Boolean {
        val target = File(uri).canonicalFile
        return allowedLocalRoots.any { root ->
            val canonicalRoot = root.canonicalFile
            target.path == canonicalRoot.path ||
                target.path.startsWith(canonicalRoot.path + File.separator)
        }
    }
}
