$ErrorActionPreference='Stop'
$taskRoot=$PSScriptRoot
$env:JAVA_HOME=(Get-ChildItem "$taskRoot\.tools\jdk" -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME="$taskRoot\.tools\android-sdk"
$env:ANDROID_USER_HOME="$taskRoot\.tools\android-home"
$env:ANDROID_AVD_HOME="$taskRoot\.tools\avd"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$sdkmanager="$taskRoot\.tools\sdk\cmdline-tools\bin\sdkmanager.bat"
& $sdkmanager "--sdk_root=$env:ANDROID_HOME" 'emulator' 'system-images;android-36;google_apis;x86_64'
if ($LASTEXITCODE -ne 0) { throw 'Emulator installation failed' }
New-Item -ItemType Directory -Force $env:ANDROID_AVD_HOME | Out-Null
if (!(Test-Path "$env:ANDROID_AVD_HOME\TabletCast.avd")) {
    'no' | & "$taskRoot\.tools\sdk\cmdline-tools\bin\avdmanager.bat" create avd -n TabletCast -k 'system-images;android-36;google_apis;x86_64' -p "$env:ANDROID_AVD_HOME\TabletCast.avd" -d 'pixel_tablet'
    if ($LASTEXITCODE -ne 0) { throw 'AVD creation failed' }
}
$configPath="$env:ANDROID_AVD_HOME\TabletCast.avd\config.ini"
$configText=Get-Content $configPath -Raw
foreach($setting in @{'hw.lcd.width'='1600';'hw.lcd.height'='1068';'hw.lcd.density'='240';'hw.initialOrientation'='landscape';'hw.ramSize'='2048';'showDeviceFrame'='no';'hw.gpu.enabled'='yes';'image.sysdir.1'="$env:ANDROID_HOME/system-images/android-36/google_apis/x86_64/"}.GetEnumerator()) {
    $pattern='(?m)^'+[regex]::Escape($setting.Key)+'=.*$'
    if ($configText -match $pattern) {$configText=[regex]::Replace($configText,$pattern,$setting.Key+'='+$setting.Value)}
    else {$configText+="`n"+$setting.Key+'='+$setting.Value}
}
$configText | Set-Content -Encoding ascii $configPath
& "$env:ANDROID_HOME\emulator\emulator.exe" -accel-check
