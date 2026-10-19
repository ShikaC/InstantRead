package com.personalai.instantread

data class PreviewSearchState(
    val isOpen: Boolean = false,
    val query: String = "",
    val activeMatchOrdinal: Int = 0,
    val totalMatches: Int = 0
) {
    fun displayLabel(): String {
        if (query.isBlank()) return ""
        if (totalMatches <= 0) return "无匹配"
        val current = activeMatchOrdinal.coerceIn(0, totalMatches - 1) + 1
        return "$current/$totalMatches"
    }
}
