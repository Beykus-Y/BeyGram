[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$TdLibOutput
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$tdlibRoot = (Resolve-Path $TdLibOutput).Path
$javaRoot = Join-Path $tdlibRoot 'java'
$libsRoot = Join-Path $tdlibRoot 'libs'
$appLibs = Join-Path $projectRoot 'app\libs'
$jniLibs = Join-Path $projectRoot 'app\src\main\jniLibs'

if (-not (Test-Path (Join-Path $javaRoot 'org\drinkless\tdlib\TdApi.java'))) {
    throw "TdApi.java is missing in '$javaRoot'. Build TDLib with the Java interface."
}
if (-not (Test-Path $libsRoot)) {
    throw "Native TDLib libraries are missing in '$libsRoot'."
}

$javaFiles = Get-ChildItem $javaRoot -Recurse -Filter '*.java'
if (-not $javaFiles) { throw 'No generated TDLib Java files were found.' }

New-Item -ItemType Directory -Force -Path $appLibs | Out-Null
$staging = Join-Path $env:TEMP 'beygram-tdlib-java'
Remove-Item -LiteralPath $staging -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $staging | Out-Null

$androidJar = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\platforms" -Recurse -Filter android.jar |
    Sort-Object FullName -Descending | Select-Object -First 1
if (-not $androidJar) { throw 'Android platform android.jar was not found.' }

$annotationJar = Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\androidx.annotation\annotation-jvm" -Recurse -Filter annotation-jvm-*.jar |
    Where-Object { $_.Name -notlike '*-sources.jar' } |
    Select-Object -First 1
if (-not $annotationJar) {
    throw 'AndroidX annotation JAR was not found. Run a Gradle build once to resolve androidx.annotation.'
}

$javac = Get-Command javac -ErrorAction SilentlyContinue
if (-not $javac) { throw 'JDK javac is required to package tdlib-java.jar.' }
& $javac.Source -encoding UTF-8 -classpath "$($androidJar.FullName);$($annotationJar.FullName)" -d $staging $javaFiles.FullName
if ($LASTEXITCODE -ne 0) { throw 'javac failed while compiling TDLib bindings.' }

$jar = Get-Command jar -ErrorAction SilentlyContinue
if (-not $jar) { throw 'JDK jar tool is required to package tdlib-java.jar.' }
& $jar.Source --create --file (Join-Path $appLibs 'tdlib-java.jar') -C $staging .
if ($LASTEXITCODE -ne 0) { throw 'jar failed while packaging TDLib bindings.' }

 $abis = Get-ChildItem $libsRoot -Directory | Where-Object {
    Test-Path (Join-Path $_.FullName 'libtdjni.so')
}
if (-not $abis) { throw 'No libtdjni.so files were found.' }

foreach ($abiDirectory in $abis) {
    $abi = $abiDirectory.Name
    $destination = Join-Path $jniLibs $abi
    New-Item -ItemType Directory -Force -Path $destination | Out-Null
    Copy-Item -Path (Join-Path $abiDirectory.FullName '*.so') -Destination $destination -Force
}

Write-Host 'TDLib Java bindings and native libraries were installed into the app module.'
