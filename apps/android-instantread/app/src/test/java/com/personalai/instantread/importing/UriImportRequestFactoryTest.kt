package com.personalai.instantread.importing

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.CursorWrapper
import android.database.MatrixCursor
import android.net.Uri
import android.os.OperationCanceledException
import android.provider.OpenableColumns
import com.personalai.instantread.io.FileTaskCancellationToken
import com.personalai.instantread.io.FileTaskCancelledException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UriImportRequestFactoryTest {
    @Test
    fun cancellationBeforeMetadataQueryPreventsProviderAccess() {
        val context = RuntimeEnvironment.getApplication()
        val provider = DisplayNameProvider("cancel.txt", "text/plain")
        ShadowContentResolver.registerProviderInternal(
            "com.personalai.instantread.test.cancel-before-query",
            provider
        )
        val expected = FileTaskCancelledException()
        val factory = UriImportRequestFactory(context.contentResolver)

        val actual = assertThrows(FileTaskCancelledException::class.java) {
            factory.fromUri(
                Uri.parse("content://com.personalai.instantread.test.cancel-before-query/file.txt"),
                SourceKind.APP_PICKER,
                cancellationToken = FileTaskCancellationToken { throw expected }
            )
        }

        assertSame(expected, actual)
        assertEquals(0, provider.queryCalls)
        assertEquals(0, provider.getTypeCalls)
    }

    @Test
    fun cancellationAfterMetadataQueryPreventsMimeLookup() {
        val context = RuntimeEnvironment.getApplication()
        val provider = DisplayNameProvider("cancel.txt", "text/plain")
        ShadowContentResolver.registerProviderInternal(
            "com.personalai.instantread.test.cancel-after-query",
            provider
        )
        val expected = FileTaskCancelledException()
        var checks = 0
        val factory = UriImportRequestFactory(context.contentResolver)

        val actual = assertThrows(FileTaskCancelledException::class.java) {
            factory.fromUri(
                Uri.parse("content://com.personalai.instantread.test.cancel-after-query/file.txt"),
                SourceKind.APP_PICKER,
                cancellationToken = FileTaskCancellationToken {
                    checks += 1
                    if (checks == 4) throw expected
                }
            )
        }

        assertSame(expected, actual)
        assertEquals(1, provider.queryCalls)
        assertEquals(0, provider.getTypeCalls)
        assertTrue("The cursor must close when post-query cancellation wins.", provider.lastCursor!!.closed)
    }

    @Test
    fun providerOperationCancellationFallsBackWhenAppTokenIsStillActive() {
        val context = RuntimeEnvironment.getApplication()
        val provider = DisplayNameProvider(
            displayName = "cancel.txt",
            mimeType = "text/plain",
            queryFailure = OperationCanceledException("provider cancelled")
        )
        ShadowContentResolver.registerProviderInternal(
            "com.personalai.instantread.test.provider-cancelled",
            provider
        )
        val factory = UriImportRequestFactory(context.contentResolver)

        val request = factory.fromUri(
            Uri.parse("content://com.personalai.instantread.test.provider-cancelled/file.txt"),
            SourceKind.APP_PICKER
        )

        assertEquals("file.txt", request.displayName)
        assertEquals("text/plain", request.mimeType)
        assertEquals(1, provider.queryCalls)
        assertEquals(1, provider.getTypeCalls)
    }

    @Test
    fun readsProviderSizeWhileQueryingDisplayNameAndSizeTogether() {
        val context = RuntimeEnvironment.getApplication()
        val provider = DisplayNameProvider(
            displayName = "sized.md",
            mimeType = "text/markdown",
            sizeBytes = 2_048L,
            includeSizeColumn = true
        )
        ShadowContentResolver.registerProviderInternal(
            "com.personalai.instantread.test.sized",
            provider
        )
        val factory: ImportRequestFactory = UriImportRequestFactory(context.contentResolver)

        val request = factory.fromUri(
            Uri.parse("content://com.personalai.instantread.test.sized/share/sized.md"),
            SourceKind.APP_PICKER
        )

        assertEquals("sized.md", request.displayName)
        assertEquals(2_048L, request.declaredSizeBytes)
        assertEquals(
            listOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            provider.lastProjection?.toList()
        )
    }

    @Test
    fun ignoresMetadataQueryFailureAndUsesUriFallbackName() {
        val context = RuntimeEnvironment.getApplication()
        ShadowContentResolver.registerProviderInternal(
            "com.personalai.instantread.test.queryfailure",
            DisplayNameProvider(
                displayName = "unused.md",
                mimeType = "text/markdown",
                queryFailure = IllegalStateException("metadata unavailable")
            )
        )
        val factory = UriImportRequestFactory(context.contentResolver)

        val request = factory.fromUri(
            Uri.parse("content://com.personalai.instantread.test.queryfailure/share/fallback.md"),
            SourceKind.ACTION_SEND
        )

        assertEquals("fallback.md", request.displayName)
        assertNull(request.declaredSizeBytes)
    }

    @Test
    fun treatsMissingProviderSizeColumnAsUnknown() {
        val context = RuntimeEnvironment.getApplication()
        ShadowContentResolver.registerProviderInternal(
            "com.personalai.instantread.test.missingsize",
            DisplayNameProvider(displayName = "missing.txt", mimeType = "text/plain")
        )
        val factory = UriImportRequestFactory(context.contentResolver)

        val request = factory.fromUri(
            Uri.parse("content://com.personalai.instantread.test.missingsize/share/missing.txt"),
            SourceKind.ACTION_VIEW
        )

        assertEquals("missing.txt", request.displayName)
        assertNull(request.declaredSizeBytes)
    }

    @Test
    fun treatsNegativeProviderSizeAsUnknown() {
        val context = RuntimeEnvironment.getApplication()
        ShadowContentResolver.registerProviderInternal(
            "com.personalai.instantread.test.negativesize",
            DisplayNameProvider(
                displayName = "negative.zip",
                mimeType = "application/zip",
                sizeBytes = -1L,
                includeSizeColumn = true
            )
        )
        val factory = UriImportRequestFactory(context.contentResolver)

        val request = factory.fromUri(
            Uri.parse("content://com.personalai.instantread.test.negativesize/share/negative.zip"),
            SourceKind.ACTION_SEND
        )

        assertEquals("negative.zip", request.displayName)
        assertNull(request.declaredSizeBytes)
    }

    @Test
    fun treatsEmptyMetadataCursorAsUnknown() {
        val context = RuntimeEnvironment.getApplication()
        ShadowContentResolver.registerProviderInternal(
            "com.personalai.instantread.test.emptycursor",
            DisplayNameProvider(
                displayName = "unused.html",
                mimeType = "text/html",
                includeSizeColumn = true,
                includeRow = false
            )
        )
        val factory = UriImportRequestFactory(context.contentResolver)

        val request = factory.fromUri(
            Uri.parse("content://com.personalai.instantread.test.emptycursor/share/fallback.html"),
            SourceKind.ACTION_VIEW
        )

        assertEquals("fallback.html", request.displayName)
        assertNull(request.declaredSizeBytes)
    }

    @Test
    fun keepsValidSizeWhenDisplayNameColumnIsMissing() {
        val context = RuntimeEnvironment.getApplication()
        ShadowContentResolver.registerProviderInternal(
            "com.personalai.instantread.test.missingname",
            DisplayNameProvider(
                displayName = "unused.txt",
                mimeType = "text/plain",
                sizeBytes = 1_234L,
                includeDisplayNameColumn = false,
                includeSizeColumn = true
            )
        )
        val factory = UriImportRequestFactory(context.contentResolver)

        val request = factory.fromUri(
            Uri.parse("content://com.personalai.instantread.test.missingname/share/fallback.txt"),
            SourceKind.ACTION_SEND
        )

        assertEquals("fallback.txt", request.displayName)
        assertEquals(1_234L, request.declaredSizeBytes)
    }

    @Test
    fun keepsProviderDisplayNameWhenSizeColumnIsNull() {
        val context = RuntimeEnvironment.getApplication()
        ShadowContentResolver.registerProviderInternal(
            "com.personalai.instantread.test.nullsize",
            DisplayNameProvider(
                displayName = "known.md",
                mimeType = "text/markdown",
                sizeBytes = null,
                includeSizeColumn = true
            )
        )
        val factory = UriImportRequestFactory(context.contentResolver)

        val request = factory.fromUri(
            Uri.parse("content://com.personalai.instantread.test.nullsize/share/fallback.md"),
            SourceKind.APP_PICKER
        )

        assertEquals("known.md", request.displayName)
        assertNull(request.declaredSizeBytes)
    }

    @Test
    fun usesFileUriPathSegmentWhenOpenableDisplayNameIsUnavailable() {
        val context = RuntimeEnvironment.getApplication()
        val factory = UriImportRequestFactory(context.contentResolver)

        val request = factory.fromUri(
            Uri.parse("file:///sdcard/Download/android-instantread-validation/sample.html"),
            SourceKind.ACTION_VIEW
        )

        assertEquals("sample.html", request.displayName)
        assertEquals(SourceKind.ACTION_VIEW, request.sourceKind)
    }

    @Test
    fun usesDecodedFileNameFromDocumentUriWhenOpenableDisplayNameIsUnavailable() {
        val context = RuntimeEnvironment.getApplication()
        val factory = UriImportRequestFactory(context.contentResolver)

        val request = factory.fromUri(
            Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2F阶段%206%20Sample.HTML"),
            SourceKind.ACTION_SEND
        )

        assertEquals("阶段 6 Sample.HTML", request.displayName)
        assertEquals(SourceKind.ACTION_SEND, request.sourceKind)
    }

    @Test
    fun usesIntentMimeTypeWhenResolverMimeTypeIsUnavailable() {
        val context = RuntimeEnvironment.getApplication()
        val factory = UriImportRequestFactory(context.contentResolver)

        val request = factory.fromUri(
            uri = Uri.parse("content://example.no.provider/share/no-extension"),
            sourceKind = SourceKind.ACTION_SEND,
            intentMimeType = "text/plain"
        )

        assertEquals("no-extension", request.displayName)
        assertEquals("text/plain", request.mimeType)
        assertEquals(SourceKind.ACTION_SEND, request.sourceKind)
    }

    @Test
    fun fallsBackToUriFileNameWhenProviderReturnsBlankDisplayName() {
        val context = RuntimeEnvironment.getApplication()
        ShadowContentResolver.registerProviderInternal(
            "com.personalai.instantread.test.blankname",
            BlankDisplayNameProvider()
        )
        val factory = UriImportRequestFactory(context.contentResolver)

        val request = factory.fromUri(
            Uri.parse("content://com.personalai.instantread.test.blankname/share/chat%20sample.txt"),
            SourceKind.ACTION_SEND
        )

        assertEquals("chat sample.txt", request.displayName)
        assertEquals("application/octet-stream", request.mimeType)
        assertEquals(SourceKind.ACTION_SEND, request.sourceKind)
    }

    @Test
    fun prefersUriFileNameWhenProviderDisplayNameHasNoExtensionAndMimeIsOctetStream() {
        val context = RuntimeEnvironment.getApplication()
        ShadowContentResolver.registerProviderInternal(
            "com.personalai.instantread.test.noextension",
            DisplayNameProvider(displayName = "shared-file", mimeType = "application/octet-stream")
        )
        val factory = UriImportRequestFactory(context.contentResolver)

        val request = factory.fromUri(
            Uri.parse("content://com.personalai.instantread.test.noextension/share/AI%20report.html"),
            SourceKind.ACTION_SEND
        )

        assertEquals("AI report.html", request.displayName)
        assertEquals("application/octet-stream", request.mimeType)
        assertEquals(SourceKind.ACTION_SEND, request.sourceKind)
    }

    @Test
    fun keepsProviderDisplayNameWhenItAlreadyHasAnExtension() {
        val context = RuntimeEnvironment.getApplication()
        ShadowContentResolver.registerProviderInternal(
            "com.personalai.instantread.test.providerextension",
            DisplayNameProvider(displayName = "provider-name.txt", mimeType = "application/octet-stream")
        )
        val factory = UriImportRequestFactory(context.contentResolver)

        val request = factory.fromUri(
            Uri.parse("content://com.personalai.instantread.test.providerextension/share/path-name.html"),
            SourceKind.ACTION_SEND
        )

        assertEquals("provider-name.txt", request.displayName)
        assertEquals("application/octet-stream", request.mimeType)
        assertEquals(SourceKind.ACTION_SEND, request.sourceKind)
    }

    private class BlankDisplayNameProvider : DisplayNameProvider(
        displayName = "   ",
        mimeType = "application/octet-stream"
    )

    private open class DisplayNameProvider(
        private val displayName: String,
        private val mimeType: String,
        private val sizeBytes: Long? = null,
        private val includeDisplayNameColumn: Boolean = true,
        private val includeSizeColumn: Boolean = false,
        private val includeRow: Boolean = true,
        private val queryFailure: RuntimeException? = null
    ) : ContentProvider() {
        var lastProjection: Array<out String>? = null
        var queryCalls: Int = 0
        var getTypeCalls: Int = 0
        var lastCursor: TrackingCursor? = null

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            queryCalls += 1
            lastProjection = projection
            queryFailure?.let { throw it }
            val columns = buildList {
                if (includeDisplayNameColumn) add(OpenableColumns.DISPLAY_NAME)
                if (includeSizeColumn) add(OpenableColumns.SIZE)
            }.toTypedArray()
            val cursor = MatrixCursor(columns).apply {
                if (includeRow) {
                    val row = buildList<Any?> {
                        if (includeDisplayNameColumn) add(displayName)
                        if (includeSizeColumn) add(sizeBytes)
                    }
                    addRow(row.toTypedArray())
                }
            }
            return TrackingCursor(cursor).also { lastCursor = it }
        }

        override fun getType(uri: Uri): String {
            getTypeCalls += 1
            return mimeType
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int = 0
    }

    private class TrackingCursor(cursor: Cursor) : CursorWrapper(cursor) {
        var closed = false

        override fun close() {
            closed = true
            super.close()
        }
    }
}
