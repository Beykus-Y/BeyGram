#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Generates the BeyGram Baseline Profile on a connected physical device (#1119).

.DESCRIPTION
    Runs the profile-producing instrumentation test of :macrobenchmark against the release-like `benchmark` build type
    of :app, then copies the produced `baseline-prof.txt` into `app/src/main/`, where AGP packages it into the APK for
    `profileinstaller`.

    Only a file produced by *this* run of*this* variant is accepted: the script records the start time and rejects
    anything older, so a stale result or the profile of another flavour can never be copied. Nothing is written when the
    run produced no profile or too few entries: a hand-written or empty profile is worse than none.

    The benchmark build type has its own application id (`<package>.benchmark`), so the benchmark never installs over
    Dev or Stable and never touches the owner's session.

.EXAMPLE
    ./scripts/baseline-profile.ps1 -Flavor dev
    ./scripts/baseline-profile.ps1 -Flavor prod -Serial R58M1234567
    ./scripts/baseline-profile.ps1 -ApkPath app/build/outputs/apk/prodRelease/app-prod-release.apk   # only verify
#>
[CmdletBinding()]
param(
    [ValidateSet('dev', 'prod')]
    [string]$Flavor = 'dev',
    [string]$Serial,
    # Keep the previous profile as a sibling file instead of deleting it.
    [switch]$KeepPrevious,
    # Verify an existing release APK instead of generating a profile.
    [string]$ApkPath
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$appDir = Join-Path $projectRoot 'app'
$destination = Join-Path $appDir 'src/main/baseline-prof.txt'
$variant = "${Flavor}Benchmark"
$outputRoot = Join-Path $projectRoot "macrobenchmark/build/outputs/androidTest-results/connected"

function Assert-BaselineProfileInApk([string]$Path) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $entry = $zip.Entries | Where-Object { $_.FullName -eq 'assets/dexopt/baseline.prof' }
        if (-not $entry) { throw "No assets/dexopt/baseline.prof in $Path" }
        if ($entry.Length -le 0) { throw "assets/dexopt/baseline.prof in $Path is empty" }
        Write-Host "OK: baseline profile is packaged in $Path ($($entry.Length) bytes)."
    } finally {
        $zip.Dispose()
    }
}

if ($ApkPath) {
    if (-not (Test-Path -LiteralPath $ApkPath)) { throw "APK not found: $ApkPath" }
    Assert-BaselineProfileInApk (Resolve-Path $ApkPath).Path
    exit 0
}

$gradeFlavor = if ($Flavor -eq 'dev') { 'Dev' } else { 'Prod' }
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

# Anything older than this belongs to a previous run (possibly of another flavour) and must not be copied.
$startedAt = Get-Date
& $gradlew ":macrobenchmark:connected${gradeFlavor}BenchmarkAndroidTest" `
    "-Pandroid.testInstrumentationRunnerArguments.class=com.beykus.beygram.macrobenchmark.BaselineProfileGenerator" `
    '--console=plain'
if ($LASTEXITCODE -ne 0) { throw "Baseline Profile generation failed (gradle exit $LASTEXITCODE)." }

$generated = Get-ChildItem -Path $outputRoot -Recurse -Filter 'baseline-prof.txt' -ErrorAction SilentlyContinue |
    Where-Object { $_.LastWriteTime -gt $startedAt.AddMinutes(-1) } |
    Where-Object { $_.FullName -match [regex]::Escape($variant) } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $generated) {
    throw "No baseline-prof.txt for $variant was produced by this run under $outputRoot. Nothing was copied."
}

$lines = (Get-Content -LiteralPath $generated.FullName | Where-Object { $_.Trim().Length -gt 0 }).Count
if ($lines -lt 100) {
    throw "The produced profile has only $lines entries, which means the run barely exercised the app. Do not commit it."
}

if ((Test-Path -LiteralPath $destination) -and $KeepPrevious) {
    Copy-Item -LiteralPath $destination -Destination "$destination.previous" -Force
}

Copy-Item -LiteralPath $generated.FullName -Destination $destination -Force
Write-Host "Baseline Profile written to $destination ($lines entries, run started $($startedAt.ToString('s')))."
Write-Host "Verify it is packaged: ./scripts/baseline-profile.ps1 -ApkPath app/build/outputs/apk/${Flavor}Release/app-${Flavor}-release.apk"
Write-Host 'Measure before/after with the startup benchmark; never claim an improvement without the numbers.'