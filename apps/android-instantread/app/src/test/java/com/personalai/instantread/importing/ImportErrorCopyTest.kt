package com.personalai.instantread.importing

import com.personalai.instantread.io.ImportResourcePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportErrorCopyTest {
    @Test
    fun cannotReadSourceMessageExplainsRecovery() {
        val message = ImportError.CannotReadSource.toUserMessage()

        assertTrue(message.contains("无法读取文件"))
        assertTrue(message.contains("请重新选择文件"))
        assertTrue(message.contains("来源应用"))
    }

    @Test
    fun emptyFileMessageExplainsRecovery() {
        val message = ImportError.EmptyFile.toUserMessage()

        assertTrue(message.contains("文件为空"))
        assertTrue(message.contains("无法预览"))
        assertTrue(message.contains("包含内容的文件"))
    }

    @Test
    fun unsupportedFileTypeMessageListsSupportedFormatsAndDetails() {
        val message = ImportError.UnsupportedFileType(
            displayName = "report.pdf",
            mimeType = "application/pdf"
        ).toUserMessage()

        assertTrue(message.contains("暂不支持该文件类型"))
        assertTrue(message.contains("HTML"))
        assertTrue(message.contains("Markdown"))
        assertTrue(message.contains("TXT"))
        assertTrue(message.contains("ZIP"))
        assertTrue(message.contains("report.pdf"))
        assertTrue(message.contains("application/pdf"))
    }

    @Test
    fun copyFailedMessageExplainsRecoveryWithoutLeakingSourceDetails() {
        val message = ImportError.CopyFailed(
            "permission denied for content://com.example.provider/private/report.md " +
                "at C:\\Users\\example\\Documents\\report.md " +
                "and /data/user/0/com.personalai.instantread/cache/report.md"
        ).toUserMessage()

        assertTrue(message.contains("导入失败"))
        assertTrue(message.contains("请重新选择文件"))
        assertTrue(message.contains("来源文件仍可访问"))
        assertFalse(message.contains("permission denied"))
        assertFalse(message.contains("content://"))
        assertFalse(message.contains("C:\\Users"))
        assertFalse(message.contains("/data/user/0"))
        assertFalse(message.contains("技术细节"))
    }

    @Test
    fun fileTooLargeMessagesNameEachFormatAndBinaryMibLimit() {
        val labels = mapOf(
            FileType.MARKDOWN to "Markdown",
            FileType.TEXT to "TXT",
            FileType.HTML to "HTML",
            FileType.ZIP to "ZIP"
        )

        labels.forEach { (fileType, label) ->
            val message = ImportError.FileTooLarge(
                fileType = fileType,
                actualBytes = 3L * ImportResourcePolicy.MIB,
                limitBytes = 2L * ImportResourcePolicy.MIB
            ).toUserMessage()

            assertTrue(message.contains(label))
            assertTrue(message.contains("2 MiB"))
            assertTrue(message.contains("未保存"))
            assertTrue(message.contains("较小"))
        }
    }
}
