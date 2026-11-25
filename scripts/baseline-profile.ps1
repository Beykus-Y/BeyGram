#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Generates the BeyGram Baseline Profile on a connected physical device (#1119).

.DESCRIPTION
    Runs the profile-producing instrumentation test of :macrobenchmark against the release-like `benchmark` build type
    of :app, then copies the produced `baseline-prof.txt` into `app/src/main/`, where AGP packages it into the APK for
    `profileinstaller`.

    Nothing here runs in CI or in a pull-request build. It needs a physical device: an emulator measures and profiles
    the host CPU, which is not the target hardware.

    The script never writes a profile it did not receive from a real run. If the test produced nothing, it fails instead
    of creating an empty or hand-written file.

.EXAMPLE
    ./scripts/baseline-profile.ps1 -Flavor dev
    ./scripts/baseline-profile.ps1 -Flavor prod -Serial R58M1234567
#>
[CmdletBinding()]
param(
    [ValidateSet('dev', 'prod')]
    [string]$Flavor = 'dev',
    [string]$Serial,
    # Keep the previous profile as a sibling file instead of deleting it.
    [switch]$KeepPrevious
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$appDir = Join-Path $projectRoot 'app'
$destination = Join-Path $appDir 'src/main/baseline-prof.txt'
$outputRoot = Join-Path $projectRoot "macrobenchmark/build/outputs/androidTest-results/connected"

$gradlew = if ($IsWindows -or $env:OS -eq 'Windows_NT') {
    Join-Path $projectRoot 'gradlew.bat'
} else {
    Join-Path $projectRoot 'gradlew'
}

if (-not $env:JAVA_HOME) {
    $jbr = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path -LiteralPath $jbr) { $env:JAVA_HOME = $jbr }
}

$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { throw "adb not found at $adb" }

$devices = & $adb devices | Select-String -Pattern '\sdevice$' | ForEach-Object { ($_ -split '\s+')[0] }
if ($Serial) { $devices = @($devices | Where-Object { $_ -eq $Serial }) }
if ($devices.Count -ne 1) {
    throw "Connect exactly one physical device (found: $($devices -join ', ')). Use -Serial to choose one."
}
Write-Host "Target device: $($devices[0])"

$testClass = 'com.beykus.beygram.macrobenchmark.BaselineProfileGenerator'
& $gradlew ":macrobenchmark:connected${($Flavor.Substring(0,1).ToUpper())}${($Flavor.Substring(1))}BenchmarkAndroidTest" `
    "-Pandroid.testInstrumentationRunnerArguments.class=$testClass" `
    '--console=plain'
if ($LASTEXITCODE -ne 0) { throw "Baseline Profile generation failed (gradle exit $LASTEXITCODE)." }

$generated = Get-ChildItem -Path $outputRoot -Recurse -Filter 'baseline-prof.txt' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $generated) {
    throw "No baseline-prof.txt was produced under $outputRoot. Check the test output for a failed profile run."
}

$lines = (Get-Content -LiteralPath $generated.FullName | Where-Object { $_.Trim().Length -gt 0 }).Count
if ($lines -lt 100) {
    throw "The produced profile has only $lines entries, which means the run barely exercised the app. Do not commit it."
}

if ((Test-Path -LiteralPath $destination) -and $KeepPrevious) {
    Copy-Item -LiteralPath $destination -Destination "$destination.previous" -Force
}

Copy-Item -LiteralPath $generated.FullName -Destination $destination -Force
Write-Host "Baseline Profile written to $destination ($lines entries)."
Write-Host 'Verify it is packaged: ./gradlew :app:assembleDevRelease, then check assets/dexopt/baseline.prof inside the APK.'
Write-Host 'Measure before/after with the startup benchmark; never claim an improvement without the numbers.'