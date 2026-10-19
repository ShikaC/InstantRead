package com.personalai.instantread.importing

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.OpenableColumns
import com.personalai.instantread.io.FileTaskCancellationToken
import com.personalai.instantread.io.FileTaskCancelledException

class UriImportRequestFactory(
    private val contentResolver: ContentResolver
) : ImportRequestFactory {
    override fun fromUri(
        uri: Uri,
        sourceKind: SourceKind,
        intentMimeType: String?,
        entryAction: String?,
        entryUriSource: String?,
        cancellationToken: FileTaskCancellationToken
    ): FileImportRequest {
        cancellationToken.throwIfCancelled()
        val metadata = queryMetadata(uri, cancellationToken)
        val fallbackDisplayName = fallbackDisplayName(uri)
        val displayName = chooseDisplayName(metadata.displayName, fallbackDisplayName)
        cancellationToken.throwIfCancelled()
        val providerMimeType = contentResolver.getType(uri)
        cancellationToken.throwIfCancelled()
        val mimeType = providerMimeType ?: intentMimeType
        return FileImportRequest(
            displayName = displayName,
            mimeType = mimeType,
            sourceKind = sourceKind,
            openInputStream = { contentResolver.openInputStream(uri) },
            providerMimeType = providerMimeType,
            intentMimeType = intentMimeType,
            entryAction = entryAction,
            entryUriSource = entryUriSource,
            declaredSizeBytes = metadata.sizeBytes
        )
    }

    private fun queryMetadata(
        uri: Uri,
        cancellationToken: FileTaskCancellationToken
    ): OpenableMetadata {
        cancellationToken.throwIfCancelled()
        val cancellationSignal = CancellationSignal()
        val cancellationRegistration = cancellationToken.invokeOnCancellation(cancellationSignal::cancel)
        return try {
            cancellationToken.throwIfCancelled()
            val cursor: Cursor? = contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
                cancellationSignal
            )
            if (cursor == null) {
                cancellationToken.throwIfCancelled()
                return OpenableMetadata.EMPTY
            }

            val metadata = cursor.use {
                cancellationToken.throwIfCancelled()
                if (!it.moveToFirst()) {
                    OpenableMetadata.EMPTY
                } else {
                    val displayNameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    val displayName = if (displayNameIndex >= 0) {
                        it.getString(displayNameIndex)?.takeIf(String::isNotBlank)
                    } else {
                        null
                    }
                    val sizeBytes = if (sizeIndex >= 0 && !it.isNull(sizeIndex)) {
                        it.getLong(sizeIndex).takeIf { size -> size >= 0L }
                    } else {
                        null
                    }
                    OpenableMetadata(displayName, sizeBytes)
                }
            }
            cancellationToken.throwIfCancelled()
            metadata
        } catch (error: FileTaskCancelledException) {
            throw error
        } catch (_: OperationCanceledException) {
            cancellationToken.throwIfCancelled()
            OpenableMetadata.EMPTY
        } catch (_: Exception) {
            cancellationToken.throwIfCancelled()
            OpenableMetadata.EMPTY
        } finally {
            cancellationRegistration.dispose()
        }
    }

    private fun fallbackDisplayName(uri: Uri): String? {
        return uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
    }

    private fun chooseDisplayName(providerDisplayName: String?, fallbackDisplayName: String?): String? {
        return when {
            providerDisplayName == null -> fallbackDisplayName
            !providerDisplayName.hasExtension() && fallbackDisplayName.hasExtension() -> fallbackDisplayName
            else -> providerDisplayName
        }
    }

    private fun String?.hasExtension(): Boolean {
        return this
            ?.substringAfterLast('/', missingDelimiterValue = this)
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.isNotBlank()
            ?: false
    }

    private data class OpenableMetadata(
        val displayName: String?,
        val sizeBytes: Long?
    ) {
        companion object {
            val EMPTY = OpenableMetadata(displayName = null, sizeBytes = null)
        }
    }
}
