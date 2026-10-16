param(
    [string]$Adb = 'C:\Users\Beykus\AppData\Local\Android\Sdk\platform-tools\adb.exe',
    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $Adb)) { throw "ADB not found: $Adb" }

$devices = @(& $Adb devices -l)
Write-Output '=== ADB devices ==='
$devices | Select-Object -Skip 1 | Where-Object { $_.Trim() }
$deviceLine = $devices | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice\s' } | Select-Object -First 1
if (-not $deviceLine) { throw 'No authorized ADB device is connected.' }

Write-Output "`n=== Android device ==="
& $Adb shell getprop ro.product.model
& $Adb shell getprop ro.build.version.release

Write-Output "`n=== BeyGram variants ==="
foreach ($packageName in @('com.beykus.beygram.dev', 'com.beykus.beygram')) {
    $apkPath = @(& $Adb shell pm path $packageName 2>$null)
    if ($apkPath -match '^package:') {
        $packageInfo = @(& $Adb shell dumpsys package $packageName 2>$null)
        $version = $packageInfo | Select-String 'versionName=|versionCode=|userId=' | Select-Object -First 3
        Write-Output "$packageName installed"
        $version | ForEach-Object { Write-Output $_.Line.Trim() }
    }
}

Write-Output "`n=== Android network and VPN ==="
$networkInfo = @(& $Adb shell dumpsys connectivity)
$activeNetwork = $networkInfo | Select-String 'Active default network:' | Select-Object -First 1
if ($activeNetwork) { Write-Output $activeNetwork.Line.Trim() }
$vpnEntry = $networkInfo | Select-String 'ni\{VPN (CONNECTED|CONNECTING|DISCONNECTED)' | Select-Object -First 1
if ($vpnEntry) {
    if ($vpnEntry.Line -match 'ni\{VPN ([A-Z]+) extra: VPN:([^}]+)') { Write-Output "VPN $($Matches[1].ToLower()): $($Matches[2])" }
    if ($vpnEntry.Line -match 'Capabilities: (.*?)(?: TransportInfo:| Uids:| OwnerUid:|\]})') { Write-Output "VPN capabilities: $($Matches[1])" }
    $vpnUidLine = $networkInfo | Select-String 'Uids: <\{([^}]*)\}' | Select-Object -First 1
    if ($vpnUidLine -and $vpnUidLine.Line -match 'Uids: <\{([^}]*)\}') {
        $vpnUidRanges = $Matches[1] -split ','
        $installedPackages = @(& $Adb shell pm list packages -U)
        foreach ($packageName in @('com.beykus.beygram.dev', 'com.beykus.beygram')) {
            $uidLine = $installedPackages | Select-String "^package:$([regex]::Escape($packageName)) uid:(\d+)$" | Select-Object -First 1
            if ($uidLine -and $uidLine.Line -match 'uid:(\d+)') {
                $appUid = [int]$Matches[1]
                $covered = $false
                foreach ($uidRange in $vpnUidRanges) {
                    if ($uidRange.Trim() -match '^(\d+)-(\d+)$' -and $appUid -ge [int]$Matches[1] -and $appUid -le [int]$Matches[2]) { $covered = $true }
                    elseif ($uidRange.Trim() -match '^\d+$' -and $appUid -eq [int]$uidRange.Trim()) { $covered = $true }
                }
                Write-Output "VPN UID coverage for $packageName ($appUid): $covered"
            }
        }
    } else { Write-Output 'Android did not expose VPN app UID ranges in connectivity diagnostics.' }
} else { Write-Output 'No active VPN network reported by Android.' }
$wifiEntry = $networkInfo | Select-String 'ni\{WIFI CONNECTED' | Select-Object -First 1
$cellEntry = $networkInfo | Select-String 'ni\{MOBILE.*CONNECTED' | Select-Object -First 1
if ($wifiEntry) { Write-Output 'Wi-Fi: connected' }
if ($cellEntry) { Write-Output 'Mobile data: connected' }

Write-Output "`n=== Background notifications ==="
foreach ($notificationPackage in @('com.beykus.beygram.dev', 'com.beykus.beygram')) {
    $notificationPackageInfo = @(& $Adb shell dumpsys package $notificationPackage 2>$null)
    $notificationPackageInfo | Select-String 'android.permission.POST_NOTIFICATIONS: granted=' | ForEach-Object { Write-Output "$notificationPackage $($_.Line.Trim())" }
    $notificationService = @(& $Adb shell dumpsys activity services "$notificationPackage/com.beykus.beygram.notifications.TelegramNotificationService" 2>$null)
    $notificationService | Select-String 'isForeground=|foregroundId=' | ForEach-Object { Write-Output "$notificationPackage $($_.Line.Trim())" }
}
Write-Output "`n=== Recent TDLib connection/notification diagnostics (redacted) ==="
$logLines = @(& $Adb logcat -d -t 2500 -s DLTD:W BeyGramNet:I BeyGramNotify:I)
$diagnostics = $logLines | Where-Object { $_ -match '(?i)connect|resolve|network|proxy|timeout|error|failed|refused|HTTPS|TCP|Default route|authorization|active groups|notification' } | Select-Object -Last 100
$diagnostics = $diagnostics | ForEach-Object {
    $_ -replace '(?i)(api_hash\s*=\s*)"[^"]*"', '$1"[redacted]"' `
       -replace '(?i)(phone(_number|Number)?\s*=\s*)"?[+\d()\-\s]{7,}"?', '$1"[redacted]"' `
       -replace '(?i)((password|secret)\s*=\s*)"[^"]*"', '$1"[redacted]"'
}
if ($diagnostics.Count -eq 0) { Write-Output 'No matching TDLib connection errors found in the current log buffer.' }
else { $diagnostics }

if ($OutputPath) {
    $networkSummary = @(
        'BeyGram Android diagnostics',
        "Captured: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')",
        "Device: $($deviceLine.Trim())",
        '', 'Network summary:',
        $(if ($activeNetwork) { $activeNetwork.Line.Trim() }),
        $(if ($vpnEntry -and $vpnEntry.Line -match 'ni\{VPN ([A-Z]+) extra: VPN:([^}]+)') { "VPN $($Matches[1].ToLower()): $($Matches[2])" } else { 'No active VPN network reported by Android.' }),
        $(if ($wifiEntry) { 'Wi-Fi: connected' }),
        $(if ($cellEntry) { 'Mobile data: connected' }),
        '', 'TDLib connection diagnostics:', ($diagnostics | Out-String)
    )
    $networkSummary | Set-Content -LiteralPath $OutputPath -Encoding UTF8
    Write-Output "`nRedacted report saved to $OutputPath"
}
