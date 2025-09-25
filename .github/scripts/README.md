# Recycle Workflow Runs

This GitHub Action automates the cleanup of old workflow runs in the repository. It replaces the manual PowerShell script with an automated solution that can handle multiple workflows and provides better filtering capabilities.

## Features

- **Multiple workflows**: Clean up runs from multiple workflows in a single execution
- **Age-based filtering**: Only delete runs older than a configurable number of days (default: 14 days)
- **Pagination support**: Handles repositories with large numbers of workflow runs
- **Rate limiting**: Automatically handles GitHub API rate limits
- **Dry-run mode**: Test the cleanup process without actually deleting runs
- **Detailed logging**: Provides clear feedback on what is being processed and deleted

## Usage

### Manual Trigger

The workflow can be triggered manually through the GitHub Actions UI:

1. Go to **Actions** > **Recycle runs**
2. Click **Run workflow**
3. Configure the parameters:
   - **workflow_names**: Comma-separated list of workflow names (e.g., "Build App,CodeQL Advanced,Copilot")
   - **days_to_keep**: Number of days to keep runs (default: 14)
   - **dry_run**: Enable to see what would be deleted without actually deleting (default: false)

### Workflow Parameters

| Parameter | Description | Default | Required |
|-----------|-------------|---------|----------|
| `workflow_names` | Comma-separated list of workflow names to clean up | `Build App` | Yes |
| `days_to_keep` | Number of days to keep runs (older runs will be deleted) | `14` | No |
| `dry_run` | Only show what would be deleted without actually deleting | `false` | No |

## Examples

### Clean up multiple workflows
```
workflow_names: "Build App,CodeQL Advanced,Copilot,Lock Threads"
days_to_keep: 14
dry_run: false
```

### Test cleanup for a single workflow
```
workflow_names: "Build App"
days_to_keep: 7
dry_run: true
```

## Improvements over the PowerShell Script

The original PowerShell script (`scripts/debug/win/gh-workflow-runs-delete.ps1`) had limitations:

1. **Single workflow**: Only processed one workflow at a time
2. **No age filtering**: Deleted ALL runs regardless of age
3. **Manual execution**: Required manual script execution for each workflow
4. **Windows dependency**: Required PowerShell and Windows environment

This GitHub Action addresses all these limitations:

1. **Multiple workflows**: Process multiple workflows in one execution
2. **Smart filtering**: Only delete runs older than the specified number of days
3. **Automated**: Can be scheduled or triggered automatically
4. **Cross-platform**: Runs on Ubuntu using Python

## Error Handling

The action includes robust error handling:

- **API rate limiting**: Automatically waits and retries when rate limits are hit
- **Network errors**: Retries failed requests with exponential backoff
- **Invalid workflows**: Reports missing workflows and continues with valid ones
- **Permission errors**: Provides clear error messages for permission issues

## Security

The action uses the `GITHUB_TOKEN` provided by GitHub Actions, which has appropriate permissions to:
- Read workflow information
- Delete workflow runs

No additional tokens or permissions are required.