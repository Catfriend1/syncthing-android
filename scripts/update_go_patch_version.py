#!/usr/bin/env python3
"""
Update the Go patch version in gradle/libs.versions.toml.

This script:
1. Parses the Syncthing submodule workflow to get the GO_VERSION base (e.g., ~1.25.0 -> 1.25)
2. Queries https://go.dev/dl/?mode=json to find the latest patch release for that base
3. Writes/updates the 'go_version' version in gradle/libs.versions.toml under [versions]

Usage:
    python3 scripts/update_go_patch_version.py            # Update with auto-detected version
    python3 scripts/update_go_patch_version.py --dry-run  # Show what would be written
    python3 scripts/update_go_patch_version.py --set 1.25.4  # Set specific version
"""

import argparse
import json
import os
import re
import sys
import tempfile
import urllib.request


def parse_go_version_from_workflow(workflow_path):
    """
    Parse GO_VERSION from the Syncthing workflow file.
    Converts ~1.25.0 to base version 1.25.
    """
    if not os.path.exists(workflow_path):
        raise FileNotFoundError(f"Workflow file not found: {workflow_path}")
    
    with open(workflow_path, 'r') as f:
        for line in f:
            if line.strip().startswith("GO_VERSION:"):
                raw = line.split(":", 1)[1].strip().strip('"').strip("'")
                # "~1.25.0" to "1.25"
                if raw.startswith("~"):
                    parts = raw[1:].split(".")
                    base_version = ".".join(parts[0:2])  # "1.25"
                else:
                    # If it's already x.y or x.y.z, extract x.y
                    parts = raw.split(".")
                    base_version = ".".join(parts[0:2])
                return base_version
    
    raise RuntimeError("Could not find GO_VERSION in build-syncthing.yaml")


def get_latest_patch_version(base_version):
    """
    Query go.dev to find the latest patch release for a base version.
    For example, base_version="1.25" might return "1.25.4".
    """
    url = "https://go.dev/dl/?mode=json"
    try:
        with urllib.request.urlopen(url, timeout=10) as resp:
            releases = json.load(resp)
    except Exception as e:
        raise RuntimeError(f"Failed to fetch Go releases from {url}: {e}")
    
    # Find the latest release matching base_version
    for rel in releases:
        v = rel["version"].lstrip("go")  # "go1.25.4" -> "1.25.4"
        if v.startswith(base_version + "."):
            return v
    
    raise RuntimeError(f"No latest patch version found for Go {base_version}")


def expand_version_to_patch(version):
    """
    If version is x.y, expand to x.y.z by querying go.dev.
    If version is already x.y.z, return as-is.
    """
    parts = version.split(".")
    if len(parts) == 2:
        # x.y -> query for latest patch
        return get_latest_patch_version(version)
    elif len(parts) == 3:
        # Already x.y.z
        return version
    else:
        raise ValueError(f"Invalid version format: {version}")


def read_toml_file(toml_path):
    """Read the TOML file and return lines."""
    if not os.path.exists(toml_path):
        raise FileNotFoundError(f"TOML file not found: {toml_path}")
    
    with open(toml_path, 'r') as f:
        return f.readlines()


def update_go_version_in_toml(lines, new_version):
    """
    Update or insert the 'go_version' version in the [versions] section.
    Returns updated lines.
    """
    new_line = f'go_version = "{new_version}"\n'
    updated_lines = []
    go_version_found = False
    
    for line in lines:
        # Simple lookup: find first line containing "go_version"
        if 'go_version' in line and '=' in line:
            updated_lines.append(new_line)
            go_version_found = True
        else:
            updated_lines.append(line)
    
    # If 'go_version' key was not found, insert it right after [versions]
    if not go_version_found:
        for i, line in enumerate(updated_lines):
            if line.strip() == "[versions]":
                updated_lines.insert(i + 1, new_line)
                break
        else:
            raise RuntimeError("[versions] section not found in TOML file")
    
    return updated_lines


def write_toml_file_atomic(toml_path, lines):
    """
    Write the TOML file atomically using a temporary file and rename.
    This prevents partial writes.
    """
    toml_dir = os.path.dirname(toml_path)
    
    # Create a temporary file in the same directory
    fd, temp_path = tempfile.mkstemp(dir=toml_dir, prefix='.tmp_', suffix='.toml')
    try:
        with os.fdopen(fd, 'w') as f:
            f.writelines(lines)
        
        # Atomic rename
        os.replace(temp_path, toml_path)
    except Exception as e:
        # Clean up temp file on error
        if os.path.exists(temp_path):
            os.unlink(temp_path)
        raise e


def main():
    parser = argparse.ArgumentParser(
        description="Update Go patch version in gradle/libs.versions.toml"
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Show what would be written without modifying files"
    )
    parser.add_argument(
        "--set",
        metavar="VERSION",
        help="Set a specific Go version (e.g., 1.25.4) instead of auto-detecting"
    )
    args = parser.parse_args()
    
    # Determine paths
    script_dir = os.path.dirname(os.path.abspath(__file__))
    repo_root = os.path.dirname(script_dir)
    workflow_path = os.path.join(
        repo_root,
        'syncthing',
        'src',
        'github.com',
        'syncthing',
        'syncthing',
        '.github',
        'workflows',
        'build-syncthing.yaml'
    )
    toml_path = os.path.join(repo_root, 'gradle', 'libs.versions.toml')
    
    # Determine the version to use
    if args.set:
        version = args.set
        print(f"Using manually specified version: {version}")
        # Validate and expand if needed
        version = expand_version_to_patch(version)
    else:
        # Auto-detect from workflow
        base_version = parse_go_version_from_workflow(workflow_path)
        print(f"Detected base Go version from workflow: {base_version}")
        version = get_latest_patch_version(base_version)
        print(f"Latest patch version for {base_version}: {version}")
    
    # Read current TOML
    lines = read_toml_file(toml_path)
    
    # Update the version
    updated_lines = update_go_version_in_toml(lines, version)
    
    if args.dry_run:
        print(f"\n[DRY RUN] Would update {toml_path} with:")
        print(f'go_version = "{version}"')
        print("\nNo files were modified.")
    else:
        # Write atomically
        write_toml_file_atomic(toml_path, updated_lines)
        print(f"\nSuccessfully updated {toml_path}")
        print(f'Set: go_version = "{version}"')


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)
