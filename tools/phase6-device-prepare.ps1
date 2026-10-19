param(
    [string]$Serial = "",
    [string]$DeviceValidationDir = "/sdcard/Download/android-instantread-validation",
    [string]$PackageName = "com.personalai.instantread.debug",
    [switch]$AllowDirtyInstall
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = (Resolve-Path (Join-Path $ScriptDir "..")).Path
$AppDir = Join-Path $RepoRoot "apps/android-instantread"
$ApkPath = Join-Path $AppDir "app/build/outputs/apk/debug/app-debug.apk"
$SamplesDir = Join-Path $RepoRoot "docs/validation/android-instantread/samples"
$ResultsTemplatePath = Join-Path $RepoRoot "docs/validation/android-instantread/phase6-real-source-results.md"
$ArtifactsDir = Join-Path $AppDir "app/build/outputs/phase6-device-smoke"
$RunSummaryPath = Join-Path $ArtifactsDir "phase6-device-run-summary.md"
$ManualNextStepsPath = Join-Path $ArtifactsDir "phase6-manual-next-steps.md"
$AdbDevicesPath = Join-Path $ArtifactsDir "adb-devices.txt"
$SampleNames = @(
    "sample.html",
    "sample.md",
    "sample.txt",
    "sample-site.zip",
    "unsafe-traversal.zip"
)
$ExpectedSmokeArtifactNames = @(
    "phase6-device-run-summary.md",
    "phase6-manual-next-steps.md",
    "phase6-real-source-results.md",
    "adb-install.txt",
    "monkey-launch.txt",
    "dumpsys-activity.txt",
    "uiautomator-dump.txt",
    "window_dump.xml",
    "dumpsys-package.txt",
    "device-samples.txt",
    "adb-devices.txt"
)

$script:AdbSerial = $null

function Stop-Phase6Script {
    param(
        [string]$Message,
        [int]$Code = 1
    )

    [Console]::Error.WriteLine($Message)
    exit $Code
}

function Write-AdbDevicesArtifact {
    $output = @(& adb devices -l)
    if ($LASTEXITCODE -ne 0) {
        Stop-Phase6Script "adb devices failed with exit code $LASTEXITCODE."
    }

    [System.IO.File]::WriteAllLines($AdbDevicesPath, $output, [System.Text.Encoding]::UTF8)
    return $output
}

function Get-ConnectedDeviceRows {
    param([string[]]$AdbDevicesOutput)

    return @($AdbDevicesOutput | Where-Object { $_ -match "\sdevice(\s|$)" })
}

function Get-AdbArguments {
    param([string[]]$Arguments)

    if ($script:AdbSerial) {
        return @("-s", $script:AdbSerial) + $Arguments
    }

    return $Arguments
}

function Invoke-Adb {
    param(
        [string[]]$Arguments,
        [string]$OutputFile = ""
    )

    $fullArguments = Get-AdbArguments -Arguments $Arguments
    Write-Host "> adb $($fullArguments -join ' ')"

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    if ($OutputFile) {
        & adb @fullArguments 2>&1 | Tee-Object -FilePath $OutputFile
    } else {
        & adb @fullArguments
    }
    $ErrorActionPreference = $previousErrorActionPreference

    if ($LASTEXITCODE -ne 0) {
        Stop-Phase6Script "adb command failed with exit code $($LASTEXITCODE): adb $($fullArguments -join ' ')"
    }
}

function Get-DeviceProperty {
    param([string]$Name)

    $arguments = Get-AdbArguments -Arguments @("shell", "getprop", $Name)
    $value = (& adb @arguments 2>$null | Select-Object -First 1)
    if ($LASTEXITCODE -ne 0) {
        return "unavailable"
    }

    $trimmedValue = "$value".Trim()
    if ($trimmedValue) {
        return $trimmedValue
    }

    return "unavailable"
}

function Assert-SmokeArtifacts {
    foreach ($artifactName in $ExpectedSmokeArtifactNames) {
        $artifactPath = Join-Path $ArtifactsDir $artifactName
        if (-not (Test-Path -LiteralPath $artifactPath)) {
            Stop-Phase6Script "Expected smoke artifact missing: $artifactPath"
        }
    }
}

function Get-GitHead {
    $value = (& git -C $RepoRoot rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) {
        Stop-Phase6Script "git rev-parse failed with exit code $($LASTEXITCODE)."
    }

    return $value
}

function Get-GitStatusLines {
    $value = @(& git -C $RepoRoot status --short --branch)
    if ($LASTEXITCODE -ne 0) {
        Stop-Phase6Script "git status failed with exit code $($LASTEXITCODE)."
    }

    return $value
}

function Assert-CleanGitStatusForInstall {
    $dirtyLines = @(Get-GitStatusLines | Where-Object { $_ -notmatch "^## " })
    if ($dirtyLines.Count -gt 0) {
        $details = $dirtyLines -join "; "
        Stop-Phase6Script "Refusing to install from a dirty working tree. Commit changes before final Phase 6 acceptance, or Use -AllowDirtyInstall for an intentional scratch install. Dirty entries: $details" 3
    }
}

function Write-RunSummary {
    $apkItem = Get-Item -LiteralPath $ApkPath
    $apkHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $ApkPath).Hash
    $deviceSerial = "pending"
    $deviceModel = "pending"
    $deviceSdk = "pending"
    if ($script:AdbSerial) {
        $deviceSerial = $script:AdbSerial
        $deviceModel = Get-DeviceProperty -Name "ro.product.model"
        $deviceSdk = Get-DeviceProperty -Name "ro.build.version.sdk"
    }
    $gitHead = Get-GitHead
    $gitStatus = Get-GitStatusLines
    $sampleLines = @()
    foreach ($sampleName in $SampleNames) {
        $samplePath = Join-Path $SamplesDir $sampleName
        $sampleItem = Get-Item -LiteralPath $samplePath
        $sampleHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $samplePath).Hash
        $sampleLines += "- ``$sampleName`` ($($sampleItem.Length) bytes, SHA-256: $sampleHash)"
    }

    $lines = @(
        "# Phase 6 Device Run Summary",
        "",
        "Generated: $((Get-Date).ToString('yyyy-MM-dd HH:mm:ss zzz'))",
        "",
        "## Build",
        "",
        "- Debug APK: ``$ApkPath``",
        "- APK size: $($apkItem.Length) bytes",
        "- APK SHA-256: $apkHash",
        "- Package: ``$PackageName``",
        "",
        "## Device",
        "",
        "- ADB serial: ``$deviceSerial``",
        "- Device model: ``$deviceModel``",
        "- Android SDK: ``$deviceSdk``",
        "",
        "## Git",
        "",
        "- Git HEAD: ``$gitHead``",
        "- Git status:"
    ) + ($gitStatus | ForEach-Object { "  - ``$_``" }) + @(
        "",
        "## Validation Inputs",
        "",
        "- Device validation directory: ``$DeviceValidationDir``",
        "- Results file: ``docs/validation/android-instantread/phase6-real-source-results.md``",
        "- Smoke artifact results copy: ``$ArtifactsDir\phase6-real-source-results.md``",
        "- ADB device list: ``$AdbDevicesPath``",
        "",
        "## Samples"
    ) + $sampleLines + @(
        "",
        "## Expected Smoke Artifacts",
        ""
    ) + ($ExpectedSmokeArtifactNames | ForEach-Object { "- ``$_``" }) + @(
        "",
        "## Manual Acceptance Sources",
        "",
        "- system file manager",
        "- chat/share source",
        "- browser download source",
        "",
        "After each import, return home and copy the diagnostics summary into the results file."
    )

    [System.IO.File]::WriteAllLines($RunSummaryPath, $lines, [System.Text.Encoding]::UTF8)
}

