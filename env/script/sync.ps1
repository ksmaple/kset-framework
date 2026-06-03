param(
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"

$ScriptDir = $PSScriptRoot
$EnvDir = Split-Path $ScriptDir -Parent
$RepoRoot = Split-Path $EnvDir -Parent

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

function Resolve-TargetPath([string]$Target) {
    $normalized = $Target -replace '/', [IO.Path]::DirectorySeparatorChar
    if ([IO.Path]::IsPathRooted($normalized)) {
        return $normalized
    }
    return Join-Path $RepoRoot $normalized
}

function Sync-ConfigFile([string]$Source, [string]$Target) {
    $src = Join-Path $RepoRoot ($Source -replace '/', [IO.Path]::DirectorySeparatorChar)
    if (-not (Test-Path $src)) {
        Write-Info "skip (missing): $Source"
        return
    }

    $dst = Resolve-TargetPath $Target
    Ensure-Dir (Split-Path $dst -Parent)
    Copy-Item -Path $src -Destination $dst -Force
    Write-Info "sync: $Source -> $Target"
}

$envTarget = Join-Path $EnvDir ".env"
$envExample = Join-Path $EnvDir ".env.example"

if (-not (Test-Path $envTarget) -and (Test-Path $envExample)) {
    Copy-Item -Path $envExample -Destination $envTarget -Force
    Write-Info "init: env/.env.example -> env/.env"
}

Sync-ConfigFile "env/cat/client/client.xml" "/data/appdatas/cat/client.xml"
