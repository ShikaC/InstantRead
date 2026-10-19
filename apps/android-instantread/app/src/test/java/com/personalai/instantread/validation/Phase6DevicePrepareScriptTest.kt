package com.personalai.instantread.validation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Phase6DevicePrepareScriptTest {
    @Test
    fun devicePreparationScriptDefaultsToInstantReadDebugPackage() {
        val script = File(REPO_ROOT, "tools/phase6-device-prepare.ps1").readText()
        val retiredPackage = listOf("com", "personalai", "glimpse").joinToString(".")

        assertTrue(
            "Debug APK validation should target the InstantRead debug application ID.",
            script.contains("[string]${'$'}PackageName = \"com.personalai.instantread.debug\"")
        )
        assertFalse(
            "Device validation must not target the retired Glimpse application ID.",
            script.contains("[string]${'$'}PackageName = \"$retiredPackage\"")
        )
    }

    @Test
    fun devicePreparationScriptPointsManualEvidenceToResultsFile() {
        val script = File(REPO_ROOT, "tools/phase6-device-prepare.ps1").readText()
        val resultsFile = File(
            REPO_ROOT,
            "docs/validation/android-instantread/phase6-real-source-results.md"
        )

        assertTrue("Phase 6 real-source results file should exist.", resultsFile.isFile)
        assertTrue(
            "Device preparation script should direct manual evidence into the results file.",
            script.contains("docs/validation/android-instantread/phase6-real-source-results.md")
        )
        assertFalse(
            "Device preparation script should not direct final evidence back into README.md.",
            script.contains("copy the diagnostics summary into docs/validation/android-instantread/README.md")
        )
    }

    @Test
    fun devicePreparationScriptKeepsFailureOutputPlainAndPreservesExitCode() {
        val script = File(REPO_ROOT, "tools/phase6-device-prepare.ps1").readText()

        assertTrue(
            "Failure helper should write a plain stderr line.",
            script.contains("[Console]::Error.WriteLine(${'$'}Message)")
        )
        assertFalse(
            "Write-Error becomes terminating under ErrorActionPreference=Stop and skips explicit exit codes.",
            script.contains("Write-Error ${'$'}Message")
        )
        assertTrue(
            "Failure helper should still exit with the requested code.",
            script.contains("exit ${'$'}Code")
        )
    }

    @Test
    fun devicePreparationScriptDoesNotTreatSuccessfulAdbStderrAsFailure() {
        val script = File(REPO_ROOT, "tools/phase6-device-prepare.ps1").readText()
        val invokeAdbFunction = script.substringAfter("function Invoke-Adb").substringBefore("function Get-DeviceProperty")

        assertTrue(
            "Invoke-Adb should preserve the caller ErrorActionPreference before running adb.",
            invokeAdbFunction.contains("${'$'}previousErrorActionPreference = ${'$'}ErrorActionPreference")
        )
        assertTrue(
            "Invoke-Adb should temporarily allow native stderr so adb progress output does not become a terminating PowerShell error.",
            invokeAdbFunction.contains("${'$'}ErrorActionPreference = \"Continue\"")
        )
        assertTrue(
            "Invoke-Adb should restore ErrorActionPreference after adb returns.",
            invokeAdbFunction.contains("${'$'}ErrorActionPreference = ${'$'}previousErrorActionPreference")
        )
        assertTrue(
            "Invoke-Adb should still check LASTEXITCODE after restoring strict error handling.",
            invokeAdbFunction.indexOf("if (${'$'}LASTEXITCODE -ne 0)") >
                invokeAdbFunction.indexOf("${'$'}ErrorActionPreference = ${'$'}previousErrorActionPreference")
        )
    }

    @Test
    fun devicePreparationScriptCopiesBlankEvidenceSheetIntoSmokeArtifacts() {
        val script = File(REPO_ROOT, "tools/phase6-device-prepare.ps1").readText()
        val copyIndex = script.indexOf(
            "Copy-Item -LiteralPath ${'$'}ResultsTemplatePath -Destination (Join-Path ${'$'}ArtifactsDir \"phase6-real-source-results.md\") -Force"
        )
        val deviceCheckIndex = script.indexOf("${'$'}deviceRows = @(Get-ConnectedDeviceRows")

        assertTrue(
            "Device preparation should copy the blank evidence sheet into smoke artifacts.",
            copyIndex >= 0
        )
        assertTrue(
            "The artifact copy should be created before adb device checks so no-device dry runs still leave the sheet.",
            copyIndex < deviceCheckIndex
        )
        assertTrue(
            "Device preparation should tell the tester where the artifact copy lives.",
            script.contains(
                "A copy of the blank evidence sheet is also saved at ${'$'}ArtifactsDir\\phase6-real-source-results.md."
            )
        )
    }

    @Test
    fun devicePreparationScriptPrefillsResultsBatchMetadataBeforeDeviceChecks() {
        val script = File(REPO_ROOT, "tools/phase6-device-prepare.ps1").readText()
        val copyIndex = script.indexOf(
            "Copy-Item -LiteralPath ${'$'}ResultsTemplatePath -Destination (Join-Path ${'$'}ArtifactsDir \"phase6-real-source-results.md\") -Force"
        )
        val updateFunctionIndex = script.indexOf("function Update-ResultsBatchMetadata")
        val updateCallIndex = script.indexOf("\nUpdate-ResultsBatchMetadata", copyIndex)
        val deviceCheckIndex = script.indexOf("${'$'}deviceRows = @(Get-ConnectedDeviceRows")
        val deviceResolvedIndex = script.indexOf("Using adb device:")
        val updateCallAfterDeviceResolved = script.indexOf("\nUpdate-ResultsBatchMetadata", deviceResolvedIndex)

        assertTrue(
            "Device preparation should update the smoke-artifact results sheet with batch metadata.",
            updateFunctionIndex >= 0
        )
        assertTrue(
            "Results metadata update should run after copying the template and before adb device checks.",
            updateCallIndex > copyIndex && updateCallIndex < deviceCheckIndex
        )
        assertTrue(
            "Results metadata update should run again after the final adb serial is selected.",
            updateCallAfterDeviceResolved > deviceResolvedIndex
        )
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
            "Smoke artifact directory",
            "phase6-device-run-summary.md",
            "phase6-manual-next-steps.md",
            "device-samples.txt",
            "Overall batch result"
        ).forEach { metadataField ->
            assertTrue(
                "Batch metadata updater should fill $metadataField.",
                script.contains(metadataField)
            )
        }
        assertTrue(
            "Batch metadata updater should rewrite the smoke-artifact results sheet.",
            script.contains("[System.IO.File]::WriteAllText(") &&
                script.contains("phase6-real-source-results.md")
        )
    }

    @Test
    fun devicePreparationScriptWritesRunSummaryForManualAcceptance() {
        val script = File(REPO_ROOT, "tools/phase6-device-prepare.ps1").readText()

        assertTrue(
            "Device preparation should write a run summary beside smoke artifacts.",
            script.contains("${'$'}RunSummaryPath = Join-Path ${'$'}ArtifactsDir \"phase6-device-run-summary.md\"")
        )
        assertTrue(
            "Run summary should include the APK path under validation.",
            script.contains("Debug APK")
        )
        assertTrue(
            "Run summary should include the pushed validation sample names.",
            script.contains("${'$'}SampleNames")
        )
        assertTrue(
            "Run summary should include the device validation directory.",
            script.contains("${'$'}DeviceValidationDir")
        )
        assertTrue(
            "Run summary should point to the real-source results file.",
            script.contains("phase6-real-source-results.md")
        )
        assertTrue(
            "Run summary should preserve the three pending real-source classes.",
            script.contains("system file manager") &&
                script.contains("chat/share source") &&
                script.contains("browser download source")
        )
    }

    @Test
    fun devicePreparationRunSummaryIncludesSha256ForApkAndSamples() {
        val script = File(REPO_ROOT, "tools/phase6-device-prepare.ps1").readText()

        assertTrue(
            "Run summary should use SHA-256 file hashes for traceability.",
            script.contains("Get-FileHash -Algorithm SHA256")
        )
        assertTrue(
            "Run summary should include the APK SHA-256 hash.",
            script.contains("APK SHA-256")
        )
        assertTrue(
            "Run summary should include sample SHA-256 hashes.",
            script.contains("SHA-256: $")
        )
    }

    @Test
    fun devicePreparationRunSummaryIncludesGitVersionAndStatus() {
        val script = File(REPO_ROOT, "tools/phase6-device-prepare.ps1").readText()

        assertTrue(
            "Run summary should read git HEAD from the repository root.",
            script.contains("git -C ${'$'}RepoRoot rev-parse HEAD")
        )
        assertTrue(
            "Run summary should read git status from the repository root.",
            script.contains("git -C ${'$'}RepoRoot status --short --branch")
        )
        assertTrue(
            "Run summary should include the git HEAD row.",
            script.contains("Git HEAD")
        )
        assertTrue(
            "Run summary should include the git status section.",
            script.contains("Git status")
        )
    }

    @Test
    fun devicePreparationScriptBlocksDirtyConnectedInstallByDefault() {
        val script = File(REPO_ROOT, "tools/phase6-device-prepare.ps1").readText()
        val deviceResolvedIndex = script.indexOf("Using adb device:")
        val cleanCheckCallIndex = script.indexOf("Assert-CleanGitStatusForInstall", deviceResolvedIndex)
        val installIndex = script.indexOf("Invoke-Adb -Arguments @(\"install\", \"-r\", ${'$'}ApkPath)")

        assertTrue(
            "Device preparation should offer an explicit escape hatch for intentional scratch installs.",
            script.contains("[switch]${'$'}AllowDirtyInstall")
        )
        assertTrue(
            "Device preparation should define a clean-git guard for final acceptance installs.",
            script.contains("function Assert-CleanGitStatusForInstall")
        )
        assertTrue(
            "Clean-git guard should ignore the branch status line and detect real dirty entries.",
            script.contains("Get-GitStatusLines | Where-Object") &&
                script.contains("^## ")
        )
        assertTrue(
            "Dirty connected installs should fail with a clear final-acceptance message.",
            script.contains("Refusing to install from a dirty working tree") &&
                script.contains("Use -AllowDirtyInstall")
        )
        assertTrue(
            "Clean-git guard should run by default but allow intentional dirty installs.",
            script.contains("if (-not ${'$'}AllowDirtyInstall)")
        )
        assertTrue(
            "Clean-git guard should run after adb device selection and before adb install.",
            cleanCheckCallIndex > deviceResolvedIndex && cleanCheckCallIndex < installIndex
        )
    }

    @Test
    fun devicePreparationScriptCapturesAdbDeviceListBeforeDeviceSelection() {
        val script = File(REPO_ROOT, "tools/phase6-device-prepare.ps1").readText()
        val writeFunctionIndex = script.indexOf("function Write-AdbDevicesArtifact")
        val writeCallIndex = script.indexOf("${'$'}adbDevicesOutput = Write-AdbDevicesArtifact")
        val deviceRowsIndex = script.indexOf("${'$'}deviceRows = @(Get-ConnectedDeviceRows")

        assertTrue(
            "Device preparation should define a path for the raw adb devices smoke artifact.",
            script.contains("${'$'}AdbDevicesPath = Join-Path ${'$'}ArtifactsDir \"adb-devices.txt\"")
        )
        assertTrue(
            "Raw adb device list should be part of the expected smoke artifact set.",
            script.contains("\"adb-devices.txt\"")
        )
        assertTrue(
            "Device preparation should capture adb devices -l output into an artifact.",
            writeFunctionIndex >= 0 &&
                script.contains("adb devices -l") &&
                script.contains("[System.IO.File]::WriteAllLines(${'$'}AdbDevicesPath")
        )
        assertTrue(
            "Device rows should be parsed from the captured adb devices output.",
            script.contains("Get-ConnectedDeviceRows -AdbDevicesOutput ${'$'}adbDevicesOutput")
        )
        assertTrue(
            "Raw adb device list should be captured before selecting or rejecting a device.",
            writeCallIndex > writeFunctionIndex && writeCallIndex < deviceRowsIndex
        )
        assertTrue(
            "Manual next-steps artifact should point to the raw adb device list.",
            script.contains("ADB device list: ``adb-devices.txt``")
        )
    }

    @Test
    fun devicePreparationRunSummaryListsExpectedSmokeArtifacts() {
        val script = File(REPO_ROOT, "tools/phase6-device-prepare.ps1").readText()
        val sectionStart = script.indexOf("## Expected Smoke Artifacts")
        val manualAcceptanceStart = script.indexOf("## Manual Acceptance Sources")

        assertTrue(
            "Run summary should list the expected smoke artifacts for final acceptance.",
            sectionStart >= 0
        )
        assertTrue(
            "Expected smoke artifact list should appear before the manual acceptance source list.",
            manualAcceptanceStart > sectionStart
        )

        val section = script.substring(sectionStart, manualAcceptanceStart)
        assertTrue(
            "Run summary should render the shared smoke artifact list.",
            section.contains("${'$'}ExpectedSmokeArtifactNames | ForEach-Object")
        )
        listOf(
            "adb-install.txt",
            "monkey-launch.txt",
            "dumpsys-activity.txt",
            "uiautomator-dump.txt",
            "window_dump.xml",
            "dumpsys-package.txt",
            "device-samples.txt",
            "adb-devices.txt",
            "phase6-real-source-results.md",
            "phase6-device-run-summary.md",
            "phase6-manual-next-steps.md"
        ).forEach { artifactName ->
            assertTrue(
                "Shared expected smoke artifact list should include $artifactName.",
                script.contains("\"$artifactName\"")
            )
        }
    }

    @Test
    fun devicePreparationScriptWritesManualNextStepsArtifactBeforeDeviceChecks() {
        val script = File(REPO_ROOT, "tools/phase6-device-prepare.ps1").readText()
        val writeFunctionIndex = script.indexOf("function Write-ManualNextSteps")
        val writeCallIndex = script.lastIndexOf("\nWrite-ManualNextSteps")
        val deviceCheckIndex = script.indexOf("${'$'}deviceRows = @(Get-ConnectedDeviceRows")

        assertTrue(
            "Device preparation should write a manual next-steps artifact beside smoke artifacts.",
            script.contains("${'$'}ManualNextStepsPath = Join-Path ${'$'}ArtifactsDir \"phase6-manual-next-steps.md\"")
        )
        assertTrue(
            "Manual next-steps artifact should be generated by an explicit helper.",
            writeFunctionIndex >= 0
        )
        assertTrue(
            "Manual next-steps artifact should use UTF-8 file output.",
            script.contains("[System.IO.File]::WriteAllLines(${'$'}ManualNextStepsPath, ${'$'}lines, [System.Text.Encoding]::UTF8)")
        )
        listOf(
            "docs/validation/android-instantread/phase6-real-source-results.md",
            "phase6-device-run-summary.md",
            "device-samples.txt",
            "system file manager",
            "chat/share source",
            "browser download source"
        ).forEach { requiredText ->
            assertTrue(
                "Manual next-steps artifact should mention $requiredText.",
                script.contains(requiredText)
            )
        }
        assertTrue(
            "Manual next-steps artifact should include the validation sample names.",
            script.contains("${'$'}SampleNames | ForEach-Object")
        )
        assertTrue(
            "Manual next-steps artifact should be written before adb device checks so no-device dry runs still leave it.",
            writeCallIndex > writeFunctionIndex && writeCallIndex < deviceCheckIndex
        )
    }

    @Test
    fun manualNextStepsExplainFinalAuditStatusValues() {
        val script = File(REPO_ROOT, "tools/phase6-device-prepare.ps1").readText()
        val manualStepsFunction = script.substringAfter("function Write-ManualNextSteps")

        listOf(
            "tools/phase6-results-audit.ps1",
            "Overall batch result",
            "Passed",
            "Pass",
            "Fail",
            "Diagnostic only",
            "Pending"
        ).forEach { requiredText ->
            assertTrue(
                "Manual next-steps artifact should explain final audit status value: $requiredText.",
                manualStepsFunction.contains(requiredText)
            )
        }
    }

    @Test
    fun devicePreparationScriptVerifiesSmokeArtifactsBeforeReportingCompletion() {
        val script = File(REPO_ROOT, "tools/phase6-device-prepare.ps1").readText()
        val assertFunctionStart = script.indexOf("function Assert-SmokeArtifacts")
        val completionIndex = script.indexOf("Phase 6 device preparation completed.")
        val assertCallIndex = script.lastIndexOf("Assert-SmokeArtifacts")

        assertTrue(
            "Device preparation should define an explicit smoke artifact assertion helper.",
            assertFunctionStart >= 0
        )
        assertTrue(
            "Smoke artifact assertion helper should verify each expected file exists.",
            script.contains("foreach (${'$'}artifactName in ${'$'}ExpectedSmokeArtifactNames)") &&
                script.contains("Test-Path -LiteralPath ${'$'}artifactPath")
        )
        assertTrue(
            "Smoke artifact assertion helper should fail with a clear missing-artifact message.",
            script.contains("Expected smoke artifact missing")
        )
        assertTrue(
            "Smoke artifact assertion should run before the final completion message.",
            assertCallIndex in (assertFunctionStart + 1) until completionIndex
        )
    }

    @Test
    fun devicePreparationScriptCapturesDeviceSampleListingAfterPush() {
        val script = File(REPO_ROOT, "tools/phase6-device-prepare.ps1").readText()
        val pushLoopIndex = script.lastIndexOf("foreach (${'$'}sampleName in ${'$'}SampleNames)")
        val sampleListingIndex = script.lastIndexOf("device-samples.txt")
        val launchIndex = script.lastIndexOf("monkey")

        assertTrue(
            "Device sample listing should be a smoke artifact.",
            script.contains("\"device-samples.txt\"")
        )
        assertTrue(
            "Device sample listing should be captured with adb shell ls -l.",
            script.contains("@(\"shell\", \"ls\", \"-l\", ${'$'}DeviceValidationDir)") ||
                script.contains("@(\"shell\", \"ls\", \"-l\", \"${'$'}DeviceValidationDir\")")
        )
        assertTrue(
            "Device sample listing should be captured after pushing samples and before launching the app.",
            sampleListingIndex > pushLoopIndex && sampleListingIndex < launchIndex
        )
    }

    @Test
    fun devicePreparationScriptRecordsConnectedDeviceIdentityInRunSummary() {
        val script = File(REPO_ROOT, "tools/phase6-device-prepare.ps1").readText()
        val deviceResolvedIndex = script.indexOf("Using adb device:")
        val summaryWriteAfterDeviceResolved = script.indexOf("Write-RunSummary", deviceResolvedIndex)

        assertTrue(
            "Run summary should expose an explicit device section.",
            script.contains("## Device")
        )
        assertTrue(
            "Run summary should include the selected adb serial.",
            script.contains("ADB serial")
        )
        assertTrue(
            "Run summary should include the device model.",
            script.contains("Device model")
        )
        assertTrue(
            "Run summary should include the Android SDK level.",
            script.contains("Android SDK")
        )
        assertTrue(
            "Device model should come from adb getprop.",
            script.contains("ro.product.model")
        )
        assertTrue(
            "Android SDK level should come from adb getprop.",
            script.contains("ro.build.version.sdk")
        )
        assertTrue(
            "Run summary should be rewritten after the final adb serial is selected.",
            summaryWriteAfterDeviceResolved > deviceResolvedIndex
        )
    }

    private companion object {
        val REPO_ROOT: File = listOf(File("../../.."), File("../../../.."))
            .map { it.canonicalFile }
            .first { File(it, "tools/phase6-device-prepare.ps1").isFile }
    }
}
