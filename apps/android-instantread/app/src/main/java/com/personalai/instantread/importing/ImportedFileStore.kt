package com.personalai.instantread.importing

import com.personalai.instantread.io.FileTaskCancellationToken
import com.personalai.instantread.io.ImportResourcePolicy
import com.personalai.instantread.io.LimitedInputCopier
import com.personalai.instantread.io.isSafeImportId
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class EmptyImportException : Exception("Imported file is empty")

internal fun promoteImportDirectoryWithoutReplace(
    stagingDirectory: File,
    finalDirectory: File,
    renameDirectory: (File, File) -> Boolean = { source, target -> source.renameTo(target) }
): Boolean {
    if (finalDirectory.exists()) return false
    val stagingEntries = stagingDirectory.listFiles() ?: return false
    if (stagingEntries.size != 1 || !stagingEntries[0].isFile) return false

    if (!renameDirectory(stagingDirectory, finalDirectory)) return false
    return !stagingDirectory.exists() && finalDirectory.isDirectory
}

private data class StagedImport(
    val id: String,
    val stagingDirectory: File,
    val finalDirectory: File,
    val sourceName: String,
    val sizeBytes: Long
)

class ImportedFileStore(
    private val filesDir: File,
    internal val resourcePolicy: ImportResourcePolicy = ImportResourcePolicy.DEFAULT,
    private val copier: LimitedInputCopier = LimitedInputCopier(),
    private val promoteDirectory: (File, File) -> Boolean = { staging, final ->
        promoteImportDirectoryWithoutReplace(staging, final)
    },
    private val deleteStagingDirectory: (File) -> Boolean = { it.deleteRecursively() },
    private val canonicalizeFile: (File) -> File = { it.canonicalFile },
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) {
    fun discard(importedFile: ImportedFile) {
        if (!importedFile.id.isSafeImportId(resourcePolicy)) {
            throw IOException("Refusing to discard import with unsafe id: ${importedFile.id}")
        }
        val importsRoot = canonicalizeFile(File(filesDir, "imports"))
        val importDirectory = canonicalizeFile(File(importsRoot, importedFile.id))
        val expectedSource = canonicalizeFile(
            File(importDirectory, "source.${importedFile.fileType.defaultExtension}")
        )
        val actualSource = canonicalizeFile(importedFile.localFile)
        if (
            importDirectory.parentFile != importsRoot ||
            expectedSource.parentFile != importDirectory ||
            actualSource != expectedSource
        ) {
            throw IOException("Refusing to discard unowned import path: ${importedFile.localFile}")
        }
        if (!importDirectory.exists()) return
        if (!importDirectory.isDirectory || !deleteStagingDirectory(importDirectory) || importDirectory.exists()) {
            throw IOException("Could not discard import directory: $importDirectory")
        }
    }

    fun save(
        fileType: FileType,
        displayName: String?,
        sourceKind: SourceKind,
        inputStream: InputStream,
        cancellationToken: FileTaskCancellationToken = FileTaskCancellationToken.NONE,
        onBytesCopied: (Long) -> Unit = {}
    ): ImportedFile {
        var stagingDirectoryForCleanup: File? = null
        var promoted = false
        var primaryFailure: Throwable? = null
        try {
            val stagedImport = inputStream.use { input ->
                val id = idGenerator()
                val importsRoot = File(filesDir, "imports")
                val stagingDir = File(importsRoot, "$id.partial")
                val finalDir = File(importsRoot, id)
                val sourceName = "source.${fileType.defaultExtension}"
                stagingDirectoryForCleanup = stagingDir

                if (!importsRoot.exists() && !importsRoot.mkdirs()) {
                    throw IOException("Could not create imports directory: $importsRoot")
                }
                if (!importsRoot.isDirectory) {
                    throw IOException("Imports path is not a directory: $importsRoot")
                }
                if (finalDir.exists()) {
                    throw IOException("Import directory already exists: $finalDir")
                }
                if (stagingDir.exists() && !deleteStagingDirectory(stagingDir)) {
                    throw IOException("Could not delete stale import staging directory: $stagingDir")
                }
                if (!stagingDir.mkdirs()) {
                    throw IOException("Could not create import staging directory: $stagingDir")
                }

                val stagingFile = File(stagingDir, sourceName)
                val sizeBytes = stagingFile.outputStream().use { output ->
                    copier.copy(
                        input = input,
                        output = output,
                        maxBytes = resourcePolicy.maxSourceBytes(fileType),
                        cancellationToken = cancellationToken,
                        onBytesCopied = onBytesCopied
                    )
                }

                if (sizeBytes == 0L) {
                    throw EmptyImportException()
                }

                StagedImport(
                    id = id,
                    stagingDirectory = stagingDir,
                    finalDirectory = finalDir,
                    sourceName = sourceName,
                    sizeBytes = sizeBytes
                )
            }

            cancellationToken.throwIfCancelled()
            if (stagedImport.finalDirectory.exists()) {
                throw IOException("Import directory already exists: ${stagedImport.finalDirectory}")
            }
            val localFile = canonicalizeFile(
                File(stagedImport.finalDirectory, stagedImport.sourceName)
            )
            if (!promoteDirectory(stagedImport.stagingDirectory, stagedImport.finalDirectory)) {
                throw IOException("Could not promote import staging directory: ${stagedImport.stagingDirectory}")
            }
            promoted = true

            return ImportedFile(
                id = stagedImport.id,
                displayName = displayName,
                fileType = fileType,
                sourceKind = sourceKind,
                localFile = localFile,
                sizeBytes = stagedImport.sizeBytes
            )
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            if (!promoted) {
                val stagingDirectory = stagingDirectoryForCleanup
                val cleanupFailure = try {
                    if (
                        stagingDirectory == null ||
                        !stagingDirectory.exists() ||
                        deleteStagingDirectory(stagingDirectory)
                    ) {
                        null
                    } else {
                        IOException("Could not delete import staging directory: $stagingDirectory")
                    }
                } catch (error: Throwable) {
                    error
                }
                if (cleanupFailure != null) {
                    val failure = primaryFailure
                    if (failure == null) {
                        throw cleanupFailure
                    }
                    if (cleanupFailure !== failure) {
                        failure.addSuppressed(cleanupFailure)
                    }
                }
            }
        }
    }

    private val FileType.defaultExtension: String
        get() = when (this) {
            FileType.HTML -> "html"
            FileType.MARKDOWN -> "md"
            FileType.TEXT -> "txt"
            FileType.ZIP -> "zip"
        }

}
