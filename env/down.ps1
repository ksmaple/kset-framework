param(
    [switch]$NoCat,
    [switch]$Volumes
)

$ErrorActionPreference = "Stop"

$EnvDir = $PSScriptRoot
Set-Location $EnvDir

$envFile = Join-Path $EnvDir ".env"
if (Test-Path $envFile) {
    $envArgs = @("--env-file", ".env")
}
else {
    $envArgs = @()
}

$composeBase = @("compose") + $envArgs + @("-f", "docker-compose.yml")
if (-not $NoCat) {
    $composeBase += @("-f", "cat/docker-compose.yml")
}

$downArgs = $composeBase + @("down")
if ($Volumes) {
    $downArgs += "-v"
}

Write-Host "docker $($downArgs -join ' ')"
& docker @downArgs