function Update-ResultsBatchMetadata {
    $resultsCopyPath = Join-Path $ArtifactsDir "phase6-real-source-results.md"
    $text = [System.IO.File]::ReadAllText($resultsCopyPath, [System.Text.Encoding]::UTF8)
    $apkItem = Get-Item -LiteralPath $ApkPath
    $apkHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $ApkPath).Hash
    $deviceSerial = "pending"
    $deviceModel = "pending"
    $deviceSdk = "pending"
    if ($script:AdbSerial) {
        $deviceSerial = $script:AdbSerial
        $deviceModel = Get-DeviceProperty -Name "ro.product.model"
        $deviceSdk = Get-DeviceProperty -Name "ro.build.version.sdk"
    }

    $metadataRows = @{
        "Script run time" = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss zzz')
        "Git HEAD" = Get-GitHead
        "Git status" = ((Get-GitStatusLines) -join "; ")
        "Debug APK path" = $ApkPath
        "APK size" = "$($apkItem.Length) bytes"
        "APK SHA-256" = $apkHash
        "ADB serial" = $deviceSerial
        "Device model" = $deviceModel
        "Android SDK" = $deviceSdk
        "Device validation directory" = $DeviceValidationDir
        "Smoke artifact directory" = $ArtifactsDir
        "``phase6-device-run-summary.md``" = $RunSummaryPath
        "``phase6-manual-next-steps.md``" = $ManualNextStepsPath
        "``device-samples.txt``" = (Join-Path $ArtifactsDir "device-samples.txt")
        "``adb-devices.txt``" = $AdbDevicesPath
        "Overall batch result" = "Pending manual validation"
    }

    foreach ($key in $metadataRows.Keys) {
        $escapedKey = [regex]::Escape($key)
        $replacement = "| $key | $($metadataRows[$key]) |"
        $text = [regex]::Replace($text, "\| $escapedKey \| [^\r\n]* \|", $replacement)
    }

    [System.IO.File]::WriteAllText($resultsCopyPath, $text, [System.Text.Encoding]::UTF8)
}

