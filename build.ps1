$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$root = $PSScriptRoot
$gradleHome = Join-Path $root '.gradle-dist'
$gradleBin = Join-Path $gradleHome 'gradle-8.8\bin\gradle.bat'

Write-Host '=== Concert Stage : build Forge + Fabric 1.20.1 ===' -ForegroundColor Cyan

# 1. Java 17
$v = cmd /c "java -version 2>&1" | Out-String
$compiler = Get-Command javac -ErrorAction SilentlyContinue
if ($v -notmatch 'version "17' -or -not $compiler) {
    throw 'Installe un JDK 17 (avec javac), puis relance build.bat.'
}

# 2. Gradle 8.8 (telecharge dans le dossier du projet)
if (-not (Test-Path $gradleBin)) {
    Write-Host 'Telechargement de Gradle 8.8...'
    $zip = Join-Path $env:TEMP 'gradle-8.8-bin.zip'
    Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.8-bin.zip' -OutFile $zip
    Expand-Archive $zip -DestinationPath $gradleHome -Force
}

# 3. Build Forge puis Fabric (un journal par loader : build-log-forge.txt / build-log-fabric.txt)
$out = Join-Path $root 'jars'
New-Item -ItemType Directory -Force -Path $out | Out-Null

foreach ($loader in 'forge', 'fabric') {
    Write-Host "=== Compilation $loader (le premier build est long) ===" -ForegroundColor Cyan
    Push-Location (Join-Path $root $loader)
    try {
        & $gradleBin wrapper --gradle-version 8.8
        if ($LASTEXITCODE -ne 0) { throw "Echec du wrapper ($loader)" }
        $log = Join-Path $root "build-log-$loader.txt"
        # cmd fait la redirection : PowerShell ne transforme pas les lignes d'erreur de javac en exceptions (ce qui coupait le journal)
        cmd /c "gradlew.bat build --console=plain > `"$log`" 2>&1"
        $code = $LASTEXITCODE
        Get-Content $log | Select-Object -Last 40
        if ($code -ne 0) {
            Write-Host ''
            Write-Host "Erreurs du compilateur (copie complete dans build-log-$loader.txt) :" -ForegroundColor Yellow
            Select-String -Path $log -Pattern 'error:|erreur' -Context 0,3 | ForEach-Object { $_.ToString() }
            throw "Echec de la compilation ($loader)"
        }
        # Fabric : le jar remappe (pas le -dev ni les sources)
        Get-ChildItem 'build\libs\*.jar' | Where-Object { $_.Name -notlike '*-sources*' -and $_.Name -notlike '*-dev*' } |
            ForEach-Object { Copy-Item $_.FullName $out -Force }
    } finally {
        Pop-Location
    }
}

Write-Host ''
Write-Host "Termine ! Tes .jar sont dans : $out" -ForegroundColor Green
Get-ChildItem $out
