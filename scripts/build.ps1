param([string[]]$Tasks = @('testDebugUnitTest', 'lintDebug', 'assembleDebug', 'assembleDebugAndroidTest'), [switch]$Stacktrace, [switch]$Clean)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$jdkPath = Get-ChildItem -LiteralPath "$projectRoot\.tools" -Directory -Filter 'jdk-17*' | Select-Object -First 1
if ($jdkPath) { $env:JAVA_HOME = $jdkPath.FullName; $env:Path = "$env:JAVA_HOME\bin;$env:Path" }
$env:GRADLE_USER_HOME = "$projectRoot\.tools\gradle-home"
$env:ANDROID_HOME = "$projectRoot\.tools\android-sdk"
$env:DEBUG = ''
Push-Location $projectRoot
try {
    $options = @('--console=plain', '--no-daemon')
    if ($Clean) { $Tasks = @('clean') + $Tasks }
    if ($Stacktrace) { $options += '--stacktrace' }
    if (Test-Path -LiteralPath '.\.tools\gradle-8.13\bin\gradle.bat') { & '.\.tools\gradle-8.13\bin\gradle.bat' @Tasks @options }
    else { & .\gradlew.bat @Tasks @options }
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
} finally { Pop-Location }
