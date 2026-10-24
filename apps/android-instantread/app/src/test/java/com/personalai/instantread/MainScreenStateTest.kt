package com.personalai.instantread

import android.os.Bundle
import com.personalai.instantread.ui.HomeListFilter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainScreenStateTest {
    @Test
    fun restoresPreviewDocumentAndHomeFiltersAfterActivityRecreation() {
        val bundle = Bundle()
        MainScreenState(
            screen = MainScreen.PREVIEW,
            previewHistoryId = "history-1",
            previewDocumentMode = PreviewDocumentMode.REVIEW,
            homeListFilter = HomeListFilter.FAVORITES,
            homeSearchQuery = "报告"
        ).writeTo(bundle)

        assertEquals(
            MainScreenState(
                screen = MainScreen.PREVIEW,
                previewHistoryId = "history-1",
                previewDocumentMode = PreviewDocumentMode.REVIEW,
                homeListFilter = HomeListFilter.FAVORITES,
                homeSearchQuery = "报告"
            ),
            MainScreenState.restore(bundle)
        )
    }

    @Test
    fun fallsBackToHomeWithoutTrustingMalformedSavedState() {
        val bundle = Bundle().apply {
            putString("instantread.screen", "BROKEN")
            putString("instantread.preview.history_id", "")
            putString("instantread.preview.document_mode", "BROKEN")
            putString("instantread.home.list_filter", "BROKEN")
        }

        assertEquals(MainScreenState(), MainScreenState.restore(bundle))
    }

    @Test
    fun previewWithoutHistoryIdCannotRestoreAnEmptyPreview() {
        val bundle = Bundle()
        MainScreenState(screen = MainScreen.PREVIEW).writeTo(bundle)

        assertEquals(MainScreenState(), MainScreenState.restore(bundle))
    }
}
