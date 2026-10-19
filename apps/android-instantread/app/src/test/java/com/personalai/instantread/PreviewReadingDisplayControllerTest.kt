package com.personalai.instantread

import com.personalai.instantread.preview.ReadingDisplayOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewReadingDisplayControllerTest {
    @Test
    fun openCloseAndClearUpdatePanelStateAndNotifyChrome() {
        val changedIds = mutableListOf<String>()
        val controller = PreviewReadingDisplayController(
            onPanelStateChanged = { changedIds += it },
            onPreparationRequested = {},
            onOptionsCommitted = {}
        )

        controller.open("history-1")
        assertTrue(controller.isPanelOpen)
        controller.close("history-1")
        assertFalse(controller.isPanelOpen)
        controller.open("history-2")
        controller.clear()

        assertFalse(controller.isPanelOpen)
        assertEquals(listOf("history-1", "history-1", "history-2"), changedIds)
    }

    @Test
    fun applyStagesEffectiveOptionsAndRefreshesChromeBeforePreparation() {
        val harness = controllerHarness()
        val committed = ReadingDisplayOptions.DEFAULT

        val request = harness.controller.apply("history-1", committed) {
            it.copy(fontSizeSp = 20)
        }

        assertEquals(listOf("panel:history-1", "prepare:history-1"), harness.events)
        assertEquals(20, request.options.fontSizeSp)
        assertTrue(harness.commits.isEmpty())
        assertEquals(request.options, harness.controller.optionsFor("history-1", committed))
        assertEquals(committed, harness.controller.optionsFor("history-2", committed))
    }

    @Test
    fun resetStagesDefaultWithoutCommitting() {
        val harness = controllerHarness()

        harness.controller.reset("history-1")

        assertEquals(listOf("panel:history-1", "prepare:history-1"), harness.events)
        assertEquals(ReadingDisplayOptions.DEFAULT, harness.preparations.single().second.options)
        assertTrue(harness.commits.isEmpty())
        assertEquals(
            ReadingDisplayOptions.DEFAULT,
            harness.controller.optionsFor(
                "history-1",
                ReadingDisplayOptions(fontSizeSp = 22)
            )
        )
    }

    @Test
    fun consecutiveApplyCommitsOnlyTheLatestCompleteOptions() {
        val harness = controllerHarness()
        val committed = ReadingDisplayOptions.DEFAULT

        val firstRequest = harness.controller.apply("history-1", committed) {
            it.copy(fontSizeSp = 19)
        }
        val latestRequest = harness.controller.apply("history-1", committed) {
            it.copy(letterSpacingEm = 0.04f)
        }
        val latest = latestRequest.options

        assertEquals(19, latest.fontSizeSp)
        assertEquals(0.04f, latest.letterSpacingEm, 0.001f)
        assertFalse(harness.controller.complete(firstRequest, success = true))
        assertTrue(harness.controller.complete(latestRequest, success = true))
        assertEquals(listOf(latest), harness.commits)
        assertEquals(latest, harness.controller.optionsFor("history-1", latest))
    }

    @Test
    fun successfulCompletionSavesCurrentPositionBeforeCommitAndChromeRefresh() {
        val harness = controllerHarness()
        val request = harness.controller.apply("history-1", ReadingDisplayOptions.DEFAULT) {
            it.copy(fontSizeSp = 21)
        }
        harness.events.clear()

        assertTrue(harness.controller.complete(request, success = true) { harness.events += "save" })

        assertEquals(listOf("save", "commit", "panel:history-1"), harness.events)
    }

    @Test
    fun failedCompletionDiscardsPendingAndRefreshesCommittedOptions() {
        val harness = controllerHarness()
        val committed = ReadingDisplayOptions.DEFAULT
        val request = harness.controller.apply("history-1", committed) {
            it.copy(lineHeight = 2.05f)
        }
        harness.events.clear()

        assertTrue(harness.controller.complete(request, success = false))

        assertTrue(harness.commits.isEmpty())
        assertEquals(listOf("panel:history-1"), harness.events)
        assertEquals(committed, harness.controller.optionsFor("history-1", committed))
    }

    @Test
    fun cancellingPendingDiscardsItWithoutCommitAndRefreshesChrome() {
        val harness = controllerHarness()
        val committed = ReadingDisplayOptions.DEFAULT
        harness.controller.apply("history-1", committed) {
            it.copy(paragraphSpacingEm = 1.1f)
        }
        harness.events.clear()

        harness.controller.cancelPending()

        assertTrue(harness.commits.isEmpty())
        assertEquals(listOf("panel:history-1"), harness.events)
        assertEquals(committed, harness.controller.optionsFor("history-1", committed))
    }

    @Test
    fun staleOrWrongHistoryCompletionCannotCommitNewerPendingOptions() {
        val harness = controllerHarness()
        val committed = ReadingDisplayOptions.DEFAULT
        val olderRequest = harness.controller.apply("history-1", committed) {
            it.copy(fontSizeSp = 18)
        }
        val newerRequest = harness.controller.apply("history-1", committed) {
            it.copy(letterSpacingEm = 0.05f)
        }
        val wrongHistoryRequest = newerRequest.copy(historyId = "history-2")

        assertFalse(harness.controller.complete(wrongHistoryRequest, success = true))
        assertFalse(harness.controller.complete(olderRequest, success = false))

        assertTrue(harness.commits.isEmpty())
        assertEquals(
            newerRequest.options,
            harness.controller.optionsFor("history-1", ReadingDisplayOptions.DEFAULT)
        )
    }

    @Test
    fun resetSupersedesOlderRequestAndCommitsOnlyDefault() {
        val harness = controllerHarness()
        val older = harness.controller.apply("history-1", ReadingDisplayOptions.DEFAULT) {
            it.copy(fontSizeSp = 23)
        }
        val reset = harness.controller.reset("history-1")

        assertFalse(harness.controller.complete(older, success = true))
        assertTrue(harness.controller.complete(reset, success = true))
        assertEquals(listOf(ReadingDisplayOptions.DEFAULT), harness.commits)
    }

    @Test
    fun clearAndAbandonInvalidatePendingCompletions() {
        val harness = controllerHarness()
        val beforeClear = harness.controller.apply("history-1", ReadingDisplayOptions.DEFAULT) {
            it.copy(fontSizeSp = 18)
        }
        harness.controller.clear()
        assertFalse(harness.controller.complete(beforeClear, success = true))

        val beforeAbandon = harness.controller.apply("history-1", ReadingDisplayOptions.DEFAULT) {
            it.copy(lineHeight = 2f)
        }
        harness.controller.abandonPending()
        assertFalse(harness.controller.complete(beforeAbandon, success = true))
        assertTrue(harness.commits.isEmpty())
    }

    private fun controllerHarness(): ControllerHarness {
        val events = mutableListOf<String>()
        val preparations = mutableListOf<Pair<String, ReadingDisplayPreparationRequest>>()
        val commits = mutableListOf<ReadingDisplayOptions>()
        val panelChanged: (String) -> Unit = { events += "panel:$it" }
        val preparationRequested: (ReadingDisplayPreparationRequest) -> Unit = { request ->
            events += "prepare:${request.historyId}"
            preparations += request.historyId to request
        }
        val controller = PreviewReadingDisplayController(panelChanged, preparationRequested) {
            events += "commit"
            commits += it
        }
        return ControllerHarness(controller, events, preparations, commits)
    }

    private data class ControllerHarness(
        val controller: PreviewReadingDisplayController,
        val events: MutableList<String>,
        val preparations: MutableList<Pair<String, ReadingDisplayPreparationRequest>>,
        val commits: MutableList<ReadingDisplayOptions>
    )
}
