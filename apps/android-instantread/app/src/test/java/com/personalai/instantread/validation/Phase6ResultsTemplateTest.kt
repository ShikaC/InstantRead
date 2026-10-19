package com.personalai.instantread.validation

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Phase6ResultsTemplateTest {
    @Test
    fun realSourceResultsTemplateCoversRequiredSourcesSamplesAndEvidenceFields() {
        val template = File(
            REPO_ROOT,
            "docs/validation/android-instantread/phase6-real-source-results.md"
        ).readText()

        listOf(
            "## System File Manager",
            "## Chat Or Share Source",
            "## Browser Download Source",
            "## Unsupported Or Unsafe Path Checks",
            "## Acceptance Summary"
        ).forEach { heading ->
            assertTrue("Results template should contain $heading.", template.contains(heading))
        }

        val normalSamples = listOf("sample.html", "sample.md", "sample.txt", "sample-site.zip")
        listOf(
            section(template, "## System File Manager", "## Chat Or Share Source"),
            section(template, "## Chat Or Share Source", "## Browser Download Source"),
            section(template, "## Browser Download Source", "## Unsupported Or Unsafe Path Checks")
        ).forEach { sourceSection ->
            normalSamples.forEach { sample ->
                assertTrue("Source section should include $sample.", sourceSection.contains("`$sample`"))
            }
        }

        listOf(
            "`unsafe-traversal.zip` through system file manager",
            "`unsafe-traversal.zip` through share source",
            "`unsafe-traversal.zip` through browser download source",
            "True multi-file share",
            "Direct shell `file:// /sdcard` probe"
        ).forEach { unsafeCase ->
            assertTrue("Unsafe checks should include $unsafeCase.", template.contains(unsafeCase))
        }

        listOf(
            "entry action",
            "entry Uri source",
            "Uri scheme",
            "Uri authority",
            "Intent.type",
            "ContentResolver.getType(uri)",
            "display name",
            "display extension",
            "resolved file type",
            "user-facing error"
        ).forEach { evidenceField ->
            assertTrue("Evidence fields should mention $evidenceField.", template.contains(evidenceField))
        }

        val acceptance = section(template, "## Acceptance Summary", null)
        listOf(
            "App picker baseline remains usable",
            "system file manager or Downloads source",
            "chat/share source",
            "browser download source",
            "application/octet-stream",
            "Unsafe ZIP is shown as a safe blocking error"
        ).forEach { acceptanceItem ->
            assertTrue("Acceptance summary should mention $acceptanceItem.", acceptance.contains(acceptanceItem))
        }
    }

    @Test
    fun realSourceResultsTemplateRecordsFinalAcceptanceBatchMetadata() {
        val template = File(
            REPO_ROOT,
            "docs/validation/android-instantread/phase6-real-source-results.md"
        ).readText()
        val metadata = section(template, "## Final Acceptance Batch", "## App Picker Baseline")

        listOf(
            "Script run time",
            "Git HEAD",
            "Git status",
            "Debug APK path",
            "APK size",
            "APK SHA-256",
            "ADB serial",
            "Device model",
            "Android SDK",
            "Device validation directory",
            "Smoke artifact directory",
            "phase6-device-run-summary.md",
            "phase6-manual-next-steps.md",
            "adb-devices.txt",
            "device-samples.txt",
            "Tester",
            "Overall batch result"
        ).forEach { metadataField ->
            assertTrue(
                "Final acceptance batch metadata should mention $metadataField.",
                metadata.contains(metadataField)
            )
        }
    }

    private fun section(text: String, start: String, end: String?): String {
        val startIndex = text.indexOf(start)
        assertTrue("Missing section start $start.", startIndex >= 0)
        val endIndex = if (end == null) text.length else text.indexOf(end, startIndex + start.length)
        assertTrue("Missing section end $end.", endIndex >= 0)
        return text.substring(startIndex, endIndex)
    }

    private companion object {
        val REPO_ROOT: File = listOf(File("../../.."), File("../../../.."))
            .map { it.canonicalFile }
            .first { File(it, "docs/validation/android-instantread/phase6-real-source-results.md").isFile }
    }
}
