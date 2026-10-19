package com.personalai.instantread.io

import java.io.InputStream
import java.io.OutputStream

class LimitedInputCopier(private val bufferSize: Int = DEFAULT_BUFFER_SIZE) {
    init {
        require(bufferSize > 0) { "bufferSize must be positive" }
    }

    fun copy(
        input: InputStream,
        output: OutputStream,
        maxBytes: Long,
        cancellationToken: FileTaskCancellationToken = FileTaskCancellationToken.NONE,
        onBytesCopied: (Long) -> Unit = {}
    ): Long {
        require(maxBytes >= 0L) { "maxBytes must be non-negative" }

        val buffer = ByteArray(bufferSize)
        var copied = 0L
        while (true) {
            cancellationToken.throwIfCancelled()
            val read = input.read(buffer).let { bulkRead ->
                if (bulkRead != 0) {
                    bulkRead
                } else {
                    cancellationToken.throwIfCancelled()
                    val singleByte = input.read()
                    if (singleByte < 0) {
                        -1
                    } else {
                        buffer[0] = singleByte.toByte()
                        1
                    }
                }
            }
            if (read < 0) return copied
            val readBytes = read.toLong()
            if (readBytes > maxBytes - copied) {
                val actualBytes = if (readBytes > Long.MAX_VALUE - copied) {
                    Long.MAX_VALUE
                } else {
                    copied + readBytes
                }
                throw SourceFileTooLargeException(actualBytes, maxBytes)
            }
            output.write(buffer, 0, read)
            copied += readBytes
            onBytesCopied(copied)
        }
    }

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 16 * 1024
    }
}
