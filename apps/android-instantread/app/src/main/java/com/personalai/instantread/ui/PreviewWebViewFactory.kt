package com.personalai.instantread.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import com.personalai.instantread.preview.LocalPreviewWebViewClient
import com.personalai.instantread.preview.WebViewPolicy
import java.io.File

class PreviewWebViewFactory(
    private val context: Context,
    private val onOpenExternalUrl: (String) -> Unit = {}
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 每个文档使用自己的授权根，而不是整个私有目录。
     *
     * `resourceRoot` 为 null 表示 App 生成的阅读页（Markdown/TXT），不需要文件访问。
     */
    fun create(
        javaScriptEnabled: Boolean,
        resourceRoot: File?,
        onPageFinished: (String) -> Unit = {},
        onResourceBlocked: (String) -> Unit = {},
        onLocalResourceMissing: (String) -> Unit = {},
        onMainFrameLoadFailed: (String) -> Unit = {}
    ): WebView {
        return WebView(context).apply {
            WebViewPolicy.applyTo(this, javaScriptEnabled, allowFileAccess = resourceRoot != null)
            webViewClient = LocalPreviewWebViewClient(
                allowedLocalRoots = listOfNotNull(resourceRoot),
                onOpenExternalUrl = onOpenExternalUrl,
                onPageFinished = onPageFinished,
                onResourceBlocked = onResourceBlocked,
                onLocalResourceMissing = onLocalResourceMissing,
                onMainFrameLoadFailed = onMainFrameLoadFailed,
                dispatchResourceBlocked = { callback -> mainHandler.post(callback) }
            )
        }
    }
}
