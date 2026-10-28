$serviceRoot = Split-Path -Parent $PSScriptRoot
$runner = Join-Path $PSScriptRoot 'live_analysis.py'
$python = Join-Path $serviceRoot '.venv\Scripts\python.exe'
$index = 0
foreach ($input in Get-ChildItem (Join-Path $PSScriptRoot 'documents') -Filter '*.pdf' | Sort-Object Name) {
    if ($input.Name -eq 'AgentFit.pdf') {
        Write-Output 'SKIPPED id=agentfit reason=result_exists'
        continue
    }
    $index += 1
    $id = 'doc-{0:D2}' -f $index
    $output = Join-Path $PSScriptRoot "pending\$id-document-parse.json"
    if (Test-Path $output) {
        Write-Output "SKIPPED id=$id reason=result_exists"
        continue
    }

    Write-Output "STARTED id=$id"
    & $python $runner --document $input.FullName --document-id $id --pipeline document-parse --output $output
    if ($LASTEXITCODE -eq 0) {
        Write-Output "SUCCEEDED id=$id"
    } else {
        Write-Output "FAILED id=$id exitCode=$LASTEXITCODE"
    }
}
