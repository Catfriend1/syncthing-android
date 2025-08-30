#!/usr/bin/env python3
import os
import sys

# Simulate no Go in PATH by checking for a specific environment variable
if os.environ.get('SIMULATE_NO_GO') == '1':
    def which_no_go(program):
        if program == 'go':
            return None
        # For other programs, use normal detection
        return which_original(program)
    
    # Save original which function
    def which_original(program):
        def which_raw(program):
            def is_exe(fpath):
                return os.path.isfile(fpath) and os.access(fpath, os.X_OK)
            fpath, fname = os.path.split(program)
            if fpath:
                if is_exe(program):
                    return program
            else:
                for path in os.environ.get("PATH", "").split(os.pathsep):
                    exe_file = os.path.join(path, program)
                    if is_exe(exe_file):
                        return exe_file
            return None
        
        if (sys.platform == 'win32'):
            which_result = which_raw(program + ".bat")
            if not which_result:
                which_result = which_raw(program + ".cmd")
                if not which_result:
                    which_result = which_raw(program + ".exe")
            return which_result
        else:
            return which_raw(program)
    
    which = which_no_go
else:
    def which(program):
        def which_raw(program):
            def is_exe(fpath):
                return os.path.isfile(fpath) and os.access(fpath, os.X_OK)
            fpath, fname = os.path.split(program)
            if fpath:
                if is_exe(program):
                    return program
            else:
                for path in os.environ.get("PATH", "").split(os.pathsep):
                    exe_file = os.path.join(path, program)
                    if is_exe(exe_file):
                        return exe_file
            return None
        
        if (sys.platform == 'win32'):
            which_result = which_raw(program + ".bat")
            if not which_result:
                which_result = which_raw(program + ".cmd")
                if not which_result:
                    which_result = which_raw(program + ".exe")
            return which_result
        else:
            return which_raw(program)

def get_go_version_from_dockerfile():
    dockerfile_path = os.path.join('docker', 'Dockerfile')
    try:
        with open(dockerfile_path, 'r') as f:
            for line in f:
                line = line.strip()
                if line.startswith('ENV GO_VERSION='):
                    return line.split('=')[1]
    except Exception as e:
        print('Warning: Could not read GO_VERSION from Dockerfile:', e)
        return '1.25.0'  # fallback to default
    return '1.25.0'  # fallback if not found

# Test the logic
print("Testing Go detection:")
go_bin = which("go")
print("Go found at:", go_bin)

if not go_bin:
    print("No Go found - would trigger install_go()")
    go_version = get_go_version_from_dockerfile()
    print("GO_VERSION from Dockerfile:", go_version)
    print("Would download Go source from: https://github.com/golang/go/archive/go" + go_version + ".tar.gz")
else:
    print("Go is available - install_go() would not be called")
