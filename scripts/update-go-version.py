#!/usr/bin/env python3
"""
Update the Go version in gradle/libs.versions.toml.

Auto-detects the base version from the Syncthing workflow and 
updates to the latest patch release.

Usage:
    python3 scripts/update-go-version.py
    python scripts/update-go-version.py
"""

import json
import os
import sys
import urllib.request


def get_latest_patch_version(base_version):
    """Query go.dev for latest patch. E.g., "1.25" -> "1.25.4"."""
    url = "https://go.dev/dl/?mode=json"
    with urllib.request.urlopen(url, timeout=10) as resp:
        releases = json.load(resp)
    
    for rel in releases:
        v = rel["version"].lstrip("go")
        if v.startswith(base_version + "."):
            return v
    
    raise RuntimeError(f"No patch version found for Go {base_version}")


def parse_workflow_version():
    """Parse GO_VERSION from Syncthing workflow. ~1.25.0 -> 1.25"""
    script_dir = os.path.dirname(os.path.abspath(__file__))
    workflow_path = os.path.join(
        os.path.dirname(script_dir),
        'syncthing/src/github.com/syncthing/syncthing/.github/workflows/build-syncthing.yaml'
    )
    
    with open(workflow_path, 'r') as f:
        for line in f:
            if 'GO_VERSION:' in line:
                raw = line.split(":", 1)[1].strip().strip('"').strip("'")
                if raw.startswith("~"):
                    raw = raw[1:]
                parts = raw.split(".")
                return ".".join(parts[0:2])
    
    raise RuntimeError("Could not find GO_VERSION in workflow")


def main():
    base = parse_workflow_version()
    print(f"Detected base version: {base}")
    
    version = get_latest_patch_version(base)
    print(f"Setting Go version to: {version}")
    
    script_dir = os.path.dirname(os.path.abspath(__file__))
    toml_path = os.path.join(os.path.dirname(script_dir), 'gradle/libs.versions.toml')
    
    with open(toml_path, 'r') as f:
        lines = f.readlines()
    
    with open(toml_path, 'w') as f:
        for line in lines:
            if 'go_version' in line and '=' in line:
                f.write(f'go_version = "{version}"\n')
            else:
                f.write(line)
    
    print(f"Updated {toml_path}")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)
