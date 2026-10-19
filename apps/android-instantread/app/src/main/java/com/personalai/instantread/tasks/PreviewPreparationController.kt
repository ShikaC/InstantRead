package com.personalai.instantread.tasks

import com.personalai.instantread.displayTitle
import com.personalai.instantread.history.PreviewHistory
import com.personalai.instantread.importing.FileType
import com.personalai.instantread.preview.PreviewController
import com.personalai.instantread.preview.PreviewModel
import com.personalai.instantread.preview.PreviewOptions
import com.personalai.instantread.preview.PreviewPreparationStage
import com.personalai.instantread.preview.discardPreparedResources
import com.personalai.instantread.preview.publishPreparedResources
import com.personalai.instantread.toImportedFile

class PreviewPreparationController(
    private val taskController: ForegroundFileTaskController,
    private val previewController: PreviewController
) {
    fun initialState(history: PreviewHistory): ForegroundTaskState {
        val stage = when (history.fileType) {
            FileType.MARKDOWN, FileType.TEXT -> PreviewPreparationStage.CHECKING_TEXT_ENCODING
            FileType.ZIP -> PreviewPreparationStage.EXTRACTING_ZIP
            FileType.HTML -> throw IllegalArgumentException("HTML previews do not require background preparation")
        }
        return ForegroundTaskState(history.displayTitle(), stage.toTaskStage())
    }

    fun start(
        history: PreviewHistory,
        options: PreviewOptions,
        onProgress: (ForegroundTaskState) -> Unit,
        onCompleted: (PreviewModel) -> Unit
    ) {
        taskController.start<PreviewModel, ForegroundTaskState>(
            work = { token, report ->
                previewController.prepare(history.toImportedFile(), options, token) { stage ->
                    report(ForegroundTaskState(history.displayTitle(), stage.toTaskStage()))
                }
            },
            onProgress = onProgress,
            onSuccess = { preview ->
                preview.publishPreparedResources()
                onCompleted(preview)
            },
            onFailure = {
                onCompleted(
                    PreviewModel.Error(
                        title = "预览准备失败",
                        message = "无法完成本地预览准备。请返回首页后重新打开文件。"
                    )
                )
            },
            onDiscardedResult = { preview -> preview.discardPreparedResources() }
        )
    }
}

private fun PreviewPreparationStage.toTaskStage(): ForegroundTaskStage = when (this) {
    PreviewPreparationStage.CHECKING_TEXT_ENCODING -> ForegroundTaskStage.CHECKING_TEXT_ENCODING
    PreviewPreparationStage.GENERATING_READING_PAGE -> ForegroundTaskStage.GENERATING_READING_PAGE
    PreviewPreparationStage.EXTRACTING_ZIP -> ForegroundTaskStage.EXTRACTING_ZIP
}
