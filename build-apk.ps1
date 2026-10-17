$ErrorActionPreference='Stop'
$taskRoot=$PSScriptRoot
$env:JAVA_HOME=(Get-ChildItem "$taskRoot\.tools\jdk" -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME="$taskRoot\.tools\android-sdk"
$env:GRADLE_USER_HOME="$taskRoot\.tools\gradle-home"
$env:ANDROID_USER_HOME="$taskRoot\.tools\android-home"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$sdkmanager="$taskRoot\.tools\sdk\cmdline-tools\bin\sdkmanager.bat"
if (!(Test-Path "$env:ANDROID_HOME\platforms\android-35\android.jar")) {
    1..100 | ForEach-Object { 'y' } | & $sdkmanager "--sdk_root=$env:ANDROID_HOME" --licenses
    & $sdkmanager "--sdk_root=$env:ANDROID_HOME" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
    if ($LASTEXITCODE -ne 0) { throw 'SDK installation failed' }
}
$sdkPath=$env:ANDROID_HOME.Replace('\','/')
"sdk.dir=$sdkPath" | Set-Content -Encoding ascii "$taskRoot\local.properties"
$gradle="$taskRoot\.tools\gradle\gradle-8.13\bin\gradle.bat"
& $gradle --no-daemon assembleDebug testDebugUnitTest lintDebug
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
New-Item -ItemType Directory -Force "$taskRoot\dist" | Out-Null
Copy-Item "$taskRoot\app\build\outputs\apk\debug\app-debug.apk" "$taskRoot\dist\TabletCast-0.1.0.apk" -Force
& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --verbose "$taskRoot\dist\TabletCast-0.1.0.apk"
