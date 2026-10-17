$ErrorActionPreference = 'Stop'
$previousJava = $env:JAVA_HOME
$bundledJava = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
try {
    if (Test-Path (Join-Path $bundledJava 'bin\java.exe')) { $env:JAVA_HOME = $bundledJava }
    Push-Location $PSScriptRoot
    try {
        & .\gradlew.bat assembleDebug --console=plain --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "Android build failed ($LASTEXITCODE)." }
        New-Item -ItemType Directory -Force artifacts | Out-Null
        Copy-Item -LiteralPath app\build\outputs\apk\debug\app-debug.apk -Destination artifacts\DevSurf.apk -Force
        Write-Host 'Built artifacts\DevSurf.apk'
    } finally { Pop-Location }
} finally { $env:JAVA_HOME = $previousJava }
