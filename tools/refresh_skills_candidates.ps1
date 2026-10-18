param(
    [Parameter(Mandatory = $true)][string]$Query,
    [ValidateRange(1, 20)][int]$Limit = 2,
    [string]$SnapshotPath = '../../catalog-candidates/skills-sh-candidates.json',
    [switch]$Public
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$coreApi = Join-Path $PSScriptRoot '../services/core-api'
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java.exe' }
$versionLine = (& $java -version 2>&1 | Select-Object -First 1).ToString()
if ($versionLine -match 'version "(?<major>\d+)') {
    $javaMajor = [int]$Matches.major
} else {
    throw 'Could not detect the local Java version.'
}

if ($javaMajor -lt 21) {
    $snapshot = [System.IO.Path]::GetFullPath((Join-Path $coreApi $SnapshotPath))
    $snapshotDirectory = Split-Path -Parent $snapshot
    New-Item -ItemType Directory -Force -Path $snapshotDirectory | Out-Null
    $containerSnapshot = '/snapshot/' + (Split-Path -Leaf $snapshot)
    Push-Location $repo
    try {
        & docker build -q -f infra/docker/core-api.Dockerfile --target builder -t agentfit-core-builder .
        if ($LASTEXITCODE -ne 0) { throw 'Java 21 Docker builder could not be built.' }
        $command = 'mvn -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/discovery-classpath.txt && java -cp "target/classes:$(cat target/discovery-classpath.txt)" com.agentfit.coreapi.catalog.discovery.SkillsShCandidateRefreshCommand "$1" "$2" "$3"'
        if ($Public) { $command += ' --public' }
        & docker run --rm -v "${snapshotDirectory}:/snapshot" -w /workspace/services/core-api `
            -e VERCEL_OIDC_TOKEN agentfit-core-builder sh -c $command -- `
            $Query $Limit.ToString() $containerSnapshot
        if ($LASTEXITCODE -ne 0) { throw 'Skill candidate refresh failed.' }
    } finally {
        Pop-Location
    }
    return
}

$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($maven) {
    $maven = $maven.Source
} else {
    $intellijMaven = 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.1.3\plugins\maven\lib\maven3\bin\mvn.cmd'
    if (-not (Test-Path -LiteralPath $intellijMaven)) {
        throw 'Maven executable not found. Put mvn.cmd on PATH.'
    }
    $maven = $intellijMaven
}
Push-Location $coreApi
try {
    & $maven -q '-DskipTests' compile dependency:build-classpath '-Dmdep.outputFile=target/discovery-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Maven could not prepare the discovery classpath.' }
    $dependencies = (Get-Content -LiteralPath 'target/discovery-classpath.txt' -Raw).Trim()
    if (-not $dependencies) { throw 'Discovery classpath is empty.' }
    $classPath = "target/classes;$dependencies"
    $commandArgs = @('-cp', $classPath,
        'com.agentfit.coreapi.catalog.discovery.SkillsShCandidateRefreshCommand',
        $Query, $Limit.ToString(), $SnapshotPath)
    if ($Public) { $commandArgs += '--public' }
    & $java @commandArgs
    if ($LASTEXITCODE -ne 0) { throw 'Skill candidate refresh failed.' }
} finally {
    Pop-Location
}
