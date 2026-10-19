package com.personalai.instantread.preview

import android.net.Uri
import android.webkit.WebResourceRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalPreviewWebViewClientTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun allowsSubresourcesInsideTheDocumentRoot() {
        val root = temp.newFolder("root")
        val image = File(root, "logo.png").apply { writeText("png") }
        val blocked = mutableListOf<String>()
        val client = LocalPreviewWebViewClient(listOf(root), onResourceBlocked = { blocked += it })

        val response = client.shouldInterceptRequest(null, request(image.toURI().toString()))

        assertNull(response)
        assertTrue(blocked.isEmpty())
    }

    @Test
    fun blocksSubresourcesOutsideTheDocumentRoot() {
        val root = temp.newFolder("root")
        val outside = File(temp.newFolder("other-import"), "secret.html").apply { writeText("secret") }
        val blocked = mutableListOf<String>()
        val client = LocalPreviewWebViewClient(listOf(root), onResourceBlocked = { blocked += it })

        val response = client.shouldInterceptRequest(null, request(outside.toURI().toString()))

        assertNotNull(response)
        assertEquals(listOf(outside.toURI().toString()), blocked)
    }

    @Test
    fun reportsMissingLocalSubresourceInsideTheDocumentRoot() {
        val root = temp.newFolder("root")
        val missing = File(root, "assets/style.css")
        val blocked = mutableListOf<String>()
        val missingResources = mutableListOf<String>()
        val client = LocalPreviewWebViewClient(
            allowedLocalRoots = listOf(root),
            onResourceBlocked = { blocked += it },
            onLocalResourceMissing = { missingResources += it }
        )

        val response = client.shouldInterceptRequest(null, request(missing.toURI().toString()))

        assertNotNull(response)
        assertEquals(listOf(missing.toURI().toString()), missingResources)
        assertTrue(blocked.isEmpty())
    }

    @Test
    fun reportsMissingLocalSubresourceOnlyOnce() {
        val root = temp.newFolder("root")
        val missingResources = mutableListOf<String>()
        val client = LocalPreviewWebViewClient(
            allowedLocalRoots = listOf(root),
            onLocalResourceMissing = { missingResources += it }
        )

        client.shouldInterceptRequest(null, request(File(root, "a.png").toURI().toString()))
        client.shouldInterceptRequest(null, request(File(root, "b.png").toURI().toString()))

        assertEquals(1, missingResources.size)
    }

    @Test
    fun doesNotReportExistingLocalSubresourceAsMissing() {
        val root = temp.newFolder("root")
        val existing = File(root, "logo.png").apply { writeText("png") }
        val missingResources = mutableListOf<String>()
        val client = LocalPreviewWebViewClient(
            allowedLocalRoots = listOf(root),
            onLocalResourceMissing = { missingResources += it }
        )

        val response = client.shouldInterceptRequest(null, request(existing.toURI().toString()))

        assertNull(response)
        assertTrue(missingResources.isEmpty())
    }

    @Test
    fun blocksNetworkSubresourcesAndReportsThemOnlyOnce() {
        val root = temp.newFolder("root")
        val blocked = mutableListOf<String>()
        val client = LocalPreviewWebViewClient(listOf(root), onResourceBlocked = { blocked += it })

        val first = client.shouldInterceptRequest(null, request("https://cdn.example.com/app.js"))
        val second = client.shouldInterceptRequest(null, request("http://cdn.example.com/style.css"))

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(listOf("https://cdn.example.com/app.js"), blocked)
    }

    @Test
    fun dispatchesBlockedResourceCallbackInsteadOfUpdatingUiFromRequestThread() {
        val root = temp.newFolder("root")
        val blocked = mutableListOf<String>()
        val pendingCallbacks = mutableListOf<() -> Unit>()
        val client = LocalPreviewWebViewClient(
            allowedLocalRoots = listOf(root),
            onResourceBlocked = { blocked += it },
            dispatchResourceBlocked = { pendingCallbacks += it }
        )

        val response = client.shouldInterceptRequest(
            null,
            request("https://cdn.example.com/app.css")
        )

        assertNotNull(response)
        assertTrue(blocked.isEmpty())
        assertEquals(1, pendingCallbacks.size)
        pendingCallbacks.single().invoke()
        assertEquals(listOf("https://cdn.example.com/app.css"), blocked)
    }

    @Test
    fun leavesMainFrameRequestsToNavigationPolicy() {
        val root = temp.newFolder("root")
        val outside = File(temp.newFolder("other-import"), "secret.html").apply { writeText("secret") }
        val blocked = mutableListOf<String>()
        val client = LocalPreviewWebViewClient(listOf(root), onResourceBlocked = { blocked += it })

        val response = client.shouldInterceptRequest(
            null,
            request(outside.toURI().toString(), isForMainFrame = true)
        )

        assertNull(response)
        assertTrue(blocked.isEmpty())
    }

    @Test
    fun reportsOnlyMainFrameLoadFailures() {
        val failures = mutableListOf<String>()
        val client = LocalPreviewWebViewClient(emptyList(), onMainFrameLoadFailed = { failures += it })

        client.handleLoadError("file:///missing.html", isForMainFrame = true)
        client.handleLoadError("file:///image.png", isForMainFrame = false)
        client.handleLoadError(null, isForMainFrame = true)

        assertEquals(listOf("file:///missing.html"), failures)
    }

    private fun request(url: String, isForMainFrame: Boolean = false): WebResourceRequest =
        FakeWebResourceRequest(url, isForMainFrame)

    private class FakeWebResourceRequest(
        private val url: String,
        private val mainFrame: Boolean
    ) : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame(): Boolean = mainFrame
        override fun isRedirect(): Boolean = false
        override fun hasGesture(): Boolean = false
        override fun getMethod(): String = "GET"
        override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
    }
}
