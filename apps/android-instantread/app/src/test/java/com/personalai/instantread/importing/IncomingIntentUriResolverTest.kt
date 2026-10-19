package com.personalai.instantread.importing

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IncomingIntentUriResolverTest {
    @Test
    fun resolvesViewDataUri() {
        val uri = Uri.parse("content://example/source/sample.html")
        val intent = Intent(Intent.ACTION_VIEW, uri)

        val resolved = IncomingIntentUriResolver.resolve(intent)

        assertEquals(IncomingIntentUri(uri, SourceKind.ACTION_VIEW, null, Intent.ACTION_VIEW, "data"), resolved)
    }

    @Test
    fun resolvesSendExtraStreamUriBeforeClipData() {
        val extraUri = Uri.parse("content://example/share/from-extra.txt")
        val clipUri = Uri.parse("content://example/share/from-clip.txt")
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, extraUri)
            clipData = ClipData.newRawUri("shared file", clipUri)
            type = "text/plain"
        }

        val resolved = IncomingIntentUriResolver.resolve(intent)

        assertEquals(
            IncomingIntentUri(extraUri, SourceKind.ACTION_SEND, "text/plain", Intent.ACTION_SEND, "extraStream"),
            resolved
        )
    }

    @Test
    fun resolvesSendClipDataUriWhenExtraStreamIsMissing() {
        val uri = Uri.parse("content://example/share/clip-only.md")
        val intent = Intent(Intent.ACTION_SEND).apply {
            clipData = ClipData.newRawUri("shared file", uri)
        }

        val resolved = IncomingIntentUriResolver.resolve(intent)

        assertEquals(IncomingIntentUri(uri, SourceKind.ACTION_SEND, null, Intent.ACTION_SEND, "clipData"), resolved)
    }

    @Test
    fun resolvesSendMultipleWhenItContainsExactlyOneSharedFile() {
        val uri = Uri.parse("content://example/share/single-multiple.zip")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(uri))
            type = "application/zip"
        }

        val resolved = IncomingIntentUriResolver.resolve(intent)

        assertEquals(
            IncomingIntentUri(
                uri,
                SourceKind.ACTION_SEND,
                "application/zip",
                Intent.ACTION_SEND_MULTIPLE,
                "extraStreamList"
            ),
            resolved
        )
    }

    @Test
    fun ignoresSendMultipleWhenItContainsMoreThanOneSharedFile() {
        val firstUri = Uri.parse("content://example/share/first.html")
        val secondUri = Uri.parse("content://example/share/second.html")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(firstUri, secondUri))
            type = "text/html"
        }

        val resolved = IncomingIntentUriResolver.resolve(intent)

        assertEquals(null, resolved)
    }

    @Test
    fun ignoresSendIntentWithoutSingleFileUri() {
        val intent = Intent(Intent.ACTION_SEND)

        val resolved = IncomingIntentUriResolver.resolve(intent)

        assertEquals(null, resolved)
    }
}
