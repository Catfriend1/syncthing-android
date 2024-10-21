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
# - Python 3.9.6
#

PLATFORM_DIRS = {
    'Windows': 'windows-x86_64',
    'Linux': 'linux-x86_64',
    'Darwin': 'darwin-x86_64',
}

# Leave empty to auto-detect version by 'git describe'.
FORCE_DISPLAY_SYNCTHING_VERSION = ''
FILENAME_SYNCTHING_BINARY = 'libsyncthingnative.so'

GO_VERSION = '1.22.5'
GO_EXPECTED_SHASUM_LINUX = '904b924d435eaea086515bc63235b192ea441bd8c9b198c507e85009e6e4c7f0'
GO_EXPECTED_SHASUM_WINDOWS = '59968438b8d90f108fd240d4d2f95b037e59716995f7409e0a322dcb996e9f42'

NDK_VERSION = 'r27'
NDK_EXPECTED_SHASUM_LINUX = '5e5cd517bdb98d7e0faf2c494a3041291e71bdcc'
NDK_EXPECTED_SHASUM_WINDOWS = '0ea2756e6815356831bda3af358cce4cdb6a981e'

BUILD_TARGETS = [
    {
        'arch': 'arm',
        'goarch': 'arm',
        'jni_dir': 'armeabi',
        'cc': 'armv7a-linux-androideabi{}-clang',
    },
    {
        'arch': 'arm64',
        'goarch': 'arm64',
        'jni_dir': 'arm64-v8a',
        'cc': 'aarch64-linux-android{}-clang',
    },
    {
        'arch': 'x86',
        'goarch': '386',
        'jni_dir': 'x86',
        'cc': 'i686-linux-android{}-clang',
    },
    {
        'arch': 'x86_64',
        'goarch': 'amd64',
        'jni_dir': 'x86_64',
        'cc': 'x86_64-linux-android{}-clang',
    }
]


def fail(message, *args, **kwargs):
    print((message % args).format(**kwargs))
    sys.exit(1)


def get_min_sdk(project_dir):
    with open(os.path.join(project_dir, 'app', 'build.gradle.kts')) as file_handle:
        for line in file_handle:
            tokens = list(filter(None, line.split()))
            if len(tokens) == 3 and tokens[0] == 'minSdk':
                return int(tokens[2])

    fail('Failed to find minSdkVersion')

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

def install_git():
    import os
    import zipfile
    import hashlib

    if sys.version_info[0] >= 3:
        from urllib.request import urlretrieve
    else:
        from urllib import urlretrieve

    if not os.path.isdir(prerequisite_tools_dir):
        os.makedirs(prerequisite_tools_dir)

    if sys.platform == 'win32':
        url =               'https://github.com/git-for-windows/git/releases/download/v2.19.0.windows.1/MinGit-2.19.0-64-bit.zip'
        expected_shasum =   '424d24b5fc185a9c5488d7872262464f2facab4f1d4693ea8008196f14a3c19b'
        zip_fullfn = prerequisite_tools_dir + os.path.sep + 'mingit.zip';
    else:
        print('Portable on-demand git installation is currently not supported on linux.')
        return None

    # Download MinGit.
    url_base_name = os.path.basename(url)
    if not os.path.isfile(zip_fullfn):
        print('Downloading MinGit to:', zip_fullfn)
        zip_fullfn = urlretrieve(url, zip_fullfn)[0]
    print('Downloaded MinGit to:', zip_fullfn)

    # Verify SHA-256 checksum of downloaded files.
    with open(zip_fullfn, 'rb') as f:
        contents = f.read()
        found_shasum = hashlib.sha256(contents).hexdigest()
        print("SHA-256:", zip_fullfn, "%s" % found_shasum)
    if found_shasum != expected_shasum:
        fail('Error: SHA-256 checksum ' + found_shasum + ' of downloaded file does not match expected checksum ' + expected_shasum)
    print("[ok] Checksum of", zip_fullfn, "matches expected value.")

    # Proceed with extraction of the MinGit.
    if not os.path.isfile(prerequisite_tools_dir + os.path.sep + 'mingit' + os.path.sep + 'LICENSE.txt'):
        print("Extracting MinGit ...")
        # This will go to a subfolder "mingit" in the current path.
        zip = zipfile.ZipFile(zip_fullfn, 'r')
        zip.extractall(prerequisite_tools_dir + os.path.sep + 'mingit')
        zip.close()

    # Add "mingit/cmd" to the PATH.
    git_bin_path = prerequisite_tools_dir + os.path.sep + 'mingit' + os.path.sep + 'cmd'
    print('Adding to PATH:', git_bin_path)
    os.environ["PATH"] += os.pathsep + git_bin_path


