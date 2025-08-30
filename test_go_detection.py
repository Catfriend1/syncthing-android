#!/usr/bin/env python3
import os
import sys
import shutil

# Add the syncthing directory to path
sys.path.insert(0, os.path.join(os.getcwd(), 'syncthing'))

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

def which(program):
    if (sys.platform == 'win32'):
        which_result = which_raw(program + ".bat")
        if not which_result:
            which_result = which_raw(program + ".cmd")
            if not which_result:
                which_result = which_raw(program + ".exe")
        return which_result
    else:
        return which_raw(program)

# Test go detection
print("Testing Go detection:")
go_bin = which("go")
print("Go found at:", go_bin)

if not go_bin:
    print("No Go found - would trigger install_go()")
    
    # Test reading GO_VERSION from Dockerfile
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
    
    go_version = get_go_version_from_dockerfile()
    print("GO_VERSION from Dockerfile:", go_version)
    
    # Test the source URL that would be used
    go_source_url = 'https://github.com/golang/go/archive/go' + go_version + '.tar.gz'
    print("Would download Go source from:", go_source_url)
else:
    print("Go is available - install_go() would not be called")
