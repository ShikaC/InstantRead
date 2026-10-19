package com.personalai.instantread.preview

import com.personalai.instantread.importing.FileType
import com.personalai.instantread.io.FileTaskCancellationToken
import com.personalai.instantread.io.ImportResourcePolicy
import com.personalai.instantread.io.LimitedInputCopier
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

class LocalTextDecoder(
    private val resourcePolicy: ImportResourcePolicy = ImportResourcePolicy.DEFAULT,
    private val copier: LimitedInputCopier = LimitedInputCopier()
) : TextDecoder {
    override fun decode(
        file: File,
        fileType: FileType,
        cancellationToken: FileTaskCancellationToken
    ): DecodedText {
        require(fileType == FileType.MARKDOWN || fileType == FileType.TEXT) {
            "Local text decoding supports only Markdown and text files"
        }

        val output = ByteArrayOutputStream()
        file.inputStream().use { input ->
            copier.copy(
                input = input,
                output = output,
                maxBytes = resourcePolicy.maxSourceBytes(fileType),
                cancellationToken = cancellationToken
            )
        }
        cancellationToken.throwIfCancelled()
        return decodeBytes(output.toByteArray())
    }

    private fun decodeBytes(bytes: ByteArray): DecodedText {
        val decoded = when {
            bytes.startsWith(UTF8_BOM) -> DecodedText(
                text = decodeRequired(bytes.copyOfRange(UTF8_BOM.size, bytes.size), Charsets.UTF_8),
                encoding = LocalTextEncoding.UTF8
            )

            bytes.startsWith(UTF16_LE_BOM) -> DecodedText(
                text = decodeRequired(
                    bytes.copyOfRange(UTF16_LE_BOM.size, bytes.size),
                    Charsets.UTF_16LE
                ),
                encoding = LocalTextEncoding.UTF16_LE
            )

            bytes.startsWith(UTF16_BE_BOM) -> DecodedText(
                text = decodeRequired(
                    bytes.copyOfRange(UTF16_BE_BOM.size, bytes.size),
                    Charsets.UTF_16BE
                ),
                encoding = LocalTextEncoding.UTF16_BE
            )

            else -> decodeStrict(bytes, Charsets.UTF_8)
                ?.let { DecodedText(it, LocalTextEncoding.UTF8) }
                ?: decodeStrict(bytes, Charset.forName(GB18030_NAME))
                    ?.let { DecodedText(it, LocalTextEncoding.GB18030) }
                ?: throw UnknownTextEncodingException()
        }

        validatePlausibleText(decoded.text)
        return decoded
    }

    private fun decodeRequired(bytes: ByteArray, charset: Charset): String =
        decodeStrict(bytes, charset) ?: throw UnknownTextEncodingException()

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun validatePlausibleText(text: String) {
        if ('\u0000' in text) throw NotTextContentException()

        val disallowedControlCount = text.count { character ->
            character < ' ' &&
                character != '\t' &&
                character != '\n' &&
                character != '\r' &&
                character != '\u000C'
        }
        if (disallowedControlCount > maxOf(MINIMUM_CONTROL_LIMIT, text.length / 100)) {
            throw NotTextContentException()
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { index -> this[index] == prefix[index] }

    private companion object {
        const val GB18030_NAME = "GB18030"
        const val MINIMUM_CONTROL_LIMIT = 8

        val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val UTF16_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val UTF16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
    }
}
