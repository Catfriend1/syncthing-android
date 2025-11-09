#!/usr/bin/env python3
"""
Recycle Workflow Runs Script

This script deletes old workflow runs for specified workflows in a GitHub repository.
"""

import os
import sys
import requests
import time
import json
from datetime import datetime, timezone, timedelta
from dateutil import parser
from typing import List, Dict, Any, Optional


class GitHubAPI:
    def __init__(self, token: str, repo: str):
        self.token = token
        self.repo = repo
        self.headers = {
            'Authorization': f'token {token}',
            'Accept': 'application/vnd.github.v3+json',
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.3485.81'
        }
        self.base_url = 'https://api.github.com'
        
    def _make_request(self, method: str, url: str, **kwargs) -> Optional[requests.Response]:
        """Make a request with rate limiting and error handling."""
        max_retries = 3
        retry_delay = 1
        
        for attempt in range(max_retries):
            try:
                response = requests.request(method, url, headers=self.headers, **kwargs)
                
                # Handle rate limiting
                if response.status_code == 429:
                    reset_time = int(response.headers.get('X-RateLimit-Reset', 0))
                    if reset_time:
                        wait_time = max(1, reset_time - int(time.time()) + 1)
                        print(f"⏳ Rate limit hit, waiting {wait_time} seconds...")
                        time.sleep(wait_time)
                        continue
                    else:
                        time.sleep(retry_delay * (2 ** attempt))
                        continue
                
                # Handle other errors
                if response.status_code >= 400:
                    print(f"❌ API request failed: {response.status_code} - {response.text}")
                    if attempt == max_retries - 1:
                        return None
                    time.sleep(retry_delay * (2 ** attempt))
                    continue
                
                return response
                
            except requests.RequestException as e:
                print(f"❌ Request exception: {e}")
                if attempt == max_retries - 1:
                    return None
                time.sleep(retry_delay * (2 ** attempt))
        
        return None
    
    def get_workflows(self) -> List[Dict[str, Any]]:
        """Get all workflows in the repository."""
        url = f'{self.base_url}/repos/{self.repo}/actions/workflows'
        response = self._make_request('GET', url)
        
        if not response:
            return []
            
        data = response.json()
        return data.get('workflows', [])
    
    def get_workflow_runs(self, workflow_id: int, per_page: int = 100) -> List[Dict[str, Any]]:
        """Get all workflow runs for a specific workflow with pagination."""
        all_runs = []
        page = 1
        
        while True:
            url = f'{self.base_url}/repos/{self.repo}/actions/workflows/{workflow_id}/runs'
            params = {
                'per_page': per_page,
                'page': page
            }
            
            response = self._make_request('GET', url, params=params)
            if not response:
                break
                
            data = response.json()
            runs = data.get('workflow_runs', [])
            
            if not runs:
                break
                
            all_runs.extend(runs)
            print(f"  📄 Fetched page {page} ({len(runs)} runs)")
            
            # Check if there are more pages
            if len(runs) < per_page:
                break
                
            page += 1
            
        return all_runs
    
    def delete_workflow_run(self, run_id: int) -> bool:
        """Delete a specific workflow run."""
        url = f'{self.base_url}/repos/{self.repo}/actions/runs/{run_id}'
        response = self._make_request('DELETE', url)
        return response is not None and response.status_code == 204


def parse_workflow_names(workflow_names_str: str) -> List[str]:
    """Parse comma-separated workflow names."""
    names = [name.strip() for name in workflow_names_str.split(',')]
    return [name for name in names if name]


def is_run_older_than_days(run_date_str: str, days: int) -> bool:
    """Check if a workflow run is older than the specified number of days."""
    try:
        run_date = parser.parse(run_date_str)
        cutoff_date = datetime.now(timezone.utc) - timedelta(days=days)
        return run_date < cutoff_date
    except Exception as e:
        print(f"❌ Error parsing date {run_date_str}: {e}")
        return False


