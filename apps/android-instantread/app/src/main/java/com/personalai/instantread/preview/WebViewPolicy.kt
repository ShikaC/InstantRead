package com.personalai.instantread.preview

import android.webkit.WebView

object WebViewPolicy {
    /**
     * 集中应用预览 WebView 的基线安全策略。
     *
     * `allowFileAccess` 只对需要读取本地副本的文档（HTML/ZIP）开启；App 生成的阅读页
     * 关闭文件访问。`allowFileAccessFromFileURLs` 与 `allowUniversalAccessFromFileURLs`
     * 显式关闭：即使页面本身来自 file://，也不能用脚本读取其它本地文件。
     */
    fun applyTo(
        webView: WebView,
        javaScriptEnabled: Boolean = false,
        allowFileAccess: Boolean = false
    ) {
        webView.settings.javaScriptEnabled = javaScriptEnabled
        webView.settings.domStorageEnabled = false
        webView.settings.allowFileAccess = allowFileAccess
        webView.settings.allowContentAccess = false
        @Suppress("DEPRECATION")
        webView.settings.allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        webView.settings.allowUniversalAccessFromFileURLs = false
    }
}
