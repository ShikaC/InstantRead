param(
    [string]$ResultsPath = "docs/validation/android-instantread/phase6-real-source-results.md"
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = (Resolve-Path (Join-Path $ScriptDir "..")).Path
$ResolvedResultsPath = Join-Path $RepoRoot $ResultsPath
$Failures = New-Object System.Collections.Generic.List[string]

function Add-Failure {
    param([string]$Message)
    $Failures.Add($Message) | Out-Null
}

function Get-Section {
    param(
        [string]$Text,
        [string]$Start,
        [string]$End = ""
    )

    $startIndex = $Text.IndexOf($Start, [System.StringComparison]::Ordinal)
    if ($startIndex -lt 0) {
        Add-Failure "Missing section: $Start"
        return ""
    }

    if (-not $End) {
        return $Text.Substring($startIndex)
    }

    $endIndex = $Text.IndexOf($End, $startIndex + $Start.Length, [System.StringComparison]::Ordinal)
    if ($endIndex -lt 0) {
        Add-Failure "Missing section end after $($Start): $End"
        return $Text.Substring($startIndex)
    }

    return $Text.Substring($startIndex, $endIndex - $startIndex)
}

function Assert-Contains {
    param(
        [string]$Text,
        [string]$Needle,
        [string]$Context
    )

    if (-not $Text.Contains($Needle)) {
        Add-Failure "Missing $($Context): $Needle"
    }
}

function Get-MarkdownCells {
    param([string]$Line)

    return @($Line.Trim().Trim("|") -split "\|" | ForEach-Object { $_.Trim() })
}

function Assert-CellValueIn {
    param(
        [string]$Line,
        [int]$OneBasedColumn,
        [string[]]$AllowedValues,
        [string]$Context
    )

    $cells = @(Get-MarkdownCells -Line $Line)
    if ($cells.Count -lt $OneBasedColumn) {
        Add-Failure "Missing $($Context) column: $Line"
        return
    }

    $value = $cells[$OneBasedColumn - 1]
    if (-not ($AllowedValues -contains $value)) {
        Add-Failure "Invalid $($Context): $value in $Line"
    }
}

function Get-MetadataValue {
    param(
        [string[]]$Lines,
        [string]$Field
    )

    foreach ($line in $Lines) {
        $cells = @(Get-MarkdownCells -Line $line)
        if ($cells.Count -ge 2 -and $cells[0] -eq $Field) {
            return $cells[1]
        }
    }

    Add-Failure "Missing final batch metadata: $Field"
    return ""
}

function Assert-MetadataMatches {
    param(
        [string[]]$Lines,
        [string]$Field,
        [string]$Pattern
    )

    $value = Get-MetadataValue -Lines $Lines -Field $Field
    if ($value -and $value -notmatch $Pattern) {
        Add-Failure "Invalid final batch metadata: $Field = $value"
    }
}

if (-not (Test-Path -LiteralPath $ResolvedResultsPath)) {
    [Console]::Error.WriteLine("Phase 6 results file not found: $ResolvedResultsPath")
    exit 1
}

Write-Host "Auditing Phase 6 results: $ResolvedResultsPath"

$text = [System.IO.File]::ReadAllText($ResolvedResultsPath, [System.Text.Encoding]::UTF8)

@(
    "## Final Acceptance Batch",
    "## System File Manager",
    "## Chat Or Share Source",
    "## Browser Download Source",
    "## Unsupported Or Unsafe Path Checks",
    "## Acceptance Summary"
) | ForEach-Object {
    Assert-Contains -Text $text -Needle $_ -Context "required section"
}

$finalBatchSection = Get-Section -Text $text -Start "## Final Acceptance Batch" -End "## App Picker Baseline"
$finalBatchLines = @($finalBatchSection -split "`r?`n")
Assert-MetadataMatches -Lines $finalBatchLines -Field "Script run time" -Pattern "^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} .+"
Assert-MetadataMatches -Lines $finalBatchLines -Field "Git HEAD" -Pattern "^[0-9a-f]{40}$"
Assert-MetadataMatches -Lines $finalBatchLines -Field "Git status" -Pattern "^## .+"
Assert-MetadataMatches -Lines $finalBatchLines -Field "Debug APK path" -Pattern "app-debug\.apk$"
Assert-MetadataMatches -Lines $finalBatchLines -Field "APK size" -Pattern "^\d+ bytes$"
Assert-MetadataMatches -Lines $finalBatchLines -Field "APK SHA-256" -Pattern "^[0-9A-Fa-f]{64}$"
Assert-MetadataMatches -Lines $finalBatchLines -Field "ADB serial" -Pattern "^\S+$"
Assert-MetadataMatches -Lines $finalBatchLines -Field "Device model" -Pattern "^.+$"
Assert-MetadataMatches -Lines $finalBatchLines -Field "Android SDK" -Pattern "^\d+$"
Assert-MetadataMatches -Lines $finalBatchLines -Field "``phase6-device-run-summary.md``" -Pattern "phase6-device-run-summary\.md$"
Assert-MetadataMatches -Lines $finalBatchLines -Field "``phase6-manual-next-steps.md``" -Pattern "phase6-manual-next-steps\.md$"
Assert-MetadataMatches -Lines $finalBatchLines -Field "``adb-devices.txt``" -Pattern "adb-devices\.txt$"
Assert-MetadataMatches -Lines $finalBatchLines -Field "``device-samples.txt``" -Pattern "device-samples\.txt$"

$overallBatchLines = @($text -split "`r?`n" | Where-Object { $_ -match "^\|\s*Overall batch result\s*\|" })
if ($overallBatchLines.Count -eq 0) {
    Add-Failure "Missing Overall batch result row"
} else {
    Assert-CellValueIn -Line $overallBatchLines[0] -OneBasedColumn 2 -AllowedValues @("Passed") -Context "Overall batch result"
}

$normalSamples = @(
    "sample.html",
    "sample.md",
    "sample.txt",
    "sample-site.zip"
)

$realSourceSections = @(
    @{ Name = "System File Manager"; Start = "## System File Manager"; End = "## Chat Or Share Source" },
    @{ Name = "Chat Or Share Source"; Start = "## Chat Or Share Source"; End = "## Browser Download Source" },
    @{ Name = "Browser Download Source"; Start = "## Browser Download Source"; End = "## Unsupported Or Unsafe Path Checks" }
)

foreach ($sectionSpec in $realSourceSections) {
    $section = Get-Section -Text $text -Start $sectionSpec.Start -End $sectionSpec.End
    $sectionLines = @($section -split "`r?`n")
    foreach ($sample in $normalSamples) {
        Assert-Contains -Text $section -Needle "``$sample``" -Context "$($sectionSpec.Name) sample row"
        $sampleCell = "``$sample``"
        $sampleRows = @($sectionLines | Where-Object {
            $cells = @(Get-MarkdownCells -Line $_)
            $cells.Count -gt 0 -and $cells[0] -eq $sampleCell
        })
        foreach ($sampleRow in $sampleRows) {
            Assert-CellValueIn -Line $sampleRow -OneBasedColumn 16 -AllowedValues @("Pass", "Fail", "Diagnostic only") -Context "Pass/Fail value"
        }
    }
}

$unsafeSection = Get-Section -Text $text -Start "## Unsupported Or Unsafe Path Checks" -End "## Acceptance Summary"
$unsafeCases = @(
    "``unsafe-traversal.zip`` through system file manager",
    "``unsafe-traversal.zip`` through share source",
    "``unsafe-traversal.zip`` through browser download source",
    "True multi-file share",
    "Direct shell ``file:// /sdcard`` probe"
)
$unsafeCases | ForEach-Object {
    Assert-Contains -Text $unsafeSection -Needle $_ -Context "unsafe or unsupported path row"
}
$unsafeSectionLines = @($unsafeSection -split "`r?`n")
foreach ($unsafeCase in $unsafeCases) {
    $unsafeRows = @($unsafeSectionLines | Where-Object {
        $cells = @(Get-MarkdownCells -Line $_)
        $cells.Count -gt 0 -and $cells[0] -eq $unsafeCase
    })
    foreach ($unsafeRow in $unsafeRows) {
        Assert-CellValueIn -Line $unsafeRow -OneBasedColumn 6 -AllowedValues @("Pass", "Fail", "Diagnostic only") -Context "Pass/Fail value"
    }
}

$pendingLines = @($text -split "`r?`n" | Where-Object { $_ -match "\|\s*Pending\s*(\||$)" })
foreach ($line in $pendingLines) {
    Add-Failure "Unresolved placeholder: $line"
}

if ($Failures.Count -gt 0) {
    [Console]::Error.WriteLine("Phase 6 results audit failed:")
    foreach ($failure in $Failures) {
        [Console]::Error.WriteLine("- $failure")
    }
    exit 1
}

Write-Host "Phase 6 results audit passed."