def main():
    # Get environment variables with hardcoded defaults
    token = os.environ.get('GITHUB_TOKEN')
    repo = os.environ.get('REPO')
    
    # Hardcoded defaults - no longer dependent on workflow inputs
    workflow_names_str = os.environ.get('WORKFLOW_NAMES', 'Build App,Copilot,Copilot coding agent,Copilot Setup Steps,Dependabot Updates,Lock Threads,Recycle Runs,Release App,Update Go Version')
    days_to_keep_str = os.environ.get('DAYS_TO_KEEP', '14')
    dry_run_str = os.environ.get('DRY_RUN', 'false')
    
    # Handle days_to_keep with proper validation
    try:
        days_to_keep = int(days_to_keep_str) if days_to_keep_str.strip() else 14
        if days_to_keep < 1:
            days_to_keep = 14
    except (ValueError, AttributeError):
        print(f"⚠️  Invalid DAYS_TO_KEEP value '{days_to_keep_str}', using default: 14")
        days_to_keep = 14
    
    # Handle dry_run with proper validation
    dry_run = dry_run_str.lower() in ('true', '1', 'yes', 'on')
    
    if not token:
        print("❌ GITHUB_TOKEN environment variable is required")
        sys.exit(1)
        
    if not repo:
        print("❌ REPO environment variable is required")
        sys.exit(1)
    
    print(f"🚀 Starting workflow runs cleanup for repository: {repo}")
    print(f"📝 Target workflows: {workflow_names_str}")
    print(f"📅 Keeping runs newer than {days_to_keep} days")
    print(f"🔍 Dry run mode: {'enabled' if dry_run else 'disabled'}")
    print()
    
    # Parse workflow names
    workflow_names = parse_workflow_names(workflow_names_str)
    if not workflow_names:
        print("❌ No valid workflow names provided")
        sys.exit(1)
    
    # Initialize GitHub API client
    api = GitHubAPI(token, repo)
    
    # Get all workflows
    print("📋 Fetching workflows...")
    workflows = api.get_workflows()
    if not workflows:
        print("❌ Failed to fetch workflows or no workflows found")
        sys.exit(1)
    
    # Find target workflows by name
    target_workflows = []
    for name in workflow_names:
        workflow = next((w for w in workflows if w['name'] == name), None)
        if workflow:
            target_workflows.append(workflow)
            print(f"✅ Found workflow '{name}' (ID: {workflow['id']})")
        else:
            print(f"❌ Workflow '{name}' not found")
    
    if not target_workflows:
        print("❌ No valid workflows found to process")
        sys.exit(1)
    
    print()
    
    # Process each workflow
    total_deleted = 0
    total_skipped = 0
    total_would_delete = 0
    
    for workflow in target_workflows:
        workflow_name = workflow['name']
        workflow_id = workflow['id']
        
        print(f"🔄 Processing workflow '{workflow_name}' (ID: {workflow_id})")
        
        # Get all runs for this workflow
        runs = api.get_workflow_runs(workflow_id)
        if not runs:
            print(f"ℹ️  No runs found for workflow '{workflow_name}'")
            continue
        
        print(f"📊 Found {len(runs)} total runs")
        
        # Filter runs older than the specified number of days
        old_runs = []
        for run in runs:
            if is_run_older_than_days(run['created_at'], days_to_keep):
                old_runs.append(run)
        
        if not old_runs:
            print(f"ℹ️  No runs older than {days_to_keep} days found for '{workflow_name}'")
            total_skipped += len(runs)
            continue
        
        print(f"🗑️  Found {len(old_runs)} runs older than {days_to_keep} days")
        
        # Delete old runs
        deleted_count = 0
        for run in old_runs:
            run_id = run['id']
            run_date = run['created_at']
            run_status = run['status']
            run_conclusion = run['conclusion']
            
            if dry_run:
                print(f"  🔍 Would delete Run-ID {run_id} (created: {run_date}, status: {run_status}, conclusion: {run_conclusion})")
            else:
                print(f"  🗑️  Deleting Run-ID {run_id} (created: {run_date}, status: {run_status}, conclusion: {run_conclusion})...")
                
                if api.delete_workflow_run(run_id):
                    deleted_count += 1
                    # Small delay to avoid overwhelming the API
                    time.sleep(0.1)
                else:
                    print(f"    ❌ Failed to delete Run-ID {run_id}")
        
        if dry_run:
            print(f"  ✅ Dry run complete for '{workflow_name}': {len(old_runs)} runs would be deleted")
            total_would_delete += len(old_runs)
        else:
            print(f"  ✅ Completed '{workflow_name}': {deleted_count}/{len(old_runs)} runs deleted")
            total_deleted += deleted_count
        
        total_skipped += (len(runs) - len(old_runs))
        print()
    
    # Summary
    print("📈 Summary:")
    if dry_run:
        print(f"  🔍 Dry run mode: {total_would_delete} runs would be deleted")
    else:
        print(f"  🗑️  Total runs deleted: {total_deleted}")
    print(f"  ⏭️  Total runs skipped (newer than {days_to_keep} days): {total_skipped}")
    print(f"  🎯 Processed {len(target_workflows)} workflow(s)")
    
    print("\n✅ Workflow runs cleanup completed successfully!")


if __name__ == '__main__':
    main()
