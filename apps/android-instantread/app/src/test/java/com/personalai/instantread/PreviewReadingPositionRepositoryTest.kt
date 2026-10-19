package com.personalai.instantread

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreviewReadingPositionRepositoryTest {
    @Test
    fun savesReadingAndReviewPositionsSeparatelyPerHistoryId() {
        val repository = PreviewReadingPositionRepository(RuntimeEnvironment.getApplication())
        val historyId = "history-${System.nanoTime()}"

        repository.save(historyId, PreviewDocumentMode.READING, 120)

        assertEquals(120, repository.load(historyId, PreviewDocumentMode.READING))
        assertEquals(0, repository.load(historyId, PreviewDocumentMode.REVIEW))

        repository.save(historyId, PreviewDocumentMode.REVIEW, 280)

        assertEquals(120, repository.load(historyId, PreviewDocumentMode.READING))
        assertEquals(280, repository.load(historyId, PreviewDocumentMode.REVIEW))
    }

    @Test
    fun negativePositionsAreStoredAsZero() {
        val repository = PreviewReadingPositionRepository(RuntimeEnvironment.getApplication())
        val historyId = "history-negative-${System.nanoTime()}"

        repository.save(historyId, PreviewDocumentMode.READING, -20)

        assertEquals(0, repository.load(historyId, PreviewDocumentMode.READING))
    }
}
