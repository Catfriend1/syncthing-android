from __future__ import print_function
import os
import os.path
import sys
import subprocess
import platform
#
# Script Compatibility:
# - Python 2.7.15
# - Python 3.7.0
#

SUPPORTED_PYTHON_PLATFORMS = ['Windows', 'Linux', 'Darwin']

def fail(message, *args, **kwargs):
    print((message % args).format(**kwargs))
    sys.exit(1)

def which(program):
    import os
    def is_exe(fpath):
        return os.path.isfile(fpath) and os.access(fpath, os.X_OK)

    if (sys.platform == 'win32'):
        program += ".exe"
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


#
# Push APK to device.
#
if platform.system() not in SUPPORTED_PYTHON_PLATFORMS:
    fail('Unsupported python platform %s. Supported platforms: %s', platform.system(),
         ', '.join(SUPPORTED_PYTHON_PLATFORMS))
print ('')

# Build FullFN of "app-debug.apk".
current_dir = os.path.dirname(os.path.realpath(__file__))
enable_push_to_device = os.path.realpath(os.path.join(current_dir, "..", "#enable_push_to_device"))

# Check if push to device is enabled.
if not enable_push_to_device or not os.path.isfile(enable_push_to_device):
    print('[INFO] push-to-device after build is DISABLED. To enable it, create the file \'' + enable_push_to_device + '\'')
    sys.exit(0)

debug_apk = os.path.realpath(os.path.join(current_dir, 'build', 'outputs', 'apk', 'debug', 'app-debug.apk'))
if not debug_apk or not os.path.isfile(debug_apk):
    fail('[ERROR] app-debug.apk not found.');
print('[INFO] debug_apk=' + debug_apk)

# Check if adb is available.
adb_bin = which("adb");
if not adb_bin:
    print('[WARNING] adb is not available on the PATH.')
    # install_adb();
    # Retry: Check if adb is available.
    # adb_bin = which("adb");
    # if not adb_bin:
    #     fail('[ERROR] adb is not available on the PATH.')
print('[INFO] adb_bin=\'' + adb_bin + '\'')

print('[INFO] Connecting to attached usb device ...')
try:
    subprocess.check_call([
        adb_bin,
        'devices'
    ])
except:
    sys.exit(0)

print('[INFO] Installing APK to attached usb device ...')
try:
    subprocess.check_call(adb_bin + ' install -r --user 0 ' + debug_apk)
except:
    sys.exit(0)

print('[INFO] Starting app ...')
try:
    subprocess.check_call(adb_bin + ' shell monkey -p com.github.catfriend1.syncthingandroid.debug 1')
except:
    sys.exit(0)
