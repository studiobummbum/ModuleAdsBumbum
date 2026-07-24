# Deploy app-demo debug to the first connected emulator/device and launch Splash.
$ErrorActionPreference = "Stop"

$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"

Set-Location (Split-Path -Parent $PSScriptRoot)

$devices = adb devices | Select-String "`tdevice$"
if (-not $devices) {
    Write-Error "No emulator/device online. Start an AVD or plug in a phone with USB debugging."
}

Write-Host "Installing :app-demo:debug ..."
.\gradlew :app-demo:installDebug
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Launching SplashActivity ..."
adb shell am force-stop com.example.adsdemo
adb shell am start -n com.example.adsdemo/.splash.SplashActivity
Write-Host "Done."
