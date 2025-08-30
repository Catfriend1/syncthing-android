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

def get_go_version_from_dockerfile():
    """Read GO_VERSION from docker/Dockerfile"""
    dockerfile_path = os.path.join(os.path.dirname(os.path.dirname(os.path.realpath(__file__))), 'docker', 'Dockerfile')
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

GO_VERSION = get_go_version_from_dockerfile()
GO_EXPECTED_SHASUM_LINUX = '2852af0cb20a13139b3448992e69b868e50ed0f8a1e5940ee1de9e19a123b613'
GO_EXPECTED_SHASUM_WINDOWS = '89efb4f9b30812eee083cc1770fdd2913c14d301064f6454851428f9707d190b'

NDK_VERSION = 'r28'
NDK_EXPECTED_SHASUM_LINUX = '894f469c5192a116d21f412de27966140a530ebc'
NDK_EXPECTED_SHASUM_WINDOWS = 'f79a00c721dc5c15b2bf093d7bb2af96496a42b2'

# The values here must correspond with those in ../docker/prebuild.sh
BUILD_TARGETS = [
    {
        'arch': 'arm',
        'goarch': 'arm',
        'jni_dir': 'armeabi-v7a',
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

# If building locally for Android studio tests, build only required arch.
if os.environ.get('BUILD_FOR_AVD', '') == '1':
    BUILD_TARGETS = [t for t in BUILD_TARGETS if t['arch'] in ('arm64', 'x86_64')]

def fail(message, *args, **kwargs):
    print((message % args).format(**kwargs))
    sys.exit(1)


def get_min_sdk(project_dir):
    with open(os.path.join(project_dir, 'gradle', 'libs.versions.toml')) as file_handle:
        for line in file_handle:
            tokens = list(filter(None, line.split('"')))
            if len(tokens) == 3 and tokens[0] == 'min-sdk = ':
                return int(tokens[1])

    fail('Failed to find min-sdk')

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

def get_go_version(go_binary):
    """Get the version of a Go binary"""
    try:
        result = subprocess.check_output([go_binary, 'version'], stderr=subprocess.STDOUT, timeout=10)
        # Parse "go version go1.25.0 linux/amd64" -> "1.25.0"
        version_line = result.decode().strip()
        parts = version_line.split()
        if len(parts) >= 3 and parts[2].startswith('go'):
            return parts[2][2:]  # Remove "go" prefix
        return None
    except:
        return None

def install_go():
    import os
    import tarfile
    import shutil
    import subprocess

    if sys.version_info[0] >= 3:
        from urllib.request import urlretrieve
    else:
        from urllib import urlretrieve

    if not os.path.isdir(prerequisite_tools_dir):
        os.makedirs(prerequisite_tools_dir)

    go_build_dir = os.path.join(prerequisite_tools_dir, 'go')
    go_bin_path = os.path.join(go_build_dir, 'bin')
    
    # Check if we already have a built Go with correct version
    built_go = os.path.join(go_bin_path, 'go')
    if os.path.isfile(built_go):
        built_version = get_go_version(built_go)
        if built_version == GO_VERSION:
            print('Go', GO_VERSION, 'already built at:', go_bin_path)
            os.environ["PATH"] = go_bin_path + os.pathsep + os.environ["PATH"]
            return
        else:
            print('Built Go version', built_version, 'does not match required', GO_VERSION, '- rebuilding')

    # Find system Go for bootstrap (assume it exists as per fdroid environment)
    system_go = which("go")
    if not system_go:
        fail('Error: No system Go found for bootstrap. golang-go package should be installed in fdroid environment.')
    
    system_version = get_go_version(system_go)
    print('Found system Go version:', system_version, 'at:', system_go)
    print('Required Go version:', GO_VERSION)
    
    if system_version == GO_VERSION:
        print('System Go version matches required version. No build needed.')
        return
    
    print('System Go version differs from required. Building Go', GO_VERSION, 'from source using system Go as bootstrap...')

    # Download Go source code from GitHub
    go_source_url = 'https://github.com/golang/go/archive/go' + GO_VERSION + '.tar.gz'
    go_source_tar = os.path.join(prerequisite_tools_dir, 'go-source-' + GO_VERSION + '.tar.gz')
    
    if not os.path.isfile(go_source_tar):
        print('Downloading Go source to:', go_source_tar)
        urlretrieve(go_source_url, go_source_tar)
    print('Downloaded Go source to:', go_source_tar)

    # Extract Go source
    go_extract_dir = os.path.join(prerequisite_tools_dir, 'go-go' + GO_VERSION)
    if not os.path.isdir(go_extract_dir):
        print("Extracting Go source ...")
        with tarfile.open(go_source_tar, 'r:gz') as tar:
            tar.extractall(prerequisite_tools_dir)

    # Prepare the Go build directory
    if os.path.isdir(go_build_dir):
        shutil.rmtree(go_build_dir)
    
    # Copy source to build directory
    shutil.copytree(go_extract_dir, go_build_dir)

    # Build Go from source using system Go as bootstrap
    print('Building Go from source using system Go as bootstrap...')
    build_env = os.environ.copy()
    build_env['GOROOT_BOOTSTRAP'] = os.path.dirname(os.path.dirname(system_go))
    build_env['GOROOT'] = go_build_dir
    
    if sys.platform == 'win32':
        build_script = os.path.join(go_build_dir, 'src', 'make.bat')
        subprocess.check_call([build_script], env=build_env, cwd=os.path.join(go_build_dir, 'src'))
    else:
        build_script = os.path.join(go_build_dir, 'src', 'make.bash')
        # Make sure the script is executable
        os.chmod(build_script, 0o755)
        subprocess.check_call(['bash', build_script], env=build_env, cwd=os.path.join(go_build_dir, 'src'))

    # Verify the build
    if not os.path.isfile(built_go):
        fail('Go build failed: go binary not found at ' + built_go)
    
    # Test the built Go and verify version
    try:
        result = subprocess.check_output([built_go, 'version'], stderr=subprocess.STDOUT)
        print('Successfully built Go:', result.decode().strip())
        
        built_version = get_go_version(built_go)
        if built_version != GO_VERSION:
            fail('Built Go version ' + str(built_version) + ' does not match expected ' + GO_VERSION)
        
    except Exception as e:
        fail('Built Go is not working: ' + str(e))

    # Add built Go to PATH and set GOROOT
    print('Adding built Go to PATH:', go_bin_path)
    print('Setting GOROOT to:', go_build_dir)
    os.environ["PATH"] = go_bin_path + os.pathsep + os.environ["PATH"]
    os.environ["GOROOT"] = go_build_dir


def write_file(fullfn, text):
    with open(fullfn, 'w') as hFile:
        hFile.write(text + '\n')


def get_ndk_ready():
    if os.environ.get('ANDROID_NDK_HOME', ''):
        return
    ndk_env_vars_defined = True
    if not os.environ.get('NDK_VERSION', ''):
        print('NDK_VERSION is NOT defined.')
        ndk_env_vars_defined = False
    if not os.environ.get('ANDROID_HOME', ''):
        print('ANDROID_HOME is NOT defined.')
        ndk_env_vars_defined = False
    if not ndk_env_vars_defined:
        print('ANDROID_NDK_HOME or NDK_VERSION and ANDROID_HOME environment variable must be defined.')
        install_ndk()
        return
    os.environ["ANDROID_NDK_HOME"] = os.path.join(os.environ['ANDROID_HOME'], 'ndk', os.environ['NDK_VERSION'])
    return


def install_ndk():
    import hashlib
    import os
    import zipfile

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
        if sys.platform == 'win32':
            zip = zipfile.ZipFile(zip_fullfn, 'r')
            zip.extractall(prerequisite_tools_dir)
            zip.close()
        else:
            from subprocess import STDOUT
            subprocess.check_output(['unzip', '-q', zip_fullfn, '-d', prerequisite_tools_dir], stderr=STDOUT)

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
    fail('Error: git is not available on the PATH.')

print('git_bin=\'' + git_bin + '\'')

# Check if go is available and has correct version.
go_bin = which("go");
if not go_bin:
    print('Info: go is not available on the PATH. Trying install_go')
    install_go();
    # Retry: Check if go is available.
    go_bin = which("go");
    if not go_bin:
        fail('Error: go is not available on the PATH.')
else:
    # Check if the available Go has the correct version
    current_version = get_go_version(go_bin)
    print('Found Go version:', current_version, 'at:', go_bin)
    print('Required Go version:', GO_VERSION)
    if current_version != GO_VERSION:
        print('Go version mismatch. Installing correct version...')
        install_go()
        # Update go_bin to point to the newly built Go
        go_bin = which("go")
        if not go_bin:
            fail('Error: go is not available on the PATH after installation.')

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

if os.environ.get('CLEANUP_BEFORE_BUILD', '') == "1":
    print('Cleaning go-build cache')
    subprocess.check_call([go_bin, 'clean', '-cache'], cwd=syncthing_dir)

print('Building syncthing version', syncthingVersion);
print('SOURCE_DATE_EPOCH=[' + os.environ['SOURCE_DATE_EPOCH'] + ']');
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

    # See why "-checklinkname=0" is required: https://github.com/wlynxg/anet?tab=readme-ov-file#how-to-build-with-go-1230-or-later 
    environ = os.environ.copy()
    environ.update({
        'GOPATH': module_dir,
        'GO111MODULE': 'on',
        'CGO_ENABLED': '1',
        'EXTRA_LDFLAGS': '-checklinkname=0',
    })

    subprocess.check_call([go_bin, 'mod', 'download'], cwd=syncthing_dir)
    subprocess.check_call(
                              [go_bin, 'version'],
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
