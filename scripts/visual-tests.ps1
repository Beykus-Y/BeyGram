[CmdletBinding(DefaultParameterSetName = 'Goldens')]
param(
    [Parameter(ParameterSetName = 'Goldens')]
    [ValidateSet('verify', 'compare', 'record')]
    [string]$Mode = 'verify',

    [Parameter(ParameterSetName = 'Goldens')]
    [ValidateNotNullOrEmpty()]
    [string[]]$TestPattern = @('com.beykus.beygram.ui.screenshots.*'),

    [Parameter(ParameterSetName = 'Stealth', Mandatory)]
    [ValidateNotNullOrEmpty()]
    [string]$StealthCaptureDir,

    [Parameter(ParameterSetName = 'Stealth')]
    [ValidateSet('before', 'after')]
    [string]$Stage = 'after'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$wrapperName = if ($env:OS -eq 'Windows_NT') { 'gradlew.bat' } else { 'gradlew' }
$wrapper = Join-Path $projectRoot $wrapperName
$previousCaptureDir = $env:BEYGRAM_STEALTH_CAPTURE_DIR
$previousCaptureStage = $env:BEYGRAM_STEALTH_CAPTURE_STAGE

if ($PSCmdlet.ParameterSetName -eq 'Stealth') {
    $captureDir = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($StealthCaptureDir)
    New-Item -ItemType Directory -Path $captureDir -Force | Out-Null
    $Mode = 'record'
    $TestPattern = @('com.beykus.beygram.ui.screens.settings.StealthUiCapture')
}

$gradleArguments = @('--no-daemon', ':app:testDevDebugUnitTest', "-Pscreenshots=$($Mode.ToLowerInvariant())")
foreach ($pattern in $TestPattern) {
    $gradleArguments += '--tests', $pattern
}
if ($PSCmdlet.ParameterSetName -eq 'Stealth') {
    # This capture writes outside Gradle's normal test outputs, so run it even if test results are cached.
    $gradleArguments += '--rerun'
}

Push-Location -LiteralPath $projectRoot
try {
    if ($PSCmdlet.ParameterSetName -eq 'Stealth') {
        $env:BEYGRAM_STEALTH_CAPTURE_DIR = $captureDir
        $env:BEYGRAM_STEALTH_CAPTURE_STAGE = $Stage.ToLowerInvariant()
    }
    & $wrapper @gradleArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Visual tests failed (Gradle exit $LASTEXITCODE)."
    }
    if ($PSCmdlet.ParameterSetName -eq 'Stealth') {
        Write-Host "PNG: $captureDir"
    } else {
        Write-Host 'Goldens: app/src/test/screenshots'
        Write-Host 'Comparison output: app/build/outputs/roborazzi'
    }
} finally {
    $env:BEYGRAM_STEALTH_CAPTURE_DIR = $previousCaptureDir
    $env:BEYGRAM_STEALTH_CAPTURE_STAGE = $previousCaptureStage
    Pop-Location
}
