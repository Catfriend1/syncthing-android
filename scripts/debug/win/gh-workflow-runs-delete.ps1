# Prerequiste
## winget install --accept-source-agreements --source winget --exact --id GitHub.cli --scope machine

param (
    [string]$Repo = "Catfriend1/syncthing-android",

    [string]$WorkflowName = "Build App"
    # [string]$WorkflowName = "CodeQL Advanced"
    # [string]$WorkflowName = "Copilot"
    # [string]$WorkflowName = "Copilot Setup Steps"
    # [string]$WorkflowName = "Dependabot Updates"
    # [string]$WorkflowName = "Lock Threads"
)

# Fetch workflow id.
$workflowList = gh api "repos/$Repo/actions/workflows" | ConvertFrom-Json
$workflow = $workflowList.workflows | Where-Object { $_.name -eq $WorkflowName }

if (-not $workflow) {
    Write-Host "❌ Workflow '$WorkflowName' not found in repo '$Repo'."
    exit 1
}

$workflowId = $workflow.id
Write-Host "✅ Workflow-ID for '$WorkflowName': $workflowId"

# Fetch all workflow runs.
$runList = gh api "repos/$Repo/actions/workflows/$workflowId/runs" | ConvertFrom-Json
$runs = $runList.workflow_runs

if (-not $runs) {
    Write-Host "ℹ️ No runs found for workflow '$WorkflowName'."
    exit 0
}

# Delete all workflow runs.
foreach ($run in $runs) {
    Write-Host "🗑️ Delete Run-ID $($run.id)..."
    gh api -X DELETE "repos/$Repo/actions/runs/$($run.id)"
}

Write-Host "✅ All runs deleted."
