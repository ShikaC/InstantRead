package com.personalai.instantread

import android.content.Context

interface PreviewReadingPositionStore {
    fun save(historyId: String, documentMode: PreviewDocumentMode, scrollY: Int)
    fun load(historyId: String, documentMode: PreviewDocumentMode): Int
}

class PreviewReadingPositionRepository(
    context: Context
) : PreviewReadingPositionStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun save(historyId: String, documentMode: PreviewDocumentMode, scrollY: Int) {
        preferences.edit()
            .putInt(key(historyId, documentMode), scrollY.coerceAtLeast(0))
            .apply()
    }

    override fun load(historyId: String, documentMode: PreviewDocumentMode): Int {
        return preferences.getInt(key(historyId, documentMode), 0)
    }

    private fun key(historyId: String, documentMode: PreviewDocumentMode): String {
        return "$historyId:${documentMode.name}"
    }

    private companion object {
        const val PREFERENCES_NAME = "preview-reading-position"
    }
}
