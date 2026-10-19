package com.personalai.instantread.preview

import android.annotation.TargetApi
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class LocalPreviewWebViewClient(
    private val allowedLocalRoots: List<File>,
    private val onOpenExternalUrl: (String) -> Unit = {},
    onPageFinished: (String) -> Unit = {},
    private val onResourceBlocked: (String) -> Unit = {},
    private val onLocalResourceMissing: (String) -> Unit = {},
    private val onMainFrameLoadFailed: (String) -> Unit = {},
    private val dispatchResourceBlocked: (() -> Unit) -> Unit = { it() }
) : WebViewClient() {
    private val onPageFinishedCallback = onPageFinished
    private var hasReportedPageFinished = false
    private val hasReportedBlockedResource = AtomicBoolean(false)
    private val hasReportedMissingLocalResource = AtomicBoolean(false)

    @Deprecated("Used by WebView on older Android versions.")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        return handleNavigation(url)
    }

    @TargetApi(Build.VERSION_CODES.N)
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return true
        return handleNavigation(url)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        if (!hasReportedPageFinished && url != null) {
            hasReportedPageFinished = true
            onPageFinishedCallback(url)
        }
    }

    /**
     * 拦截页面内的子资源请求（图片、CSS、脚本、iframe 等）。
     *
     * 顶层导航由 [shouldOverrideUrlLoading] 处理，但子资源不会走那条路径；只靠导航回调
     * 无法阻止越界文件或网络资源被加载。这里对每个非主框架请求做授权判定，越界或
     * 不允许的协议返回空响应，并只上报一次“已拦截资源”，避免逐条刷新界面。授权根内
     * 但实际不存在的本地资源单独上报，用于提示单 HTML 缺少兄弟资源或包内引用缺失。
     */
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url?.toString() ?: return null
        if (request.isForMainFrame) return null
        return when (PreviewNavigationPolicy.decideResourceRequest(url, allowedLocalRoots)) {
            PreviewNavigationDecision.AllowInPreview -> {
                if (PreviewNavigationPolicy.isMissingLocalFile(url, allowedLocalRoots)) {
                    reportMissingLocalResourceOnce(url)
                    blockedResourceResponse()
                } else {
                    null
                }
            }
            else -> {
                reportBlockedResourceOnce(url)
                blockedResourceResponse()
            }
        }
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: android.webkit.WebResourceError?
    ) {
        handleLoadError(
            url = request?.url?.toString(),
            isForMainFrame = request?.isForMainFrame == true
        )
    }

    internal fun handleLoadError(url: String?, isForMainFrame: Boolean) {
        if (!isForMainFrame || url == null) return
        onMainFrameLoadFailed(url)
    }

    private fun reportBlockedResourceOnce(url: String) {
        if (!hasReportedBlockedResource.compareAndSet(false, true)) return
        // shouldInterceptRequest 可能运行在 WebView 后台线程，资源提示必须回到拥有 View 的主线程。
        dispatchResourceBlocked { onResourceBlocked(url) }
    }

    private fun reportMissingLocalResourceOnce(url: String) {
        if (!hasReportedMissingLocalResource.compareAndSet(false, true)) return
        dispatchResourceBlocked { onLocalResourceMissing(url) }
    }

    private fun blockedResourceResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    private fun handleNavigation(url: String?): Boolean {
        if (url == null) {
            return true
        }
        return when (PreviewNavigationPolicy.decide(url, allowedLocalRoots)) {
            PreviewNavigationDecision.AllowInPreview -> false
            PreviewNavigationDecision.OpenExternally -> {
                onOpenExternalUrl(url)
                true
            }
            PreviewNavigationDecision.Block -> true
        }
    }
}
