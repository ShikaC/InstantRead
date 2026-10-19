package com.personalai.instantread.zip

enum class ZipPreviewEntryType {
    HTML,
    MARKDOWN
}

data class ZipPreviewEntry(
    val path: String,
    val type: ZipPreviewEntryType
)

object ZipEntrySelector {
    fun selectEntry(entryNames: List<String>): ZipPreviewEntry? {
        val candidates = entryNames.mapIndexedNotNull { index, rawName ->
            toCandidate(index, rawName)
        }
        val htmlEntries = candidates.filter { it.type == ZipPreviewEntryType.HTML }
        selectHtmlEntry(htmlEntries)?.let { return it }
        return selectMarkdownEntry(candidates.filter { it.type == ZipPreviewEntryType.MARKDOWN })
    }

    private fun selectHtmlEntry(candidates: List<ZipPreviewEntryCandidate>): ZipPreviewEntry? {
        return candidates.firstOrNull { it.path.equals("index.html", ignoreCase = true) }?.toEntry()
            ?: candidates.firstOrNull { it.path.equals("index.htm", ignoreCase = true) }?.toEntry()
            ?: candidates
                .filter { it.fileName.equals("index.html", ignoreCase = true) || it.fileName.equals("index.htm", ignoreCase = true) }
                .minWithOrNull(compareBy<ZipPreviewEntryCandidate> { it.path.length }.thenBy { it.zipOrder })
                ?.toEntry()
            ?: candidates.firstOrNull()?.toEntry()
    }

    private fun selectMarkdownEntry(candidates: List<ZipPreviewEntryCandidate>): ZipPreviewEntry? {
        return candidates.firstOrNull { it.path.equals("index.md", ignoreCase = true) }?.toEntry()
            ?: candidates.firstOrNull { it.path.equals("index.markdown", ignoreCase = true) }?.toEntry()
            ?: candidates
                .filter {
                    it.fileName.equals("index.md", ignoreCase = true) ||
                        it.fileName.equals("index.markdown", ignoreCase = true)
                }
                .minWithOrNull(compareBy<ZipPreviewEntryCandidate> { it.path.length }.thenBy { it.zipOrder })
                ?.toEntry()
            ?: candidates.firstOrNull()?.toEntry()
    }

    private fun toCandidate(zipOrder: Int, rawName: String): ZipPreviewEntryCandidate? {
        val normalized = rawName.trim().replace('\\', '/')
        if (normalized.isBlank() || normalized.endsWith("/")) return null
        if (!isSafeRelativePath(normalized)) return null
        val type = when {
            normalized.endsWith(".html", ignoreCase = true) ||
                normalized.endsWith(".htm", ignoreCase = true) -> ZipPreviewEntryType.HTML
            normalized.endsWith(".md", ignoreCase = true) ||
                normalized.endsWith(".markdown", ignoreCase = true) -> ZipPreviewEntryType.MARKDOWN
            else -> return null
        }

        return ZipPreviewEntryCandidate(
            path = normalized,
            fileName = normalized.substringAfterLast('/'),
            zipOrder = zipOrder,
            type = type
        )
    }

    private fun isSafeRelativePath(path: String): Boolean {
        if (path.startsWith("/") || path.startsWith("\\")) return false
        val parts = path.split('/')
        return parts.all { part -> part.isNotBlank() && part != "." && part != ".." }
    }

    private data class ZipPreviewEntryCandidate(
        val path: String,
        val fileName: String,
        val zipOrder: Int,
        val type: ZipPreviewEntryType
    ) {
        fun toEntry() = ZipPreviewEntry(path = path, type = type)
    }
}