def install_go():
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

    if sys.platform == 'win32':
        url =               'https://dl.google.com/go/go' + GO_VERSION + '.windows-amd64.zip'
        expected_shasum =   GO_EXPECTED_SHASUM_WINDOWS
        tar_gz_fullfn = prerequisite_tools_dir + os.path.sep + 'go_' + GO_VERSION + '.zip';
    else:
        url =               'https://dl.google.com/go/go' + GO_VERSION + '.linux-amd64.tar.gz'
        expected_shasum =   GO_EXPECTED_SHASUM_LINUX
        tar_gz_fullfn = prerequisite_tools_dir + os.path.sep + 'go_' + GO_VERSION + '.tgz';

    # Download prebuilt-go.
    url_base_name = os.path.basename(url)
    if not os.path.isfile(tar_gz_fullfn):
        print('Downloading prebuilt-go to:', tar_gz_fullfn)
        tar_gz_fullfn = urlretrieve(url, tar_gz_fullfn)[0]
    print('Downloaded prebuilt-go to:', tar_gz_fullfn)

    # Verify SHA-256 checksum of downloaded files.
    with open(tar_gz_fullfn, 'rb') as f:
        contents = f.read()
        found_shasum = hashlib.sha256(contents).hexdigest()
        print("SHA-256:", tar_gz_fullfn, "%s" % found_shasum)
    if found_shasum != expected_shasum:
        fail('Error: SHA-256 checksum ' + found_shasum + ' of downloaded file does not match expected checksum ' + expected_shasum)
    print("[ok] Checksum of", tar_gz_fullfn, "matches expected value.")

    # Proceed with extraction of the prebuilt go.
    go_extracted_folder = prerequisite_tools_dir + os.path.sep + 'go_' + GO_VERSION
    if not os.path.isfile(go_extracted_folder + os.path.sep + 'LICENSE'):
        print("Extracting prebuilt-go ...")
        # This will go to a subfolder "go" in the current path.
        if sys.platform == 'win32':
            zip = zipfile.ZipFile(tar_gz_fullfn, 'r')
            zip.extractall(prerequisite_tools_dir)
            zip.close()
        else:
            tar = tarfile.open(tar_gz_fullfn)
            tar.extractall(prerequisite_tools_dir)
            tar.close()
        os.rename(prerequisite_tools_dir + os.path.sep + 'go', go_extracted_folder)

    # Add "go/bin" to the PATH.
    go_bin_path = go_extracted_folder + os.path.sep + 'bin'
    print('Adding to PATH:', go_bin_path)
    os.environ["PATH"] += os.pathsep + go_bin_path


def write_file(fullfn, text):
    with open(fullfn, 'w') as hFile:
        hFile.write(text + '\n')


def get_ndk_ready():
    if os.environ.get('ANDROID_NDK_HOME', ''):
        return
    if not (os.environ.get('NDK_VERSION', '') and os.environ.get('ANDROID_SDK_ROOT', '')):
        print('ANDROID_NDK_HOME env var is not defined. Then, NDK_VERSION and ANDROID_SDK_ROOT env vars must be defined.')
        install_ndk()
        return
    os.environ["ANDROID_NDK_HOME"] = os.path.join(os.environ['ANDROID_SDK_ROOT'], 'ndk', os.environ['NDK_VERSION'])
    return