function Write-ManualNextSteps {
    $sampleLines = $SampleNames | ForEach-Object { "- ``$_``" }
    $lines = @(
        "# Phase 6 Manual Next Steps",
        "",
        "Use this file after the final connected-device preparation run. It is a local smoke artifact, not the final evidence sheet.",
        "",
        "## Evidence Files",
        "",
        "- Run summary: ``phase6-device-run-summary.md``",
        "- ADB device list: ``adb-devices.txt``",
        "- Device sample listing: ``device-samples.txt``",
        "- Final results sheet: ``docs/validation/android-instantread/phase6-real-source-results.md``",
        "- Smoke-artifact results copy: ``$ArtifactsDir\phase6-real-source-results.md``",
        "",
        "## Sources To Validate",
        "",
        "- system file manager",
        "- chat/share source",
        "- browser download source",
        "",
        "## Samples To Use",
        ""
    ) + $sampleLines + @(
        "",
        "## What To Record",
        "",
        "- For each import, return to the app home screen.",
        "- Long-press and copy the diagnostics summary.",
        "- Paste the diagnostics summary into ``docs/validation/android-instantread/phase6-real-source-results.md``.",
        "- Record whether preview, recent file, favorite, delete, and relevant ZIP behavior matched the expected result.",
        "- Replace every ``Pending`` cell in the final results sheet before closing Phase 6.",
        "- Use only ``Pass``, ``Fail``, or ``Diagnostic only`` in source-row Pass/Fail cells.",
        "- Set ``Overall batch result`` to ``Passed`` only after every required source row and unsafe/unsupported row has been recorded.",
        "",
        "## Final Audit",
        "",
        "Run ``tools/phase6-results-audit.ps1`` after filling ``docs/validation/android-instantread/phase6-real-source-results.md``.",
        "Phase 6 is not closed until the audit prints ``Phase 6 results audit passed.``.",
        "",
        "No extra install is needed for incremental development. Use the final connected-device preparation run once, then complete manual validation in one pass."
    )

    [System.IO.File]::WriteAllLines($ManualNextStepsPath, $lines, [System.Text.Encoding]::UTF8)
}

if (-not (Test-Path -LiteralPath $ApkPath)) {
    Stop-Phase6Script "Debug APK not found: $ApkPath. Run apps/android-instantread/gradlew.bat clean :app:testDebugUnitTest :app:assembleDebug first."
}

foreach ($sampleName in $SampleNames) {
    $samplePath = Join-Path $SamplesDir $sampleName
    if (-not (Test-Path -LiteralPath $samplePath)) {
        Stop-Phase6Script "Validation sample not found: $samplePath"
    }
}

if (-not (Test-Path -LiteralPath $ResultsTemplatePath)) {
    Stop-Phase6Script "Phase 6 results template not found: $ResultsTemplatePath"
}

