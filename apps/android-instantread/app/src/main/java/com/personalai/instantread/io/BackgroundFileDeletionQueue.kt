package com.personalai.instantread.io

import java.io.File
import java.util.concurrent.Executors

internal object BackgroundFileDeletionQueue {
    private val executor = Executors.newSingleThreadExecutor { action ->
        Thread(action, "instantread-file-cleanup").apply { isDaemon = true }
    }

    fun execute(action: () -> Unit) {
        executor.execute(action)
    }

    fun schedule(path: File) {
        execute {
            repeat(DELETE_ATTEMPTS) {
                if (!path.exists()) return@execute
                val deleted = try {
                    path.deleteRecursively()
                } catch (_: Exception) {
                    false
                }
                if (deleted || !path.exists()) return@execute
            }
            // Crash leftovers are queued again during the next App startup.
        }
    }

    private const val DELETE_ATTEMPTS = 3
}