def install_ndk():
    import os
    import zipfile
    import hashlib

    if sys.version_info[0] >= 3:
        from urllib.request import urlretrieve
    else:
        from urllib import urlretrieve

    if not os.path.isdir(prerequisite_tools_dir):
        os.makedirs(prerequisite_tools_dir)

    if sys.platform == 'win32':
        url =               'https://dl.google.com/android/repository/android-ndk-' + NDK_VERSION + '-windows.zip'
        expected_shasum =   NDK_EXPECTED_SHASUM_WINDOWS

    else:
        url =               'https://dl.google.com/android/repository/android-ndk-' + NDK_VERSION + '-linux.zip'
        expected_shasum =   NDK_EXPECTED_SHASUM_LINUX

    zip_fullfn = prerequisite_tools_dir + os.path.sep + 'ndk_' + NDK_VERSION + '.zip';
    # Download NDK.
    url_base_name = os.path.basename(url)
    if not os.path.isfile(zip_fullfn):
        print('Downloading NDK to:', zip_fullfn)
        zip_fullfn = urlretrieve(url, zip_fullfn)[0]
    print('Downloaded NDK to:', zip_fullfn)

    # Verify SHA-1 checksum of downloaded files.
    with open(zip_fullfn, 'rb') as f:
        contents = f.read()
        found_shasum = hashlib.sha1(contents).hexdigest()
        print("SHA-1:", zip_fullfn, "%s" % found_shasum)
    if found_shasum != expected_shasum:
        fail('Error: SHA-256 checksum ' + found_shasum + ' of downloaded file does not match expected checksum ' + expected_shasum)
    print("[ok] Checksum of", zip_fullfn, "matches expected value.")

    # Proceed with extraction of the NDK if necessary.
    ndk_home_path = prerequisite_tools_dir + os.path.sep + 'android-ndk-' + NDK_VERSION
    if not os.path.isfile(ndk_home_path + os.path.sep + "NOTICE"):
        print("Extracting NDK ...")
        # This will go to a subfolder "android-ndk-rXY" in the current path.
        zip = zipfile.ZipFile(zip_fullfn, 'r')
        zip.extractall(prerequisite_tools_dir)
        zip.close()

    # Linux only - Set executable permission on files.
    if platform.system() == 'Linux':
        print("Setting permissions on NDK executables ...")
        change_permissions_recursive(ndk_home_path, 0o755);
        #
        # Fix NDK r23 bug with incomplete path and arguments when calling "clang".
        ndk_bin_clang = os.path.join(
            ndk_home_path,
            'toolchains',
            'llvm',
            'prebuilt',
            PLATFORM_DIRS[platform.system()],
            'bin',
            'clang'
        )
        write_file (ndk_bin_clang, '`dirname $0`/clang-14 "$@"')

    # Add "ANDROID_NDK_HOME" environment variable.
    print('Adding ANDROID_NDK_HOME=\'' + ndk_home_path + '\'')
    os.environ["ANDROID_NDK_HOME"] = ndk_home_path


#
# BUILD SCRIPT MAIN.
#
if platform.system() not in PLATFORM_DIRS:
    fail('Unsupported python platform %s. Supported platforms: %s', platform.system(),
         ', '.join(PLATFORM_DIRS.keys()))

module_dir = os.path.dirname(os.path.realpath(__file__))
project_dir = os.path.realpath(os.path.join(module_dir, '..'))
syncthing_dir = os.path.join(module_dir, 'src', 'github.com', 'syncthing', 'syncthing')
prerequisite_tools_dir = os.path.dirname(os.path.realpath(__file__)) + os.path.sep + ".." + os.path.sep + ".." + os.path.sep + "syncthing-android-prereq"
min_sdk = get_min_sdk(project_dir)

