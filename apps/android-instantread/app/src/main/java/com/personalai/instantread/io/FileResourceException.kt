package com.personalai.instantread.io

import java.io.IOException

sealed class FileResourceException(message: String) : IOException(message)

class SourceFileTooLargeException(
    val actualBytes: Long,
    val limitBytes: Long
) : FileResourceException("Source file exceeded $limitBytes bytes at $actualBytes bytes")

class TooManyZipEntriesException(
    val actualCount: Int,
    val limitCount: Int
) : FileResourceException("ZIP entry count exceeded $limitCount at $actualCount")

class ZipEntryTooLargeException(
    val entryName: String,
    val actualBytes: Long,
    val limitBytes: Long
) : FileResourceException("ZIP entry $entryName exceeded $limitBytes bytes at $actualBytes bytes")

class ZipExpandedTooLargeException(
    val actualBytes: Long,
    val limitBytes: Long
) : FileResourceException("ZIP expanded bytes exceeded $limitBytes at $actualBytes")
