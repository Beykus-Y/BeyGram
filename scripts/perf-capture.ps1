#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Captures one manual performance window on a connected device: frames, memory and CPU before/after a scenario.

.DESCRIPTION
    The script is read-only with respect to the app and its data. It resets gfxinfo, waits while the person performs
    the scenario (chat open, scroll, music + scroll, ...), then saves dumps and prints frame percentiles with the same
    parser the baseline report uses (scripts/perf_framestats.py).

    One run = one scenario. Never mix two scenarios (or two devices) in one directory; the protocol in
    docs/performance-2.0-audit.md requires equal conditions for "before" and "after".

.EXAMPLE
    ./scripts/perf-capture.ps1 -Label "chat-scroll-music" -Seconds 30
    ./scripts/perf-capture.ps1 -Package com.beykus.beygram.dev.benchmark -Label "cold-start" -Seconds 0
#>
[CmdletBinding()]
param(
    [string]$Package = 'com.beykus.beygram.dev',
    [string]$Label = 'run',
    [int]$Seconds = 30,
    [int]$RefreshHz = 60,
    [string]$OutDir,
    [string]$Adb = (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $OutDir) { $OutDir = Join-Path $projectRoot "perf/$Label" }
if (-not (Test-Path -LiteralPath $Adb)) { throw "adb not found: $Adb" }

$devices = @(& $Adb devices | Select-String -Pattern '\sdevice$' | ForEach-Object { ($_ -split '\s+')[0] })
if ($devices.Count -ne 1) { throw "Connect exactly one device (found: $($devices -join ', '))" }

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
Write-Host "Device: $($devices[0]); package: $Package; output: $OutDir"

# The conditions are part of the result: without them two runs cannot be compared honestly.
& $Adb shell getprop ro.product.model > "$OutDir/device.txt"
& $Adb shell getprop ro.build.fingerprint >> "$OutDir/device.txt"
& $Adb shell getprop ro.build.version.release >> "$OutDir/device.txt"
& $Adb shell wm size >> "$OutDir/device.txt"
& $Adb shell wm density >> "$OutDir/device.txt"
& $Adb shell settings get system font_scale >> "$OutDir/device.txt"
& $Adb shell dumpsys display | Select-String -Pattern 'mRefreshRate|fps' | Select-Object -First 5 >> "$OutDir/device.txt"
& $Adb shell dumpsys package $Package | Select-String -Pattern 'versionName|versionCode|primaryCpuAbi' >> "$OutDir/device.txt"

$appPid = (& $Adb shell pidof $Package).Trim()
if (-not $appPid) { throw "$Package is not running; open it and start the scenario again" }

& $Adb shell dumpsys gfxinfo $Package reset | Out-Null
& $Adb shell dumpsys meminfo $Package > "$OutDir/mem-before.txt"
& $Adb shell dumpsys cpuinfo > "$OutDir/cpu-before.txt"

if ($Seconds -gt 0) {
    Write-Host "Perform the scenario now: $Seconds seconds."
    Start-Sleep -Seconds $Seconds
}

& $Adb shell dumpsys gfxinfo $Package framestats > "$OutDir/gfxinfo-framestats.txt"
& $Adb shell dumpsys gfxinfo $Package > "$OutDir/gfxinfo-summary.txt"
& $Adb shell dumpsys meminfo $Package > "$OutDir/mem-after.txt"
& $Adb shell dumpsys cpuinfo > "$OutDir/cpu-after.txt"
& $Adb shell top -b -n 1 -d 1 -p $appPid > "$OutDir/top-process.txt"

Write-Host "Saved dumps to $OutDir"
python (Join-Path $PSScriptRoot 'perf_framestats.py') "$OutDir/gfxinfo-framestats.txt" --refresh-hz $RefreshHz --label $Label

Write-Host ''
Write-Host 'Compare mem-before.txt and mem-after.txt: Java Heap, Native Heap, PSS Total, RSS Total; threads and fds are' 
Write-Host 'in the same files. Put one row per scenario and mode into the report table (docs/performance-2.0-audit.md).'