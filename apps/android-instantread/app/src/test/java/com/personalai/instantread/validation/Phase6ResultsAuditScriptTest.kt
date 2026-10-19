package com.personalai.instantread.validation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class Phase6ResultsAuditScriptTest {
    @Test
    fun resultsAuditScriptChecksFinalAcceptanceEvidenceBeforePhase6Closure() {
        val script = File(REPO_ROOT, "tools/phase6-results-audit.ps1")

        assertTrue("Phase 6 results audit script should exist.", script.isFile)

        val text = script.readText()
        listOf(
            "docs/validation/android-instantread/phase6-real-source-results.md",
            "Final Acceptance Batch",
            "System File Manager",
            "Chat Or Share Source",
            "Browser Download Source",
            "Unsupported Or Unsafe Path Checks",
            "Acceptance Summary"
        ).forEach { requiredText ->
            assertTrue("Audit script should mention $requiredText.", text.contains(requiredText))
        }

        listOf(
            "sample.html",
            "sample.md",
            "sample.txt",
            "sample-site.zip"
        ).forEach { sample ->
            assertTrue("Audit script should require $sample for every real source.", text.contains("\"$sample\""))
        }

        listOf(
            "``unsafe-traversal.zip`` through system file manager",
            "``unsafe-traversal.zip`` through share source",
            "``unsafe-traversal.zip`` through browser download source"
        ).forEach { unsafeCase ->
            assertTrue("Audit script should require unsafe ZIP evidence for $unsafeCase.", text.contains(unsafeCase))
        }

        assertTrue(
            "Audit script should fail while final evidence still contains Pending placeholders.",
            text.contains("Pending")
        )
        assertTrue(
            "Audit script should detect unresolved Pending cells.",
            text.contains("Unresolved placeholder")
        )
        assertTrue(
            "Audit script should report a clear success message for final closure.",
            text.contains("Phase 6 results audit passed")
        )
        assertFalse(
            "Audit script should not edit the evidence sheet.",
            text.contains("WriteAllText") || text.contains("Set-Content")
        )
    }

    @Test
    fun resultsAuditScriptPassesCommittedSheetAndFailsPendingOrInvalidCopies() {
        val committedResult = runAuditScript()

        assertEquals(
            "The committed final results sheet should pass after manual evidence is recorded.",
            0,
            committedResult.exitCode
        )
        assertTrue(
            "Passing audit output should include the explicit closure message.",
            committedResult.output.contains("Phase 6 results audit passed.")
        )

        val pendingCopy = File(
            REPO_ROOT,
            "apps/android-instantread/app/build/tmp/phase6-results-audit-test/pending-results.md"
        )
        pendingCopy.parentFile.mkdirs()
        File(REPO_ROOT, "docs/validation/android-instantread/phase6-real-source-results.md")
            .readText(Charsets.UTF_8)
            .replace("| Tester | User-confirmed manual acceptance |", "| Tester | Pending |")
            .also { pendingCopy.writeText(it, Charsets.UTF_8) }

        val pendingResult = runAuditScript("-ResultsPath", relativePath(pendingCopy))

        assertEquals(
            "A results sheet with unresolved Pending evidence should not close Phase 6.",
            1,
            pendingResult.exitCode
        )
        assertTrue(
            "Audit output should identify which results sheet was checked.",
            pendingResult.output.contains("Auditing Phase 6 results:")
        )
        assertTrue(
            "Audit should explain that Pending placeholders are blocking Phase 6 closure.",
            pendingResult.output.contains("Unresolved placeholder")
        )

        val invalidCopy = File(
            REPO_ROOT,
            "apps/android-instantread/app/build/tmp/phase6-results-audit-test/invalid-results.md"
        )
        invalidCopy.parentFile.mkdirs()
        File(REPO_ROOT, "docs/validation/android-instantread/phase6-real-source-results.md")
            .readText(Charsets.UTF_8)
            .replace("| Overall batch result | Passed |", "| Overall batch result | Recorded |")
            .replace("| None | Pass |", "| None | Recorded |")
            .also { invalidCopy.writeText(it, Charsets.UTF_8) }

        val invalidResult = runAuditScript("-ResultsPath", relativePath(invalidCopy))

        assertEquals(
            "A results sheet with arbitrary non-Pending status values should not close Phase 6.",
            1,
            invalidResult.exitCode
        )
        assertTrue(
            "Audit should reject an arbitrary overall batch result.",
            invalidResult.output.contains("Invalid Overall batch result")
        )
        assertTrue(
            "Audit should reject arbitrary per-row Pass/Fail values.",
            invalidResult.output.contains("Invalid Pass/Fail value")
        )

        val completedCopy = File(
            REPO_ROOT,
            "apps/android-instantread/app/build/tmp/phase6-results-audit-test/filled-results.md"
        )
        completedCopy.parentFile.mkdirs()
        completedCopy.writeText(completedResultsSheet(), Charsets.UTF_8)

        val filledResult = runAuditScript("-ResultsPath", relativePath(completedCopy))

        assertEquals(
            "A completed results-sheet copy should pass the final Phase 6 audit.",
            0,
            filledResult.exitCode
        )
        assertTrue(
            "Passing audit output should include the explicit closure message.",
            filledResult.output.contains("Phase 6 results audit passed.")
        )
    }

    @Test
    fun resultsAuditScriptRejectsInvalidFinalBatchMetadata() {
        val invalidMetadataCopy = File(
            REPO_ROOT,
            "apps/android-instantread/app/build/tmp/phase6-results-audit-test/invalid-metadata-results.md"
        )
        invalidMetadataCopy.parentFile.mkdirs()
        invalidMetadataCopy.writeText(
            completedResultsSheet()
                .replace("| Git HEAD | 0123456789abcdef0123456789abcdef01234567 |", "| Git HEAD | not-a-sha |")
                .replace("| APK size | 866166 bytes |", "| APK size | many bytes |")
                .replace("| APK SHA-256 | 8DCC6F4EB0A5C24C10B44CCDBAB237CFDB7CB464D0955647451DEA438287AA30 |", "| APK SHA-256 | not-a-hash |")
                .replace("| `phase6-device-run-summary.md` | <REPO_ROOT>\\apps\\android-instantread\\app\\build\\outputs\\phase6-device-smoke\\phase6-device-run-summary.md |", "| `phase6-device-run-summary.md` | missing-summary.txt |")
                .replace("| `phase6-manual-next-steps.md` | <REPO_ROOT>\\apps\\android-instantread\\app\\build\\outputs\\phase6-device-smoke\\phase6-manual-next-steps.md |", "| `phase6-manual-next-steps.md` | missing-steps.txt |")
                .replace("| `adb-devices.txt` | <REPO_ROOT>\\apps\\android-instantread\\app\\build\\outputs\\phase6-device-smoke\\adb-devices.txt |", "| `adb-devices.txt` | missing-adb.log |")
                .replace("| `device-samples.txt` | <REPO_ROOT>\\apps\\android-instantread\\app\\build\\outputs\\phase6-device-smoke\\device-samples.txt |", "| `device-samples.txt` | missing-samples.log |"),
            Charsets.UTF_8
        )

        val result = runAuditScript("-ResultsPath", relativePath(invalidMetadataCopy))

        assertEquals(
            "Invalid final batch metadata should prevent Phase 6 closure.",
            1,
            result.exitCode
        )
        assertTrue(
            "Audit should reject an invalid Git HEAD.",
            result.output.contains("Invalid final batch metadata: Git HEAD")
        )
        assertTrue(
            "Audit should reject an invalid APK size.",
            result.output.contains("Invalid final batch metadata: APK size")
        )
        assertTrue(
            "Audit should reject an invalid APK SHA-256.",
            result.output.contains("Invalid final batch metadata: APK SHA-256")
        )
        assertTrue(
            "Audit should reject smoke artifact metadata that does not point to the expected artifact name.",
            result.output.contains("Invalid final batch metadata: `phase6-device-run-summary.md`")
        )
    }

    private fun completedResultsSheet(): String {
        return File(REPO_ROOT, "docs/validation/android-instantread/phase6-real-source-results.md")
            .readLines(Charsets.UTF_8)
            .joinToString(System.lineSeparator()) { line ->
                when {
                    line.startsWith("| Script run time |") -> "| Script run time | 2026-06-04 18:30:00 +08:00 |"
                    line.startsWith("| Git HEAD |") -> "| Git HEAD | 0123456789abcdef0123456789abcdef01234567 |"
                    line.startsWith("| Git status |") -> "| Git status | ## master |"
                    line.startsWith("| Debug APK path |") -> "| Debug APK path | <REPO_ROOT>\\apps\\android-instantread\\app\\build\\outputs\\apk\\debug\\app-debug.apk |"
                    line.startsWith("| APK size |") -> "| APK size | 866166 bytes |"
                    line.startsWith("| APK SHA-256 |") -> "| APK SHA-256 | 8DCC6F4EB0A5C24C10B44CCDBAB237CFDB7CB464D0955647451DEA438287AA30 |"
                    line.startsWith("| ADB serial |") -> "| ADB serial | TEST-DEVICE-SERIAL |"
                    line.startsWith("| Device model |") -> "| Device model | Test-Device |"
                    line.startsWith("| Android SDK |") -> "| Android SDK | 36 |"
                    line.startsWith("| `phase6-device-run-summary.md` |") -> "| `phase6-device-run-summary.md` | <REPO_ROOT>\\apps\\android-instantread\\app\\build\\outputs\\phase6-device-smoke\\phase6-device-run-summary.md |"
                    line.startsWith("| `phase6-manual-next-steps.md` |") -> "| `phase6-manual-next-steps.md` | <REPO_ROOT>\\apps\\android-instantread\\app\\build\\outputs\\phase6-device-smoke\\phase6-manual-next-steps.md |"
                    line.startsWith("| `adb-devices.txt` |") -> "| `adb-devices.txt` | <REPO_ROOT>\\apps\\android-instantread\\app\\build\\outputs\\phase6-device-smoke\\adb-devices.txt |"
                    line.startsWith("| `device-samples.txt` |") -> "| `device-samples.txt` | <REPO_ROOT>\\apps\\android-instantread\\app\\build\\outputs\\phase6-device-smoke\\device-samples.txt |"
                    line.startsWith("| Overall batch result |") -> "| Overall batch result | Passed |"
                    isRealSourceRow(line) -> replaceMarkdownCell(
                        line.replace("Pending", "Recorded"),
                        oneBasedColumn = 16,
                        value = "Pass"
                    )
                    isUnsafeOrUnsupportedRow(line) -> replaceMarkdownCell(
                        line.replace("Pending", "Recorded"),
                        oneBasedColumn = 6,
                        value = if (line.startsWith("| Direct shell")) "Diagnostic only" else "Pass"
                    )
                    else -> line.replace("Pending", "Recorded")
                }
            }
    }

    private fun isRealSourceRow(line: String): Boolean {
        return listOf("sample.html", "sample.md", "sample.txt", "sample-site.zip")
            .any { sample -> line.startsWith("| `$sample` |") } &&
            markdownCellCount(line) >= 16
    }

    private fun isUnsafeOrUnsupportedRow(line: String): Boolean {
        return line.startsWith("| `unsafe-traversal.zip` through") ||
            line.startsWith("| True multi-file share |") ||
            line.startsWith("| Direct shell `file:// /sdcard` probe |")
    }

    private fun replaceMarkdownCell(line: String, oneBasedColumn: Int, value: String): String {
        val cells = line.trim().trim('|').split("|").map { it.trim() }.toMutableList()
        cells[oneBasedColumn - 1] = value
        return cells.joinToString(prefix = "| ", separator = " | ", postfix = " |")
    }

    private fun markdownCellCount(line: String): Int {
        return line.trim().trim('|').split("|").size
    }

    private fun relativePath(file: File): String {
        return REPO_ROOT.toPath()
            .relativize(file.toPath())
            .toString()
    }

    private fun runAuditScript(vararg arguments: String): CommandResult {
        val powerShell = locatePowerShell()
            ?: throw AssertionError(
                "PowerShell (pwsh) should be available for script verification."
            )

        val command = mutableListOf(
            powerShell.absolutePath,
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            File(REPO_ROOT, "tools/phase6-results-audit.ps1").absolutePath
        )
        command.addAll(arguments)

        val process = ProcessBuilder(command)
            .directory(REPO_ROOT)
            .redirectErrorStream(true)
            .start()
        val executor = Executors.newSingleThreadExecutor()
        val outputFuture = executor.submit<String> {
            process.inputStream.bufferedReader(Charsets.UTF_8).readText()
        }

        try {
            val completed = process.waitFor(30, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
            }
            assertTrue("Phase 6 results audit script should finish promptly.", completed)

            return CommandResult(
                exitCode = process.exitValue(),
                output = outputFuture.get(5, TimeUnit.SECONDS)
            )
        } finally {
            executor.shutdownNow()
        }
    }

    private fun locatePowerShell(): File? {
        val windowsPowerShell = File("C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe")
        if (windowsPowerShell.isFile) {
            return windowsPowerShell
        }

        return System.getenv("PATH").orEmpty()
            .split(File.pathSeparator)
            .asSequence()
            .filter { it.isNotBlank() }
            .flatMap { directory ->
                listOf("pwsh", "pwsh.exe").asSequence().map { File(directory, it) }
            }
            .firstOrNull { it.isFile && it.canExecute() }
    }

    private data class CommandResult(
        val exitCode: Int,
        val output: String
    )

    private companion object {
        val REPO_ROOT: File = listOf(File("../../.."), File("../../../.."))
            .map { it.canonicalFile }
            .first { File(it, "docs/validation/android-instantread/phase6-real-source-results.md").isFile }
    }
}
