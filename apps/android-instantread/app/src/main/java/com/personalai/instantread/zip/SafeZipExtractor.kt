package com.personalai.instantread.zip

import com.personalai.instantread.io.FileTaskCancellationToken
import com.personalai.instantread.io.ImportResourcePolicy
import com.personalai.instantread.io.TooManyZipEntriesException
import com.personalai.instantread.io.ZipEntryTooLargeException
import com.personalai.instantread.io.ZipExpandedTooLargeException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipInputStream

class ZipSlipException(entryName: String) : SecurityException("Unsafe ZIP entry: $entryName")

data class ZipExtractionStats(
    val entryCount: Int,
    val fileCount: Int,
    val expandedBytes: Long
)

object SafeZipExtractor {
    fun extract(
        zipFile: File,
        targetDirectory: File,
        policy: ImportResourcePolicy = ImportResourcePolicy.DEFAULT,
        cancellationToken: FileTaskCancellationToken = FileTaskCancellationToken.NONE
    ): ZipExtractionStats {
        val canonicalTarget = targetDirectory.canonicalFile
        ensureDirectory(canonicalTarget)

        var entryCount = 0
        var fileCount = 0
        var expandedBytes = 0L
        val buffer = ByteArray(BUFFER_SIZE)

        ZipInputStream(FileInputStream(zipFile)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                cancellationToken.throwIfCancelled()

                val actualEntryCount = if (entryCount == Int.MAX_VALUE) {
                    Int.MAX_VALUE
                } else {
                    entryCount + 1
                }
                if (entryCount >= policy.maxZipEntryCount) {
                    throw TooManyZipEntriesException(actualEntryCount, policy.maxZipEntryCount)
                }
                entryCount = actualEntryCount

                val output = File(canonicalTarget, entry.name).canonicalFile
                ensureInsideTarget(canonicalTarget, output, entry.name)

                if (entry.isDirectory) {
                    ensureDirectory(output)
                    expandedBytes = consumeEntryPayload(
                        zip = zip,
                        output = null,
                        entryName = entry.name,
                        buffer = buffer,
                        expandedBytes = expandedBytes,
                        policy = policy,
                        cancellationToken = cancellationToken
                    )
                } else {
                    output.parentFile?.let(::ensureDirectory)
                    output.outputStream().use { out ->
                        expandedBytes = consumeEntryPayload(
                            zip = zip,
                            output = out,
                            entryName = entry.name,
                            buffer = buffer,
                            expandedBytes = expandedBytes,
                            policy = policy,
                            cancellationToken = cancellationToken
                        )
                    }
                    fileCount += 1
                }
                zip.closeEntry()
            }
        }

        return ZipExtractionStats(
            entryCount = entryCount,
            fileCount = fileCount,
            expandedBytes = expandedBytes
        )
    }

    private fun consumeEntryPayload(
        zip: ZipInputStream,
        output: OutputStream?,
        entryName: String,
        buffer: ByteArray,
        expandedBytes: Long,
        policy: ImportResourcePolicy,
        cancellationToken: FileTaskCancellationToken
    ): Long {
        var entryBytes = 0L
        var actualExpandedBytes = expandedBytes

        while (true) {
            cancellationToken.throwIfCancelled()
            val read = zip.read(buffer)
            if (read < 0) {
                break
            }
            cancellationToken.throwIfCancelled()

            val readBytes = read.toLong()
            val nextEntryBytes = saturatedAdd(entryBytes, readBytes)
            if (readBytes > policy.maxZipEntryBytes - entryBytes) {
                throw ZipEntryTooLargeException(
                    entryName = entryName,
                    actualBytes = nextEntryBytes,
                    limitBytes = policy.maxZipEntryBytes
                )
            }

            val nextExpandedBytes = saturatedAdd(actualExpandedBytes, readBytes)
            if (readBytes > policy.maxZipExpandedBytes - actualExpandedBytes) {
                throw ZipExpandedTooLargeException(
                    actualBytes = nextExpandedBytes,
                    limitBytes = policy.maxZipExpandedBytes
                )
            }

            output?.write(buffer, 0, read)
            entryBytes = nextEntryBytes
            actualExpandedBytes = nextExpandedBytes
        }

        return actualExpandedBytes
    }

    private fun ensureInsideTarget(target: File, output: File, entryName: String) {
        val targetPath = target.path
        val outputPath = output.path
        val isInside = outputPath == targetPath || outputPath.startsWith(targetPath + File.separator)
        if (!isInside) {
            throw ZipSlipException(entryName)
        }
    }

    private fun ensureDirectory(directory: File) {
        if (!directory.exists()) {
            directory.mkdirs()
        }
        if (!directory.isDirectory) {
            throw IOException("Unable to create directory: ${directory.path}")
        }
    }

    private fun saturatedAdd(current: Long, increment: Long): Long {
        return if (increment > Long.MAX_VALUE - current) {
            Long.MAX_VALUE
        } else {
            current + increment
        }
    }

    private const val BUFFER_SIZE = 16 * 1024
}
