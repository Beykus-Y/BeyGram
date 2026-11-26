[CmdletBinding()]
param(
    # Machine-readable record produced by docs/tdlib-reproducibility.md.
    # Defaults to docs/tdlib-artifacts.json next to this script's parent directory.
    [string]$Record,
    # Print detailed evidence even when every check passes.
    [switch]$Verbose_,
    # Also assert that the shipped native library still contains the strings the record relies on.
    [switch]$InspectBinaries
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $Record) { $Record = Join-Path $projectRoot 'docs\tdlib-artifacts.json' }

if (-not (Test-Path -LiteralPath $Record)) {
    throw "TDLib artifact record not found: $Record"
}

$spec = Get-Content -LiteralPath $Record -Raw | ConvertFrom-Json
$failures = [System.Collections.Generic.List[string]]::new()
$checks = [System.Collections.Generic.List[object]]::new()

function Add-Check {
    param([string]$Name, [bool]$Ok, [string]$Detail)
    $checks.Add([pscustomobject]@{ Check = $Name; Result = $(if ($Ok) { 'PASS' } else { 'FAIL' }); Detail = $Detail })
    if (-not $Ok) { $failures.Add("$Name - $Detail") }
}

# 1. Every recorded artifact must exist with the recorded SHA-256 and size.
foreach ($artifact in $spec.artifacts) {
    $path = Join-Path $projectRoot ($artifact.path -replace '/', '\')
    $label = $artifact.path
    if (-not (Test-Path -LiteralPath $path)) {
        Add-Check "present: $label" $false 'file is missing'
        continue
    }

    $item = Get-Item -LiteralPath $path
    Add-Check "size: $label" ($item.Length -eq [int64]$artifact.size) `
        "expected $($artifact.size), found $($item.Length)"

    $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    Add-Check "sha256: $label" ($hash -eq $artifact.sha256) `
        "expected $($artifact.sha256), found $hash"
}

# 2. The packaged ABI set must still be the recorded one.
$jniRoot = Join-Path $projectRoot 'app\src\main\jniLibs'
$packaged = @()
if (Test-Path -LiteralPath $jniRoot) {
    $packaged = Get-ChildItem -LiteralPath $jniRoot -Directory |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'libtdjni.so') } |
        Select-Object -ExpandProperty Name | Sort-Object
}
$expectedAbis = @($spec.packagedAbis | Sort-Object)
Add-Check 'abis present' (($packaged -join ',') -eq ($expectedAbis -join ',')) `
    "expected $($expectedAbis -join ','), found $($packaged -join ',')"

if ($InspectBinaries) {
    $so = Get-ChildItem -LiteralPath $jniRoot -Recurse -Filter 'libtdjni.so' | Select-Object -First 1
    if (-not $so) {
        Add-Check 'binary inspection' $false 'no libtdjni.so to inspect'
    } else {
        # ASCII strings embedded in the ELF: the hard-coded version literal, the
        # generated GitCommitHash.cpp value, and the OpenSSL build string.
        $bytes = [System.IO.File]::ReadAllBytes($so.FullName)
        $text = [System.Text.Encoding]::ASCII.GetString($bytes)

        Add-Check 'version literal in native library' $text.Contains($spec.tdlib.version) `
            "expected the string '$($spec.tdlib.version)'"
        Add-Check 'commit hash placeholder in native library' $text.Contains($spec.tdlib.commitHash) `
            "expected the string '$($spec.tdlib.commitHash)'"

        $ndkFound = $text.Contains($spec.toolchain.ndkRelease) -and $text.Contains('Android')
        Add-Check 'NDK release marker in native library' $ndkFound `
            "expected '$($spec.toolchain.ndkRelease)' in .note.android.ident"

        $opensslNeedle = 'OpenSSL ' + $spec.openssl.version
        Add-Check 'OpenSSL version in native library' $text.Contains($opensslNeedle) `
            "expected '$opensslNeedle'"

        if ($Verbose_) {
            $offset = $text.IndexOf($spec.tdlib.commitHash)
            if ($offset -ge 0) {
                Write-Host ("commit hash placeholder at byte offset 0x{0:x}" -f $offset)
            }
        }
    }
}

$checks | Format-Table -AutoSize -Wrap | Out-String | Write-Host

if ($failures.Count -gt 0) {
    Write-Host "TDLib artifacts do NOT match docs/tdlib-artifacts.json ($($failures.Count) failure(s)):" -ForegroundColor Red
    $failures | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
    Write-Host 'Re-read docs/tdlib-reproducibility.md before changing the recorded values: a rebuild must be intentional and paired with an app-wide check.'
    exit 1
}

Write-Host 'TDLib artifacts match docs/tdlib-artifacts.json.'
exit 0