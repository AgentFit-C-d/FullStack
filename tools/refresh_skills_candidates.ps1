param(
    [Parameter(Mandatory = $true)][string]$Query,
    [ValidateRange(1, 20)][int]$Limit = 2,
    [string]$SnapshotPath = '../../catalog-candidates/skills-sh-candidates.json',
    [switch]$Public
)

$ErrorActionPreference = 'Stop'
$coreApi = Join-Path $PSScriptRoot '../services/core-api'
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
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java.exe' }

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