New-Item -ItemType Directory -Force -Path $ArtifactsDir | Out-Null
Copy-Item -LiteralPath $ResultsTemplatePath -Destination (Join-Path $ArtifactsDir "phase6-real-source-results.md") -Force
Update-ResultsBatchMetadata
Write-ManualNextSteps
Write-RunSummary

$adbDevicesOutput = Write-AdbDevicesArtifact
$deviceRows = @(Get-ConnectedDeviceRows -AdbDevicesOutput $adbDevicesOutput)
if ($Serial) {
    $escapedSerial = [regex]::Escape($Serial)
    $matchingRows = @($deviceRows | Where-Object { $_ -match "^$escapedSerial\s+device(\s|$)" })
    if ($matchingRows.Count -eq 0) {
        Stop-Phase6Script "Device serial '$Serial' is not connected as a usable adb device." 2
    }
    $script:AdbSerial = $Serial
} else {
    if ($deviceRows.Count -eq 0) {
        Stop-Phase6Script "No connected adb device. Connect and unlock the phone, enable USB debugging, then rerun this script." 2
    }
    if ($deviceRows.Count -gt 1) {
        Stop-Phase6Script "Multiple adb devices are connected. Rerun with -Serial <device-serial>." 2
    }
    $script:AdbSerial = ($deviceRows[0] -split "\s+")[0]
}

Write-Host "Using adb device: $script:AdbSerial"
Write-Host "APK: $ApkPath"
Write-Host "Device validation directory: $DeviceValidationDir"
Write-Host "Artifacts: $ArtifactsDir"
Write-Host "Manual evidence sheet: docs/validation/android-instantread/phase6-real-source-results.md"
Write-Host "Run summary: $RunSummaryPath"

Update-ResultsBatchMetadata
Write-RunSummary

if (-not $AllowDirtyInstall) {
    Assert-CleanGitStatusForInstall
} else {
    Write-Host "AllowDirtyInstall enabled; installing even though git status may include uncommitted changes."
}

Invoke-Adb -Arguments @("install", "-r", $ApkPath) -OutputFile (Join-Path $ArtifactsDir "adb-install.txt")
Invoke-Adb -Arguments @("shell", "mkdir", "-p", $DeviceValidationDir)

foreach ($sampleName in $SampleNames) {
    $samplePath = Join-Path $SamplesDir $sampleName
    Invoke-Adb -Arguments @("push", $samplePath, "$DeviceValidationDir/$sampleName")
}

Invoke-Adb -Arguments @("shell", "ls", "-l", $DeviceValidationDir) -OutputFile (Join-Path $ArtifactsDir "device-samples.txt")

Invoke-Adb -Arguments @("shell", "monkey", "-p", $PackageName, "1") -OutputFile (Join-Path $ArtifactsDir "monkey-launch.txt")
Start-Sleep -Seconds 2

$activityOutput = Join-Path $ArtifactsDir "dumpsys-activity.txt"
Invoke-Adb -Arguments @("shell", "dumpsys", "activity", "activities") -OutputFile $activityOutput
$activityText = Get-Content -Raw -LiteralPath $activityOutput
if ($activityText -notmatch [regex]::Escape($PackageName)) {
    Stop-Phase6Script "Launch smoke failed: dumpsys activity output does not mention $PackageName. See $activityOutput"
}

Invoke-Adb -Arguments @("shell", "uiautomator", "dump", "/sdcard/window_dump.xml") -OutputFile (Join-Path $ArtifactsDir "uiautomator-dump.txt")
Invoke-Adb -Arguments @("pull", "/sdcard/window_dump.xml", (Join-Path $ArtifactsDir "window_dump.xml"))
Invoke-Adb -Arguments @("shell", "dumpsys", "package", $PackageName) -OutputFile (Join-Path $ArtifactsDir "dumpsys-package.txt")

Assert-SmokeArtifacts

Write-Host ""
Write-Host "Phase 6 device preparation completed."
Write-Host "Next manual validation: open the samples from system file manager, chat/share source, and browser download source."
Write-Host "After each import, return home and copy the diagnostics summary into docs/validation/android-instantread/phase6-real-source-results.md."
Write-Host "A copy of the blank evidence sheet is also saved at $ArtifactsDir\phase6-real-source-results.md."
