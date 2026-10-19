Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$CredentialNames = @(
    "INSTANTREAD_RELEASE_STORE_FILE",
    "INSTANTREAD_RELEASE_STORE_PASSWORD",
    "INSTANTREAD_RELEASE_KEY_ALIAS",
    "INSTANTREAD_RELEASE_KEY_PASSWORD"
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = (Resolve-Path (Join-Path $ScriptDir "..")).Path
$AppDir = Join-Path $RepoRoot "apps/android-instantread"
$IsWindowsHost = $env:OS -eq "Windows_NT"
$GradleWrapperName = if ($IsWindowsHost) { "gradlew.bat" } else { "gradlew" }
$GradleWrapper = Join-Path $AppDir $GradleWrapperName
$BuildOutputDir = Join-Path $AppDir "app/build/outputs"

function Stop-ContractVerification {
    param(
        [string]$Message,
        [int]$Code = 1
    )

    [Console]::Error.WriteLine($Message)
    exit $Code
}

function Get-BuildArtifactFingerprint {
    if (-not (Test-Path -LiteralPath $BuildOutputDir)) {
        return @()
    }

    return @(
        Get-ChildItem -LiteralPath $BuildOutputDir -Recurse -File |
            Where-Object { $_.Extension -in @(".apk", ".aab") } |
            Sort-Object FullName |
            ForEach-Object {
                $hash = Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
                "$($_.FullName)|$($hash.Hash)"
            }
    )
}

function Invoke-ReleaseDryRun {
    Push-Location $AppDir
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = @(
            if ($IsWindowsHost) {
                & $GradleWrapper --no-daemon :app:assembleRelease --dry-run 2>&1
            } else {
                # 非 Windows 的 wrapper 可能没有可执行位,统一交给 sh 调用。
                & sh $GradleWrapper --no-daemon :app:assembleRelease --dry-run 2>&1
            }
        )
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
        Pop-Location
    }

    return [pscustomobject]@{
        ExitCode = $exitCode
        OutputText = ($output -join [Environment]::NewLine)
    }
}

if (-not (Test-Path -LiteralPath $GradleWrapper)) {
    Stop-ContractVerification "Android Gradle wrapper not found."
}

$originalEnvironment = @{}
foreach ($name in $CredentialNames) {
    $originalEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}

$beforeArtifacts = @()
$afterArtifacts = @()
$missingCredentialResult = $null
$invalidStoreResult = $null
$invalidStoreToken = $null
$invalidStorePath = $null
$ephemeralCredentialValue = $null

try {
    foreach ($name in $CredentialNames) {
        Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
    }

    $beforeArtifacts = @(Get-BuildArtifactFingerprint)
    $invalidStoreToken = [Guid]::NewGuid().ToString("N")
    $invalidStorePath = Join-Path ([System.IO.Path]::GetTempPath()) "instantread-missing-$invalidStoreToken.jks"
    $ephemeralCredentialValue = [Guid]::NewGuid().ToString("N")

    $missingCredentialResult = Invoke-ReleaseDryRun

    [Environment]::SetEnvironmentVariable(
        "INSTANTREAD_RELEASE_STORE_FILE",
        $invalidStorePath,
        "Process"
    )
    foreach ($name in $CredentialNames | Select-Object -Skip 1) {
        [Environment]::SetEnvironmentVariable(
            $name,
            $ephemeralCredentialValue,
            "Process"
        )
    }
    $invalidStoreResult = Invoke-ReleaseDryRun
    $afterArtifacts = @(Get-BuildArtifactFingerprint)
} finally {
    foreach ($name in $CredentialNames) {
        $originalValue = $originalEnvironment[$name]
        if ($null -eq $originalValue) {
            Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
        } else {
            Set-Item -LiteralPath "Env:$name" -Value $originalValue
        }
    }
}

if (($beforeArtifacts -join "`n") -ne ($afterArtifacts -join "`n")) {
    Stop-ContractVerification "Credential-free release verification changed build artifacts."
}

if ($missingCredentialResult.ExitCode -eq 0) {
    Stop-ContractVerification "Expected credential-free assembleRelease --dry-run to fail."
}

$missingCredentialMessage = "Release signing credentials are missing: " + ($CredentialNames -join ", ")
if (-not $missingCredentialResult.OutputText.Contains($missingCredentialMessage)) {
    Stop-ContractVerification "Missing credential failure did not use the safe contract message."
}

if ($invalidStoreResult.ExitCode -eq 0) {
    Stop-ContractVerification "Expected invalid-store assembleRelease --dry-run to fail."
}

$invalidStoreMessage = "Release signing credential is invalid: INSTANTREAD_RELEASE_STORE_FILE"
if (-not $invalidStoreResult.OutputText.Contains($invalidStoreMessage)) {
    Stop-ContractVerification "Invalid store failure did not use the safe contract message."
}
if (
    $invalidStoreResult.OutputText.Contains($invalidStoreToken) -or
    $invalidStoreResult.OutputText.Contains($invalidStorePath) -or
    $invalidStoreResult.OutputText.Contains($ephemeralCredentialValue)
) {
    Stop-ContractVerification "Invalid store failure leaked an environment value."
}

Write-Host "Release signing contract verified."
