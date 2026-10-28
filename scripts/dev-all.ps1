[CmdletBinding()]
param(
    [ValidateSet('all', 'ai', 'core', 'web')]
    [string]$Service = 'all',
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot

function Show-Help {
    Write-Host 'Usage:'
    Write-Host '  npm run dev:all       Start AI, Core, and web in separate terminal windows.'
    Write-Host '  npm run dev:all:help  Show this help.'
    Write-Host ''
    Write-Host 'The runner reads configuration from the repository root .env file.'
}

function Import-EnvFile([string]$Path, [string[]]$OnlyKeys = @()) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "Environment file not found: $Path" }
    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith('#')) { continue }
        if ($trimmed.StartsWith('export ')) { $trimmed = $trimmed.Substring(7).Trim() }
        if ($trimmed -notmatch '^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') { continue }
        $name = $Matches[1]
        if ($OnlyKeys.Count -gt 0 -and $OnlyKeys -notcontains $name) { continue }
        $value = $Matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
}

if ($Help) { Show-Help; exit 0 }

Import-EnvFile (Join-Path $repoRoot '.env')
$dockerEnv = Join-Path $repoRoot '.env.docker'
if ([string]::IsNullOrWhiteSpace($env:UPSTAGE_API_KEY) -and (Test-Path -LiteralPath $dockerEnv)) {
    Import-EnvFile $dockerEnv @('UPSTAGE_API_KEY')
}

# This is a local-only development credential. A real deployment must provide it explicitly.
if ([string]::IsNullOrWhiteSpace($env:AGENTFIT_AI_SERVICE_TOKEN)) {
    $env:AGENTFIT_AI_SERVICE_TOKEN = 'local-ai-service-token-20261027-abcdef'
    Write-Host 'AGENTFIT_AI_SERVICE_TOKEN was not in .env; using the local development default.'
}

$required = @('AGENTFIT_DB_URL', 'AGENTFIT_DB_USERNAME', 'AGENTFIT_DB_PASSWORD', 'AGENTFIT_HMAC_CURRENT_KEY_ID', 'AGENTFIT_HMAC_CURRENT_SECRET')
foreach ($name in $required) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) { throw "$name is missing from .env" }
}

$env:SPRING_DATASOURCE_URL = $env:AGENTFIT_DB_URL
$env:SPRING_DATASOURCE_USERNAME = $env:AGENTFIT_DB_USERNAME
$env:SPRING_DATASOURCE_PASSWORD = $env:AGENTFIT_DB_PASSWORD
$env:AGENTFIT_AI_SERVICE_URL = if ($env:AGENTFIT_AI_SERVICE_URL) { $env:AGENTFIT_AI_SERVICE_URL } else { 'http://127.0.0.1:8001/internal/v1/analysis' }
$env:API_INTERNAL_URL = if ($env:API_INTERNAL_URL) { $env:API_INTERNAL_URL } else { 'http://127.0.0.1:8080' }
$env:AGENTFIT_COOKIE_SECURE = if ($env:AGENTFIT_COOKIE_SECURE) { $env:AGENTFIT_COOKIE_SECURE } else { 'false' }
$env:AI_SERVICE_TOKEN = $env:AGENTFIT_AI_SERVICE_TOKEN

$aiDir = Join-Path $repoRoot 'services\ai-service'
$coreDir = Join-Path $repoRoot 'services\core-api'
$webDir = Join-Path $repoRoot 'apps\web'
$python = Join-Path $aiDir '.venv\Scripts\python.exe'
$java21 = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot'

switch ($Service) {
    'ai' {
        if (-not (Test-Path -LiteralPath $python)) { throw "AI virtualenv not found: $python" }
        Set-Location $aiDir
        & $python -m uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload
    }
    'core' {
        if (Test-Path -LiteralPath (Join-Path $java21 'bin\java.exe')) {
            $env:JAVA_HOME = $java21
            $env:Path = "$java21\bin;$env:Path"
        }
        Set-Location $coreDir
        & mvn spring-boot:run
    }
    'web' {
        Set-Location $webDir
        & npm run dev
    }
    'all' {
        $runner = $PSCommandPath
        Start-Process powershell.exe -ArgumentList @('-NoExit', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $runner, '-Service', 'ai') -WorkingDirectory $repoRoot
        Start-Process powershell.exe -ArgumentList @('-NoExit', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $runner, '-Service', 'core') -WorkingDirectory $repoRoot
        Start-Process powershell.exe -ArgumentList @('-NoExit', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $runner, '-Service', 'web') -WorkingDirectory $repoRoot
        Write-Host 'Started AI, Core, and web terminal windows.'
    }
}
