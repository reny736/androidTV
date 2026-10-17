param([switch]$Headless)
$ErrorActionPreference='Stop'
$taskRoot=$PSScriptRoot
$env:ANDROID_HOME="$taskRoot\.tools\android-sdk"
$env:ANDROID_USER_HOME="$taskRoot\.tools\android-home"
$env:ANDROID_AVD_HOME="$taskRoot\.tools\avd"
$adb="$env:ANDROID_HOME\platform-tools\adb.exe"
if (!(Test-Path "$env:ANDROID_AVD_HOME\TabletCast.avd")) { throw 'Run setup-emulator.ps1 first' }
$ready=& $adb devices
if (!($ready -match 'emulator-5554\s+device')) {
    $env:QT_OPENGL='desktop'
    $argsList=@('-avd','TabletCast','-port','5554','-gpu','host','-feature','-Vulkan','-no-snapshot','-no-boot-anim','-memory','2560','-cores','4','-netdelay','none','-netspeed','full')
    if($Headless){$argsList+=@('-no-window','-no-audio')}
    & "$env:ANDROID_HOME\emulator\emulator.exe" -accel-check *> "$taskRoot\.tools\accel-check.txt"
    if ($LASTEXITCODE -ne 0) { $argsList+=@('-accel','off') }
    $emulatorProcess=Start-Process -FilePath "$env:ANDROID_HOME\emulator\emulator.exe" -ArgumentList $argsList -WindowStyle Normal -RedirectStandardOutput "$taskRoot\.tools\emulator-out.log" -RedirectStandardError "$taskRoot\.tools\emulator-error.log" -PassThru
}
Write-Host 'Waiting for Android to finish booting...'
$booted=$false
for($i=0;$i -lt 240;$i++) {
    if($emulatorProcess -and $emulatorProcess.HasExited -and $emulatorProcess.ExitCode -ne 0) { throw 'Emulator exited; inspect .tools/emulator-error.log' }
    $online=& $adb devices
    if (!($online -match 'emulator-5554\s+device')) { Start-Sleep -Seconds 3;continue }
    $boot=& $adb -s emulator-5554 shell getprop sys.boot_completed 2>$null
    if ("$boot".Trim() -eq '1') { $booted=$true;break }
    Start-Sleep -Seconds 3
}
if (!$booted) { throw 'Emulator boot timed out; inspect .tools/emulator-error.log' }
& $adb -s emulator-5554 install -r "$taskRoot\dist\TabletCast-0.1.0.apk"
if ($LASTEXITCODE -ne 0) { throw 'APK install failed' }
& $adb -s emulator-5554 shell am start -n cn.tabletcast/.MainActivity
