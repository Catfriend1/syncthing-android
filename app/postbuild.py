from __future__ import print_function
import os
import os.path
import sys
import subprocess
import platform
import codecs
import re
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

def calcAndPrintCertHash(apk_fullfn, apk_build_type):
    if not apk_fullfn or not os.path.isfile(apk_fullfn):
        return None

    keytool_bin = which("keytool")
    if not keytool_bin:
        keytool_bin = os.environ.get('ProgramFiles') + os.path.sep + 'Android' + os.path.sep + 'Android Studio' + os.path.sep + 'jre' + os.path.sep + 'bin' + os.path.sep + 'keytool.exe'
    try:
        if (platform.system() == "Windows"):
            shell_result = subprocess.check_output(keytool_bin + ' -list -printcert -jarfile "' + apk_fullfn + '"')
        else:
            shell_result = subprocess.check_output(keytool_bin + ' -list -printcert -jarfile "' + apk_fullfn + '"', shell=True)
    except Exception as e:
        print('[WARN] Failed to exec keytool: ' + str(e));
        return None

    try:
        result_array = codecs.decode(shell_result, 'cp1252').splitlines()
        for result_line in result_array:
            if result_line:
                result_line = result_line.strip()
                if 'SHA1: ' in result_line:
                    result_hex = result_line.replace('SHA1: ', '')
                    result_hex_cleaned = re.sub('[^A-Fa-f0-9]+', '', result_hex)
                    result_hash = codecs.encode(codecs.decode(result_hex_cleaned, 'hex'), 'base64').decode('utf-8')
                    result_hash = result_hash.strip('\n')
    except Exception as e:
        print('[WARN] Failed to parse keytool result: ' + str(e));
        return None

    release_types = {
        "2ScaPj41giu4vFh+Y7Q0GJTqwbA=": "GitHub",
        "nyupq9aU0x6yK8RHaPra5GbTqQY=": "F-Droid",
        "dQAnHXvlh80yJgrQUCo6LAg4294=": "Google Play"
    }
    print('[INFO] Built ' + apk_build_type + ' APK for ' + release_types.get(result_hash, "INVALID_CHANNEL") + ' (signing certificate hash: ' + result_hash + ')')

    return None

def pushAPKtoDevice(apk_package_name, apk_fullfn_to_push):
    if not debug_apk or not os.path.isfile(debug_apk):
        print('[ERROR] pushAPKtoDevice: APK not found.');
        return None

    # Check if adb is available.
    adb_bin = which("adb");
    if not adb_bin:
        print('[WARNING] adb is not available on the PATH.')
        # install_adb();
        # Retry: Check if adb is available.
        # adb_bin = which("adb");
        if not adb_bin:
            print('[ERROR] adb is not available on the PATH.')
            sys.exit(0)
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
        subprocess.check_call(adb_bin + ' install -r --user 0 ' + apk_fullfn_to_push)
    except:
        sys.exit(0)

    print('[INFO] Starting app ...')
    try:
        subprocess.check_call(adb_bin + ' shell monkey -p ' + apk_package_name + ' 1')
    except:
        sys.exit(0)

    return None


#################
# Script Main   #
#################
if platform.system() not in SUPPORTED_PYTHON_PLATFORMS:
    fail('Unsupported python platform %s. Supported platforms: %s', platform.system(),
         ', '.join(SUPPORTED_PYTHON_PLATFORMS))
print ('')

# Build FullFNs.
current_dir = os.path.dirname(os.path.realpath(__file__))
enable_push_to_device = os.path.realpath(os.path.join(current_dir, "..", "#enable_push_to_device"))
debug_apk = os.path.realpath(os.path.join(current_dir, 'build', 'outputs', 'apk', 'debug', 'app-debug.apk'))
release_apk = os.path.realpath(os.path.join(current_dir, 'build', 'outputs', 'apk', 'release', 'app-release.apk'))

# Calculate certificate hash of built APKs and output if it matches a known release channel.
# See the wiki for more details: wiki/Switch-between-releases_Verify-APK-is-genuine.md
calcAndPrintCertHash(debug_apk, "debug");
calcAndPrintCertHash(release_apk, "release");

#
# Check if push to device is enabled.
#
# Purpose: Push to device eases deployment on a real Android test device for developers
#           that cannot or do not wish to install the full Android Studio IDE.
if not enable_push_to_device or not os.path.isfile(enable_push_to_device):
    # print('[INFO] push-to-device after build is DISABLED. To enable it, run \'echo . > ' + enable_push_to_device + '\'')
    sys.exit(0)

pushAPKtoDevice("com.github.catfriend1.syncthingandroid.debug", debug_apk)
