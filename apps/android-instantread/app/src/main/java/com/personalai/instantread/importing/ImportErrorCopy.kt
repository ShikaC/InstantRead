package com.personalai.instantread.importing

import com.personalai.instantread.io.ImportResourcePolicy
import java.util.Locale

fun ImportError.toUserMessage(): String {
    return when (this) {
        ImportError.CannotReadSource ->
            "无法读取文件。请重新选择文件，并确认来源应用仍允许访问该文件。"
        ImportError.EmptyFile ->
            "文件为空，无法预览。请重新选择一个包含内容的文件。"
        is ImportError.FileTooLarge ->
            "所选 ${fileType.displayLabel} 文件超过 ${limitBytes.toBinaryMiBLabel()} 上限，已停止导入且未保存。请选择较小的文件后重试。"
        is ImportError.CopyFailed ->
            "导入失败。请重新选择文件，或确认来源文件仍可访问。"
        is ImportError.UnsupportedFileType ->
            buildString {
                append("暂不支持该文件类型。当前支持 HTML、Markdown、TXT 和 ZIP 网页包，请重新选择支持的文件。")
                val details = listOfNotNull(
                    displayName?.takeIf { it.isNotBlank() }?.let { "文件名：$it" },
                    mimeType?.takeIf { it.isNotBlank() }?.let { "MIME：$it" }
                )
                if (details.isNotEmpty()) {
                    append("\n")
                    append(details.joinToString("；"))
                }
            }
    }
}

private val FileType.displayLabel: String
    get() = when (this) {
        FileType.HTML -> "HTML"
        FileType.MARKDOWN -> "Markdown"
        FileType.TEXT -> "TXT"
        FileType.ZIP -> "ZIP"
    }

private fun Long.toBinaryMiBLabel(): String {
    val wholeMiB = this / ImportResourcePolicy.MIB
    val number = if (this % ImportResourcePolicy.MIB == 0L) {
        wholeMiB.toString()
    } else {
        String.format(
            Locale.US,
            "%.2f",
            toDouble() / ImportResourcePolicy.MIB.toDouble()
        ).trimEnd('0').trimEnd('.')
    }
    return "$number MiB"
}
