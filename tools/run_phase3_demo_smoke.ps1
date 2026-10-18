param(
    [Parameter(Mandatory = $true)][string]$EnvFile
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$environmentFile = (Resolve-Path -LiteralPath $EnvFile).Path
$compose = Join-Path $repo 'infra/compose.fullstack.yaml'
$demoCompose = Join-Path $repo 'infra/compose.demo.yaml'
$catalog = Join-Path $repo 'demo/catalog-synthetic'
$candidateSnapshot = Join-Path $repo 'catalog-candidates/skills-sh-candidates.json'
if (-not (Test-Path -LiteralPath $candidateSnapshot -PathType Leaf)) {
    throw 'Refresh skills.sh candidates before running the demo smoke.'
}

Push-Location $repo
try {
    & docker compose -p infra --env-file $environmentFile -f $compose -f $demoCompose up -d --build
    if ($LASTEXITCODE -ne 0) { throw 'Demo services did not start.' }

    & docker build -q -f infra/docker/core-api.Dockerfile --target builder -t agentfit-core-builder .
    if ($LASTEXITCODE -ne 0) { throw 'Java 21 demo test image did not build.' }

    $testCommand = 'export AGENTFIT_TEST_POSTGRES_PASSWORD="$AGENTFIT_LOCAL_DB_PASSWORD"; mvn -q -Dtest=LiveAuthenticatedRecommendationHttpTest test'
    & docker run --rm --network infra_default --env-file $environmentFile `
        -e AGENTFIT_LIVE_AI_URL=http://ai-service:8001 `
        -e AGENTFIT_TEST_POSTGRES_URL=jdbc:postgresql://postgres:5432/agentfit `
        -v "${catalog}:/workspace/demo/catalog-synthetic:ro" `
        -v "${repo}/catalog-candidates:/workspace/catalog-candidates:ro" `
        -w /workspace/services/core-api agentfit-core-builder sh -c $testCommand
    if ($LASTEXITCODE -ne 0) { throw 'Authenticated recommendation-to-ZIP demo smoke failed.' }
    Write-Output 'DEMO_BACKEND_FLOW_PASS'
} finally {
    Pop-Location
}
