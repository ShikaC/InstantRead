package com.personalai.instantread.tasks

import java.math.BigInteger

enum class ForegroundTaskStage(val label: String) {
    READING_METADATA("正在读取文件信息"),
    IMPORTING_FILE("正在导入文件"),
    CHECKING_TEXT_ENCODING("正在检查文本编码"),
    GENERATING_READING_PAGE("正在生成阅读页"),
    EXTRACTING_ZIP("正在安全解压 ZIP")
}

data class ForegroundTaskState(
    val displayName: String?,
    val stage: ForegroundTaskStage,
    val completedBytes: Long? = null,
    val totalBytes: Long? = null
) {
    val percent: Int?
        get() {
            val total = totalBytes?.takeIf { it > 0 } ?: return null
            val completed = completedBytes?.coerceAtLeast(0) ?: 0L
            if (completed >= total) return 100
            return BigInteger.valueOf(completed)
                .multiply(ONE_HUNDRED)
                .divide(BigInteger.valueOf(total))
                .toInt()
        }

    private companion object {
        val ONE_HUNDRED: BigInteger = BigInteger.valueOf(100)
    }
}

fun interface FileTaskCallbackDispatcher {
    fun dispatch(block: () -> Unit)
}
