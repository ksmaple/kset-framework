param(
    [string]$NexusUrl = "http://192.168.53.5:8081",
    [string]$Username,
    [string]$Password,
    [string]$ReleaseRepository = "maven-releases",
    [string]$SnapshotRepository = "maven-snapshots",
    [switch]$SkipTests,
    [switch]$RunTests,
    [switch]$KeepSettings
)

$ErrorActionPreference = "Stop"

$ScriptDir = $PSScriptRoot
$EnvDir = Split-Path $ScriptDir -Parent
$RepoRoot = Split-Path $EnvDir -Parent
$SettingsFile = Join-Path ([IO.Path]::GetTempPath()) ("kset-nexus-settings-{0}.xml" -f ([Guid]::NewGuid().ToString("N")))

if (-not $Username) {
    $Username = "admin"
}

if (-not $Password) {
    $Password = "12345678"
}

function Escape-Xml([string]$Value) {
    return [System.Security.SecurityElement]::Escape($Value)
}

function Write-SettingsFile {
    $settings = @"
<?xml version="1.0" encoding="UTF-8"?>
<settings>
    <servers>
        <server>
            <id>kset-nexus-releases</id>
            <username>$(Escape-Xml $Username)</username>
            <password>$(Escape-Xml $Password)</password>
        </server>
        <server>
            <id>kset-nexus-snapshots</id>
            <username>$(Escape-Xml $Username)</username>
            <password>$(Escape-Xml $Password)</password>
        </server>
    </servers>
</settings>
"@

    Set-Content -LiteralPath $SettingsFile -Value $settings -Encoding UTF8
}

$mvnArgs = @(
    "-s", $SettingsFile,
    "clean", "deploy",
    "-Pnexus",
    "-Dkset.nexus.url=$NexusUrl",
    "-Dkset.nexus.release.repository=$ReleaseRepository",
    "-Dkset.nexus.snapshot.repository=$SnapshotRepository"
)

if (-not $RunTests) {
    $mvnArgs += @("-DskipTests", "-Dmaven.test.skip=true")
}

Push-Location $RepoRoot
try {
    Write-SettingsFile
    Write-Host "mvn $($mvnArgs -join ' ')"
    & mvn @mvnArgs
}
finally {
    Pop-Location
    if (-not $KeepSettings -and (Test-Path $SettingsFile)) {
        Remove-Item -LiteralPath $SettingsFile -Force
    }
}
