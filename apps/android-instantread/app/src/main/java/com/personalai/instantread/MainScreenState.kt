package com.personalai.instantread

import android.os.Bundle
import com.personalai.instantread.ui.HomeListFilter

internal enum class MainScreen {
    HOME,
    PREVIEW,
    SETTINGS
}

internal data class MainScreenState(
    val screen: MainScreen = MainScreen.HOME,
    val previewHistoryId: String? = null,
    val previewDocumentMode: PreviewDocumentMode = PreviewDocumentMode.READING,
    val homeListFilter: HomeListFilter = HomeListFilter.ALL,
    val homeSearchQuery: String = ""
) {
    fun writeTo(outState: Bundle) {
        outState.putString(KEY_SCREEN, screen.name)
        outState.putString(KEY_PREVIEW_HISTORY_ID, previewHistoryId)
        outState.putString(KEY_PREVIEW_DOCUMENT_MODE, previewDocumentMode.name)
        outState.putString(KEY_HOME_LIST_FILTER, homeListFilter.name)
        outState.putString(KEY_HOME_SEARCH_QUERY, homeSearchQuery)
    }

    fun restoreScreen(onSettings: () -> Unit, onPreview: (String, PreviewDocumentMode) -> Unit) {
        when (screen) {
            MainScreen.PREVIEW -> previewHistoryId?.let { onPreview(it, previewDocumentMode) }
            MainScreen.SETTINGS -> onSettings()
            MainScreen.HOME -> Unit
        }
    }

    companion object {
        fun capture(
            isShowingPreview: Boolean,
            isShowingSettings: Boolean,
            isShowingTask: Boolean,
            previewHistoryId: String?,
            previewDocumentMode: PreviewDocumentMode,
            homeListFilter: HomeListFilter,
            homeSearchQuery: String
        ): MainScreenState {
            val screen = when {
                isShowingSettings -> MainScreen.SETTINGS
                (isShowingPreview || isShowingTask) && previewHistoryId != null -> MainScreen.PREVIEW
                else -> MainScreen.HOME
            }
            return MainScreenState(screen, previewHistoryId, previewDocumentMode, homeListFilter, homeSearchQuery)
        }

        fun restore(savedInstanceState: Bundle?): MainScreenState {
            if (savedInstanceState == null) return MainScreenState()
            val previewHistoryId = savedInstanceState.getString(KEY_PREVIEW_HISTORY_ID)
                ?.takeUnless(String::isBlank)
            val requestedScreen = savedInstanceState.getString(KEY_SCREEN).enumValueOrNull<MainScreen>()
                ?: MainScreen.HOME
            return MainScreenState(
                screen = if (requestedScreen == MainScreen.PREVIEW && previewHistoryId == null) {
                    MainScreen.HOME
                } else {
                    requestedScreen
                },
                previewHistoryId = previewHistoryId,
                previewDocumentMode = savedInstanceState.getString(KEY_PREVIEW_DOCUMENT_MODE)
                    .enumValueOrNull<PreviewDocumentMode>() ?: PreviewDocumentMode.READING,
                homeListFilter = savedInstanceState.getString(KEY_HOME_LIST_FILTER)
                    .enumValueOrNull<HomeListFilter>() ?: HomeListFilter.ALL,
                homeSearchQuery = savedInstanceState.getString(KEY_HOME_SEARCH_QUERY).orEmpty()
            )
        }

        private inline fun <reified T : Enum<T>> String?.enumValueOrNull(): T? =
            this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

        private const val KEY_SCREEN = "instantread.screen"
        private const val KEY_PREVIEW_HISTORY_ID = "instantread.preview.history_id"
        private const val KEY_PREVIEW_DOCUMENT_MODE = "instantread.preview.document_mode"
        private const val KEY_HOME_LIST_FILTER = "instantread.home.list_filter"
        private const val KEY_HOME_SEARCH_QUERY = "instantread.home.search_query"
    }
}
