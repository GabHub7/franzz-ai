$ErrorActionPreference = 'Stop'
$GradleVersion = '8.9'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Tools = Join-Path $Root '.tools'
$Dist = Join-Path $Tools "gradle-$GradleVersion"
$Gradle = Join-Path $Dist 'bin\gradle.bat'

if (-not (Test-Path $Gradle)) {
    New-Item -ItemType Directory -Path $Tools -Force | Out-Null
    $Zip = Join-Path $Tools "gradle-$GradleVersion-bin.zip"
    $ChecksumUrl = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip.sha256"
    $DownloadUrl = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"
    Write-Host "Mengunduh Gradle $GradleVersion..."
    Invoke-WebRequest -Uri $DownloadUrl -OutFile $Zip
    $Expected = (Invoke-RestMethod -Uri $ChecksumUrl).Trim().ToLowerInvariant()
    $Actual = (Get-FileHash -Path $Zip -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($Expected -ne $Actual) {
        Remove-Item $Zip -Force -ErrorAction SilentlyContinue
        throw 'Checksum Gradle tidak cocok; unduhan dihentikan.'
    }
    Expand-Archive -Path $Zip -DestinationPath $Tools -Force
    Remove-Item $Zip -Force
}
Push-Location $Root
try {
    & $Gradle @args
    $BuildExitCode = $LASTEXITCODE
} finally { Pop-Location }
exit $BuildExitCode
