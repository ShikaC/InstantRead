package com.personalai.instantread.preview

enum class ReadingComfort(
    val label: String,
    val fontSizeSp: Int,
    val lineHeight: Float
) {
    COMPACT("紧凑", 15, 1.55f),
    STANDARD("标准", 17, 1.75f),
    COMFORTABLE("舒适", 19, 1.9f);

    companion object {
        fun fromStoredValue(value: String?): ReadingComfort {
            return entries.firstOrNull { it.name == value } ?: STANDARD
        }
    }
}
