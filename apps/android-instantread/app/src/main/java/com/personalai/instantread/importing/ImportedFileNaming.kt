package com.personalai.instantread.importing

/**
 * 为重复文件名生成带序号后缀的显示名，例如 `index.html` 已存在时改用 `index(1).html`。
 *
 * 只按显示名去重，不读取文件内容，也不合并或删除任何记录：每次导入仍然是独立的一条记录。
 * 按内容摘要识别同一文件的提示仍属于后续 P1 需求（见 ROADMAP 的 F12）。
 */
fun ImportedFile.withUniqueDisplayName(existingDisplayNames: Collection<String?>): ImportedFile {
    val current = displayName ?: return this
    val unique = uniqueDisplayName(current, existingDisplayNames.filterNotNull())
    return if (unique == current) this else copy(displayName = unique)
}

/**
 * 返回不与 [existingDisplayNames] 冲突的显示名。
 *
 * 比较忽略大小写，避免 `index.html` 与 `Index.html` 在列表里看起来是同一份文件；
 * 后缀插在扩展名之前，并跳过已经占用的序号。
 */
fun uniqueDisplayName(displayName: String, existingDisplayNames: Collection<String>): String {
    val taken = existingDisplayNames.mapTo(mutableSetOf()) { it.lowercase() }
    if (displayName.lowercase() !in taken) return displayName

    val (base, extension) = splitName(displayName)
    var index = 1
    while (true) {
        val candidate = "$base($index)$extension"
        if (candidate.lowercase() !in taken) return candidate
        index += 1
    }
}

/** 拆分「主名 + 扩展名」；无扩展名（含仅以点开头的隐藏名）时扩展名为空。 */
private fun splitName(name: String): Pair<String, String> {
    val dot = name.lastIndexOf('.')
    if (dot <= 0) return name.replace(TRAILING_INDEX, "") to ""
    return name.substring(0, dot).replace(TRAILING_INDEX, "") to name.substring(dot)
}

/** 识别 `(1)`、`(2)` 这类已存在的序号后缀，避免重复叠加成 `index(1)(1).html`。 */
private val TRAILING_INDEX = Regex("""\(\d+\)$""")