# print ('Info: min_sdk = ' + str(min_sdk))

# Check if git is available.
git_bin = which("git");
if not git_bin:
    print('Warning: git is not available on the PATH.')
    install_git();
    # Retry: Check if git is available.
    git_bin = which("git");
    if not git_bin:
        fail('Error: git is not available on the PATH.')
print('git_bin=\'' + git_bin + '\'')

# Check if go is available.
go_bin = which("go");
if not go_bin:
    print('Info: go is not available on the PATH. Trying install_go')
    install_go();
    # Retry: Check if go is available.
    go_bin = which("go");
    if not go_bin:
        fail('Error: go is not available on the PATH.')
print('go_bin=\'' + go_bin + '\'')

# Check if "ANDROID_NDK_HOME" env var is set. If not, try to discover and set it.
get_ndk_ready()
if not os.environ.get('ANDROID_NDK_HOME', ''):
    fail('Error: ANDROID_NDK_HOME environment variable not defined')
print('ANDROID_NDK_HOME=\'' + os.environ.get('ANDROID_NDK_HOME', '') + '\'')

# Make sure all tags are available for git describe
print('Invoking git fetch ...')
subprocess.check_call([
    'git',
    '-C',
    syncthing_dir,
    'fetch',
    '--tags'
])

if FORCE_DISPLAY_SYNCTHING_VERSION:
    syncthingVersion = FORCE_DISPLAY_SYNCTHING_VERSION.replace("rc", "preview");
else:
    print('Invoking git describe ...')
    syncthingVersion = subprocess.check_output([
        git_bin,
        '-C',
        syncthing_dir,
        'describe',
        '--always'
    ]).strip();
    syncthingVersion = syncthingVersion.decode().replace("rc", "preview");

print('Cleaning go-build cache')
subprocess.check_call([go_bin, 'clean', '-cache'], cwd=syncthing_dir)

print('Building syncthing version', syncthingVersion);
for target in BUILD_TARGETS:
    print('')
    print('*** Building for', target['arch'])

    cc = os.path.join(
        os.environ['ANDROID_NDK_HOME'],
        'toolchains',
        'llvm',
        'prebuilt',
        PLATFORM_DIRS[platform.system()],
        'bin',
        target['cc'].format(min_sdk),
    )

    environ = os.environ.copy()
    environ.update({
        'GOPATH': module_dir,
        'GO111MODULE': 'on',
        'CGO_ENABLED': '1',
    })

    subprocess.check_call([go_bin, 'mod', 'download'], cwd=syncthing_dir)
    subprocess.check_call(
                              [go_bin, 'version'],
                              env=environ, cwd=syncthing_dir)
    subprocess.check_call(
                              [go_bin, 'run', 'build.go', 'version'],
                              env=environ, cwd=syncthing_dir)
    subprocess.check_call([
                              go_bin, 'run', 'build.go', '-goos', 'android',
                              '-goarch', target['goarch'],
                              '-cc', cc,
                              '-version', syncthingVersion
                          ] + ['-no-upgrade', 'build'], env=environ, cwd=syncthing_dir)

    # Determine path of source artifact
    source_artifact = os.path.join(syncthing_dir, 'syncthing')

    # Copy compiled binary to jniLibs folder
    target_dir = os.path.join(project_dir, 'app', 'src', 'main', 'jniLibs', target['jni_dir'])
    if not os.path.isdir(target_dir):
        os.makedirs(target_dir)
    target_artifact = os.path.join(target_dir, FILENAME_SYNCTHING_BINARY)
    if os.path.exists(target_artifact):
        os.unlink(target_artifact)
    os.rename(os.path.join(syncthing_dir, 'syncthing'), target_artifact)

    print('*** Finished build for', target['arch'])

print('All builds finished')
