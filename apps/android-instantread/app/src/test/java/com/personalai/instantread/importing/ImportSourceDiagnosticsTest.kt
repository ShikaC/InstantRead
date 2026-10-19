package com.personalai.instantread.importing

import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportSourceDiagnosticsTest {
    @Test
    fun formatsSourceEvidenceWithoutFullUriPath() {
        val request = FileImportRequest(
            displayName = "报告 FINAL.HTML",
            mimeType = "text/html",
            sourceKind = SourceKind.ACTION_SEND,
            openInputStream = { ByteArrayInputStream("x".toByteArray()) },
            providerMimeType = null,
            intentMimeType = "text/html",
            entryAction = "android.intent.action.SEND_MULTIPLE",
            entryUriSource = "extraStreamList"
        )

        val summary = ImportSourceDiagnostics.format(
            uri = Uri.parse("content://downloads.example/public_downloads/123/report.html"),
            request = request,
            result = FileImportResult.Failure(ImportError.CannotReadSource)
        )

        assertTrue(summary.contains("sourceKind=ACTION_SEND"))
        assertTrue(summary.contains("entryAction=android.intent.action.SEND_MULTIPLE"))
        assertTrue(summary.contains("entryUriSource=extraStreamList"))
        assertTrue(summary.contains("scheme=content"))
        assertTrue(summary.contains("authority=downloads.example"))
        assertTrue(summary.contains("pathSegments=3"))
        assertTrue(summary.contains("lastPathExtension=html"))
        assertTrue(summary.contains("displayName=报告 FINAL.HTML"))
        assertTrue(summary.contains("displayExtension=html"))
        assertTrue(summary.contains("intentMime=text/html"))
        assertTrue(summary.contains("providerMime=<none>"))
        assertTrue(summary.contains("resolvedMime=text/html"))
        assertTrue(summary.contains("result=failure:CannotReadSource"))
        assertFalse(summary.contains("public_downloads/123/report.html"))
    }

    @Test
    fun labelsFileTooLargeFailure() {
        val request = FileImportRequest(
            displayName = "large.zip",
            mimeType = "application/zip",
            sourceKind = SourceKind.APP_PICKER,
            openInputStream = { ByteArrayInputStream(byteArrayOf(1)) }
        )

        val summary = ImportSourceDiagnostics.format(
            uri = Uri.parse("content://example/large.zip"),
            request = request,
            result = FileImportResult.Failure(
                ImportError.FileTooLarge(FileType.ZIP, actualBytes = 101L, limitBytes = 100L)
            )
        )

        assertTrue(summary.contains("result=failure:FileTooLarge"))
    }
}
