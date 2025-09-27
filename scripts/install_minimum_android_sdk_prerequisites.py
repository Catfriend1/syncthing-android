from __future__ import print_function
import os
import os.path
import shutil
import subprocess
import sys
import platform
#
# Run script from command line
## Debian Linux / WSL
### python3 scripts/install_minimum_android_sdk_prerequisites.py
##
## Windows
### python scripts/install_minimum_android_sdk_prerequisites.py
#

SUPPORTED_PYTHON_PLATFORMS = ['Windows', 'Linux', 'Darwin']

def get_android_cmdline_tools_version():
    """
    Get Android cmdline-tools version from gradle/libs.versions.toml.
    This centralizes the version reference to avoid duplication across scripts and workflows.
    """
    import os
    script_dir = os.path.dirname(os.path.realpath(__file__))
    libs_versions_path = os.path.join(script_dir, '..', 'gradle', 'libs.versions.toml')
    with open(libs_versions_path, 'r') as f:
        for line in f:
            if line.strip().startswith('android-cmdline-tools = '):
                version = line.split('"')[1]
                return version
    fail('get_android_cmdline_tools_version FAILED')

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
        url =               'https://dl.google.com/android/repository/commandlinetools-win-' + get_android_cmdline_tools_version() + '_latest.zip'
    else:
        url =               'https://dl.google.com/android/repository/commandlinetools-linux-' + get_android_cmdline_tools_version() + '_latest.zip'

    # Download sdk-tools.
    url_base_name = os.path.basename(url)
    if not os.path.isfile(zip_fullfn):
        print('Downloading sdk-tools to:', zip_fullfn)
        zip_fullfn = urlretrieve(url, zip_fullfn)[0]
    print('Downloaded sdk-tools to:', zip_fullfn)

    # Proceed with extraction of the SDK if necessary.
    sdk_tools_path = prerequisite_tools_dir + os.path.sep + 'cmdline-tools'
    if not os.path.isfile(sdk_tools_path + os.path.sep + "source.properties"):
        print("Extracting sdk-tools ...")
        # This will go to a subfolder "tools" in the current path.
        file_name, file_extension = os.path.splitext(url_base_name)
        zip = zipfile.ZipFile(zip_fullfn, 'r')
        zip.extractall(prerequisite_tools_dir)
        zip.close()

    # Move contents of cmdline-tools one level deeper into cmdline-tools/latest
    sdk_tools_latest_path = sdk_tools_path + os.path.sep + 'latest'
    if os.path.isdir(sdk_tools_latest_path):
        shutil.rmtree(sdk_tools_latest_path)
    os.makedirs(sdk_tools_latest_path)
    shutil.move(sdk_tools_path + os.path.sep + 'NOTICE.txt', sdk_tools_latest_path)
    shutil.move(sdk_tools_path + os.path.sep + 'source.properties', sdk_tools_latest_path)
    shutil.move(sdk_tools_path + os.path.sep + 'bin', sdk_tools_latest_path)
    shutil.move(sdk_tools_path + os.path.sep + 'lib', sdk_tools_latest_path)

    # Linux only - Set executable permission on files.
    if platform.system() == 'Linux':
        print("Setting permissions on sdk-tools executables ...")
        change_permissions_recursive(sdk_tools_path, 0o755);

    # Add tools/bin to PATH.
    sdk_tools_bin_path = sdk_tools_latest_path + os.path.sep + 'bin'
    print('Adding to PATH:', sdk_tools_bin_path)
    os.environ["PATH"] += os.pathsep + sdk_tools_bin_path
    os.environ["ANDROID_HOME"] = os.path.realpath(prerequisite_tools_dir)
    os.environ["ANDROID_SDK_ROOT"] = os.path.realpath(prerequisite_tools_dir)



#
# SCRIPT MAIN.
#
if platform.system() not in SUPPORTED_PYTHON_PLATFORMS:
    fail('Unsupported python platform %s. Supported platforms: %s', platform.system(),
         ', '.join(SUPPORTED_PYTHON_PLATFORMS))

prerequisite_tools_dir = os.path.dirname(os.path.realpath(__file__)) + os.path.sep + ".." + os.path.sep + ".." + os.path.sep + "syncthing-android-prereq"

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
#
# Update SDK repository.
print('[INFO] sdk_manager_bin --update')
subprocess.check_call([sdk_manager_bin, '--update'])
#
# Auto accept all sdkmanager licenses.
if sys.platform == 'win32':
    powershell_bin = which('powershell')
    subprocess.check_call([powershell_bin, 'for($i=0;$i -lt 50;$i++) { $response += \"y`n\"}; $response | sdkmanager --licenses'], stdout=subprocess.DEVNULL)
else:
    print('[INFO] sdkmanager --licenses')
    os.system('yes | sdkmanager --licenses')
#
print('Done.')
