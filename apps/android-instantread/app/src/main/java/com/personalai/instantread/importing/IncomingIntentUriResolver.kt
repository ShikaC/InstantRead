package com.personalai.instantread.importing

import android.content.Intent
import android.net.Uri

data class IncomingIntentUri(
    val uri: Uri,
    val sourceKind: SourceKind,
    val mimeType: String?,
    val entryAction: String,
    val entryUriSource: String
)

object IncomingIntentUriResolver {
    fun resolve(intent: Intent?): IncomingIntentUri? {
        val action = intent?.action ?: return null
        return when (action) {
            Intent.ACTION_VIEW -> intent.data?.let {
                IncomingIntentUri(it, SourceKind.ACTION_VIEW, intent.type, action, "data")
            }
            Intent.ACTION_SEND -> resolveSendUri(intent)?.let { resolved ->
                IncomingIntentUri(resolved.uri, SourceKind.ACTION_SEND, intent.type, action, resolved.source)
            }
            Intent.ACTION_SEND_MULTIPLE -> resolveSingleSendMultipleUri(intent)
                ?.let { IncomingIntentUri(it, SourceKind.ACTION_SEND, intent.type, action, "extraStreamList") }
            else -> null
        }
    }

    private fun resolveSendUri(intent: Intent): ResolvedEntryUri? {
        return intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { ResolvedEntryUri(it, "extraStream") }
            ?: intent.clipData
                ?.takeIf { it.itemCount == 1 }
                ?.getItemAt(0)
                ?.uri
                ?.let { ResolvedEntryUri(it, "clipData") }
    }

    private fun resolveSingleSendMultipleUri(intent: Intent): Uri? {
        return intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            ?.takeIf { it.size == 1 }
            ?.firstOrNull()
    }

    private data class ResolvedEntryUri(
        val uri: Uri,
        val source: String
    )
}
