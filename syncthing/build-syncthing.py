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

GO_VERSION = '1.12.1'
GO_EXPECTED_SHASUM_LINUX = '2a3fdabf665496a0db5f41ec6af7a9b15a49fbe71a85a50ca38b1f13a103aeec'
GO_EXPECTED_SHASUM_WINDOWS = '2f4849b512fffb2cf2028608aa066cc1b79e730fd146c7b89015797162f08ec5'

BUILD_TARGETS = [
    {
        'arch': 'arm',
        'goarch': 'arm',
        'jni_dir': 'armeabi',
        'cc': 'arm-linux-androideabi-clang',
        'patch_underaligned_tls': 'yes',
    },
    {
        'arch': 'arm64',
        'goarch': 'arm64',
        'jni_dir': 'arm64-v8a',
        'cc': 'aarch64-linux-android-clang',
        'patch_underaligned_tls': 'yes',
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

def install_git():
    import os
    import zipfile
    import hashlib

    if sys.version_info[0] >= 3:
        from urllib.request import urlretrieve
    else:
        from urllib import urlretrieve

    # Consts.
    pwd_path = os.path.dirname(os.path.realpath(__file__))
    if sys.platform == 'win32':
        url =               'https://github.com/git-for-windows/git/releases/download/v2.19.0.windows.1/MinGit-2.19.0-64-bit.zip'
        expected_shasum =   '424d24b5fc185a9c5488d7872262464f2facab4f1d4693ea8008196f14a3c19b'
        zip_fullfn = pwd_path + os.path.sep + 'mingit.zip';
    else:
        print('Portable on-demand git installation is currently not supported on linux.')
        return None

    # Download MinGit.
    url_base_name = os.path.basename(url)
    if not os.path.isfile(zip_fullfn):
        print('Downloading MinGit to:', zip_fullfn)
        zip_fullfn = urlretrieve(url, zip_fullfn)[0]
    print('Downloaded MinGit to:', zip_fullfn)

    # Verfiy SHA-256 checksum of downloaded files.
    with open(zip_fullfn, 'rb') as f:
        contents = f.read()
        found_shasum = hashlib.sha256(contents).hexdigest()
        print("SHA-256:", zip_fullfn, "%s" % found_shasum)
    if found_shasum != expected_shasum:
        fail('Error: SHA-256 checksum ' + found_shasum + ' of downloaded file does not match expected checksum ' + expected_shasum)
    print("[ok] Checksum of", zip_fullfn, "matches expected value.")

    # Proceed with extraction of the MinGit.
    if not os.path.isfile(pwd_path + os.path.sep + 'mingit' + os.path.sep + 'LICENSE.txt'):
        print("Extracting MinGit ...")
        # This will go to a subfolder "mingit" in the current path.
        zip = zipfile.ZipFile(zip_fullfn, 'r')
        zip.extractall(pwd_path + os.path.sep + 'mingit')
        zip.close()

    # Add "mingit/cmd" to the PATH.
    git_bin_path = pwd_path + os.path.sep + 'mingit' + os.path.sep + 'cmd'
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

    # Consts.
    pwd_path = os.path.dirname(os.path.realpath(__file__))
    if sys.platform == 'win32':
        url =               'https://dl.google.com/go/go' + GO_VERSION + '.windows-amd64.zip'
        expected_shasum =   GO_EXPECTED_SHASUM_WINDOWS
        tar_gz_fullfn = pwd_path + os.path.sep + 'go.zip';
    else:
        url =               'https://dl.google.com/go/go' + GO_VERSION + '.linux-amd64.tar.gz'
        expected_shasum =   GO_EXPECTED_SHASUM_LINUX
        tar_gz_fullfn = pwd_path + os.path.sep + 'go.tgz';

    # Download prebuilt-go.
    url_base_name = os.path.basename(url)
    if not os.path.isfile(tar_gz_fullfn):
        print('Downloading prebuilt-go to:', tar_gz_fullfn)
        tar_gz_fullfn = urlretrieve(url, tar_gz_fullfn)[0]
    print('Downloaded prebuilt-go to:', tar_gz_fullfn)

    # Verfiy SHA-256 checksum of downloaded files.
    with open(tar_gz_fullfn, 'rb') as f:
        contents = f.read()
        found_shasum = hashlib.sha256(contents).hexdigest()
        print("SHA-256:", tar_gz_fullfn, "%s" % found_shasum)
    if found_shasum != expected_shasum:
        fail('Error: SHA-256 checksum ' + found_shasum + ' of downloaded file does not match expected checksum ' + expected_shasum)
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

    # Add "go/bin" to the PATH.
    go_bin_path = pwd_path + os.path.sep + 'go' + os.path.sep + 'bin'
    print('Adding to PATH:', go_bin_path)
    os.environ["PATH"] += os.pathsep + go_bin_path




def install_ndk():
    import os
    import zipfile
    import hashlib

    if sys.version_info[0] >= 3:
        from urllib.request import urlretrieve
    else:
        from urllib import urlretrieve

    # Consts.
    pwd_path = os.path.dirname(os.path.realpath(__file__))
    if sys.platform == 'win32':
        url =               'https://dl.google.com/android/repository/android-ndk-r18-windows-x86_64.zip'
        expected_shasum =   '7fc0e0f94d86ea389bd18761abdc1bae2c005587'

    else:
        url =               'https://dl.google.com/android/repository/android-ndk-r18-linux-x86_64.zip'
        expected_shasum =   '2ac2e8e1ef73ed551cac3a1479bb28bd49369212'

    zip_fullfn = pwd_path + os.path.sep + 'ndk.zip';
    # Download NDK.
    url_base_name = os.path.basename(url)
    if not os.path.isfile(zip_fullfn):
        print('Downloading NDK to:', zip_fullfn)
        zip_fullfn = urlretrieve(url, zip_fullfn)[0]
    print('Downloaded NDK to:', zip_fullfn)

    # Verfiy SHA-1 checksum of downloaded files.
    with open(zip_fullfn, 'rb') as f:
        contents = f.read()
        found_shasum = hashlib.sha1(contents).hexdigest()
        print("SHA-1:", zip_fullfn, "%s" % found_shasum)
    if found_shasum != expected_shasum:
        fail('Error: SHA-256 checksum ' + found_shasum + ' of downloaded file does not match expected checksum ' + expected_shasum)
    print("[ok] Checksum of", zip_fullfn, "matches expected value.")

    # Proceed with extraction of the NDK if necessary.
    ndk_home_path = pwd_path + os.path.sep + 'android-ndk-r18'
    if not os.path.isfile(ndk_home_path + os.path.sep + "sysroot" + os.path.sep + "NOTICE"):
        print("Extracting NDK ...")
        # This will go to a subfolder "android-ndk-r18" in the current path.
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


def artifact_patch_underaligned_tls(artifact_fullfn):
    import struct

    with open(artifact_fullfn, 'r+b') as f:
        f.seek(0)
        hdr = struct.unpack('16c', f.read(16))
        if hdr[0] != b'\x7f' or hdr[1] != b'E' or hdr[2] != b'L' or hdr[3] != b'F':
            print('artifact_patch_underaligned_tls: Not an ELF file')
            return None

        if hdr[4] == b'\x01':
            # 32 bit code
            f.seek(28)
            offset = struct.unpack('<I', f.read(4))[0]
            f.seek(42)
            phsize = struct.unpack('<H', f.read(2))[0]
            phnum = struct.unpack('<H', f.read(2))[0]
            for i in range(0, phnum):
                f.seek(offset + i * phsize)
                t = struct.unpack('<I', f.read(4))[0]
                if t == 7:
                    f.seek(28 - 4, 1)
                    align = struct.unpack('<I', f.read(4))[0]
                    if (align < 32):
                        print('artifact_patch_underaligned_tls: Patching underaligned TLS segment from ' + str(align) + ' to 32')
                        f.seek(-4, 1)
                        f.write(struct.pack('<I', 32))

        elif hdr[4] == b'\x02':
            # 64 bit code
            f.seek(32)
            offset = struct.unpack('<Q', f.read(8))[0]
            f.seek(54)
            phsize = struct.unpack('<H', f.read(2))[0]
            phnum = struct.unpack('<H', f.read(2))[0]
            for i in range(0, phnum):
                f.seek(offset + i * phsize)
                t = struct.unpack('<I', f.read(4))[0]
                if t == 7:
                    f.seek(48 - 4, 1)
                    align = struct.unpack('<Q', f.read(8))[0]
                    if (align < 64):
                        print('artifact_patch_underaligned_tls: Patching underaligned TLS segment from ' + str(align) + ' to 64')
                        f.seek(-8, 1)
                        f.write(struct.pack('<H', 64))

        else:
            print('artifact_patch_underaligned_tls: Unknown ELF file class')


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
    print('Warning: go is not available on the PATH.')
    install_go();
    # Retry: Check if go is available.
    go_bin = which("go");
    if not go_bin:
        fail('Error: go is not available on the PATH.')
print('go_bin=\'' + go_bin + '\'')

# Check if ANDROID_NDK_HOME variable is set.
if not os.environ.get('ANDROID_NDK_HOME', ''):
    print('Warning: ANDROID_NDK_HOME environment variable not defined.')
    install_ndk();
    # Retry: Check if ANDROID_NDK_HOME variable is set.
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

print('Invoking git describe ...')
syncthingVersion = subprocess.check_output([
    git_bin,
    '-C',
    syncthing_dir,
    'describe',
    '--always'
]).strip();
syncthingVersion = syncthingVersion.decode().replace("rc", "preview");
print('Building syncthing version', syncthingVersion);

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
        'GO111MODULE': 'on',
        'CGO_ENABLED': '1',
    })

    subprocess.check_call([go_bin, 'mod', 'download'], cwd=syncthing_dir)
    subprocess.check_call([
                              go_bin, 'run', 'build.go', '-goos', 'android', '-goarch', target['goarch'],
                              '-cc', os.path.join(standalone_ndk_dir, 'bin', target['cc']),
                              '-version', syncthingVersion
                          ] + pkg_argument + ['-no-upgrade', 'build'], env=environ, cwd=syncthing_dir)

    # Determine path of source artifact
    source_artifact = os.path.join(syncthing_dir, 'syncthing')

    # Patch artifact to work around golang bug.
    # See issues:
    # - https://github.com/Catfriend1/syncthing-android/issues/370
    # - https://github.com/golang/go/issues/29674
    if 'patch_underaligned_tls' in target:
        artifact_patch_underaligned_tls(source_artifact)

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
