package com.personalai.instantread.preview

import com.personalai.instantread.importing.FileType
import com.personalai.instantread.io.FileTaskCancellationToken
import java.io.File
import java.io.IOException

enum class LocalTextEncoding {
    UTF8,
    UTF16_LE,
    UTF16_BE,
    GB18030
}

data class DecodedText(
    val text: String,
    val encoding: LocalTextEncoding
)

sealed class TextDecodingException(message: String) : IOException(message)

class UnknownTextEncodingException : TextDecodingException("Unknown text encoding")

class NotTextContentException : TextDecodingException("Decoded content is not plausible text")

interface TextDecoder {
    fun decode(
        file: File,
        fileType: FileType,
        cancellationToken: FileTaskCancellationToken = FileTaskCancellationToken.NONE
    ): DecodedText
}
