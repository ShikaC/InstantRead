package com.personalai.instantread.io

import java.io.File

internal fun String.isSafeImportId(
    policy: ImportResourcePolicy = ImportResourcePolicy.DEFAULT
): Boolean =
    length <= policy.maxImportIdLength &&
    isNotBlank() &&
    this != "." &&
    this != ".." &&
    '/' !in this &&
    '\\' !in this &&
    !File(this).isAbsolute
