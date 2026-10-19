package com.personalai.instantread.preview

import com.personalai.instantread.importing.FileType
import com.personalai.instantread.io.FileTaskCancellationToken
import com.personalai.instantread.io.FileTaskCancelledException
import com.personalai.instantread.io.ImportResourcePolicy
import com.personalai.instantread.io.SourceFileTooLargeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.Charset

class LocalTextDecoderTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val decoder = LocalTextDecoder()

    @Test
    fun decodesStrictUtf8WithoutBom() {
        val result = decoder.decode(
            file("utf8.txt", "你好，UTF-8".toByteArray(Charsets.UTF_8)),
            FileType.TEXT
        )

        assertEquals(LocalTextEncoding.UTF8, result.encoding)
        assertEquals("你好，UTF-8", result.text)
    }

    @Test
    fun prefersUtf8ForAsciiBytesThatAreAlsoValidGb18030() {
        val result = decoder.decode(
            file("ascii.txt", "plain text".toByteArray(Charsets.US_ASCII)),
            FileType.TEXT
        )

        assertEquals(LocalTextEncoding.UTF8, result.encoding)
        assertEquals("plain text", result.text)
    }

    @Test
    fun decodesUtf8BomAndRemovesBom() {
        val payload = UTF8_BOM + "标题".toByteArray(Charsets.UTF_8)

        val result = decoder.decode(file("utf8-bom.md", payload), FileType.MARKDOWN)

        assertEquals(LocalTextEncoding.UTF8, result.encoding)
        assertEquals("标题", result.text)
    }

    @Test
    fun decodesUtf16LittleEndianBom() {
        val payload = UTF16_LE_BOM + "中文 LE".toByteArray(Charsets.UTF_16LE)

        val result = decoder.decode(file("utf16le.txt", payload), FileType.TEXT)

        assertEquals(LocalTextEncoding.UTF16_LE, result.encoding)
        assertEquals("中文 LE", result.text)
    }

    @Test
    fun decodesUtf16BigEndianBom() {
        val payload = UTF16_BE_BOM + "中文 BE".toByteArray(Charsets.UTF_16BE)

        val result = decoder.decode(file("utf16be.txt", payload), FileType.TEXT)

        assertEquals(LocalTextEncoding.UTF16_BE, result.encoding)
        assertEquals("中文 BE", result.text)
    }

    @Test
    fun fallsBackToStrictGb18030() {
        val charset = Charset.forName("GB18030")

        val result = decoder.decode(
            file("gb18030.txt", "简体中文报告".toByteArray(charset)),
            FileType.TEXT
        )

        assertEquals(LocalTextEncoding.GB18030, result.encoding)
        assertEquals("简体中文报告", result.text)
    }

    @Test
    fun rejectsBytesInvalidForUtf8AndGb18030() {
        assertThrows(UnknownTextEncodingException::class.java) {
            decoder.decode(file("invalid.txt", byteArrayOf(0x81.toByte())), FileType.TEXT)
        }
    }

    @Test
    fun rejectsMalformedUtf8AfterBomWithoutFallingBackToGb18030() {
        val validGb18030ButInvalidUtf8 = "文".toByteArray(Charset.forName("GB18030"))

        assertThrows(UnknownTextEncodingException::class.java) {
            decoder.decode(
                file("malformed-utf8-bom.txt", UTF8_BOM + validGb18030ButInvalidUtf8),
                FileType.TEXT
            )
        }
    }

    @Test
    fun rejectsOddUtf16LittleEndianPayloadAfterBom() {
        assertThrows(UnknownTextEncodingException::class.java) {
            decoder.decode(
                file("odd-utf16le.txt", UTF16_LE_BOM + byteArrayOf(0x41)),
                FileType.TEXT
            )
        }
    }

    @Test
    fun rejectsOddUtf16BigEndianPayloadAfterBom() {
        assertThrows(UnknownTextEncodingException::class.java) {
            decoder.decode(
                file("odd-utf16be.txt", UTF16_BE_BOM + byteArrayOf(0x41)),
                FileType.TEXT
            )
        }
    }

    @Test
    fun doesNotGuessUtf16WithoutBom() {
        assertThrows(NotTextContentException::class.java) {
            decoder.decode(
                file("bomless-utf16.txt", "AB".toByteArray(Charsets.UTF_16LE)),
                FileType.TEXT
            )
        }
    }

    @Test
    fun rejectsNulEvenWhenUtf8DecodingSucceeds() {
        assertThrows(NotTextContentException::class.java) {
            decoder.decode(
                file("binary.txt", byteArrayOf('A'.code.toByte(), 0, 'B'.code.toByte())),
                FileType.TEXT
            )
        }
    }

    @Test
    fun permitsEightDisallowedControlCharactersAtTheMinimumThreshold() {
        val text = String(ByteArray(8) { 0x01 }, Charsets.UTF_8)

        val result = decoder.decode(file("controls-boundary.txt", text.toByteArray()), FileType.TEXT)

        assertEquals(text, result.text)
        assertEquals(LocalTextEncoding.UTF8, result.encoding)
    }

    @Test
    fun rejectsExcessiveDisallowedControlCharacters() {
        val payload = ByteArray(9) { 0x01 }

        assertThrows(NotTextContentException::class.java) {
            decoder.decode(file("controls.txt", payload), FileType.TEXT)
        }
    }

    @Test
    fun permitsWhitespaceControlCharactersUsedByTextDocuments() {
        val text = "tab\tline\nreturn\rform-feed\u000C"

        val result = decoder.decode(file("whitespace.txt", text.toByteArray()), FileType.TEXT)

        assertEquals(text, result.text)
    }

    @Test
    fun acceptsFilesExactlyAtTheirConfiguredLimits() {
        val boundedDecoder = LocalTextDecoder(
            resourcePolicy = ImportResourcePolicy.DEFAULT.copy(
                markdownMaxBytes = 4,
                textMaxBytes = 4
            )
        )

        assertEquals(
            "1234",
            boundedDecoder.decode(file("exact.md", "1234".toByteArray()), FileType.MARKDOWN).text
        )
        assertEquals(
            "5678",
            boundedDecoder.decode(file("exact.txt", "5678".toByteArray()), FileType.TEXT).text
        )
    }

    @Test
    fun rejectsFilesOneByteOverTheConfiguredTextLimit() {
        val boundedDecoder = LocalTextDecoder(
            resourcePolicy = ImportResourcePolicy.DEFAULT.copy(textMaxBytes = 4)
        )

        val error = assertThrows(SourceFileTooLargeException::class.java) {
            boundedDecoder.decode(file("large.txt", "12345".toByteArray()), FileType.TEXT)
        }

        assertEquals(5L, error.actualBytes)
        assertEquals(4L, error.limitBytes)
    }

    @Test
    fun supportsOnlyMarkdownAndTextBeforeOpeningTheFile() {
        val missingFile = File(temp.root, "missing")

        assertThrows(IllegalArgumentException::class.java) {
            decoder.decode(missingFile, FileType.HTML)
        }
        assertThrows(IllegalArgumentException::class.java) {
            decoder.decode(missingFile, FileType.ZIP)
        }
    }

    @Test
    fun propagatesCancellationFromTheSameTokenAfterCopying() {
        val expected = FileTaskCancelledException()
        var checks = 0
        val token = FileTaskCancellationToken {
            checks += 1
            if (checks == 3) throw expected
        }

        val actual = assertThrows(FileTaskCancelledException::class.java) {
            decoder.decode(file("cancelled.txt", byteArrayOf('A'.code.toByte())), FileType.TEXT, token)
        }

        assertSame(expected, actual)
        assertEquals(3, checks)
    }

    @Test
    fun decodesAnEmptyFileAsUtf8() {
        val result = decoder.decode(file("empty.txt", ByteArray(0)), FileType.TEXT)

        assertEquals(LocalTextEncoding.UTF8, result.encoding)
        assertEquals("", result.text)
    }

    private fun file(name: String, bytes: ByteArray): File =
        temp.newFile(name).apply { writeBytes(bytes) }

    private companion object {
        val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val UTF16_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val UTF16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
    }
}
