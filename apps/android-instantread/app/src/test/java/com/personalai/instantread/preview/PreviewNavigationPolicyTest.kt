package com.personalai.instantread.preview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PreviewNavigationPolicyTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun allowsFileUrlsInsideAllowedRoots() {
        val root = temp.newFolder("root")
        val file = File(root, "index.html").apply { writeText("html") }

        assertTrue(PreviewNavigationPolicy.isAllowed(file.toURI().toString(), listOf(root)))
        assertEquals(
            PreviewNavigationDecision.AllowInPreview,
            PreviewNavigationPolicy.decide(file.toURI().toString(), listOf(root))
        )
    }

    @Test
    fun blocksFileUrlsOutsideAllowedRoots() {
        val root = temp.newFolder("root")
        val outside = temp.newFolder("outside")
        val file = File(outside, "index.html").apply { writeText("html") }

        assertFalse(PreviewNavigationPolicy.isAllowed(file.toURI().toString(), listOf(root)))
    }

    @Test
    fun allowsDataAndAboutUrlsForGeneratedHtml() {
        assertTrue(PreviewNavigationPolicy.isAllowed("about:blank", emptyList()))
        assertTrue(PreviewNavigationPolicy.isAllowed("data:text/plain,hello", emptyList()))
    }

    @Test
    fun delegatesHttpAndHttpsUrlsToExternalApps() {
        assertEquals(
            PreviewNavigationDecision.OpenExternally,
            PreviewNavigationPolicy.decide("https://example.com", emptyList())
        )
        assertEquals(
            PreviewNavigationDecision.OpenExternally,
            PreviewNavigationPolicy.decide("http://example.com", emptyList())
        )
        assertFalse(PreviewNavigationPolicy.isAllowed("https://example.com", emptyList()))
        assertFalse(PreviewNavigationPolicy.isAllowed("http://example.com", emptyList()))
    }

    @Test
    fun blocksMalformedUrls() {
        assertFalse(PreviewNavigationPolicy.isAllowed("not a url", emptyList()))
        assertEquals(
            PreviewNavigationDecision.Block,
            PreviewNavigationPolicy.decide("not a url", emptyList())
        )
    }

    @Test
    fun resourceRequestsStayInsideTheDocumentRoot() {
        val root = temp.newFolder("resource-root")
        val inside = File(root, "assets/logo.png").apply {
            parentFile?.mkdirs()
            writeText("png")
        }
        val outside = File(temp.newFolder("resource-other"), "secret.png").apply { writeText("png") }

        assertEquals(
            PreviewNavigationDecision.AllowInPreview,
            PreviewNavigationPolicy.decideResourceRequest(inside.toURI().toString(), listOf(root))
        )
        assertEquals(
            PreviewNavigationDecision.Block,
            PreviewNavigationPolicy.decideResourceRequest(outside.toURI().toString(), listOf(root))
        )
        assertEquals(
            PreviewNavigationDecision.Block,
            PreviewNavigationPolicy.decideResourceRequest(outside.toURI().toString(), emptyList())
        )
    }

    @Test
    fun resourceRequestsBlockNetworkAndUnknownSchemes() {
        // 子资源不做“交给外部应用”的降级：网络地址、未知协议一律阻止。
        assertEquals(
            PreviewNavigationDecision.Block,
            PreviewNavigationPolicy.decideResourceRequest("https://cdn.example.com/app.js", emptyList())
        )
        assertEquals(
            PreviewNavigationDecision.Block,
            PreviewNavigationPolicy.decideResourceRequest("content://media/external/images/1", emptyList())
        )
        assertEquals(
            PreviewNavigationDecision.Block,
            PreviewNavigationPolicy.decideResourceRequest("not a url", emptyList())
        )
        assertEquals(
            PreviewNavigationDecision.AllowInPreview,
            PreviewNavigationPolicy.decideResourceRequest("data:image/png;base64,AAAA", emptyList())
        )
        assertEquals(
            PreviewNavigationDecision.AllowInPreview,
            PreviewNavigationPolicy.decideResourceRequest("about:blank", emptyList())
        )
    }

    @Test
    @Suppress("DEPRECATION")
    fun webViewClientConsumesExternalLinksAndReportsThem() {
        val openedUrls = mutableListOf<String>()
        val client = LocalPreviewWebViewClient(
            allowedLocalRoots = emptyList(),
            onOpenExternalUrl = { openedUrls.add(it) }
        )

        val consumed = client.shouldOverrideUrlLoading(null, "https://example.com/report")

        assertTrue(consumed)
        assertEquals(listOf("https://example.com/report"), openedUrls)
    }

    @Test
    fun webViewClientReportsWhenTheMainPageFinishesLoading() {
        val finishedUrls = mutableListOf<String>()
        val client = LocalPreviewWebViewClient(
            allowedLocalRoots = emptyList(),
            onPageFinished = { finishedUrls += it }
        )

        client.onPageFinished(null, "about:blank")
        client.onPageFinished(null, "about:blank#section")

        assertEquals(listOf("about:blank"), finishedUrls)
    }
}
