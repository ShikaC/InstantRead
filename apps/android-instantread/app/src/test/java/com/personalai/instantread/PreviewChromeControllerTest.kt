package com.personalai.instantread

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.personalai.instantread.history.PreviewHistory
import com.personalai.instantread.importing.FileType
import com.personalai.instantread.importing.SourceKind
import com.personalai.instantread.ui.PreviewWorkspaceActions
import com.personalai.instantread.ui.PreviewWorkspaceState
import com.personalai.instantread.ui.PreviewWorkspaceViewFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreviewChromeControllerTest {
    @Test
    fun missingLocalResourceOnSingleHtmlPromptsPackagingAsZip() {
        val status = noticeAfter(
            history = history(FileType.HTML, "missing-asset.html"),
            note = { controller, id -> controller.noteMissingLocalResource(id) }
        )

        assertTrue(status.contains("缺少本地图片或样式"))
        assertTrue(status.contains("打包为 ZIP"))
    }

    @Test
    fun blockedResourceNoticeStaysGenericForZipPackages() {
        val status = noticeAfter(
            history = history(FileType.ZIP, "site.zip"),
            note = { controller, id -> controller.noteBlockedResource(id) }
        )

        assertTrue(status.contains("已拦截页面外资源，页面可能不完整"))
    }

    @Test
    fun noticesAreIgnoredForOtherDocuments() {
        val status = noticeAfter(
            history = history(FileType.HTML, "other.html"),
            note = { controller, _ -> controller.noteMissingLocalResource("someone-else") }
        )

        assertEquals("安全沙箱已激活", status)
    }

    private fun noticeAfter(
        history: PreviewHistory,
        note: (PreviewChromeController, String) -> Unit
    ): String {
        val context = RuntimeEnvironment.getApplication()
        val factory = PreviewWorkspaceViewFactory(context)
        val state = PreviewWorkspaceState(
            title = history.displayName ?: history.localFile.name,
            fileTypeLabel = history.fileType.name,
            isFavorite = false,
            isDarkTheme = false,
            javaScriptStatus = "JS 已禁用",
            supportsScriptAuthorization = history.fileType == FileType.HTML ||
                history.fileType == FileType.ZIP,
            navigationStatus = "安全沙箱已激活"
        )
        val workspace = factory.create(state, TextView(context), PreviewWorkspaceActions({}, {}))
        val controller = PreviewChromeController(factory, { PreviewWorkspaceActions({}, {}) }) { state }
        controller.bind(history.id, workspace)

        note(controller, history.id)
        controller.refresh(history.id, history)

        return workspace.viewWithTag("preview-sandbox-label").let { (it as TextView).text.toString() }
    }

    private fun history(fileType: FileType, name: String): PreviewHistory = PreviewHistory(
        id = "history-id",
        displayName = name,
        fileType = fileType,
        sourceKind = SourceKind.APP_PICKER,
        localFile = File(name),
        createdAt = 0,
        lastOpenedAt = 0,
        favorite = false,
        jsAllowed = false
    )

    private fun View.viewWithTag(expected: String): View {
        if (tag == expected) return this
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                val found = runCatching { getChildAt(index).viewWithTag(expected) }.getOrNull()
                if (found != null) return found
            }
        }
        throw AssertionError("View not found with tag: $expected")
    }
}
