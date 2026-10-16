param(
    [string]$Serial = 'R58NB4AXMPZ',
    [string]$Adb = 'C:\Users\Beykus\AppData\Local\Android\Sdk\platform-tools\adb.exe',
    [string]$Apk = (Join-Path $PSScriptRoot '..\app\build\outputs\apk\dev\debug\app-dev-debug.apk')
)

$ErrorActionPreference = 'Stop'
$package = 'com.beykus.beygram.dev'
if (-not (Test-Path -LiteralPath $Apk -PathType Leaf)) { throw "APK not found: $Apk" }
if (-not (Test-Path -LiteralPath $Adb -PathType Leaf)) { throw "ADB not found: $Adb" }

# This workflow only replaces an existing installation; it never removes app data.
$installed = & $Adb -s $Serial shell pm path $package
if ($LASTEXITCODE -ne 0 -or -not ($installed -match '^package:')) {
    throw 'Dev is not installed on the selected phone. This update script does not perform clean installations.'
}
$before = & $Adb -s $Serial shell dumpsys package $package
if ($LASTEXITCODE -ne 0) { throw 'Cannot read package metadata before updating.' }
$firstInstall = @($before | Select-String 'firstInstallTime=' | ForEach-Object { $_.Line.Trim() })
if ($firstInstall.Count -ne 1) { throw 'Cannot establish original installation time; update stopped.' }

& $Adb -s $Serial install -r $Apk
if ($LASTEXITCODE -ne 0) { throw 'Replacement installation failed. No uninstall or data reset will be attempted.' }

$after = & $Adb -s $Serial shell dumpsys package $package
if ($LASTEXITCODE -ne 0) { throw 'Cannot read package metadata after updating.' }
$afterFirstInstall = @($after | Select-String 'firstInstallTime=' | ForEach-Object { $_.Line.Trim() })
if ($afterFirstInstall.Count -ne 1 -or $afterFirstInstall[0] -ne $firstInstall[0]) {
    throw 'Installation identity could not be verified. Stop and inspect the device; do not clear data.'
}
Write-Output "Replacement update complete; original $($firstInstall[0]) preserved."
& $Adb -s $Serial shell am start -W -n "$package/com.beykus.beygram.MainActivity"
if ($LASTEXITCODE -ne 0) { throw 'Updated APK was installed, but starting the activity failed.' }
Write-Output 'Check AuthorizationStateReady or the account screen before claiming that the session survived.'
