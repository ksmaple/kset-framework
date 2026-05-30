param(
    [switch]$Build,
    [switch]$NoCat
)

$ErrorActionPreference = "Stop"

$EnvDir = $PSScriptRoot
Set-Location $EnvDir

& (Join-Path $EnvDir "sync-config.ps1")

$envFile = Join-Path $EnvDir ".env"
if (-not (Test-Path $envFile)) {
    throw "Missing env/.env. Run: Copy-Item env/config/.env.example env/config/.env"
}

$composeBase = @("compose", "--env-file", ".env", "-f", "docker-compose.yml")
if (-not $NoCat) {
    $composeBase += @("-f", "cat/docker-compose.yml")
}

$upArgs = $composeBase + @("up", "-d")
if ($Build) {
    $upArgs += "--build"
}

Write-Host "docker $($upArgs -join ' ')"
& docker @upArgs

& docker @($composeBase + @("ps"))
