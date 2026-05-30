param(
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"

$EnvDir = $PSScriptRoot
$RepoRoot = Split-Path $EnvDir -Parent
$ConfigDir = Join-Path $EnvDir "config"

function Write-Info([string]$Message) {
    if (-not $Quiet) {
        Write-Host $Message
    }
}

function Ensure-Dir([string]$Path) {
    if (-not (Test-Path $Path)) {
        New-Item -ItemType Directory -Force -Path $Path | Out-Null
    }
}

function Sync-ConfigFile([string]$RelativeSrc, [string]$RelativeDst) {
    $src = Join-Path $ConfigDir $RelativeSrc
    if (-not (Test-Path $src)) {
        Write-Info "skip (missing): $RelativeSrc"
        return
    }

    $dst = Join-Path $RepoRoot ($RelativeDst -replace '/', [IO.Path]::DirectorySeparatorChar)
    Ensure-Dir (Split-Path $dst -Parent)
    Copy-Item -Path $src -Destination $dst -Force
    Write-Info "sync: config/$RelativeSrc -> $RelativeDst"
}

$envTarget = Join-Path $EnvDir ".env"
$envOverride = Join-Path $ConfigDir ".env"
$envExample = Join-Path $ConfigDir ".env.example"

if (Test-Path $envOverride) {
    Copy-Item -Path $envOverride -Destination $envTarget -Force
    Write-Info "sync: config/.env -> env/.env"
}
elseif (-not (Test-Path $envTarget) -and (Test-Path $envExample)) {
    Copy-Item -Path $envExample -Destination $envTarget -Force
    Write-Info "init: config/.env.example -> env/.env"
}

Sync-ConfigFile "cat/client.xml" "env/cat/appdatas/client.xml"
Sync-ConfigFile "cat/client.xml" "kset-demo/env/cat/client.xml"
Sync-ConfigFile "cat/client.xml" "data/appdatas/cat/client.xml"
