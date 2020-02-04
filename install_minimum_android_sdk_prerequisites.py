from __future__ import print_function
import os
import os.path
import shutil
import subprocess
import sys
import platform
#
# Script Compatibility:
# - Python 2.7.15
# - Python 3.7.0
#
# Run script from command line with:
#   python install_minimum_android_sdk_prerequisites.py
#

SUPPORTED_PYTHON_PLATFORMS = ['Windows', 'Linux', 'Darwin']

ANDROID_SDK_TOOLS_VERSION = '4333796'
ANDROID_SDK_TOOLS_SHASUM_LINUX = '92ffee5a1d98d856634e8b71132e8a95d96c83a63fde1099be3d86df3106def9'
ANDROID_SDK_TOOLS_SHASUM_WINDOWS = '7e81d69c303e47a4f0e748a6352d85cd0c8fd90a5a95ae4e076b5e5f960d3c7a'

def fail(message, *args, **kwargs):
    print((message % args).format(**kwargs))
    sys.exit(1)

def which_raw(program):
    import os
    def is_exe(fpath):
        return os.path.isfile(fpath) and os.access(fpath, os.X_OK)

    fpath, fname = os.path.split(program)
    if fpath:
        if is_exe(program):
            return program
    else:
        for path in os.environ["PATH"].split(os.pathsep):
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

def change_permissions_recursive(path, mode):
    import os
    for root, dirs, files in os.walk(path, topdown=False):
        for dir in [os.path.join(root,d) for d in dirs]:
            os.chmod(dir, mode)
        for file in [os.path.join(root, f) for f in files]:
            os.chmod(file, mode)

def install_sdk_tools():
    import os
    import tarfile
    import zipfile
    import hashlib

    if sys.version_info[0] >= 3:
        from urllib.request import urlretrieve
    else:
        from urllib import urlretrieve

    if not os.path.isdir(prerequisite_tools_dir):
        os.makedirs(prerequisite_tools_dir)

    zip_fullfn = prerequisite_tools_dir + os.path.sep + 'sdk-tools.zip';
    if sys.platform == 'win32':
        url =               'https://dl.google.com/android/repository/sdk-tools-windows-' + ANDROID_SDK_TOOLS_VERSION + '.zip'
        expected_shasum =   ANDROID_SDK_TOOLS_SHASUM_WINDOWS
    else:
        url =               'https://dl.google.com/android/repository/sdk-tools-linux-' + ANDROID_SDK_TOOLS_VERSION + '.zip'
        expected_shasum =   ANDROID_SDK_TOOLS_SHASUM_LINUX

    # Download sdk-tools.
    url_base_name = os.path.basename(url)
    if not os.path.isfile(zip_fullfn):
        print('Downloading sdk-tools to:', zip_fullfn)
        zip_fullfn = urlretrieve(url, zip_fullfn)[0]
    print('Downloaded sdk-tools to:', zip_fullfn)

    # Verify SHA-1 checksum of downloaded files.
    with open(zip_fullfn, 'rb') as f:
        contents = f.read()
        found_shasum = hashlib.sha256(contents).hexdigest()
        print("SHA-256:", zip_fullfn, "%s" % found_shasum)
    if found_shasum != expected_shasum:
        fail('Error: SHA-256 checksum ' + found_shasum + ' of downloaded file does not match expected checksum ' + expected_shasum)
    print("[ok] Checksum of", zip_fullfn, "matches expected value.")

    # Proceed with extraction of the NDK if necessary.
    sdk_tools_path = prerequisite_tools_dir + os.path.sep + 'tools'
    if not os.path.isfile(sdk_tools_path + os.path.sep + "source.properties"):
        print("Extracting sdk-tools ...")
        # This will go to a subfolder "tools" in the current path.
        file_name, file_extension = os.path.splitext(url_base_name)
        zip = zipfile.ZipFile(zip_fullfn, 'r')
        zip.extractall(prerequisite_tools_dir)
        zip.close()

    # Linux only - Set executable permission on files.
    if platform.system() == 'Linux':
        print("Setting permissions on sdk-tools executables ...")
        change_permissions_recursive(sdk_tools_path, 0o755);

    # Add tools/bin to PATH.
    sdk_tools_bin_path = sdk_tools_path + os.path.sep + 'bin'
    print('Adding to PATH:', sdk_tools_bin_path)
    os.environ["PATH"] += os.pathsep + sdk_tools_bin_path

    print('')
    print('-------------------------------------------------------------------------')
    print('Adding user env variable:')
    print('SET ANDROID_HOME="' + os.path.realpath(prerequisite_tools_dir) + '"')
    print('-------------------------------------------------------------------------')
    print('')
    subprocess.check_call([which('setx'), 'ANDROID_HOME', os.path.realpath(prerequisite_tools_dir)])


#
# SCRIPT MAIN.
#
if platform.system() not in SUPPORTED_PYTHON_PLATFORMS:
    fail('Unsupported python platform %s. Supported platforms: %s', platform.system(),
         ', '.join(SUPPORTED_PYTHON_PLATFORMS))

if not sys.platform == 'win32':
    fail ('Script is currently supported to run under Windows only.')

prerequisite_tools_dir = os.path.dirname(os.path.realpath(__file__)) + os.path.sep + ".." + os.path.sep + "syncthing-android-prereq"

# Check if "sdk-manager" of sdk-tools package is available.
sdk_manager_bin = which("sdkmanager")
if not sdk_manager_bin:
        print('Warning: sdkmanager from sdk-tools package is not available on PATH.')
        install_sdk_tools();
        # Retry.
        sdk_manager_bin = which("sdkmanager")
        if not sdk_manager_bin:
            fail('Error: sdkmanager from sdk-tools package is not available on PATH.')
print('sdk_manager_bin=\'' + sdk_manager_bin + '\'')

# Windows only - Auto accept all sdkmanager licenses.
if sys.platform == 'win32':
    subprocess.check_call([sdk_manager_bin, '--update'])
    powershell_bin = which('powershell')
    subprocess.check_call([powershell_bin, 'for($i=0;$i -lt 30;$i++) { $response += \"y`n\"}; $response | sdkmanager --licenses'])
    subprocess.check_call([sdk_manager_bin, 'platforms;android-29'])
    subprocess.check_call([sdk_manager_bin, 'build-tools;29.0.2'])

print('Done.')
