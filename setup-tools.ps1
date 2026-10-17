$ErrorActionPreference = 'Stop'
$taskRoot = $PSScriptRoot
$toolRoot = Join-Path $taskRoot '.tools'
New-Item -ItemType Directory -Force $toolRoot | Out-Null
$downloads = @(
    @{Name='jdk'; Url='https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip'},
    @{Name='gradle'; Url='https://services.gradle.org/distributions/gradle-8.13-bin.zip'},
    @{Name='sdk'; Url='https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip'}
)
$jobs = foreach ($item in $downloads) {
    Start-Job -ArgumentList $item,$toolRoot -ScriptBlock {
        param($item,$toolRoot)
        $ErrorActionPreference='Stop'
        $zip=Join-Path $toolRoot ($item.Name+'.zip')
        $dest=Join-Path $toolRoot $item.Name
        if (!(Test-Path $dest)) {
            Invoke-WebRequest -Uri $item.Url -OutFile $zip
            Expand-Archive -LiteralPath $zip -DestinationPath $dest -Force
        }
        Write-Output ($item.Name+' ready')
    }
}
$jobs | Wait-Job | Receive-Job
if ($jobs.State -contains 'Failed') { throw 'Tool download failed' }
