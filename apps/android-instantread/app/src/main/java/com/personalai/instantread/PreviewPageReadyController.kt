package com.personalai.instantread

data class PreviewPageLoad(
    val historyId: String,
    val generation: Long
)

class PreviewPageReadyController(
    private val scrollPositionController: PreviewScrollPositionController,
    private val searchController: PreviewSearchController
) {
    private var generation = 0L

    fun beginLoad(historyId: String): PreviewPageLoad {
        generation += 1
        return PreviewPageLoad(historyId, generation)
    }

    fun onPageFinished(load: PreviewPageLoad) {
        if (load.generation != generation) return
        searchController.restoreCurrentQuery(load.historyId) {
            if (load.generation == generation) {
                scrollPositionController.restoreCurrent(load.historyId)
            }
        }
    }

    fun clear() {
        generation += 1
    }
}
