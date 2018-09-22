from __future__ import print_function
import os
import os.path
import sys
import subprocess
import platform

SUPPORTED_PYTHON_PLATFORMS = ['Windows', 'Linux', 'Darwin']

BUILD_TARGETS = [
    {
        'arch': 'arm',
        'goarch': 'arm',
        'jni_dir': 'armeabi',
        'cc': 'arm-linux-androideabi-clang',
    },
    {
        'arch': 'arm64',
        'goarch': 'arm64',
        'jni_dir': 'arm64-v8a',
        'cc': 'aarch64-linux-android-clang',
        'min_sdk': 21,
    },
    {
        'arch': 'x86',
        'goarch': '386',
        'jni_dir': 'x86',
        'cc': 'i686-linux-android-clang',
    }
]


def fail(message, *args, **kwargs):
    print((message % args).format(**kwargs))
    sys.exit(1)


def get_min_sdk(project_dir):
    with open(os.path.join(project_dir, 'app', 'build.gradle')) as file_handle:
        for line in file_handle:
            tokens = list(filter(None, line.split()))
            if len(tokens) == 2 and tokens[0] == 'minSdkVersion':
                return int(tokens[1])

    fail('Failed to find minSdkVersion')

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

def change_permissions_recursive(path, mode):
    import os
    for root, dirs, files in os.walk(path, topdown=False):
        for dir in [os.path.join(root,d) for d in dirs]:
            os.chmod(dir, mode)
        for file in [os.path.join(root, f) for f in files]:
            os.chmod(file, mode)

def install_go():
    import os
    import tarfile
    import zipfile
    import urllib
    import hashlib

    # Consts.
    pwd_path = os.path.dirname(os.path.realpath(__file__))
    if sys.platform == 'win32':
        url =               'https://dl.google.com/go/go1.9.7.windows-amd64.zip'
        expected_shasum =   '8db4b21916a3bc79f48d0611202ee5814c82f671b36d5d2efcb446879456cd28'
        tar_gz_fullfn = pwd_path + os.path.sep + 'go.zip';
    else:
        url =               'https://dl.google.com/go/go1.9.7.linux-amd64.tar.gz'
        expected_shasum =   '88573008f4f6233b81f81d8ccf92234b4f67238df0f0ab173d75a302a1f3d6ee'
        tar_gz_fullfn = pwd_path + os.path.sep + 'go.tgz';

    # Download prebuilt-go.
    url_base_name = os.path.basename(url)
    if not os.path.isfile(tar_gz_fullfn):
        print('Downloading prebuilt-go to:', tar_gz_fullfn)
        tar_gz_fullfn = urllib.urlretrieve(url, tar_gz_fullfn)[0]
    print('Downloaded prebuilt-go to:', tar_gz_fullfn)

    # Verfiy SHA-256 checksum of downloaded files.
    with open(tar_gz_fullfn, 'rb') as f:
        contents = f.read()
        found_shasum = hashlib.sha256(contents).hexdigest()
        print("SHA-256:", tar_gz_fullfn, "%s" % found_shasum)
    if found_shasum != expected_shasum:
        fail('Error: SHA-256 checksum', found_shasum, 'of downloaded file does not match expected checksum', expected_shasum)
    print("[ok] Checksum of", tar_gz_fullfn, "matches expected value.")

    # Proceed with extraction of the prebuilt go.
    if not os.path.isfile(pwd_path + os.path.sep + 'go' + os.path.sep + 'LICENSE'):
        print("Extracting prebuilt-go ...")
        # This will go to a subfolder "go" in the current path.
        file_name, file_extension = os.path.splitext(url_base_name)
        if sys.platform == 'win32':
            zip = zipfile.ZipFile(tar_gz_fullfn, 'r')
            zip.extractall(pwd_path)
            zip.close()
        else:
            tar = tarfile.open(tar_gz_fullfn)
            tar.extractall(pwd_path)
            tar.close()

    # Add (...).tar/go/bin" to the PATH.
    go_bin_path = pwd_path + os.path.sep + 'go' + os.path.sep + 'bin'
    print('Adding to PATH:', go_bin_path)
    os.environ["PATH"] += os.pathsep + go_bin_path




def install_ndk():
    import os
    import zipfile
    import urllib
    import hashlib

    # Consts.
    pwd_path = os.path.dirname(os.path.realpath(__file__))
    if sys.platform == 'win32':
        url =               'https://dl.google.com/android/repository/android-ndk-r16b-windows-x86_64.zip'
        expected_shasum =   'f3f1909ed1052e98dda2c79d11c22f3da28daf25'

    else:
        url =               'https://dl.google.com/android/repository/android-ndk-r16b-linux-x86_64.zip'
        expected_shasum =   '42aa43aae89a50d1c66c3f9fdecd676936da6128'

    zip_fullfn = pwd_path + os.path.sep + 'ndk.zip';
    # Download NDK.
    url_base_name = os.path.basename(url)
    if not os.path.isfile(zip_fullfn):
        print('Downloading NDK to:', zip_fullfn)
        zip_fullfn = urllib.urlretrieve(url, zip_fullfn)[0]
    print('Downloaded NDK to:', zip_fullfn)

    # Verfiy SHA-1 checksum of downloaded files.
    with open(zip_fullfn, 'rb') as f:
        contents = f.read()
        found_shasum = hashlib.sha1(contents).hexdigest()
        print("SHA-1:", zip_fullfn, "%s" % found_shasum)
    if found_shasum != expected_shasum:
        fail('Error: SHA-1 checksum', found_shasum, 'of downloaded file does not match expected checksum', expected_shasum)
    print("[ok] Checksum of", zip_fullfn, "matches expected value.")

    # Proceed with extraction of the NDK if necessary.
    ndk_home_path = pwd_path + os.path.sep + 'android-ndk-r16b'
    if not os.path.isfile(ndk_home_path + os.path.sep + "sysroot" + os.path.sep + "NOTICE"):
        print("Extracting NDK ...")
        # This will go to a subfolder "android-ndk-r16b" in the current path.
        file_name, file_extension = os.path.splitext(url_base_name)
        zip = zipfile.ZipFile(zip_fullfn, 'r')
        zip.extractall(pwd_path)
        zip.close()

    # Linux only - Set executable permission on files.
    if platform.system() == 'Linux':
        print("Setting permissions on NDK executables ...")
        change_permissions_recursive(ndk_home_path, 0o755);

    # Add "ANDROID_NDK_HOME" environment variable.
    print('Adding ANDROID_NDK_HOME=\'' + ndk_home_path + '\'')
    os.environ["ANDROID_NDK_HOME"] = ndk_home_path



#
# BUILD SCRIPT MAIN.
#
if platform.system() not in SUPPORTED_PYTHON_PLATFORMS:
    fail('Unsupported python platform %s. Supported platforms: %s', platform.system(),
         ', '.join(SUPPORTED_PYTHON_PLATFORMS))

module_dir = os.path.dirname(os.path.realpath(__file__))
project_dir = os.path.realpath(os.path.join(module_dir, '..'))
# Use seperate build dir so standalone ndk isn't deleted by `gradle clean`
build_dir = os.path.join(module_dir, 'gobuild')
go_build_dir = os.path.join(build_dir, 'go-packages')
syncthing_dir = os.path.join(module_dir, 'src', 'github.com', 'syncthing', 'syncthing')
min_sdk = get_min_sdk(project_dir)

# Check if go is available.
go_bin = which("go");
if not go_bin:
    print('Warning: go is not available on the PATH.')
    install_go();
    # Retry: Check if go is available.
    go_bin = which("go");
    if not go_bin:
        fail('Error: go is not available on the PATH.')
print ('go_bin=\'' + go_bin + '\'')

# Check if ANDROID_NDK_HOME variable is set.
if not os.environ.get('ANDROID_NDK_HOME', ''):
    print('Warning: ANDROID_NDK_HOME environment variable not defined.')
    install_ndk();
    # Retry: Check if ANDROID_NDK_HOME variable is set.
    if not os.environ.get('ANDROID_NDK_HOME', ''):
        fail('Error: ANDROID_NDK_HOME environment variable not defined')
print ('ANDROID_NDK_HOME=\'' + os.environ.get('ANDROID_NDK_HOME', '') + '\'')

# Make sure all tags are available for git describe
# https://github.com/syncthing/syncthing-android/issues/872
subprocess.check_call([
    'git',
    '-C',
    syncthing_dir,
    'fetch',
    '--tags'
])

for target in BUILD_TARGETS:
    target_min_sdk = str(target.get('min_sdk', min_sdk))
    print('Building for', target['arch'])

    if os.environ.get('SYNCTHING_ANDROID_PREBUILT', ''):
        # The environment variable indicates the SDK and stdlib was prebuilt, set a custom paths.
        standalone_ndk_dir = os.environ['ANDROID_NDK_HOME'] + os.path.sep + 'standalone-ndk' + os.path.sep + 'android-' + target_min_sdk + '-' + target['goarch']
        pkg_argument = []
    else:
        # Build standalone NDK toolchain if it doesn't exist.
        # https://developer.android.com/ndk/guides/standalone_toolchain.html
        standalone_ndk_dir = build_dir + os.path.sep + 'standalone-ndk' + os.path.sep + 'android-' + target_min_sdk + '-' + target['goarch']
        pkg_argument = ['-pkgdir', os.path.join(go_build_dir, target['goarch'])]

    if not os.path.isdir(standalone_ndk_dir):
        print('Building standalone NDK for', target['arch'], 'API level', target_min_sdk, 'to', standalone_ndk_dir)
        subprocess.check_call([
            sys.executable,
            os.path.join(os.environ['ANDROID_NDK_HOME'], 'build', 'tools', 'make_standalone_toolchain.py'),
            '--arch',
            target['arch'],
            '--api',
            target_min_sdk,
            '--install-dir',
            standalone_ndk_dir,
            '-v'
        ])

    environ = os.environ.copy()
    environ.update({
        'GOPATH': module_dir,
        'CGO_ENABLED': '1',
        'CC': os.path.join(standalone_ndk_dir, 'bin', target['cc'])
    })

    syncthingVersion = subprocess.check_output([
        'git',
        '-C',
        syncthing_dir,
        'describe',
        '--always'
    ]).strip();
    syncthingVersion = syncthingVersion.replace("rc", "preview");
    print('Building syncthing version', syncthingVersion);
    subprocess.check_call([
                              go_bin, 'run', 'build.go', '-goos', 'android', '-goarch', target['goarch'],
                              '-version', syncthingVersion
                          ] + pkg_argument + ['-no-upgrade', 'build'], env=environ, cwd=syncthing_dir)

    # Copy compiled binary to jniLibs folder
    target_dir = os.path.join(project_dir, 'app', 'src', 'main', 'jniLibs', target['jni_dir'])
    if not os.path.isdir(target_dir):
        os.makedirs(target_dir)
    target_artifact = os.path.join(target_dir, 'libsyncthing.so')
    if os.path.exists(target_artifact):
        os.unlink(target_artifact)
    os.rename(os.path.join(syncthing_dir, 'syncthing'), target_artifact)

    print('Finished build for', target['arch'])

print('All builds finished')
